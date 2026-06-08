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
          <button
            class="glass-button complete-btn"
            :disabled="isDone(item)"
            @tap.stop="finishSubtask(item)"
          >
            {{ isDone(item) ? "已完成" : "完成" }}
          </button>
        </view>
      </view>

      <button class="glass-button record-action" @tap="goRecord">用行为记录推进任务</button>
      <view class="tabbar-space"></view>
    </view>
  </view>
</template>

<script setup>
import { ref } from "vue";
import { onLoad } from "@dcloudio/uni-app";
import { completeSubtask, getSubtasks } from "../../services/api";

const task = ref({
  id: "",
  title: "任务详情",
  category: "任务",
  totalProgress: 0,
});
const subtasks = ref([]);
const loading = ref(false);

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

.record-action {
  margin-top: 32rpx;
  width: 100%;
  height: 92rpx;
  line-height: 92rpx;
  font-size: 27rpx;
}

.empty-card {
  min-height: 180rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
