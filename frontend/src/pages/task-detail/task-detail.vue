<template>
  <view class="page-shell">
    <view class="safe-area">
      <view class="detail-top">
        <button class="back-btn glass-button" @tap="goBack">‹</button>
        <view>
          <text class="text-muted">{{ task.category }}</text>
          <text class="title-lg">{{ task.title }}</text>
        </view>
      </view>

      <view class="glass-card progress-card">
        <view class="progress-circle">
          <text>{{ formatProgress(task.totalProgress) }}%</text>
          <text>总进度</text>
        </view>
        <view class="progress-copy">
          <text class="section-title">子任务会驱动总进度</text>
          <text class="text-muted">
            点击“完成”会调用后端 /tasks/subtasks/{id}/complete，由后端更新子任务和主任务进度。
          </text>
        </view>
      </view>

      <view class="section-head">
        <text class="section-title">拆解步骤</text>
        <text class="text-muted small">来自 /tasks/{id}/subtasks</text>
      </view>

      <view v-if="loading" class="glass-card empty-card">
        <text>正在加载子任务...</text>
      </view>

      <view v-else class="subtask-list">
        <view
          v-for="item in subtasks"
          :key="item.id"
          class="glass-card subtask-card"
          :class="{ done: isDone(item) }"
        >
          <view class="check-dot">
            <text v-if="isDone(item)">✓</text>
          </view>
          <view class="subtask-main">
            <text class="subtask-title">{{ item.content }}</text>
            <view class="subtask-meta">
              <text>权重 {{ formatWeight(item.weight) }}%</text>
              <text>进度 {{ formatProgress(item.currentProgress) }}%</text>
            </view>
            <view class="progress-track">
              <view class="progress-fill" :style="{ width: clampProgress(item.currentProgress) + '%' }"></view>
            </view>
          </view>
          <view class="subtask-ops">
            <button
              class="glass-button complete-btn"
              :disabled="isDone(item)"
              @tap.stop="finishSubtask(item)"
            >
              {{ isDone(item) ? "已完成" : "完成" }}
            </button>
            <text class="edit-icon" @tap.stop="openEditDialog(item)">✎</text>
            <text class="delete-icon" @tap.stop="handleDeleteSubtask(item)">×</text>
          </view>
        </view>
      </view>

      <view v-if="showEditDialog" class="modal-mask" @tap="closeEditDialog">
        <view class="edit-dialog glass-card" @tap.stop>
          <view class="modal-head">
            <text class="modal-title">编辑子任务</text>
            <button class="close-btn" @tap="closeEditDialog">×</button>
          </view>
          
          <view class="form-block">
            <text class="field-label">任务内容</text>
            <input
              v-model="editingSubtask.content"
              class="glass-input edit-input"
              placeholder="请输入子任务描述"
            />
          </view>

          <view class="form-block">
            <text class="field-label">任务权重 (%)</text>
            <input
              v-model="editingSubtask.weight"
              type="number"
              class="glass-input edit-input"
              placeholder="请输入权重占比"
            />
            <text class="text-muted small">修改权重后，其他子任务的权重将自动按比例重新分配。</text>
          </view>

          <button class="glass-button save-btn" :loading="saving" @tap="saveSubtask">保存修改</button>
        </view>
      </view>

      <button class="glass-button record-action" @tap="goRecord">用行为记录推进任务</button>
      <button class="glass-button delete-task-btn" @tap="handleDeleteTask">放弃这个目标</button>
      <view class="tabbar-space"></view>
    </view>
  </view>
</template>

<script setup>
import { ref } from "vue";
import { onLoad } from "@dcloudio/uni-app";
import { completeSubtask, getSubtasks, deleteTask, deleteSubtask, updateSubtask } from "../../services/api";

const task = ref({
  id: "",
  title: "任务详情",
  category: "任务",
  totalProgress: 0,
});
const subtasks = ref([]);
const loading = ref(false);
const showEditDialog = ref(false);
const saving = ref(false);
const editingSubtask = ref({
  id: "",
  content: "",
  weight: ""
});

function formatProgress(value) {
  return Number(value || 0).toFixed(Number(value || 0) % 1 === 0 ? 0 : 1);
}

function clampProgress(value) {
  return Math.max(0, Math.min(100, Number(value || 0)));
}

function formatWeight(value) {
  const next = Number(value || 0);
  return next <= 1 ? Math.round(next * 100) : Math.round(next);
}

function isDone(item) {
  return item.isCompleted === 1 || item.isCompleted === true || Number(item.currentProgress || 0) >= 100;
}

function goBack() {
  uni.navigateBack({
    fail() {
      uni.redirectTo({ url: "/pages/tasks/tasks" });
    },
  });
}

function goRecord() {
  uni.redirectTo({ url: "/pages/record/record" });
}

async function loadSubtasks() {
  if (!task.value.id) return;
  loading.value = true;
  try {
    const data = await getSubtasks(task.value.id);
    subtasks.value = Array.isArray(data) ? data : [];
  } catch (error) {
    console.warn("load subtasks failed", error);
  } finally {
    loading.value = false;
  }
}

async function finishSubtask(item) {
  if (isDone(item)) return;
  try {
    await completeSubtask(item.id);
    item.isCompleted = 1;
    item.currentProgress = 100;
    uni.showToast({ title: "后端已同步完成状态", icon: "none" });
  } catch (error) {
    console.warn("complete subtask failed", error);
  }
}

async function handleDeleteTask() {
  uni.showModal({
    title: "确定放弃？",
    content: "删除后该目标的所有记录都将消失。",
    success: async (res) => {
      if (res.confirm) {
        try {
          await deleteTask(task.value.id);
          uni.showToast({ title: "任务已删除", icon: "none" });
          setTimeout(() => {
            uni.redirectTo({ url: "/pages/tasks/tasks" });
          }, 800);
        } catch (error) {
          console.warn("delete task failed", error);
        }
      }
    },
  });
}

async function handleDeleteSubtask(item) {
  uni.showModal({
    title: "删除子任务？",
    content: "删除后权重将自动分配给剩余步骤。",
    success: async (res) => {
      if (res.confirm) {
        try {
          await deleteSubtask(item.id);
          uni.showToast({ title: "已删除并重新分配权重", icon: "none" });
          loadSubtasks();
          // 刷新主任务进度
          refreshTaskProgress();
        } catch (error) {
          console.warn("delete subtask failed", error);
        }
      }
    },
  });
}

function openEditDialog(item) {
  editingSubtask.value = {
    id: item.id,
    content: item.content,
    weight: formatWeight(item.weight)
  };
  showEditDialog.value = true;
}

function closeEditDialog() {
  if (saving.value) return;
  showEditDialog.value = false;
}

async function saveSubtask() {
  const { id, content, weight } = editingSubtask.value;
  if (!content.trim()) {
    uni.showToast({ title: "内容不能为空", icon: "none" });
    return;
  }
  if (!weight || isNaN(Number(weight)) || Number(weight) <= 0 || Number(weight) >= 100) {
    uni.showToast({ title: "请输入有效的权重 (1-99)", icon: "none" });
    return;
  }

  saving.value = true;
  try {
    await updateSubtask(id, {
      content: content.trim(),
      weight: Number(weight) / 100
    });
    uni.showToast({ title: "更新成功", icon: "none" });
    showEditDialog.value = false;
    await loadSubtasks();
    await refreshTaskProgress();
  } catch (error) {
    console.warn("update subtask failed", error);
  } finally {
    saving.value = false;
  }
}

async function refreshTaskProgress() {
  try {
    const data = await getSubtasks(task.value.id);
    // 实际上 getSubtasks 只返回子任务，不返回主任务总进度
    // 这里需要一个获取单个任务详情的接口，或者通过子任务手动计算
    if (Array.isArray(data)) {
      const total = data.reduce((sum, st) => {
        return sum + (Number(st.weight) * Number(st.currentProgress));
      }, 0);
      task.value.totalProgress = total;
    }
  } catch (e) {}
}

onLoad((options) => {
  task.value = {
    id: options.id,
    title: decodeURIComponent(options.title || "任务详情"),
    category: decodeURIComponent(options.category || "任务"),
    totalProgress: Number(options.progress || 0),
  };
  loadSubtasks();
});
</script>

<style scoped>
.detail-top {
  display: flex;
  align-items: center;
  gap: 22rpx;
}

.detail-top view {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.back-btn {
  width: 72rpx;
  height: 72rpx;
  line-height: 66rpx;
  font-size: 54rpx;
}

.progress-card {
  margin-top: 36rpx;
  display: flex;
  align-items: center;
  gap: 28rpx;
}

.progress-circle {
  width: 168rpx;
  height: 168rpx;
  border-radius: 50%;
  flex: 0 0 auto;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
  color: #236894;
  background:
    radial-gradient(circle, rgba(255, 255, 255, 0.62) 0 53%, transparent 54%),
    conic-gradient(#6bbeff 0deg, #ffffff 260deg, rgba(255, 255, 255, 0.38) 261deg);
}

.progress-circle text:first-child {
  font-size: 38rpx;
  font-weight: 900;
}

.progress-circle text:last-child {
  font-size: 20rpx;
  font-weight: 700;
}

.progress-copy {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 14rpx;
  font-size: 24rpx;
  line-height: 1.55;
}

.small {
  font-size: 22rpx;
}

.subtask-list {
  display: flex;
  flex-direction: column;
  gap: 18rpx;
}

.subtask-card {
  display: grid;
  grid-template-columns: 52rpx 1fr 118rpx;
  align-items: center;
  gap: 18rpx;
}

.subtask-card.done {
  opacity: 0.72;
}

.check-dot {
  width: 48rpx;
  height: 48rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #236894;
  font-size: 26rpx;
  font-weight: 900;
  background: rgba(255, 255, 255, 0.5);
  border: 2rpx solid rgba(255, 255, 255, 0.7);
}

.subtask-main {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

.subtask-title {
  color: #2d3e50;
  font-size: 27rpx;
  font-weight: 800;
  line-height: 1.4;
}

.subtask-meta {
  display: flex;
  gap: 18rpx;
  color: rgba(45, 62, 80, 0.56);
  font-size: 21rpx;
}

.complete-btn {
  height: 64rpx;
  line-height: 64rpx;
  font-size: 22rpx;
}

.complete-btn[disabled] {
  color: rgba(35, 104, 148, 0.55);
}

.subtask-ops {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.delete-icon,
.edit-icon {
  width: 44rpx;
  height: 44rpx;
  line-height: 40rpx;
  text-align: center;
  border-radius: 12rpx;
  background: rgba(255, 255, 255, 0.3);
  font-size: 32rpx;
}

.delete-icon {
  color: #ff5a5f;
}

.edit-icon {
  color: #236894;
}

.edit-dialog {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}

.save-btn {
  height: 88rpx;
  line-height: 88rpx;
  font-size: 26rpx;
}

.edit-input {
  height: 92rpx;
}

.record-action {
  margin-top: 32rpx;
  width: 100%;
  height: 92rpx;
  line-height: 92rpx;
  font-size: 27rpx;
}

.delete-task-btn {
  margin-top: 24rpx;
  width: 100%;
  height: 88rpx;
  line-height: 88rpx;
  font-size: 24rpx;
  background: rgba(255, 90, 95, 0.1) !important;
  color: #ff5a5f !important;
  border-color: rgba(255, 90, 95, 0.2) !important;
}

.empty-card {
  min-height: 180rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
