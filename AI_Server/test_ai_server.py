import json
import unittest
from unittest.mock import patch
from ai_server import app, validate_decomposition


def example(steps):
    return {"goals": [{"title": "学习目标", "sub_tasks": [
        {"step": step, "weight": 1 / len(steps)} for step in steps
    ]}]}


class DecompositionTests(unittest.TestCase):
    def setUp(self):
        self.client = app.test_client()
        self.valid = example(["绘制TCP/IP分层图并说明各层协议职责", "综合分析网页访问抓包并通过分层协议与子网计算测验"])

    @patch("ai_server.call_llm")
    def test_refinement_context_and_requested_count_are_forwarded(self, llm):
        llm.return_value = json.dumps(self.valid)
        response = self.client.post("/ai/deconstruct", json={"title": "计算机网络", "guidance": "重点抓包",
            "previous_steps": ["学习IP寻址"], "step_count": 2}).get_json()
        self.assertEqual(200, response["code"])
        self.assertIn("重点抓包", llm.call_args.args[0])
        self.assertIn("学习IP寻址", llm.call_args.args[0])

    @patch("ai_server.call_llm")
    def test_missing_final_acceptance_and_wrong_count_are_rejected(self, llm):
        llm.return_value = json.dumps(example(["练习IP寻址和子网计算", "学习TCP可靠传输机制"]))
        self.assertEqual(502, self.client.post("/ai/deconstruct", json={"title": "计算机网络"}).get_json()["code"])
        llm.return_value = json.dumps(self.valid)
        self.assertEqual(502, self.client.post("/ai/deconstruct", json={"title": "计算机网络", "step_count": 3}).get_json()["code"])

    @patch("ai_server.call_llm")
    def test_ten_steps_supported_and_eleven_rejected(self, llm):
        steps = example([f"完成网络协议实验{i}并验证对应协议行为" for i in range(10)])
        llm.return_value = json.dumps(steps)
        result = self.client.post("/ai/deconstruct", json={"title": "网络课程", "step_count": 10,
            "previous_steps": [step["step"] for step in steps["goals"][0]["sub_tasks"]]}).get_json()
        self.assertEqual(200, result["code"])
        self.assertEqual(10, len(result["data"]["goals"][0]["sub_tasks"]))
        self.assertEqual(400, self.client.post("/ai/deconstruct", json={"title": "网络课程", "step_count": 11}).get_json()["code"])

    @patch("ai_server.call_llm")
    def test_report_receives_separate_achievement_and_attendance(self, llm):
        llm.return_value = json.dumps({"title": "学习总结", "ai_summary": "提前完成"})
        self.client.post("/ai/report", json={"logs": [], "plan_outcomes": [{"title": "Spring Boot",
            "status": "COMPLETED", "target_days": 3, "actual_check_in_days": 1}]})
        prompt = llm.call_args.args[0]
        self.assertIn('"actual_check_in_days": 1', prompt)
        self.assertIn("不能判为学习未达标", prompt)

    @patch("ai_server.call_llm")
    def test_specific_result_is_returned_without_replacement(self, llm):
        llm.return_value = json.dumps(self.valid)
        response = self.client.post("/ai/deconstruct", json={"title": "学习计算机网络"}).get_json()
        self.assertEqual(200, response["code"])
        self.assertEqual(self.valid, response["data"])
        self.assertEqual(1, llm.call_count)

    @patch("ai_server.call_llm")
    def test_generic_template_gets_one_correction_attempt(self, llm):
        llm.side_effect = [json.dumps(example(["阅读基础教材", "完成在线课程"])), json.dumps(self.valid)]
        result = self.client.post("/ai/deconstruct", json={"title": "学习计算机网络"}).get_json()
        self.assertEqual(200, result["code"])
        self.assertEqual(2, llm.call_count)
        self.assertIn("步骤过于通用", llm.call_args.args[0])

    @patch("ai_server.call_llm")
    def test_repeated_generic_result_fails_instead_of_using_fake_steps(self, llm):
        llm.return_value = json.dumps(example(["阅读官方文档", "完成入门教程"]))
        result = self.client.post("/ai/deconstruct", json={"title": "学习Spring Boot"}).get_json()
        self.assertEqual(502, result["code"])
        self.assertIsNone(result["data"])
        self.assertEqual(2, llm.call_count)

    @patch("ai_server.call_llm")
    def test_invalid_json_is_retried_once(self, llm):
        llm.side_effect = ["not json", json.dumps(self.valid)]
        self.assertEqual(200, self.client.post("/ai/deconstruct", json={"title": "学习计算机网络"}).get_json()["code"])

    def test_invalid_weights_and_duplicate_steps_are_rejected(self):
        invalid = example(["练习TCP三次握手分析", "练习TCP三次握手分析"])
        with self.assertRaises(ValueError):
            validate_decomposition(invalid)
        invalid = example(["练习TCP三次握手分析", "练习IP子网计算"])
        invalid["goals"][0]["sub_tasks"][0]["weight"] = -0.1
        with self.assertRaises(ValueError):
            validate_decomposition(invalid)

    def test_relative_weights_are_normalized_without_rewriting_content(self):
        result = example(["练习TCP三次握手分析", "练习IP子网计算"])
        result["goals"][0]["sub_tasks"][0]["weight"] = 0.1
        normalized = validate_decomposition(result)["goals"][0]["sub_tasks"]
        self.assertAlmostEqual(1, sum(step["weight"] for step in normalized), places=4)
        self.assertEqual("练习TCP三次握手分析", normalized[0]["step"])
        self.assertGreater(normalized[1]["weight"], normalized[0]["weight"])


if __name__ == "__main__":
    unittest.main()
