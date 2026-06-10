<template>
  <view class="page-shell">
    <view class="safe-area">
      <view class="top-row">
        <view>
          <text class="text-muted">任务中心</text>
          <text class="title-xl">拆小一点，更容易完成</text>
        </view>
        <button class="glass-button add-btn" @tap="openCreator">新建任务</button>
      </view>

      <view class="category-row">
        <text
          v-for="item in categories"
          :key="item"
          class="pill category-pill"
          :class="{ active: activeCategory === item }"
          @tap="activeCategory = item"
        >
          {{ item }}
        </text>
      </view>

      <view class="section-head">
        <text class="section-title">我的任务</text>
        <text class="text-muted small">来自 /tasks</text>
      </view>

      <view v-if="loading" class="glass-card empty-card">
        <text>正在同步后端任务...</text>
      </view>

      <view v-else-if="filteredTasks.length === 0" class="glass-card empty-card">
        <text>还没有这个分类的任务</text>
        <text class="text-muted">点右上角新建任务，可以 AI 拆解，也可以手动填写。</text>
      </view>

      <view v-else class="task-list">
        <view
          v-for="task in filteredTasks"
          :key="task.id"
          class="glass-card task-card"
          @tap="openTask(task)"
        >
          <view class="task-head">
            <view>
              <text class="task-title">{{ task.title }}</text>
              <text class="text-muted">{{ task.category }} · {{ task.updatedAt || "刚刚更新" }}</text>
            </view>
            <text class="pill">{{ task.subtaskCount || 0 }} 步</text>
          </view>
          <view class="task-bottom">
            <view class="progress-track">
              <view class="progress-fill" :style="{ width: clampProgress(task.totalProgress) + '%' }"></view>
            </view>
            <text>{{ formatProgress(task.totalProgress) }}%</text>
          </view>
        </view>
      </view>

      <view class="tabbar-space"></view>
    </view>

    <view v-if="showCreator" class="modal-mask" @tap="closeCreator">
      <view class="creator-dialog glass-card" @tap.stop>
        <view class="modal-head">
          <view>
            <text class="modal-eyebrow">新建任务</text>
            <text class="modal-title">{{ creatorMode === "ai" ? "让 AI 拆解目标" : "手动添加任务" }}</text>
          </view>
          <button class="close-btn" @tap="closeCreator">×</button>
        </view>

        <view class="mode-switch">
          <view
            class="mode-item"
            :class="{ active: creatorMode === 'ai' }"
            @tap="creatorMode = 'ai'"
          >
            <text>AI 拆解</text>
            <text>自动生成子任务</text>
          </view>
          <view
            class="mode-item"
            :class="{ active: creatorMode === 'manual' }"
            @tap="creatorMode = 'manual'"
          >
            <text>手动添加</text>
            <text>自己填写任务</text>
          </view>
        </view>

        <view class="form-block">
          <text class="field-label">{{ creatorMode === "ai" ? "想完成什么长期目标？" : "任务名称" }}</text>
          <input
            v-model="newTitle"
            class="glass-input creator-input"
            maxlength="100"
            :placeholder="creatorMode === 'ai' ? '例如：系统学习 Spring Boot' : '例如：完成数据库课设答辩'"
          />
        </view>

        <view class="form-block">
          <text class="field-label">选择分类</text>
          <view class="select-row">
            <text
              v-for="item in selectableCategories"
              :key="item"
              class="pill"
              :class="{ active: newCategory === item }"
              @tap="newCategory = item"
            >
              {{ item }}
            </text>
          </view>
        </view>

        <view class="backend-note">
          <text v-if="creatorMode === 'ai'">
            将调用后端 /tasks/deconstruct，保存主任务并生成 AI 子任务。
          </text>
          <text v-else>
            手动模式下，你可以自定义主任务和每一个具体的执行步骤。
          </text>
        </view>

        <view v-if="creatorMode === 'manual'" class="manual-subtasks-editor">
          <view class="field-label-row">
            <text class="field-label">拆解步骤 (至少一个)</text>
            <text class="add-subtask-link" @tap="addManualSubtask">+ 添加步骤</text>
          </view>
          <view
            v-for="(st, index) in manualSubtasks"
            :key="index"
            class="manual-subtask-row"
          >
            <input
              v-model="st.content"
              class="glass-input st-content-input"
              placeholder="步骤描述"
            />
            <input
              v-model="st.weight"
              type="number"
              class="glass-input st-weight-input"
              placeholder="权重(%)"
            />
            <text class="remove-st-icon" @tap="removeManualSubtask(index)">×</text>
          </view>
        </view>

        <view v-if="aiResult" class="ai-result">
          <text class="ai-title">{{ creatorMode === "ai" ? "AI 建议" : "已保存任务" }}</text>
          <text class="text-muted ai-copy">{{ aiResult.aiSuggestion }}</text>
          <view class="mini-subtasks">
            <text v-for="item in aiResult.subTasks" :key="item.content">· {{ item.content }}</text>
          </view>
        </view>

        <button class="glass-button create-btn" :loading="creating" @tap="createTask">
          {{ creatorMode === "ai" ? "生成并保存" : "保存任务" }}
        </button>
      </view>
    </view>

    <LifeTabBar current="tasks" />
  </view>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import LifeTabBar from "../../components/LifeTabBar.vue";
import { createTaskByAI, createTaskManually, getTasks } from "../../services/api";
import { TASK_CATEGORIES } from "../../services/config";

const categories = ["全部", ...TASK_CATEGORIES];
const selectableCategories = TASK_CATEGORIES;
const activeCategory = ref("全部");
const tasks = ref([]);
const loading = ref(false);
const showCreator = ref(false);
const creatorMode = ref("ai");
const creating = ref(false);
const newTitle = ref("");
const newCategory = ref("学习");
const aiResult = ref(null);
const manualSubtasks = ref([{ content: "", weight: "" }]);

const filteredTasks = computed(() => {
  if (activeCategory.value === "全部") return tasks.value;
  return tasks.value.filter((task) => task.category === activeCategory.value);
});

function formatProgress(value) {
  return Number(value || 0).toFixed(Number(value || 0) % 1 === 0 ? 0 : 1);
}

function clampProgress(value) {
  return Math.max(0, Math.min(100, Number(value || 0)));
}

async function loadTasks() {
  loading.value = true;
  try {
    const data = await getTasks();
    tasks.value = Array.isArray(data) ? data : [];
  } catch (error) {
    console.warn("load tasks failed", error);
  } finally {
    loading.value = false;
  }
}

function openTask(task) {
  const query = `id=${task.id}&title=${encodeURIComponent(task.title)}&category=${encodeURIComponent(task.category)}&progress=${task.totalProgress || 0}`;
  uni.navigateTo({ url: `/pages/task-detail/task-detail?${query}` });
}

function openCreator() {
  aiResult.value = null;
  showCreator.value = true;
}

function closeCreator() {
  if (creating.value) return;
  showCreator.value = false;
  manualSubtasks.value = [{ content: "", weight: "" }];
}

function addManualSubtask() {
  manualSubtasks.value.push({ content: "", weight: "" });
}

function removeManualSubtask(index) {
  if (manualSubtasks.value.length <= 1) {
    uni.showToast({ title: "至少需要一个步骤", icon: "none" });
    return;
  }
  manualSubtasks.value.splice(index, 1);
}

function appendCreatedTask(result, title, category) {
  const createdId = result && result.taskId;
  if (!createdId || tasks.value.some((task) => String(task.id) === String(createdId))) return;
  tasks.value = [
    {
      id: createdId,
      title,
      category,
      totalProgress: 0,
      subtaskCount: result.subTasks ? result.subTasks.length : 0,
      updatedAt: "刚刚",
    },
    ...tasks.value,
  ];
}

async function createTask() {
  const title = newTitle.value.trim();
  if (!title) {
    uni.showToast({ title: "先写一个任务名称", icon: "none" });
    return;
  }
  if (!TASK_CATEGORIES.includes(newCategory.value)) {
    uni.showToast({ title: "请选择后端支持的分类", icon: "none" });
    return;
  }

  creating.value = true;
  try {
    let result;
    if (creatorMode.value === "ai") {
      result = await createTaskByAI(title, newCategory.value);
    } else {
      // 校验手动输入的子任务
      const validSubtasks = manualSubtasks.value.filter(st => st.content.trim());
      if (validSubtasks.length === 0) {
        uni.showToast({ title: "请至少填写一个子任务步骤", icon: "none" });
        creating.value = false;
        return;
      }
      
      const data = {
        title,
        category: newCategory.value,
        subTasks: validSubtasks.map(st => ({
          content: st.content.trim(),
          weight: st.weight ? Number(st.weight) / 100 : null // 转换为 0-1 的小数
        }))
      };
      result = await createTaskManually(data);
    }
    
    aiResult.value = result;
    await loadTasks();
    appendCreatedTask(result, title, newCategory.value);
    uni.showToast({
      title: creatorMode.value === "ai" ? "AI 已生成任务" : "任务已保存",
      icon: "none",
    });
    newTitle.value = "";
    activeCategory.value = "全部";
  } catch (error) {
    console.warn("create task failed", error);
  } finally {
    creating.value = false;
  }
}

onMounted(loadTasks);
</script>

<style scoped>
.top-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24rpx;
}

.top-row view {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.add-btn {
  width: 174rpx;
  height: 72rpx;
  line-height: 72rpx;
  font-size: 24rpx;
}

.category-row,
.select-row {
  margin-top: 34rpx;
  display: flex;
  gap: 14rpx;
  flex-wrap: wrap;
}

.category-pill.active,
.select-row .active {
  background: rgba(255, 255, 255, 0.66);
  color: #145f8e;
}

.small {
  font-size: 22rpx;
}

.task-list {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

.task-card {
  display: flex;
  flex-direction: column;
  gap: 26rpx;
}

.task-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24rpx;
}

.task-head view {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 10rpx;
}

.task-title {
  color: #2d3e50;
  font-size: 30rpx;
  font-weight: 900;
  line-height: 1.35;
}

.task-bottom {
  display: grid;
  grid-template-columns: 1fr 84rpx;
  align-items: center;
  gap: 18rpx;
  color: #236894;
  font-size: 26rpx;
  font-weight: 900;
}

.empty-card {
  min-height: 180rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12rpx;
  text-align: center;
}

.modal-mask {
  position: fixed;
  inset: 0;
  z-index: 40;
  padding: 160rpx 34rpx 48rpx;
  box-sizing: border-box;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  background: rgba(42, 137, 196, 0.24);
  backdrop-filter: blur(12rpx);
}

.creator-dialog {
  width: 100%;
  max-height: 82vh;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}

.modal-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24rpx;
}

.modal-head view {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.modal-eyebrow {
  color: rgba(45, 62, 80, 0.56);
  font-size: 22rpx;
  font-weight: 700;
}

.modal-title {
  color: #2d3e50;
  font-size: 38rpx;
  font-weight: 900;
  line-height: 1.25;
}

.close-btn {
  width: 62rpx;
  height: 62rpx;
  border-radius: 50%;
  color: #236894;
  background: rgba(255, 255, 255, 0.42);
  font-size: 42rpx;
  line-height: 56rpx;
}

.mode-switch {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 14rpx;
}

.mode-item {
  min-height: 108rpx;
  padding: 20rpx;
  border-radius: 28rpx;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 8rpx;
  color: rgba(45, 62, 80, 0.58);
  background: rgba(255, 255, 255, 0.28);
  border: 2rpx solid rgba(255, 255, 255, 0.42);
}

.mode-item.active {
  color: #236894;
  background: rgba(255, 255, 255, 0.62);
  box-shadow: inset 0 2rpx 0 rgba(255, 255, 255, 0.78);
}

.mode-item text:first-child {
  font-size: 27rpx;
  font-weight: 900;
}

.mode-item text:last-child {
  font-size: 21rpx;
}

.form-block {
  display: flex;
  flex-direction: column;
  gap: 14rpx;
}

.field-label {
  color: #236894;
  font-size: 24rpx;
  font-weight: 900;
}

.creator-input {
  height: 92rpx;
}

.backend-note {
  padding: 18rpx 22rpx;
  border-radius: 24rpx;
  color: rgba(45, 62, 80, 0.56);
  font-size: 21rpx;
  line-height: 1.45;
  background: rgba(255, 255, 255, 0.34);
}

.create-btn {
  height: 88rpx;
  line-height: 88rpx;
  font-size: 26rpx;
}

.ai-result {
  padding-top: 20rpx;
  border-top: 2rpx solid rgba(255, 255, 255, 0.38);
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

.ai-title {
  color: #236894;
  font-size: 26rpx;
  font-weight: 900;
}

.ai-copy {
  font-size: 24rpx;
  line-height: 1.5;
}

.mini-subtasks {
  display: flex;
  flex-direction: column;
  gap: 8rpx;
  color: rgba(45, 62, 80, 0.66);
  font-size: 23rpx;
  line-height: 1.45;
}
.field-label-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.add-subtask-link {
  font-size: 24rpx;
  color: #236894;
  font-weight: 700;
}

.manual-subtasks-editor {
  display: flex;
  flex-direction: column;
  gap: 18rpx;
}

.manual-subtask-row {
  display: flex;
  align-items: center;
  gap: 14rpx;
}

.st-content-input {
  flex: 1;
  height: 80rpx;
}

.st-weight-input {
  width: 140rpx;
  height: 80rpx;
  text-align: center;
}

.remove-st-icon {
  font-size: 40rpx;
  color: #ff5a5f;
  padding: 10rpx;
}
</style>
