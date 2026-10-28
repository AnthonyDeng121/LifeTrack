<template>
  <view class="page-shell">
    <view class="safe-area">
      <view class="top-row">
        <view>
          <text class="text-muted date">{{ todayLabel }}</text>
          <text class="title-xl">{{ greeting }}，{{ username }}</text>
        </view>
      </view>

      <view v-if="loading" class="glass-card page-state"><text>正在加载真实统计...</text></view>
      <view v-else-if="pageError" class="glass-card page-state error-state">
        <text>{{ pageError }}</text>
        <button class="glass-button retry-btn" @tap="loadPage">重新加载</button>
      </view>
      <template v-else>

      <view class="hero-card glass-card">
        <view class="hero-copy">
          <text class="pill">学习计划完成率</text>
          <text class="hero-number">{{ formatProgress(stats.learningPlanCompletionRate) }}%</text>
          <text class="hero-desc">已完成 {{ stats.completedPlanCount || 0 }} / {{ stats.totalPlanCount || 0 }} 个未取消计划；已取消 {{ stats.cancelledPlanCount || 0 }} 个，不计入看板统计</text>
          <text class="text-muted small">仅全部学习步骤完成计入完成数；人工结束不算学习完成，打卡达标单独统计。</text>
        </view>
        <view class="hero-orb">
          <text>☁</text>
        </view>
      </view>

      <view class="metric-grid">
        <view class="metric-card glass-card">
          <text class="metric-value">{{ stats.completedPlanCount || 0 }}</text>
          <text class="metric-label">已完成计划</text>
        </view>
        <view class="metric-card glass-card">
          <text class="metric-value">{{ stats.checkedInDays || 0 }}</text>
          <text class="metric-label">累计打卡天数</text>
        </view>
        <view class="metric-card glass-card">
          <text class="metric-value">{{ stats.currentCheckInStreak || 0 }}</text>
          <text class="metric-label">当前连续</text>
        </view>
        <view class="metric-card glass-card">
          <text class="metric-value">{{ stats.longestCheckInStreak || 0 }}</text>
          <text class="metric-label">最长连续</text>
        </view>
      </view>

      <view class="section-head">
        <text class="section-title">最近七天打卡</text>

      </view>
      <view class="glass-card trend-card">
        <view v-if="stats.checkInTrend.length === 0" class="inline-empty">暂无最近七天打卡数据</view>
        <view class="trend-chart">
          <view v-for="item in stats.checkInTrend" :key="item.date" class="trend-column">
            <view class="trend-bar-wrap">
              <view class="trend-bar" :style="{ height: checkInTrendHeight(item.checkInCount) }"></view>
            </view>
            <text>{{ item.checkInCount ? "已打卡" : "未打卡" }}</text>
            <text>{{ item.date }}</text>
          </view>
        </view>
      </view>

      <view class="section-head">
        <text class="section-title">时间分布</text>

      </view>
      <view class="glass-card distribution-card">
        <view v-if="stats.timeDistribution.length === 0" class="inline-empty">今天还没有可统计的学习时长</view>
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
      </view>
      <view v-if="reportLoading" class="glass-card page-state"><text>正在生成 AI 反馈，其他统计已可使用...</text></view>
      <view v-else-if="report" class="glass-card feedback-card">
        <text class="feedback-title">{{ report.title }}</text>
        <text v-if="report.planOutcomeSummary" class="feedback-summary outcome-summary">{{ report.planOutcomeSummary }}</text>
        <text class="feedback-summary">{{ report.aiSummary }}</text>
        <view class="tag-row">
          <text v-for="tag in report.achievementTags" :key="tag" class="pill">{{ tag }}</text>
        </view>
      </view>
      <view v-else class="glass-card page-state">
        <text>{{ reportError || "暂无 AI 反馈" }}</text>
        <text class="text-muted small">这不会影响学习计划、打卡和真实统计。</text>
      </view>

      <view class="section-head">
        <text class="section-title">重点任务</text>

      </view>
      <view v-if="tasksError" class="glass-card page-state"><text>{{ tasksError }}</text></view>
      <view v-else-if="featuredTasks.length === 0" class="glass-card page-state"><text>暂无进行中的学习计划</text></view>
      <view v-else class="task-list">
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
      </template>

      <view class="tabbar-space"></view>
    </view>
    <LifeTabBar current="dashboard" />
  </view>
</template>

<script setup>
import { computed, ref } from "vue";
import { onShow, onHide, onUnload } from "@dcloudio/uni-app";
import LifeTabBar from "../../components/LifeTabBar.vue";
import { getDashboardStats, getFeedbackReport, getTasks } from "../../services/api";
import { getUser } from "../../services/http";
import { getTimeGreeting, getBusinessDateLabel } from "../../services/businessTime";

const stats = ref({
  todayTotalProgress: 0,
  dailyQuote: "记录一下今天做了什么，AI 会帮你同步任务进度。",
  timeDistribution: [],
  weeklyTrend: [],
  checkInTrend: [],
  checkedInDays: 0,
  currentCheckInStreak: 0,
  longestCheckInStreak: 0,
});
const report = ref(null);
const tasks = ref([]);
const user = ref(getUser());
const loading = ref(false);
const pageError = ref("");
const reportError = ref("");
const reportLoading = ref(false);
const tasksError = ref("");
let loadVersion = 0;

const username = computed(() => user.value.username || "同学");
const now = ref(new Date());
const greeting = computed(() => getTimeGreeting(now.value));
const todayLabel = computed(() => getBusinessDateLabel(now.value));
let clockTimer;
function stopClock() { clearInterval(clockTimer); clockTimer = undefined; }
function startClock() {
  stopClock();
  now.value = new Date();
  clockTimer = setInterval(() => { now.value = new Date(); }, 60000);
}
onHide(stopClock);
onUnload(stopClock);
const featuredTasks = computed(() => tasks.value.slice(0, 3));

function formatProgress(value) {
  const actual = Math.max(0, Math.min(100, Number(value) || 0));
  const display = actual < 100 ? Math.min(actual, 99.9) : 100;
  return display.toFixed(display % 1 === 0 ? 0 : 1);
}

function clampProgress(value) {
  return Math.max(0, Math.min(100, Number(value || 0)));
}

function checkInTrendHeight(value) {
  const max = Math.max(1, ...stats.value.checkInTrend.map(item => Number(item.checkInCount || 0)));
  return `${Number(value || 0) === 0 ? 0 : Math.max(18, Number(value) / max * 100)}%`;
}

function openTask(task) {
  const query = `id=${task.id}&title=${encodeURIComponent(task.title)}&category=${encodeURIComponent(task.category)}&progress=${task.totalProgress || 0}`;
  uni.navigateTo({ url: `/pages/task-detail/task-detail?${query}` });
}

async function loadPage() {
  const version = ++loadVersion;
  loading.value = true;
  pageError.value = "";
  tasksError.value = "";
  loadReport(version);
  const [statsResult, tasksResult] = await Promise.allSettled([
      getDashboardStats(),
      getTasks({ status: "IN_PROGRESS" }),
  ]);
  if (version !== loadVersion) return;
  if (statsResult.status === "fulfilled") stats.value = statsResult.value || stats.value;
  if (tasksResult.status === "fulfilled") tasks.value = Array.isArray(tasksResult.value) ? tasksResult.value : [];
  if (statsResult.status === "rejected") {
    pageError.value = "真实统计加载失败，请稍后重试";
  }
  if (tasksResult.status === "rejected") tasksError.value = "计划列表暂不可用，统计数据不受影响";
  loading.value = false;
}

async function loadReport(version) {
  reportLoading.value = true;
  reportError.value = "";
  report.value = null;
  try {
    const result = await getFeedbackReport(false);
    if (version === loadVersion) report.value = result;
  } catch {
    if (version !== loadVersion) return;
    report.value = null;
    reportError.value = "AI 服务暂不可用";
  } finally { if (version === loadVersion) reportLoading.value = false; }
}

onShow(() => { startClock(); loadPage(); });
</script>

<style scoped>
.top-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24rpx;
}
.page-state { margin-top: 24rpx; min-height: 150rpx; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 18rpx; text-align: center; }
.error-state { color: #a33b3b; }
.retry-btn { width: 200rpx; height: 68rpx; line-height: 68rpx; font-size: 23rpx; }
.inline-empty { min-height: 120rpx; display: flex; align-items: center; justify-content: center; color: rgba(45, 62, 80, 0.58); font-size: 24rpx; }

.top-row view {
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.date {
  font-size: 24rpx;
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
  grid-template-columns: repeat(2, 1fr);
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
