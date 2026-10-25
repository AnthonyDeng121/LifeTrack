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

      <view v-if="loading" class="glass-card page-state"><text>正在加载个人数据...</text></view>
      <view v-else-if="pageError" class="glass-card page-state error-state">
        <text>{{ pageError }}</text>
        <button class="glass-button retry-btn" @tap="loadProfile">重新加载</button>
      </view>
      <template v-else>

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
          <text>{{ report && report.achievementTags ? report.achievementTags.length : 0 }}</text>
          <text>成就标签</text>
        </view>
      </view>

      <view class="section-head">
        <text class="section-title">本周画像</text>
      </view>
      <view v-if="report" class="glass-card report-card">
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
      <view v-else class="glass-card page-state">
        <text>{{ reportError || "暂无 AI 周报" }}</text>
        <text class="text-muted small">基础计划与打卡功能仍可正常使用。</text>
      </view>

      <view class="section-head">
        <text class="section-title">情绪打卡</text>
      </view>
      <view class="glass-card mood-card">
        <view class="mood-top">
          <text>焦虑程度: {{ anxietyLevel }}/10</text>
          <text class="mood-text">{{ moodText }}</text>
        </view>
        <slider
          class="mood-slider"
          :value="anxietyLevel"
          min="1"
          max="10"
          step="1"
          activeColor="#6BBEFF"
          backgroundColor="rgba(255,255,255,.45)"
          block-size="20"
          @change="changeMood"
        />
        <button class="glass-button mood-btn" @tap="saveMood">保存情绪记录</button>
      </view>

      <button class="glass-button logout-btn" @tap="handleLogout">退出登录</button>
      </template>

      <view class="tabbar-space"></view>
    </view>
    <LifeTabBar current="profile" />
  </view>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import LifeTabBar from "../../components/LifeTabBar.vue";
import { getDashboardStats, getFeedbackReport, updateMood } from "../../services/api";
import { getUser, logout } from "../../services/http";

const user = ref(getUser());
const stats = ref({
  todayTotalProgress: 0,
  weeklyTrend: [],
});
const report = ref(null);
const loading = ref(false);
const pageError = ref("");
const reportError = ref("");

const username = computed(() => user.value.username || "林同学");
const avatarText = computed(() => username.value.slice(0, 1));



const anxietyLevel = ref(1);
const moodText = computed(() => {
  if (anxietyLevel.value <= 3) return "非常放松，动力十足";
  if (anxietyLevel.value <= 6) return "有点压力，正常节奏";
  if (anxietyLevel.value <= 8) return "比较焦虑，需要调整";
  return "极度焦虑，建议休息";
});

function changeMood(event) {
  anxietyLevel.value = Number(event.detail.value);
}

async function saveMood() {
  try {
    await updateMood(anxietyLevel.value);
    uni.showToast({ title: "情绪记录已保存", icon: "none" });
  } catch (error) {
    console.warn("save mood failed", error);
  }
}

function formatProgress(value) {
  return Number(value || 0).toFixed(Number(value || 0) % 1 === 0 ? 0 : 1);
}

async function loadProfile() {
  loading.value = true;
  pageError.value = "";
  reportError.value = "";
  const [statsResult, reportResult] = await Promise.allSettled([getDashboardStats(), getFeedbackReport(false)]);
  if (statsResult.status === "fulfilled") { stats.value = statsResult.value || stats.value; anxietyLevel.value = stats.value.currentAnxietyLevel || 1; }
  else pageError.value = "个人统计加载失败，请稍后重试";
  if (reportResult.status === "fulfilled") report.value = reportResult.value;
  else {
    report.value = null;
    reportError.value = "AI 服务暂不可用";
  }
  loading.value = false;
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
.page-state { margin-top: 24rpx; min-height: 150rpx; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 18rpx; text-align: center; }
.error-state { color: #a33b3b; }
.retry-btn { width: 200rpx; height: 68rpx; line-height: 68rpx; font-size: 23rpx; }

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

.mood-card {
  display: flex;
  flex-direction: column;
  gap: 18rpx;
}

.mood-desc {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
}

.mood-desc text:first-child {
  color: #236894;
  font-size: 32rpx;
  font-weight: 900;
}

.mood-desc text:last-child {
  font-size: 24rpx;
}

.mood-slider {
  margin: 10rpx 0;
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
.mood-card {
  display: flex;
  flex-direction: column;
  gap: 18rpx;
}

.mood-top {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  color: #236894;
  font-size: 28rpx;
  font-weight: 900;
}

.mood-text {
  font-size: 24rpx;
  font-weight: normal;
  color: rgba(45, 62, 80, 0.56);
}

.mood-slider {
  margin: 10rpx 0;
}


.mood-btn { height: 92rpx; line-height: 92rpx; font-size: 26rpx; }
</style>
