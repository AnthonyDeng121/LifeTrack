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
        <text class="section-title">每日正式打卡</text>
        <text class="hint">选好计划即可打卡。首条有效学习记录也会自动打卡，无需重复填写；打卡不增加学习进度或学习时长。</text>
        <view class="input-group"><text class="input-label">打卡计划</text>
          <picker :range="manualPlanLabels" :value="manualPlanIndex" :disabled="manualSaving" @change="selectManualPlan"><view class="glass-input">{{ manualPlanLabels[manualPlanIndex] }}</view></picker>
          <text v-if="plansError" class="hint">{{ plansError }}</text>
          <button v-if="plansError" class="glass-button" @tap="loadPlans">重新加载计划</button>
          <text v-else-if="!plans.length" class="hint">暂无有效周期内可打卡的计划。学习完成的计划也可继续打卡。</text>
        </view>
        <text class="hint">日期：{{ businessToday }}</text>
        <button class="glass-button sync-btn" :disabled="manualSaving || manualChecking || manualAlreadyChecked || syncing || !!retryingId" :loading="manualSaving || manualChecking" @tap="submitDailyCheckIn">{{ manualAlreadyChecked ? "今日已打卡" : "今日打卡" }}</button>
        <text v-if="manualMessage" class="hint" role="status">{{ manualMessage }}</text>
        <button v-if="manualResultTaskId" class="glass-button" @tap="viewManualHistory">查看该计划打卡历史</button>
      </view>

      <view class="glass-card input-card">
        <text class="section-title">AI 学习记录</text>
        <view class="input-group">
          <text class="input-label">所属学习计划</text>
          <picker :range="planLabels" :value="selectedPlanIndex" :disabled="syncing" @change="selectPlan">
            <view class="glass-input">{{ planLabels[selectedPlanIndex] }}</view>
          </picker>
          <text v-if="plansError" class="hint">{{ plansError }}</text>
        </view>
        <view class="input-group">
          <text class="input-label">行为描述</text>
          <textarea
            v-model="rawInput"
            :disabled="syncing"
            class="glass-input action-input"
            maxlength="300"
            placeholder="例如：我刚才写完了登录接口，整理了数据库表"
          />
        </view>

        <view class="input-group">
          <text class="input-label">实际学习时长</text>
          <view class="duration-fields">
            <input v-model="durationHours" type="number" class="glass-input duration-input" placeholder="0" :disabled="syncing" /><text>小时</text>
            <input v-model="durationMinutes" type="number" class="glass-input duration-input" placeholder="0" :disabled="syncing" /><text>分钟</text>
          </view>
          <text class="hint">小时和分钟均填整数，分钟为0～59；总时长1分钟～24小时。仅分析成功的记录计入学习时长。</text>
        </view>

        <button class="glass-button sync-btn" :disabled="syncing || manualSaving || !!retryingId" :loading="syncing" @tap="submitRecord">
          分析并保存记录
        </button>
        <text class="hint">
          只记录明确匹配的学习内容；范围不明确时请补充知识点，无关内容不会打卡。
        </text>
      </view>

      <view v-if="syncResult" class="glass-card result-card">
        <text class="result-title">AI 同步结果</text>
        <text v-if="syncResult.updates?.length" class="result-copy">{{ syncSummary }}</text>
        <text class="result-copy">{{ syncResult.aiAnalysis }}</text>
        <text v-if="syncResult.replayed" class="hint">该次提交已处理，未重复累计。可查看历史记录。</text>
        <text v-else-if="!syncResult.updates?.length" class="hint">原始记录和填写时长已保留，本次未累计时长或新增打卡。可在下方对该条记录重新分析。</text>
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
        <text class="section-title">尚未分析成功的记录</text>
      </view>
      <view class="glass-card input-group">
        <text class="input-label">按计划筛选学习历史与待分析记录</text>
        <picker :range="historyPlanLabels" :value="historyPlanIndex" :disabled="historyLoading || deferredLoading" @change="selectHistoryPlan"><view class="glass-input">{{ historyPlanLabels[historyPlanIndex] }}</view></picker>
      </view>
      <view v-if="deferredLoading" class="glass-card empty-card"><text>正在加载待分析记录...</text></view>
      <view v-else-if="deferredError" class="glass-card empty-card state-card"><text>{{ deferredError }}</text><button class="glass-button" @tap="loadDeferredRecords(deferredPage)">重新加载</button></view>
      <view v-else-if="pendingRecords.length === 0" class="glass-card empty-card"><text>没有待重新分析的记录</text></view>
      <view v-else class="history-list">
        <view v-for="item in pendingRecords" :key="item.id" class="glass-card deferred-card">
          <text class="history-title">{{ item.taskTitle }}</text>
          <text class="text-muted">{{ item.recordedAt }} · 填写 {{ item.durationMinutes }} 分钟（未计入）· {{ item.status === "UNMATCHED" ? "暂未匹配" : "尚未分析成功" }}</text>
          <text class="raw">{{ item.content }}</text>
          <text class="text-muted">{{ item.lastAnalysis }}</text>
          <button class="glass-button retry-analysis-btn" :disabled="syncing || !!retryingId" :loading="retryingId === item.id" @tap="retryRecord(item)">重新分析</button>
        </view>
      </view>
      <view v-if="deferredTotal" class="history-footer">
        <text class="hint">第 {{ deferredPage + 1 }} / {{ deferredPageCount }} 页，共 {{ deferredTotal }} 条待分析记录</text>
        <view class="history-pages">
          <button class="glass-button" :disabled="deferredLoading || deferredPage <= 0" @tap="previousDeferredPage">上一页</button>
          <button class="glass-button" :disabled="deferredLoading || deferredPage >= deferredPageCount - 1" @tap="nextDeferredPage">下一页</button>
        </view>
      </view>

      <view class="section-head">
        <text class="section-title">历史记录</text>
      </view>
      <text class="hint">学习记录可在同一天多次追加，正式打卡每天只有一条。已完成、人工结束或已取消计划的记录不能撤销。</text>
      <view v-if="historyLoading && history.length === 0" class="glass-card empty-card">
        <text>正在读取历史...</text>
      </view>
      <view v-if="historyError" class="glass-card empty-card state-card error-state">
        <text>{{ historyError }}</text><button class="glass-button retry-btn" :disabled="historyLoading" @tap="retryHistory">重新加载</button>
      </view>
      <view v-if="!historyLoading && !historyError && history.length === 0" class="glass-card empty-card"><text>暂无行为记录</text></view>
      <view v-if="history.length" class="history-list">
        <view v-for="item in history" :key="item.id" class="glass-card history-card">
          <view class="history-main">
            <view class="history-head">
              <text class="history-title">{{ item.taskTitle || item.category || "成长记录" }}</text>
              <text class="pill">+{{ formatProgress(item.contribution) }}%</text>
            </view>
            <text class="raw">{{ item.rawInput }}</text>
            <text class="text-muted">已计入时长：{{ item.durationMinutes || 0 }} 分钟</text>
            <text class="text-muted analysis">{{ item.aiAnalysis }}</text>
            <text class="text-muted time">{{ item.createdAt }}</text>
          </view>
          <button class="delete-btn" :disabled="!!undoingId || syncing || manualSaving || !!retryingId" @tap="removeHistory(item.id)">撤销</button>
        </view>
      </view>
      <view v-if="history.length" class="history-footer">
        <text class="hint">第 {{ historyPage + 1 }} / {{ historyPageCount }} 页，共 {{ historyTotal }} 条学习记录</text>
        <view class="history-pages">
          <button class="glass-button" :disabled="historyLoading || historyPage <= 0" @tap="previousHistoryPage">上一页</button>
          <button class="glass-button" :disabled="historyLoading || historyPage >= historyPageCount - 1" @tap="nextHistoryPage">下一页</button>
        </view>
      </view>

      <view class="tabbar-space"></view>
    </view>
    <LifeTabBar current="record" />
  </view>
</template>

<script setup>
import { computed, ref } from "vue";
import { onShow, onHide, onUnload, onLoad } from "@dcloudio/uni-app";
import LifeTabBar from "../../components/LifeTabBar.vue";
import { deleteAction, getActionHistory, syncAction, getTasks, createCheckIn, getCheckInHistory, getDeferredLearningRecords, retryDeferredLearningRecord } from "../../services/api";
import { getBusinessDate } from "../../services/businessTime";

const plans = ref([]);
const historyPlans = ref([]);
const historyTaskId = ref(null);
const historyPlanLabels = computed(() => ["全部学习计划", ...historyPlans.value.map(p => p.title)]);
const historyPlanIndex = computed(() => Math.max(0, historyPlans.value.findIndex(p => p.id === historyTaskId.value) + 1));
function selectHistoryPlan(event) {
  historyTaskId.value = historyPlans.value[Number(event.detail.value) - 1]?.id ?? null;
  loadHistory(0); loadDeferredRecords(0);
}
onLoad(options => { historyTaskId.value = Number(options.taskId) || null; });
const selectedTaskId = ref(null);
const plansError = ref("");
const businessToday = ref(getBusinessDate());
const manualTaskId = ref(null);
const manualSaving = ref(false);
const manualAlreadyChecked = ref(false);
const manualChecking = ref(false);
let manualStatusVersion = 0;
const manualMessage = ref("");
const manualResultTaskId = ref(null);
const manualPlanLabels = computed(() => ["请选择学习计划", ...plans.value.map(plan => plan.title)]);
const manualPlanIndex = computed(() => Math.max(0, plans.value.findIndex(plan => plan.id === manualTaskId.value) + 1));
function selectManualPlan(event) {
  manualTaskId.value = plans.value[Number(event.detail.value) - 1]?.id ?? null;
  manualMessage.value = "";
  manualResultTaskId.value = null;
  manualAlreadyChecked.value = false;
  loadManualAttendance();
}
async function loadManualAttendance() {
  const version = ++manualStatusVersion;
  const taskId = manualTaskId.value;
  if (!taskId) { manualChecking.value = false; manualAlreadyChecked.value = false; return; }
  manualChecking.value = true;
  manualAlreadyChecked.value = false;
  try {
    const date = getBusinessDate();
    const rows = await getCheckInHistory({ taskId, startDate: date, endDate: date });
    if (version !== manualStatusVersion) return;
    manualAlreadyChecked.value = rows.total > 0;
    if (manualAlreadyChecked.value) {
      manualResultTaskId.value = taskId;
      manualMessage.value = "今日已打卡。可继续追加学习记录，无需再次打卡或补写每日总结。";
    }
  } catch {
    if (version === manualStatusVersion) manualMessage.value = "暂时无法查询今日打卡状态，可尝试提交，由系统校验是否重复。";
  } finally { if (version === manualStatusVersion) manualChecking.value = false; }
}
function viewManualHistory() {
  if (manualResultTaskId.value) uni.navigateTo({ url: `/pages/task-detail/task-detail?id=${manualResultTaskId.value}` });
}
async function submitDailyCheckIn() {
  refreshBusinessDate();
  if (manualSaving.value || manualChecking.value || manualAlreadyChecked.value || syncing.value || retryingId.value) return;
  const plan = plans.value.find(item => item.id === manualTaskId.value);
  if (!plan) return uni.showToast({ title: "请选择进行中的学习计划", icon: "none" });
  const date = getBusinessDate();
  if (!["IN_PROGRESS", "COMPLETED"].includes(plan.status) || date < plan.startDate || date > plan.endDate) {
    return uni.showToast({ title: "仅有效周期内进行中或学习已完成的计划可打卡", icon: "none" });
  }
  const payload = { taskId: plan.id };
  manualSaving.value = true;
  manualMessage.value = "";
  manualResultTaskId.value = null;
  try {
    const saved = await createCheckIn(payload);
    manualResultTaskId.value = saved.taskId;
    manualAlreadyChecked.value = true;
    manualMessage.value = `${saved.checkInDate} 打卡成功，未增加学习进度或虚构学习时长。实际学习内容可在下方分次记录。`;
    uni.showToast({ title: "正式打卡成功", icon: "none" });
  } catch (error) {
    manualMessage.value = error.code === 409 ? "该计划当天已打卡，未新增记录或重复累计时长。继续学习可使用下方 AI 学习记录。" : error.message || "打卡失败，请检查网络后重试";
    if (error.code === 409) { manualResultTaskId.value = payload.taskId; manualAlreadyChecked.value = true; }
  } finally { manualSaving.value = false; }
}
const learningPlans = computed(() => plans.value.filter(p => p.status === "IN_PROGRESS"));
const planLabels = computed(() => ["请选择学习计划", ...learningPlans.value.map(p => p.title)]);
const selectedPlanIndex = computed(() => Math.max(0, learningPlans.value.findIndex(p => p.id === selectedTaskId.value) + 1));
function selectPlan(event) {
  selectedTaskId.value = learningPlans.value[Number(event.detail.value) - 1]?.id ?? null;
}
async function loadPlans() {
  try {
    const date = getBusinessDate();
    const rows = await getTasks();
    historyPlans.value = rows;
    plans.value = rows.filter(p => ["IN_PROGRESS", "COMPLETED"].includes(p.status) && p.startDate <= date && p.endDate >= date);
    if (!learningPlans.value.some(p => p.id === selectedTaskId.value)) selectedTaskId.value = null;
    if (!plans.value.some(p => p.id === manualTaskId.value)) manualTaskId.value = null;
    loadManualAttendance();
    plansError.value = "";
  } catch {
    plansError.value = "计划列表加载失败，仍可尝试自动匹配";
  }
}
onShow(loadPlans);
const rawInput = ref("");
const durationHours = ref("");
const durationMinutes = ref("");
const syncing = ref(false);
const syncResult = ref(null);
const resultIsRetry = ref(false);
const deferredRecords = ref([]);
const deferredLoading = ref(false);
const deferredError = ref("");
const retryingId = ref(null);
const deferredPage = ref(0);
const deferredTotal = ref(0);
const deferredPageSize = 20;
const deferredPageCount = computed(() => Math.max(1, Math.ceil(deferredTotal.value / deferredPageSize)));
let deferredVersion = 0;
const pendingRecords = computed(() => deferredRecords.value.filter(r => r.status !== "APPLIED"));
async function loadDeferredRecords(requestedPage = 0) {
  const page = Number.isInteger(requestedPage) && requestedPage > 0 ? requestedPage : 0;
  const version = ++deferredVersion;
  deferredLoading.value = true;
  try {
    const data = await getDeferredLearningRecords({ page, size: deferredPageSize, ...(historyTaskId.value ? { taskId: historyTaskId.value } : {}) });
    if (version !== deferredVersion) return;
    const last = Math.max(0, Math.ceil(data.total / deferredPageSize) - 1);
    if (page > last) return loadDeferredRecords(last);
    deferredRecords.value = data.list; deferredTotal.value = data.total; deferredPage.value = page; deferredError.value = "";
  } catch { if (version === deferredVersion) deferredError.value = "待分析记录加载失败，请重试"; }
  finally { if (version === deferredVersion) deferredLoading.value = false; }
}
function nextDeferredPage() { if (!deferredLoading.value && deferredPage.value < deferredPageCount.value - 1) return loadDeferredRecords(deferredPage.value + 1); }
function previousDeferredPage() { if (!deferredLoading.value && deferredPage.value > 0) return loadDeferredRecords(deferredPage.value - 1); }
async function retryRecord(item) {
  if (syncing.value || manualSaving.value || retryingId.value) return;
  retryingId.value = item.id;
  try {
    syncResult.value = await retryDeferredLearningRecord(item.id);
    resultIsRetry.value = true;
    await Promise.all([loadDeferredRecords(deferredPage.value), loadData(), loadPlans()]);
    uni.showToast({ title: syncResult.value.updates?.length ? "分析成功，时长已计入" : "分析未成功，时长未计入", icon: "none" });
  } catch (error) { console.warn("retry learning record failed", error); }
  finally { retryingId.value = null; }
}
onShow(loadDeferredRecords);
const history = ref([]);
const historyLoading = ref(false);
const historyError = ref("");
const historyTotal = ref(0);
const historyPage = ref(0);
const historyPageSize = 20;
const historyPageCount = computed(() => Math.max(1, Math.ceil(historyTotal.value / historyPageSize)));
let historyVersion = 0;
let requestedHistoryPage = 0;
const undoingId = ref(null);
const syncSummary = computed(() => {
  const result = syncResult.value;
  if (!result?.updates?.length) return "";
  const count = result.savedRecordCount ?? result.updates.length;
  const checkIns = result.newCheckInCount ?? result.updates.filter(item => item.checkedInNow).length;
  return `本次新增 ${count} 条步骤学习记录，新增 ${checkIns} 条正式打卡${result.durationMinutes == null ? "" : `，记录时长合计 ${result.durationMinutes} 分钟`}。同一计划当天继续记录不会重复打卡。`;
});

function formatProgress(value) {
  const actual = Math.max(0, Math.min(100, Number(value) || 0));
  const display = actual < 100 ? Math.min(actual, 99.9) : 100;
  return display.toFixed(display % 1 === 0 ? 0 : 1);
}

async function submitRecord() {
  if (syncing.value || manualSaving.value || retryingId.value) return;
  resultIsRetry.value = false;
  const value = rawInput.value.trim();
  const mins = readDurationMinutes();

  if (!value) {
    uni.showToast({ title: "先写一点今天的行为", icon: "none" });
    return;
  }
  if (!selectedTaskId.value) {
    uni.showToast({ title: "请选择学习计划", icon: "none" });
    return;
  }
  if (mins === null) {
    uni.showToast({ title: "请填写整数小时和0～59分钟，总时长1～1440分钟", icon: "none" });
    return;
  }

  syncing.value = true;
  try {
    const signature = JSON.stringify([selectedTaskId.value, value, mins]);
    if (submission.signature !== signature) submission = { signature, id: newRequestId() };
    syncResult.value = await syncAction(value, mins, selectedTaskId.value, submission.id);
    submission = {};
    rawInput.value = "";
    durationHours.value = "";
    durationMinutes.value = "";
    await Promise.all([loadData(), loadPlans(), loadDeferredRecords()]);
    uni.showToast({ title: syncResult.value?.updates?.length ? "分析成功，时长已计入" : "原记录已保存，时长未计入", icon: "none" });
  } catch (error) {
    console.warn("sync action failed", error);
    await loadDeferredRecords();
  } finally {
    syncing.value = false;
  }
}

let submission = {};
function newRequestId() {
  return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, c => {
    const n = Math.floor(Math.random() * 16); return (c === "x" ? n : (n & 3) | 8).toString(16);
  });
}
let dateTimer;
function refreshBusinessDate() {
  const current = getBusinessDate();
  if (current !== businessToday.value) {
    businessToday.value = current; manualAlreadyChecked.value = false; ++manualStatusVersion; manualChecking.value = false;
    manualMessage.value = "日期已更新，请重新确认今日打卡状态";
    loadPlans();
  }
}
onShow(() => { clearInterval(dateTimer); refreshBusinessDate(); dateTimer = setInterval(refreshBusinessDate, 1000); });
onHide(() => clearInterval(dateTimer));
onUnload(() => clearInterval(dateTimer));

function readDurationMinutes() {
  const hours = String(durationHours.value).trim();
  const minutes = String(durationMinutes.value).trim();
  if ((!hours && !minutes) || !/^\d*$/.test(hours) || !/^\d*$/.test(minutes)) return null;
  const h = Number(hours || 0), m = Number(minutes || 0);
  const total = h * 60 + m;
  return Number.isSafeInteger(h) && Number.isSafeInteger(m) && m <= 59 && total >= 1 && total <= 1440 ? total : null;
}

async function loadHistory(requestedPage = 0) {
  const page = Number.isInteger(requestedPage) && requestedPage > 0 ? requestedPage : 0;
  const version = ++historyVersion;
  requestedHistoryPage = page;
  historyLoading.value = true;
  historyError.value = "";
  try {
    const data = await getActionHistory(page, historyPageSize, historyTaskId.value);
    if (version !== historyVersion) return;
    const rows = Array.isArray(data) ? data : data?.list || [];
    const total = Number(data?.total ?? rows.length);
    const lastPage = Math.max(0, Math.ceil(total / historyPageSize) - 1);
    if (page > lastPage) return loadHistory(lastPage);
    history.value = rows;
    historyTotal.value = total;
    historyPage.value = page;
  } catch (error) {
    if (version === historyVersion) historyError.value = "历史记录加载失败，请稍后重试";
  } finally {
    if (version === historyVersion) historyLoading.value = false;
  }
}
function loadData() { return loadHistory(0); }
function nextHistoryPage() { if (!historyLoading.value && historyPage.value + 1 < historyPageCount.value) return loadHistory(historyPage.value + 1); }
function previousHistoryPage() { if (!historyLoading.value && historyPage.value > 0) return loadHistory(historyPage.value - 1); }
function retryHistory() { return loadHistory(requestedHistoryPage); }
async function removeHistory(id) {
  if (undoingId.value || syncing.value || manualSaving.value || retryingId.value) return;
  undoingId.value = id;
  try {
    await deleteAction(id);
    syncResult.value = null;
    await Promise.all([loadHistory(historyPage.value), loadPlans(), loadDeferredRecords()]);
    uni.showToast({ title: "记录已撤销", icon: "none" });
  } catch (error) {
    console.warn("delete action failed", error);
  } finally { undoingId.value = null; }
}

onShow(() => { businessToday.value = getBusinessDate(); loadData(); });
</script>

<style scoped>
.duration-fields { display: flex; align-items: center; gap: 12rpx; }
.duration-fields input { flex: 1; min-width: 0; width: 0; }
.history-footer { display: flex; flex-direction: column; align-items: center; gap: 12px; margin-top: 16px; }
.history-pages { display: flex; gap: 12px; }
.history-pages button { margin: 0; padding: 8px 18px; line-height: 1.5; font-size: 14px; }
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

.sync-btn {
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
