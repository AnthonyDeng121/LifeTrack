<template>
  <view class="page-shell">
    <view class="safe-area">
      <view class="record-hero glass-card">
        <view class="hero-icon">✎</view>
        <view class="hero-text">
          <view class="record-eyebrow">行为记录</view>
          <view class="record-title">记录今天</view>
          <view class="record-subtitle">写一句自然语言，AI 会同步行为日志和任务进度。</view>
        </view>
      </view>

      <view class="glass-card input-card">
        <view class="input-group">
          <text class="input-label">行为描述</text>
          <textarea
            v-model="rawInput"
            class="glass-input action-input"
            maxlength="300"
            placeholder="例如：我刚才写完了登录接口，整理了数据库表"
          />
        </view>

        <view class="input-group">
          <text class="input-label">所用时长 (支持自然语言)</text>
          <input
            v-model="duration"
            type="text"
            class="glass-input duration-input"
            placeholder="例如：30分钟、1.5小时、一小会儿"
          />
        </view>

        <button class="glass-button sync-btn" :loading="syncing" @tap="submitRecord">
          交给 AI 分析并写入数据库
        </button>
        <text class="hint">
          这里会调用 /actions/sync，后端会保存行为记录，并自动更新相关任务进度。
        </text>
      </view>

      <view v-if="syncResult" class="glass-card result-card">
        <text class="result-title">AI 同步结果</text>
        <text class="result-copy">{{ syncResult.aiAnalysis }}</text>
        <view class="update-list">
          <view v-for="item in syncResult.updates" :key="item.taskTitle" class="update-row">
            <view>
              <text>{{ item.taskTitle }}</text>
              <text class="text-muted">新增 {{ formatProgress(item.increment) }}%</text>
            </view>
            <text class="pill">{{ formatProgress(item.newTotalProgress) }}%</text>
          </view>
        </view>
      </view>

      <view class="section-head">
        <text class="section-title">情绪打卡</text>
        <text class="text-muted small">来自 /feedback/mood</text>
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

      <view class="section-head">
        <text class="section-title">历史记录</text>
        <text class="text-muted small">来自 /actions/history</text>
      </view>
      <view v-if="historyLoading" class="glass-card empty-card">
        <text>正在读取历史...</text>
      </view>
      <view v-else class="history-list">
        <view v-for="item in history" :key="item.id" class="glass-card history-card">
          <view class="history-main">
            <view class="history-head">
              <text class="history-title">{{ item.taskTitle || item.category || "成长记录" }}</text>
              <text class="pill">+{{ formatProgress(item.contribution) }}%</text>
            </view>
            <text class="raw">{{ item.rawInput }}</text>
            <text class="text-muted analysis">{{ item.aiAnalysis }}</text>
            <text class="text-muted time">{{ item.createdAt }}</text>
          </view>
          <button class="delete-btn" @tap="removeHistory(item.id)">撤销</button>
        </view>
      </view>

      <view class="tabbar-space"></view>
    </view>
    <LifeTabBar current="record" />
  </view>
</template>

<script setup>
import { computed, ref } from "vue";
import { onShow } from "@dcloudio/uni-app";
import LifeTabBar from "../../components/LifeTabBar.vue";
import { deleteAction, getActionHistory, syncAction, updateMood, getDashboardStats } from "../../services/api";

const rawInput = ref("");
const duration = ref("");
const syncing = ref(false);
const syncResult = ref(null);
const history = ref([]);
const historyLoading = ref(false);
const anxietyLevel = ref(1);

const moodText = computed(() => {
  if (anxietyLevel.value <= 3) return "非常放松，动力十足";
  if (anxietyLevel.value <= 6) return "有点压力，正常节奏";
  if (anxietyLevel.value <= 8) return "比较焦虑，需要调整";
  return "极度焦虑，建议休息";
});

function formatProgress(value) {
  return Number(value || 0).toFixed(Number(value || 0) % 1 === 0 ? 0 : 1);
}

async function submitRecord() {
  const value = rawInput.value.trim();
  const mins = duration.value.trim();

  if (!value) {
    uni.showToast({ title: "先写一点今天的行为", icon: "none" });
    return;
  }
  if (!mins) {
    uni.showToast({ title: "请输入大概的时长", icon: "none" });
    return;
  }

  syncing.value = true;
  try {
    syncResult.value = await syncAction(value, mins);
    rawInput.value = "";
    duration.value = "";
    await loadData();
    uni.showToast({ title: "已同步行为记录", icon: "none" });
  } catch (error) {
    console.warn("sync action failed", error);
  } finally {
    syncing.value = false;
  }
}

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

async function loadData() {
  historyLoading.value = true;
  try {
    const [stats, historyData] = await Promise.all([
      getDashboardStats(),
      getActionHistory(0, 20)
    ]);
    
    // 初始化滑块位置为后端存的真实值
    if (stats && stats.currentAnxietyLevel) {
      anxietyLevel.value = stats.currentAnxietyLevel;
    }
    
    history.value = Array.isArray(historyData) ? historyData : historyData && Array.isArray(historyData.list) ? historyData.list : [];
  } catch (error) {
    console.warn("load record data failed", error);
  } finally {
    historyLoading.value = false;
  }
}

async function removeHistory(id) {
  try {
    await deleteAction(id);
    history.value = history.value.filter((item) => item.id !== id);
    uni.showToast({ title: "已请求后端撤销记录", icon: "none" });
  } catch (error) {
    console.warn("delete action failed", error);
  }
}

onShow(loadData);
</script>

<style scoped>
.record-hero {
  padding: 26rpx 28rpx;
  display: flex;
  align-items: center;
  gap: 22rpx;
}

.hero-icon {
  width: 76rpx;
  height: 76rpx;
  border-radius: 28rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  color: #236894;
  font-size: 36rpx;
  font-weight: 900;
  background: rgba(255, 255, 255, 0.42);
  box-shadow: inset 0 2rpx 0 rgba(255, 255, 255, 0.78);
}

.hero-text {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}

.record-eyebrow {
  color: rgba(45, 62, 80, 0.56);
  font-size: 22rpx;
  font-weight: 700;
}

.record-title {
  color: #2d3e50;
  font-size: 38rpx;
  font-weight: 900;
  line-height: 1.2;
}

.record-subtitle {
  color: rgba(45, 62, 80, 0.56);
  font-size: 23rpx;
  line-height: 1.45;
}

.input-card {
  margin-top: 24rpx;
  display: flex;
  flex-direction: column;
  gap: 22rpx;
}

.action-input {
  width: 100%;
  min-height: 200rpx;
  line-height: 1.5;
  font-size: 26rpx;
}

.input-group {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

.input-label {
  font-size: 24rpx;
  color: #236894;
  font-weight: 800;
  margin-left: 4rpx;
}

.duration-input {
  width: 100%;
  height: 80rpx;
  padding: 0 24rpx;
  font-size: 26rpx;
}

.sync-btn,
.mood-btn {
  height: 92rpx;
  line-height: 92rpx;
  font-size: 26rpx;
}

.hint {
  color: rgba(45, 62, 80, 0.52);
  font-size: 22rpx;
  line-height: 1.5;
}

.result-card {
  margin-top: 26rpx;
  display: flex;
  flex-direction: column;
  gap: 18rpx;
}

.result-title {
  color: #236894;
  font-size: 30rpx;
  font-weight: 900;
}

.result-copy {
  color: rgba(45, 62, 80, 0.68);
  font-size: 25rpx;
  line-height: 1.6;
}

.update-list {
  display: flex;
  flex-direction: column;
  gap: 14rpx;
}

.update-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18rpx;
  padding: 18rpx 0 0;
  border-top: 2rpx solid rgba(255, 255, 255, 0.36);
}

.update-row view {
  display: flex;
  flex-direction: column;
  gap: 6rpx;
  color: #2d3e50;
  font-size: 25rpx;
  font-weight: 800;
}

.small {
  font-size: 22rpx;
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

.history-list {
  display: flex;
  flex-direction: column;
  gap: 18rpx;
}

.history-card {
  display: grid;
  grid-template-columns: 1fr 90rpx;
  gap: 20rpx;
  align-items: start;
}

.history-main {
  display: flex;
  flex-direction: column;
  gap: 10rpx;
}

.history-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18rpx;
}

.history-title {
  color: #2d3e50;
  font-size: 28rpx;
  font-weight: 900;
}

.raw {
  color: rgba(45, 62, 80, 0.74);
  font-size: 25rpx;
  line-height: 1.55;
}

.analysis {
  font-size: 23rpx;
  line-height: 1.5;
}

.time {
  font-size: 21rpx;
}

.delete-btn {
  width: 88rpx;
  height: 56rpx;
  border-radius: 999rpx;
  color: #236894;
  background: rgba(255, 255, 255, 0.38);
  font-size: 22rpx;
}

.empty-card {
  min-height: 160rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  }
</style>
