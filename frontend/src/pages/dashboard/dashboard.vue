<template>
  <view class="page-shell">
    <view class="safe-area">
      <view class="top-row">
        <view>
          <text class="text-muted date">{{ todayLabel }}</text>
          <text class="title-xl">早安，{{ username }}</text>
        </view>
        <button class="glass-button record-btn" @tap="goRecord">记录</button>
      </view>

      <view class="hero-card glass-card">
        <view class="hero-copy">
          <text class="pill">今日成长值</text>
          <text class="hero-number">{{ formatProgress(stats.todayTotalProgress) }}%</text>
          <text class="hero-desc">{{ stats.dailyQuote }}</text>
          <text class="hero-desc mood-quote">{{ stats.moodQuote }}</text>
        </view>
        <view class="hero-orb">
          <text>☁</text>
        </view>
      </view>

      <view class="metric-grid">
        <view class="metric-card glass-card">
          <text class="metric-value">{{ tasks.length }}</text>
          <text class="metric-label">进行中任务</text>
        </view>
        <view class="metric-card glass-card">
          <text class="metric-value">{{ formatProgress(stats.todayTotalProgress) }}</text>
          <text class="metric-label">今日进度</text>
        </view>
        <view class="metric-card glass-card">
          <text class="metric-value">{{ stats.currentAnxietyLevel || 1 }}/10</text>
          <text class="metric-label">当前焦虑</text>
        </view>
      </view>

      <view class="section-head">
        <text class="section-title">一周趋势</text>
        <text class="text-muted small">来自 /dashboard/stats</text>
      </view>
      <view class="glass-card trend-card">
        <view class="trend-chart">
          <view v-for="item in stats.weeklyTrend" :key="item.date" class="trend-column">
            <view class="trend-bar-wrap">
              <view class="trend-bar" :style="{ height: trendHeight(item.progress) }"></view>
            </view>
            <text>{{ item.date }}</text>
          </view>
        </view>
      </view>

      <view class="section-head">
        <text class="section-title">时间分布</text>
        <text class="text-muted small">AI 自动归类</text>
      </view>
      <view class="glass-card distribution-card">
        <view
          v-for="item in stats.timeDistribution"
          :key="item.name"
          class="distribution-row"
        >
          <view class="distribution-info">
            <text class="dist-name">{{ item.name }}</text>
            <text class="text-muted">{{ item.minutes }} 分钟</text>
          </view>
          <view class="distribution-track">
            <view class="distribution-fill" :style="{ width: item.percentage + '%' }"></view>
          </view>
          <text class="dist-percent">{{ item.percentage }}%</text>
        </view>
      </view>

      <view class="section-head">
        <text class="section-title">AI 反馈</text>
        <text class="text-muted small">来自 /feedback/report</text>
      </view>
      <view class="glass-card feedback-card">
        <text class="feedback-title">{{ report.title }}</text>
        <text class="feedback-summary">{{ report.aiSummary }}</text>
        <view class="tag-row">
          <text v-for="tag in report.achievementTags" :key="tag" class="pill">{{ tag }}</text>
        </view>
      </view>

      <view class="section-head">
        <text class="section-title">重点任务</text>
        <text class="link" @tap="goTasks">全部</text>
      </view>
      <view class="task-list">
        <view
          v-for="task in featuredTasks"
          :key="task.id"
          class="glass-card task-card"
          @tap="openTask(task)"
        >
          <view class="task-main">
            <text class="task-title">{{ task.title }}</text>
            <text class="text-muted">{{ task.category }} · {{ task.subtaskCount || 0 }} 个子任务</text>
          </view>
          <view class="task-progress">
            <text>{{ formatProgress(task.totalProgress) }}%</text>
            <view class="progress-track">
              <view class="progress-fill" :style="{ width: clampProgress(task.totalProgress) + '%' }"></view>
            </view>
          </view>
        </view>
      </view>

      <view class="tabbar-space"></view>
    </view>
    <LifeTabBar current="dashboard" />
  </view>
</template>

<script setup>
import { computed, ref } from "vue";
import { onShow } from "@dcloudio/uni-app";
import LifeTabBar from "../../components/LifeTabBar.vue";
import { getDashboardStats, getFeedbackReport, getTasks } from "../../services/api";
import { getUser } from "../../services/http";

const stats = ref({
  todayTotalProgress: 0,
  dailyQuote: "记录一下今天做了什么，AI 会帮你同步任务进度。",
  timeDistribution: [],
  weeklyTrend: [],
});
const report = ref({
  title: "等待你的第一条记录",
  aiSummary: "完成一次行为记录后，这里会生成更贴近你的成长反馈。",
  achievementTags: ["准备开始"],
});
const tasks = ref([]);
const user = ref(getUser());

const username = computed(() => user.value.username || "同学");
const todayLabel = computed(() => {
  const now = new Date();
  return `${now.getMonth() + 1}月${now.getDate()}日`;
});
const featuredTasks = computed(() => tasks.value.slice(0, 3));

function formatProgress(value) {
  return Number(value || 0).toFixed(Number(value || 0) % 1 === 0 ? 0 : 1);
}

function clampProgress(value) {
  return Math.max(0, Math.min(100, Number(value || 0)));
}

function trendHeight(value) {
  return `${Math.max(18, clampProgress(value))}%`;
}

function goRecord() {
  uni.redirectTo({ url: "/pages/record/record" });
}

function goTasks() {
  uni.redirectTo({ url: "/pages/tasks/tasks" });
}

function openTask(task) {
  const query = `id=${task.id}&title=${encodeURIComponent(task.title)}&category=${encodeURIComponent(task.category)}&progress=${task.totalProgress || 0}`;
  uni.navigateTo({ url: `/pages/task-detail/task-detail?${query}` });
}

async function loadPage() {
  try {
    const [nextStats, nextReport, nextTasks] = await Promise.all([
      getDashboardStats(),
      getFeedbackReport(),
      getTasks(),
    ]);
    stats.value = nextStats || stats.value;
    report.value = nextReport || report.value;
    tasks.value = Array.isArray(nextTasks) ? nextTasks : [];
  } catch (error) {
    console.warn("load dashboard failed", error);
  }
}

onShow(loadPage);
</script>

<style scoped>
.top-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24rpx;
}

.top-row view {
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.date {
  font-size: 24rpx;
}

.record-btn {
  width: 146rpx;
  height: 72rpx;
  line-height: 72rpx;
  font-size: 26rpx;
}

.hero-card {
  margin-top: 40rpx;
  min-height: 300rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24rpx;
}

.hero-copy {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 18rpx;
}

.hero-number {
  color: #236894;
  font-size: 84rpx;
  font-weight: 900;
  line-height: 1;
}

.hero-desc {
  color: rgba(45, 62, 80, 0.68);
  font-size: 26rpx;
  line-height: 1.55;
}

.mood-quote {
  margin-top: 8rpx;
  font-style: italic;
  color: #236894;
  font-weight: 700;
}

.hero-orb {
  width: 150rpx;
  height: 150rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.42);
  box-shadow: inset 0 2rpx 0 rgba(255, 255, 255, 0.76);
}

.hero-orb text {
  color: #236894;
  font-size: 80rpx;
}

.metric-grid {
  margin-top: 24rpx;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 18rpx;
}

.metric-card {
  min-height: 130rpx;
  padding: 22rpx;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 8rpx;
}

.metric-value {
  color: #236894;
  font-size: 36rpx;
  font-weight: 900;
}

.metric-label,
.small {
  font-size: 22rpx;
}

.trend-card {
  padding-top: 34rpx;
}

.trend-chart {
  height: 280rpx;
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
}

.trend-column {
  height: 100%;
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-end;
  gap: 12rpx;
  color: rgba(45, 62, 80, 0.54);
  font-size: 20rpx;
}

.trend-bar-wrap {
  width: 28rpx;
  height: 210rpx;
  border-radius: 999rpx;
  display: flex;
  align-items: flex-end;
  background: rgba(255, 255, 255, 0.4);
  overflow: hidden;
}

.trend-bar {
  width: 100%;
  border-radius: 999rpx;
  background: linear-gradient(180deg, #fff, #6bbeff);
}

.distribution-card {
  display: flex;
  flex-direction: column;
  gap: 22rpx;
}

.distribution-row {
  display: grid;
  grid-template-columns: 130rpx 1fr 72rpx;
  align-items: center;
  gap: 18rpx;
}

.distribution-info {
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.dist-name,
.dist-percent {
  color: #236894;
  font-size: 24rpx;
  font-weight: 800;
}

.distribution-track {
  height: 18rpx;
  border-radius: 999rpx;
  overflow: hidden;
  background: rgba(255, 255, 255, 0.44);
}

.distribution-fill {
  height: 100%;
  border-radius: 999rpx;
  background: linear-gradient(90deg, #fff, #6bbeff);
}

.feedback-card {
  display: flex;
  flex-direction: column;
  gap: 18rpx;
}

.feedback-title {
  color: #236894;
  font-size: 30rpx;
  font-weight: 900;
  line-height: 1.35;
}

.feedback-summary {
  color: rgba(45, 62, 80, 0.66);
  font-size: 25rpx;
  line-height: 1.6;
}

.tag-row {
  display: flex;
  gap: 12rpx;
  flex-wrap: wrap;
}

.link {
  color: #236894;
  font-size: 24rpx;
  font-weight: 800;
}

.task-list {
  display: flex;
  flex-direction: column;
  gap: 18rpx;
}

.task-card {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 24rpx;
}

.task-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 10rpx;
}

.task-title {
  color: #2d3e50;
  font-size: 28rpx;
  font-weight: 800;
}

.task-progress {
  width: 190rpx;
  display: flex;
  flex-direction: column;
  gap: 10rpx;
  color: #236894;
  font-size: 24rpx;
  font-weight: 900;
}
</style>
