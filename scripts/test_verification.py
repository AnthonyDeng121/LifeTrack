import json
from pathlib import Path
import tempfile
import threading
import subprocess
import unittest
from unittest.mock import patch
from http.server import BaseHTTPRequestHandler,ThreadingHTTPServer
from verify_course import junit_cases,passed,REQUIRED_CHECKS,check_evidence,write_json,digest,python_test_count

class EvidenceTest(unittest.TestCase):
    def test_no_cases_or_failed_command_can_never_be_pass(self):
        commands=[{'name':name,'exitCode':0,'testCount':1} for name in REQUIRED_CHECKS]
        self.assertFalse(passed(commands,[]))
        self.assertFalse(passed(commands[:-1],[{'outcome':'passed'}]))
        self.assertFalse(passed(commands,[{'outcome':'skipped'}]))
        self.assertTrue(passed(commands,[{'outcome':'passed'}]))
        commands[0]['exitCode']=1
        self.assertFalse(passed(commands,[{'outcome':'passed'}]))

    def test_python_zero_tests_is_failure_even_with_exit_zero(self):
        self.assertEqual(10,python_test_count(b'Ran 10 tests in 0.1s\nOK'))
        self.assertEqual(1,python_test_count(b'Ran 1 test in 0.1s\nOK'))
        self.assertEqual(0,python_test_count(b'Ran 0 tests in 0.0s\nNO TESTS RAN'))
        commands=[{'name':name,'exitCode':0,'testCount':1} for name in REQUIRED_CHECKS]
        next(c for c in commands if c['name']=='python-ai')['testCount']=0
        self.assertFalse(passed(commands,[{'outcome':'passed'}]))

    def test_evidence_rejects_modified_files_and_changed_sources(self):
        with tempfile.TemporaryDirectory() as tmp:
            folder=Path(tmp)
            write_json(folder/'summary.json',{'runId':'fixture','passed':True,'commands':[{'name':n,'exitCode':0,'testCount':1} for n in REQUIRED_CHECKS]})
            write_json(folder/'cases.json',[{'outcome':'passed'}])
            write_json(folder/'sources.json',{'example.java':'hash'})
            write_json(folder/'checksums.json',{p.name:digest(p.read_bytes()) for p in folder.glob('*.json')})
            with patch('verify_course.source_manifest',return_value={'example.java':'hash'}):
                self.assertTrue(check_evidence(folder)['passed'])
            with patch('verify_course.source_manifest',return_value={'example.java':'changed'}):
                with self.assertRaisesRegex(ValueError,'source files'):check_evidence(folder)
            (folder/'cases.json').write_text('[]')
            with self.assertRaisesRegex(ValueError,'checksum'):check_evidence(folder)

    def test_junit_reports_individual_failures_without_copying_secrets(self):
        with tempfile.TemporaryDirectory() as tmp:
            Path(tmp,'TEST-demo.xml').write_text('<testsuite><testcase classname="demo" name="a" time="0.1"/><testcase classname="demo" name="b"><failure message="secret">secret body</failure></testcase></testsuite>')
            cases=junit_cases(tmp)
            self.assertEqual(['passed','failure'],[c['outcome'] for c in cases])
            self.assertNotIn('secret',json.dumps(cases))

class HttpHelperTest(unittest.TestCase):
    def test_http_and_business_code_must_agree(self):
        class Handler(BaseHTTPRequestHandler):
            def log_message(self,*args): pass
            def do_GET(self):
                status,code=map(int,self.path.strip('/').split('/'))
                payload=json.dumps({'code':code,'message':'中文异常与成功提示','data':None},ensure_ascii=False).encode()
                self.send_response(status);self.send_header('Content-Type','application/json');self.send_header('Content-Length',str(len(payload)));self.end_headers();self.wfile.write(payload)
        server=ThreadingHTTPServer(('127.0.0.1',0),Handler)
        worker=threading.Thread(target=server.serve_forever,daemon=True);worker.start()
        root=Path(__file__).resolve().parents[1]
        module=root/'backend/scripts/AcceptanceHttp.psm1'
        script="""
Import-Module '%s' -Force
$base='http://127.0.0.1:%s'
foreach ($pair in @('200/200','400/400','409/409','503/503')) {
  $result=Invoke-LifeTrackApi -BaseUrl $base -Method GET -Path "/$pair"
  if ($result.code -ne [int]$pair.Split('/')[0]) { throw 'Wrong fixture response' }
  if ($result.message -ne '中文异常与成功提示') { throw 'UTF-8 JSON message was corrupted' }
}
foreach ($pair in @('200/409','409/200')) {
  $rejected=$false
  try { Invoke-LifeTrackApi -BaseUrl $base -Method GET -Path "/$pair" | Out-Null } catch { $rejected=$true }
  if (-not $rejected) { throw 'A status mismatch was accepted' }
}
""" % (str(module).replace("'","''"),server.server_port)
        try:
            result=subprocess.run(['powershell','-NoProfile','-NonInteractive','-Command',script],capture_output=True,timeout=45)
            self.assertEqual(0,result.returncode,result.stderr.decode(errors='replace'))
        finally: server.shutdown();server.server_close();worker.join()
