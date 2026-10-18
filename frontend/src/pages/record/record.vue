<template>
  <view class="page-shell">
    <view class="safe-area">
      <view class="record-hero glass-card">
        <view class="hero-icon">✎</view>
        <view class="hero-text">
          <view class="record-eyebrow">行为记录</view>
          <view class="record-title">记录今天</view>
          <view class="record-subtitle">自然语言自动匹配计划和步骤；每天首次有效记录自动打卡，后续记录继续累计进度。</view>
          <view class="record-subtitle">普通投入最多估算至95%，仍可继续记录和打卡；达到100%需描述完成产出与验收结果，也可在任务详情手动确认。</view>
        </view>
      </view>

      <view class="glass-card input-card">
        <view class="input-group">
          <text class="input-label">匹配范围</text>
          <picker :range="planLabels" :value="selectedPlanIndex" @change="selectPlan">
            <view class="glass-input">{{ planLabels[selectedPlanIndex] }}</view>
          </picker>
          <text v-if="plansError" class="hint">{{ plansError }}</text>
        </view>
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
          分析并保存记录
        </button>
        <text class="hint">
          仅实际增加步骤进度的记录会保存并触发打卡；无关内容不会打卡。
        </text>
      </view>

      <view v-if="syncResult" class="glass-card result-card">
        <text class="result-title">AI 同步结果</text>
        <text class="result-copy">{{ syncResult.aiAnalysis }}</text>
        <template v-if="syncResult.analysisUnavailable && !resultIsRetry">
          <text class="hint">AI 暂不可用，选择计划后可先保存记录并打卡。原始内容和日期会保留，恢复后点击“补分析”恢复对应进度。</text>
          <button class="glass-button" :disabled="!selectedTaskId || syncing" :loading="syncing" @tap="submitManualCheckIn">保存记录并打卡（待补分析）</button>
        </template>
        <text v-if="!syncResult.updates?.length" class="hint">{{ resultIsRetry ? "原始记录仍保留，本次未增加进度，也未重复打卡。" : "未产生有效进度，未保存学习记录，也未新增打卡。请补充学习内容，或选择计划后重试。" }}</text>
        <view class="update-list">
          <view v-for="item in syncResult.updates" :key="item.subTaskId" class="update-row">
            <view>
              <text>{{ item.taskTitle }} · {{ item.subTaskTitle }}</text>
              <text class="text-muted">{{ Number(item.increment) === 0 ? "学习记录已保存；已达估算上限，进度保持不变" : `步骤增加 ${formatProgress(item.increment)} 个百分点` }}</text>
            </view>
            <view><text class="pill">计划进度 {{ formatProgress(item.newTotalProgress) }}%</text><text class="text-muted">{{ item.checkedInNow ? "首次有效记录，今日打卡成功" : (resultIsRetry ? "保留原日期打卡，本次仅补进度" : "今日已打卡，本次只更新进度") }}</text></view>
          </view>
        </view>
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

      <view class="section-head">
        <text class="section-title">待补分析</text>
        <button class="glass-button import-deferred-btn" :disabled="syncing || retryingId || importing" @tap="importLegacyRecords">导入旧版手动打卡</button>
      </view>
      <view v-if="deferredLoading" class="glass-card empty-card"><text>正在加载待分析记录...</text></view>
      <view v-else-if="deferredError" class="glass-card empty-card state-card"><text>{{ deferredError }}</text><button class="glass-button" @tap="loadDeferredRecords">重新加载</button></view>
      <view v-else-if="pendingRecords.length === 0" class="glass-card empty-card"><text>没有待补分析的记录</text></view>
      <view v-else class="history-list">
        <view v-for="item in pendingRecords" :key="item.id" class="glass-card deferred-card">
          <text class="history-title">{{ item.taskTitle }}</text>
          <text class="text-muted">{{ item.recordedAt }} · {{ item.durationMinutes }} 分钟 · {{ item.status === "UNMATCHED" ? "暂未匹配" : "待分析" }}</text>
          <text class="raw">{{ item.content }}</text>
          <text class="text-muted">{{ item.lastAnalysis }}</text>
          <button class="glass-button retry-analysis-btn" :disabled="syncing || !!retryingId || importing" :loading="retryingId === item.id" @tap="retryRecord(item)">补分析</button>
        </view>
      </view>

      <view class="section-head">
        <text class="section-title">历史记录</text>
      </view>
      <view v-if="historyLoading" class="glass-card empty-card">
        <text>正在读取历史...</text>
      </view>
      <view v-else-if="historyError" class="glass-card empty-card state-card error-state">
        <text>{{ historyError }}</text><button class="glass-button retry-btn" @tap="loadData">重新加载</button>
      </view>
      <view v-else-if="history.length === 0" class="glass-card empty-card"><text>暂无行为记录</text></view>
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
import { deleteAction, getActionHistory, syncAction, updateMood, getDashboardStats, getTasks, saveDeferredLearningRecord, getDeferredLearningRecords, retryDeferredLearningRecord, importDeferredLearningRecords } from "../../services/api";

const plans = ref([]);
const selectedTaskId = ref(null);
const plansError = ref("");
const planLabels = computed(() => ["自动匹配全部进行中的计划", ...plans.value.map(p => p.title)]);
const selectedPlanIndex = computed(() => Math.max(0, plans.value.findIndex(p => p.id === selectedTaskId.value) + 1));
function selectPlan(event) {
  selectedTaskId.value = plans.value[Number(event.detail.value) - 1]?.id ?? null;
}
async function loadPlans() {
  try {
    plans.value = await getTasks({ status: "IN_PROGRESS" });
    if (!plans.value.some(p => p.id === selectedTaskId.value)) selectedTaskId.value = null;
    plansError.value = "";
  } catch {
    plansError.value = "计划列表加载失败，仍可尝试自动匹配";
  }
}
onShow(loadPlans);
const rawInput = ref("");
const duration = ref("");
const syncing = ref(false);
const syncResult = ref(null);
const resultIsRetry = ref(false);
const deferredRecords = ref([]);
const deferredLoading = ref(false);
const deferredError = ref("");
const retryingId = ref(null);
const importing = ref(false);
const pendingRecords = computed(() => deferredRecords.value.filter(r => r.status !== "APPLIED"));
async function loadDeferredRecords() {
  deferredLoading.value = true;
  try { deferredRecords.value = await getDeferredLearningRecords(); deferredError.value = ""; }
  catch { deferredError.value = "待分析记录加载失败，请重试"; }
  finally { deferredLoading.value = false; }
}
async function retryRecord(item) {
  if (syncing.value || retryingId.value || importing.value) return;
  retryingId.value = item.id;
  try {
    syncResult.value = await retryDeferredLearningRecord(item.id);
    resultIsRetry.value = true;
    await Promise.all([loadDeferredRecords(), loadData(), loadPlans()]);
    uni.showToast({ title: syncResult.value.updates?.length ? "补分析完成，进度已恢复" : "本次未更新进度，原记录保留", icon: "none" });
  } catch (error) { console.warn("retry learning record failed", error); }
  finally { retryingId.value = null; }
}
async function importLegacyRecords() {
  if (syncing.value || retryingId.value || importing.value) return;
  importing.value = true;
  try {
    const count = await importDeferredLearningRecords();
    await loadDeferredRecords();
    uni.showToast({ title: `已导入${count}条待分析记录`, icon: "none" });
  } catch (error) { console.warn("import legacy records failed", error); }
  finally { importing.value = false; }
}
onShow(loadDeferredRecords);
const history = ref([]);
const historyLoading = ref(false);
const historyError = ref("");
const anxietyLevel = ref(1);

const moodText = computed(() => {
  if (anxietyLevel.value <= 3) return "非常放松，动力十足";
  if (anxietyLevel.value <= 6) return "有点压力，正常节奏";
  if (anxietyLevel.value <= 8) return "比较焦虑，需要调整";
  return "极度焦虑，建议休息";
});

function formatProgress(value) {
  const actual = Math.max(0, Math.min(100, Number(value) || 0));
  const display = actual < 100 ? Math.min(actual, 99.9) : 100;
  return display.toFixed(display % 1 === 0 ? 0 : 1);
}

async function submitRecord() {
  if (syncing.value || retryingId.value || importing.value) return;
  resultIsRetry.value = false;
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
    syncResult.value = await syncAction(value, mins, selectedTaskId.value);
    if (!syncResult.value?.updates?.length) {
      uni.showToast({ title: "未更新进度，请查看分析结果", icon: "none" });
      return;
    }
    rawInput.value = "";
    duration.value = "";
    await Promise.all([loadData(), loadPlans(), loadDeferredRecords()]);
    uni.showToast({ title: "学习记录已保存", icon: "none" });
  } catch (error) {
    console.warn("sync action failed", error);
  } finally {
    syncing.value = false;
  }
}

function changeMood(event) {
  anxietyLevel.value = Number(event.detail.value);
}

async function submitManualCheckIn() {
  if (syncing.value || retryingId.value || importing.value || !selectedTaskId.value) return;
  const content = rawInput.value.trim();
  const match = duration.value.trim().match(/^(\d+(?:\.\d+)?)\s*(分钟|小时)?$/);
  if (!content || !match) {
    uni.showToast({ title: "请填写学习内容和明确时长，如30分钟", icon: "none" });
    return;
  }
  const minutes = Number(match[1]) * (match[2] === "小时" ? 60 : 1);
  if (!Number.isSafeInteger(minutes) || minutes < 0) {
    uni.showToast({ title: "学习时长应为非负整数分钟", icon: "none" });
    return;
  }
  syncing.value = true;
  try {
    await saveDeferredLearningRecord({ taskId: selectedTaskId.value, content, durationMinutes: minutes });
    rawInput.value = "";
    duration.value = "";
    syncResult.value = null;
    await Promise.all([loadData(), loadPlans(), loadDeferredRecords()]);
    uni.showToast({ title: "记录已保存并打卡，恢复后可补分析", icon: "none" });
  } catch (error) {
    console.warn("manual check-in failed", error);
  } finally {
    syncing.value = false;
  }
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
  historyError.value = "";
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
    history.value = [];
    historyError.value = "历史记录加载失败，请稍后重试";
  } finally {
    historyLoading.value = false;
  }
}

async function removeHistory(id) {
  try {
    await deleteAction(id);
    history.value = history.value.filter((item) => item.id !== id);
    syncResult.value = null;
    await Promise.all([loadPlans(), loadDeferredRecords()]);
    uni.showToast({ title: "记录已撤销", icon: "none" });
  } catch (error) {
    console.warn("delete action failed", error);
  }
}

onShow(loadData);
</script>

<style scoped>
.deferred-card { display: flex; flex-direction: column; gap: 12px; overflow-wrap: anywhere; }
.import-deferred-btn, .retry-analysis-btn { margin: 0; padding: 8px 14px; line-height: 1.5; font-size: 13px; }
.retry-analysis-btn { align-self: flex-end; }
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
.state-card { flex-direction: column; gap: 18rpx; text-align: center; }
.error-state { color: #a33b3b; }
.retry-btn { width: 200rpx; height: 68rpx; line-height: 68rpx; font-size: 23rpx; }
</style>
