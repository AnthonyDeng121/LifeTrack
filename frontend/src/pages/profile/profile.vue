<template>
  <view class="page-shell">
    <view class="safe-area">
      <view class="profile-card glass-card">
        <view class="avatar">
          <text>{{ avatarText }}</text>
        </view>
        <view class="profile-main">
          <view class="profile-eyebrow">我的</view>
          <view class="profile-name">{{ username }}</view>
          <view class="profile-subtitle">LifeTrack AI 会把你的行为记录整理成成长轨迹。</view>
        </view>
      </view>

      <view class="metric-grid">
        <view class="metric-card glass-card">
          <text>{{ formatProgress(stats.todayTotalProgress) }}%</text>
          <text>今日成长</text>
        </view>
        <view class="metric-card glass-card">
          <text>{{ stats.weeklyTrend.length || 0 }}</text>
          <text>趋势天数</text>
        </view>
        <view class="metric-card glass-card">
          <text>{{ report.achievementTags.length || 0 }}</text>
          <text>成就标签</text>
        </view>
      </view>

      <view class="section-head">
        <text class="section-title">本周画像</text>
        <text class="text-muted small">来自 /feedback/report</text>
      </view>
      <view class="glass-card report-card">
        <text class="report-title">{{ report.title }}</text>
        <text class="report-copy">{{ report.aiSummary }}</text>
        <view class="tag-row">
          <text v-for="tag in report.achievementTags" :key="tag" class="pill">{{ tag }}</text>
        </view>
        <view class="suggestion">
          <text>下步建议</text>
          <text>{{ report.suggestion }}</text>
        </view>
      </view>

      <view class="section-head">
        <text class="section-title">后端联调</text>
      </view>
      <view class="glass-card backend-card">
        <view class="backend-row">
          <text>用户 ID</text>
          <text>{{ user.userId || "开发用户" }}</text>
        </view>
        <view class="backend-row">
          <text>登录状态</text>
          <text>{{ hasToken ? "已保存 token" : "未登录" }}</text>
        </view>
        <view class="backend-row">
          <text>接口前缀</text>
          <text>{{ apiBase }}</text>
        </view>
      </view>

      <button class="glass-button logout-btn" @tap="handleLogout">退出登录</button>

      <view class="tabbar-space"></view>
    </view>
    <LifeTabBar current="profile" />
  </view>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import LifeTabBar from "../../components/LifeTabBar.vue";
import { getDashboardStats, getFeedbackReport } from "../../services/api";
import { getApiBaseUrl, getToken, getUser, logout } from "../../services/http";

const user = ref(getUser());
const stats = ref({
  todayTotalProgress: 0,
  weeklyTrend: [],
});
const report = ref({
  title: "还没有生成画像",
  aiSummary: "多记录几次行为后，这里会更像你的个人成长报告。",
  achievementTags: ["成长中"],
  suggestion: "先从今天的一条记录开始。",
});
const apiBase = ref(getApiBaseUrl());

const username = computed(() => user.value.username || "林同学");
const avatarText = computed(() => username.value.slice(0, 1));
const hasToken = computed(() => Boolean(getToken()));

function formatProgress(value) {
  return Number(value || 0).toFixed(Number(value || 0) % 1 === 0 ? 0 : 1);
}

async function loadProfile() {
  try {
    const [nextStats, nextReport] = await Promise.all([getDashboardStats(), getFeedbackReport()]);
    stats.value = nextStats || stats.value;
    report.value = nextReport || report.value;
  } catch (error) {
    console.warn("load profile failed", error);
  }
}

function handleLogout() {
  logout();
}

onMounted(loadProfile);
</script>

<style scoped>
.profile-card {
  padding: 30rpx;
  display: flex;
  align-items: center;
  gap: 24rpx;
}

.avatar {
  width: 116rpx;
  height: 116rpx;
  border-radius: 40rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #236894;
  font-size: 48rpx;
  font-weight: 900;
  background: rgba(255, 255, 255, 0.54);
  box-shadow: inset 0 2rpx 0 rgba(255, 255, 255, 0.78);
}

.profile-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.profile-eyebrow {
  color: rgba(45, 62, 80, 0.54);
  font-size: 22rpx;
  font-weight: 700;
}

.profile-name {
  color: #2d3e50;
  font-size: 42rpx;
  font-weight: 900;
  line-height: 1.2;
}

.profile-subtitle {
  color: rgba(45, 62, 80, 0.58);
  font-size: 24rpx;
  line-height: 1.45;
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

.metric-card text:first-child {
  color: #236894;
  font-size: 34rpx;
  font-weight: 900;
}

.metric-card text:last-child,
.small {
  color: rgba(45, 62, 80, 0.58);
  font-size: 22rpx;
}

.report-card {
  display: flex;
  flex-direction: column;
  gap: 18rpx;
}

.report-title {
  color: #236894;
  font-size: 30rpx;
  font-weight: 900;
  line-height: 1.4;
}

.report-copy {
  color: rgba(45, 62, 80, 0.68);
  font-size: 25rpx;
  line-height: 1.6;
}

.tag-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
}

.suggestion {
  padding-top: 18rpx;
  border-top: 2rpx solid rgba(255, 255, 255, 0.38);
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.suggestion text:first-child {
  color: #236894;
  font-size: 24rpx;
  font-weight: 900;
}

.suggestion text:last-child {
  color: rgba(45, 62, 80, 0.64);
  font-size: 24rpx;
  line-height: 1.5;
}

.backend-card {
  display: flex;
  flex-direction: column;
  gap: 18rpx;
}

.backend-row {
  display: flex;
  justify-content: space-between;
  gap: 18rpx;
  color: rgba(45, 62, 80, 0.62);
  font-size: 23rpx;
}

.backend-row text:last-child {
  max-width: 440rpx;
  color: #236894;
  font-weight: 800;
  text-align: right;
  word-break: break-all;
}

.logout-btn {
  margin-top: 30rpx;
  width: 100%;
  height: 88rpx;
  line-height: 88rpx;
  font-size: 26rpx;
}
</style>
