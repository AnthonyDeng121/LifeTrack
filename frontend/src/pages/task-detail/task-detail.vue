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

      <view v-if="detailLoading" class="glass-card empty-card state-card"><text>正在加载计划详情...</text></view>
      <view v-else-if="detailError" class="glass-card empty-card state-card error-state">
        <text>{{ detailError }}</text><button class="glass-button retry-btn" @tap="loadTask">重新加载</button>
      </view>
      <template v-if="!detailLoading && !detailError">

      <view class="glass-card progress-card">
        <view class="progress-circle">
          <text>{{ formatProgress(task.totalProgress) }}%</text>
          <text>学习进度</text>
        </view>
        <view class="progress-copy">
          <text class="section-title">子任务会驱动总进度</text>
          <text class="text-muted">
            完成一个学习步骤，计划进度就会随之更新。
          </text>
        </view>
      </view>

      <view class="glass-card plan-card">
        <view class="plan-info">
          <text>周期：{{ task.startDate }} 至 {{ task.endDate }}</text>
          <text>目标打卡：{{ task.targetDays }} 天</text>
          <text>累计学习：{{ Math.floor((task.learningDurationMinutes || 0) / 60) }} 小时 {{ (task.learningDurationMinutes || 0) % 60 }} 分钟（仅计入分析成功的记录）</text>
          <text class="pill status-badge" :class="'status-' + task.status">{{ task.statusDescription }}</text>
        </view>
        <button class="glass-button plan-edit-btn" @tap="openPlanEdit">编辑计划</button>
      </view>

      <view class="section-head">
        <text class="section-title">打卡概览</text>
      </view>
      <view v-if="!checkInLoading && !checkInError" class="checkin-metrics">
        <view class="glass-card metric-card"><text>{{ checkInStats.checkedInDays }}</text><text>已打卡天数</text></view>
        <view class="glass-card metric-card"><text>{{ checkInStats.currentStreak }}</text><text>当前连续</text></view>
        <view class="glass-card metric-card"><text>{{ checkInStats.longestStreak }}</text><text>最长连续</text></view>
        <view class="glass-card metric-card"><text>{{ task.status === 'COMPLETED' ? '已完成' : formatProgress(checkInStats.completionRate) + '%' }}</text><text>{{ task.status === 'COMPLETED' ? '学习目标' : '坚持目标达成率' }}</text></view>
      </view>
      <view v-if="!checkInLoading && !checkInError" class="target-feedback">
        <text>坚持记录：已打卡 {{ checkInStats.checkedInDays }} 天 · 原定 {{ checkInStats.targetDays || task.targetDays }} 天（{{ formatProgress(checkInStats.completionRate) }}%）</text>
        <text>{{ targetFeedback }}</text>
      </view>
      <view v-if="checkInLoading" class="glass-card empty-card"><text>正在加载打卡数据...</text></view>
      <view v-else-if="checkInError" class="glass-card empty-card state-card error-state">
        <text>{{ checkInError }}</text><button class="glass-button retry-btn" @tap="loadCheckIns">重新加载</button>
      </view>

      <view class="section-head">
        <text class="section-title">最近七天</text>
      </view>
      <view v-if="!checkInLoading && !checkInError" class="glass-card seven-day-card">
        <view v-for="day in checkInStats.recentSevenDays" :key="day.date" class="day-item" :class="{ checked: day.checkedIn }">
          <text>{{ shortDate(day.date) }}</text>
          <view class="day-dot">{{ day.checkedIn ? "✓" : "" }}</view>
          <text>{{ day.durationMinutes }}分</text>
        </view>
      </view>

      <view class="section-head">
        <text class="section-title">打卡历史</text>
      </view>
      <view class="glass-card history-filters">
        <text class="text-muted">仅筛选下方历史，概览和最近七天仍展示完整计划统计。</text>
        <view class="history-filter-row">
          <picker mode="date" :value="historyStartDate" @change="historyStartDate = $event.detail.value"><view class="glass-input">{{ historyStartDate || "开始日期" }}</view></picker>
          <picker mode="date" :value="historyEndDate" @change="historyEndDate = $event.detail.value"><view class="glass-input">{{ historyEndDate || "结束日期" }}</view></picker>
          <button class="glass-button" :disabled="checkInLoading || historyLoading" @tap="filterCheckInHistory">查询打卡</button>
          <button class="glass-button" :disabled="checkInLoading || historyLoading" @tap="resetCheckInHistory">清除日期</button>
        </view>
        <text v-if="historyFilterError" class="text-muted">{{ historyFilterError }}</text>
        <text v-if="historyLoading">正在查询打卡历史...</text>
        <text v-if="historyLoadError">{{ historyLoadError }}</text>
        <button v-if="historyLoadError" class="glass-button" @tap="loadFilteredHistory">重试历史查询</button>
      </view>
      <view v-if="!historyLoading && !historyLoadError && checkInHistory.length === 0" class="glass-card empty-card"><text>所选日期范围暂无正式打卡记录</text></view>
      <view v-if="checkInHistory.length" class="checkin-history">
        <view v-for="record in checkInHistory" :key="record.id" class="glass-card history-item">
          <view class="history-head"><text>{{ record.checkInDate }}</text><text v-if="record.durationMinutes > 0" class="pill">{{ record.durationMinutes }} 分钟</text><text v-else class="pill">已打卡</text></view>
          <text v-if="record.content" class="history-content">{{ record.automatic ? "关联学习片段" : "历史打卡备注" }}：{{ record.content }}</text>
          <text v-if="record.mood" class="text-muted small">心情：{{ record.mood }}</text>
        </view>
      </view>

      <view v-if="showPlanEdit" class="modal-mask" @tap="closePlanEdit">
        <view class="edit-dialog glass-card" @tap.stop>
          <view class="modal-head">
            <text class="modal-title">编辑学习计划</text>
            <button class="close-btn" aria-label="关闭弹窗" @tap="closePlanEdit">×</button>
          </view>
          <view class="form-block"><text class="field-label">计划名称</text><input v-model="planForm.title" class="glass-input edit-input" /></view>
          <view class="form-block"><text class="field-label">分类</text><picker :range="categories" :value="categoryIndex" @change="planForm.category = categories[$event.detail.value]"><view class="glass-input date-picker">{{ planForm.category }}</view></picker></view>
          <view class="form-block"><text class="field-label">开始日期</text><view v-if="planClosed" class="glass-input date-picker">{{ planForm.startDate }}</view><picker v-else mode="date" :disabled="savingPlan" :value="planForm.startDate" @change="planForm.startDate = $event.detail.value"><view class="glass-input date-picker">{{ planForm.startDate }}</view></picker></view>
          <view class="form-block"><text class="field-label">结束日期</text><view v-if="planClosed" class="glass-input date-picker">{{ planForm.endDate }}</view><picker v-else mode="date" :disabled="savingPlan" :value="planForm.endDate" @change="planForm.endDate = $event.detail.value"><view class="glass-input date-picker">{{ planForm.endDate }}</view></picker></view>
          <view class="form-block"><text class="field-label">目标打卡天数</text><input v-model="planForm.targetDays" :disabled="planClosed || savingPlan" type="number" class="glass-input edit-input" /></view>
          <view class="form-block"><text class="field-label">状态</text><view v-if="planClosed" class="glass-input date-picker">{{ currentStatusLabel }}</view><picker v-else :disabled="savingPlan" :range="statusLabels" :value="statusIndex" @change="planForm.status = statusOptions[$event.detail.value].value"><view class="glass-input date-picker">{{ currentStatusLabel }}</view></picker></view>
          <text class="text-muted small">{{ planClosed ? "已完成或已取消计划不可重新打开，仅可修改名称和分类。" : "未开始、进行中和已过期状态由计划周期决定。" }}</text>
          <button class="glass-button save-btn" :disabled="savingPlan" :loading="savingPlan" @tap="savePlan">保存计划</button>
          <text v-if="planConflict" class="text-muted">计划已有新修改，本次未保存。请先复制需要保留的输入，再载入最新计划重新编辑。</text>
          <button v-if="planConflict" class="glass-button" :disabled="savingPlan" @tap="openPlanEdit">载入最新计划重新编辑</button>
        </view>
      </view>

      <view class="section-head">
        <text class="section-title">子计划（{{ subtasks.length }}）</text>
        <button class="glass-button add-step-btn" :disabled="loading || saving || !stepsEditable" @tap="openAddDialog">新增子计划</button>
      </view>

      <view v-if="loading" class="glass-card empty-card">
        <text>正在加载子任务...</text>
      </view>

      <view v-else-if="subtaskError" class="glass-card empty-card state-card error-state">
        <text>{{ subtaskError }}</text><button class="glass-button retry-btn" @tap="loadSubtasks">重新加载</button>
      </view>

      <view v-else-if="subtasks.length === 0" class="glass-card empty-card"><text>该计划还没有拆解步骤</text></view>
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
              :disabled="isDone(item) || task.status !== 'IN_PROGRESS'"
              @tap.stop="finishSubtask(item)"
            >
              {{ isDone(item) ? "已完成" : "完成" }}
            </button>
            <button class="glass-button step-edit-btn" :disabled="!stepsEditable" @tap.stop="openEditDialog(item)">编辑</button>
            <button class="glass-button step-delete-btn" :disabled="subtasks.length <= 1 || saving || !stepsEditable" @tap.stop="handleDeleteSubtask(item)">删除</button>
          </view>
        </view>
      </view>

      <view v-if="showEditDialog" class="modal-mask" @tap="closeEditDialog">
        <view class="edit-dialog glass-card" @tap.stop>
          <view class="modal-head">
            <text class="modal-title">{{ editingSubtask.id ? "编辑子计划" : "新增子计划" }}</text>
            <button class="close-btn" aria-label="关闭弹窗" @tap="closeEditDialog">×</button>
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

          <button class="glass-button save-btn" :disabled="saving" :loading="saving" @tap="saveSubtask">{{ editingSubtask.id ? "保存修改" : "添加子计划" }}</button>
        </view>
      </view>

      <button class="glass-button record-action" @tap="goRecord">前往学习记录</button>
      <button class="glass-button delete-task-btn" @tap="handleDeleteTask">放弃这个目标</button>
      </template>
      <view class="tabbar-space"></view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref, watch } from "vue";
import { onLoad } from "@dcloudio/uni-app";
import { addSubtask, completeSubtask, getSubtasks, getTaskDetail, updateTask, deleteTask, deleteSubtask, updateSubtask, getCheckInHistory, getCheckInStats } from "../../services/api";
import { TASK_CATEGORIES } from "../../services/config";
import { validatePlanPeriod } from "../../services/planRules";
import { getBusinessDate } from "../../services/businessTime";

const task = ref({
  id: "",
  title: "任务详情",
  category: "任务",
  totalProgress: 0,
  startDate: "",
  endDate: "",
  targetDays: 0,
  status: "IN_PROGRESS",
  statusDescription: "进行中",
});
const detailLoading = ref(false);
const detailError = ref("");
const planClosed = computed(() => ["COMPLETED", "CANCELLED"].includes(task.value.status));
const planConflict = ref(false);
const stepsEditable = computed(() => ["NOT_STARTED", "IN_PROGRESS"].includes(task.value.status));
const categories = TASK_CATEGORIES;
const statusOptions = [
  { value: "NOT_STARTED", label: "未开始" }, { value: "IN_PROGRESS", label: "进行中" },
  { value: "COMPLETED", label: "已完成" }, { value: "CANCELLED", label: "已取消" },
  { value: "EXPIRED", label: "已过期" },
];
const statusLabels = statusOptions.map(item => item.label);
const showPlanEdit = ref(false);
const savingPlan = ref(false);
const planForm = ref({ title: "", category: "学习", startDate: "", endDate: "", targetDays: 1, status: "IN_PROGRESS" });
const categoryIndex = computed(() => Math.max(0, categories.indexOf(planForm.value.category)));
const statusIndex = computed(() => Math.max(0, statusOptions.findIndex(item => item.value === planForm.value.status)));
const currentStatusLabel = computed(() => statusOptions[statusIndex.value].label);
// 改周期时同步日期状态，避免提交旧状态。完成和取消仍由用户明确选择。
watch(() => [planForm.value.startDate, planForm.value.endDate], () => {
  if (planClosed.value || ["COMPLETED", "CANCELLED"].includes(planForm.value.status)) return;
  const today = getBusinessDate();
  planForm.value.status = planForm.value.endDate < today ? "EXPIRED"
    : planForm.value.startDate > today ? "NOT_STARTED" : "IN_PROGRESS";
}, { flush: "sync" });
const checkInHistory = ref([]);
const historyStartDate = ref("");
const historyEndDate = ref("");
const historyFilterError = ref("");
const historyLoadError = ref("");
const historyLoading = ref(false);
let appliedHistoryFilters = {};
let historyQueryVersion = 0;
function filterCheckInHistory() {
  if (checkInLoading.value || historyLoading.value) return;
  if (historyStartDate.value && historyEndDate.value && historyStartDate.value > historyEndDate.value) {
    historyFilterError.value = "查询开始日期不能晚于结束日期";
    return;
  }
  historyFilterError.value = "";
  appliedHistoryFilters = { ...(historyStartDate.value ? { startDate: historyStartDate.value } : {}), ...(historyEndDate.value ? { endDate: historyEndDate.value } : {}) };
  return loadFilteredHistory();
}
function resetCheckInHistory() {
  if (checkInLoading.value || historyLoading.value) return;
  historyStartDate.value = ""; historyEndDate.value = "";
  return filterCheckInHistory();
}
async function loadFilteredHistory() {
  if (!task.value.id) return;
  const version = ++historyQueryVersion;
  historyLoading.value = true;
  historyLoadError.value = "";
  try {
    const rows = await getCheckInHistory({ taskId: task.value.id, ...appliedHistoryFilters });
    if (version === historyQueryVersion) checkInHistory.value = Array.isArray(rows) ? rows : [];
  } catch {
    if (version === historyQueryVersion) historyLoadError.value = "打卡历史查询失败，已保留上次结果，请重试";
  } finally { if (version === historyQueryVersion) historyLoading.value = false; }
}
const checkInLoading = ref(false);
const checkInError = ref("");
const checkInStats = ref({ checkedInDays: 0, currentStreak: 0, longestStreak: 0, completionRate: 0, recentSevenDays: [] });
const targetFeedback = computed(() => {
  const remaining = Math.max(0, Number(checkInStats.value.targetDays || task.value.targetDays || 0) - Number(checkInStats.value.checkedInDays || 0));
  if (task.value.status === "COMPLETED") return remaining === 0
    ? "学习计划和打卡目标均已完成，已保留真实记录"
    : "学习目标已提前完成；坚持记录作为独立参考，不降低学习成果评价";
  if (remaining === 0) return "已达成打卡目标；学习进度单独计算，请继续完成剩余子计划";
  if (task.value.status === "EXPIRED") return `本期已结束，距打卡目标还差 ${remaining} 天`;
  if (task.value.status === "CANCELLED") return "计划已取消，已保留本期打卡记录";
  return `还差 ${remaining} 个打卡日达成目标`;
});
const subtasks = ref([]);
const loading = ref(false);
const subtaskError = ref("");
const showEditDialog = ref(false);
const saving = ref(false);
const editingSubtask = ref({
  id: "",
  content: "",
  weight: ""
});

function formatProgress(value) {
  const actual = Math.max(0, Math.min(100, Number(value) || 0));
  const display = actual < 100 ? Math.min(actual, 99.9) : 100;
  return display.toFixed(display % 1 === 0 ? 0 : 1);
}

function clampProgress(value) {
  return Math.max(0, Math.min(100, Number(value || 0)));
}

function formatWeight(value) {
  // 子计划列表接口统一返回百分数，1 表示 1%，不能再次乘100。
  return Number(Number(value || 0).toFixed(2));
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

async function loadTask() {
  if (!task.value.id) return;
  detailLoading.value = true;
  detailError.value = "";
  try {
    task.value = await getTaskDetail(task.value.id);
  } catch (error) {
    console.warn("load task detail failed", error);
    detailError.value = "计划详情加载失败";
  } finally {
    detailLoading.value = false;
  }
}

function shortDate(date) {
  if (!date) return "";
  const parts = date.split("-");
  return `${Number(parts[1])}/${Number(parts[2])}`;
}

async function loadCheckInStats() {
  if (!task.value.id) return;
  checkInLoading.value = true;
  checkInError.value = "";
  try {
    const stats = await getCheckInStats(task.value.id);
    checkInStats.value = stats || checkInStats.value;
  } catch (error) {
    console.warn("load check-ins failed", error);
    checkInError.value = "打卡数据加载失败";
  } finally {
    checkInLoading.value = false;
  }
}
function loadCheckIns() { return Promise.all([loadFilteredHistory(), loadCheckInStats()]); }

function openPlanEdit() {
  planConflict.value = false;
  planForm.value = {
    version: task.value.version,
    title: task.value.title,
    category: task.value.category,
    startDate: task.value.startDate,
    endDate: task.value.endDate,
    targetDays: task.value.targetDays,
    status: task.value.status,
  };
  showPlanEdit.value = true;
}

function closePlanEdit() { if (!savingPlan.value) showPlanEdit.value = false; }

async function savePlan() {
  if (savingPlan.value) return;
  if (!planForm.value.title.trim()) return uni.showToast({ title: "计划名称不能为空", icon: "none" });
  const periodError = validatePlanPeriod(planForm.value.startDate, planForm.value.endDate, Number(planForm.value.targetDays));
  if (periodError) return uni.showToast({ title: periodError, icon: "none" });
  savingPlan.value = true;
  try {
    const payload = { ...planForm.value, title: planForm.value.title.trim(), targetDays: Number(planForm.value.targetDays) };
    if (payload.startDate !== task.value.startDate || payload.endDate !== task.value.endDate) {
      // 每次保存重新读取，不能使用页面初次加载的旧历史作为删除确认依据。
      const history = await getCheckInHistory({ taskId: task.value.id });
      const outside = history.filter(record => record.checkInDate < payload.startDate || record.checkInDate > payload.endDate);
      if (outside.length) {
        const confirmed = await new Promise(resolve => uni.showModal({
          title: "确认修改计划周期？",
          content: `未在新计划周期内的打卡记录将清零，是否确认修改？共 ${outside.length} 条，删除后无法恢复。学习记录和学习进度保留。`,
          confirmText: "确认清零",
          cancelText: "暂不修改",
          success: result => resolve(result.confirm === true),
          fail: () => resolve(false),
        }));
        if (!confirmed) return;
        payload.discardOutsideCheckIns = true;
        payload.confirmedDiscardCheckInIds = outside.map(record => record.id);
      }
    }
    task.value = await updateTask(task.value.id, payload);
    showPlanEdit.value = false;
    uni.showToast({ title: "计划已更新", icon: "none" });
    await loadCheckIns();
  } catch (error) {
    console.warn("update task failed", error);
    if (error.code === 409) {
      planConflict.value = true;
      await loadTask();
    }
  } finally { savingPlan.value = false; }
}

async function loadSubtasks() {
  if (!task.value.id) return;
  loading.value = true;
  subtaskError.value = "";
  try {
    const data = await getSubtasks(task.value.id);
    subtasks.value = Array.isArray(data) ? data : [];
  } catch (error) {
    console.warn("load subtasks failed", error);
    subtasks.value = [];
    subtaskError.value = "计划步骤加载失败";
  } finally {
    loading.value = false;
  }
}

async function finishSubtask(item) {
  if (isDone(item) || task.value.status !== "IN_PROGRESS") return;
  try {
    await completeSubtask(item.id);
    item.isCompleted = 1;
    item.currentProgress = 100;
    await loadTask();
    uni.showToast({ title: "步骤已完成", icon: "none" });
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
  if (!stepsEditable.value) return;
  if (subtasks.value.length <= 1) {
    uni.showToast({ title: "至少保留一个子计划，请先添加替代步骤", icon: "none" });
    return;
  }
  uni.showModal({
    title: "删除子任务？",
    content: "删除后权重将自动分配给剩余步骤。",
    success: async (res) => {
      if (res.confirm) {
        try {
          await deleteSubtask(item.id);
          uni.showToast({ title: "已删除并重新分配权重", icon: "none" });
          await Promise.all([loadSubtasks(), refreshTaskProgress()]);
        } catch (error) {
          console.warn("delete subtask failed", error);
        }
      }
    },
  });
}

function openAddDialog() {
  if (!stepsEditable.value) return;
  editingSubtask.value = { id: null, content: "", weight: Math.max(1, Math.round(100 / (subtasks.value.length + 1))) };
  showEditDialog.value = true;
}

function openEditDialog(item) {
  if (!stepsEditable.value) return;
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
  if (saving.value || !stepsEditable.value) return;
  const { id, content, weight } = editingSubtask.value;
  if (!content.trim()) {
    uni.showToast({ title: "内容不能为空", icon: "none" });
    return;
  }
  if (!weight || !Number.isFinite(Number(weight)) || Number(weight) < 1 || Number(weight) > 100) {
    uni.showToast({ title: "请输入有效的权重 (1-100)", icon: "none" });
    return;
  }

  saving.value = true;
  try {
    const payload = { content: content.trim(), weight: Number(weight) / 100 };
    if (id) await updateSubtask(id, payload);
    else await addSubtask(task.value.id, payload);
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
  await loadTask();
}

onLoad((options) => {
  task.value = {
    id: options.id,
    title: decodeURIComponent(options.title || "任务详情"),
    category: decodeURIComponent(options.category || "任务"),
    totalProgress: Number(options.progress || 0),
  };
  loadSubtasks();
  loadTask();
  loadCheckIns();
});
</script>

<style scoped>
.history-filters { display: flex; flex-direction: column; gap: 10px; margin-bottom: 14px; }
.history-filter-row { display: flex; flex-wrap: wrap; gap: 10px; align-items: center; }
.history-filter-row picker { flex: 1 1 140px; }
.history-filter-row button { margin: 0; padding: 8px 14px; line-height: 1.5; font-size: 13px; }
.target-feedback { display: flex; flex-wrap: wrap; gap: 8px 24px; margin-top: 14px; color: #236894; font-size: 14px; }
.detail-top {
  display: flex;
  align-items: center;
  gap: 22rpx;
}
.state-card { margin-top: 20rpx; flex-direction: column; gap: 18rpx; text-align: center; }
.error-state { color: #a33b3b; }
.retry-btn { width: 200rpx; height: 68rpx; line-height: 68rpx; font-size: 23rpx; }

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
.plan-card {
  margin-top: 20rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20rpx;
}
.plan-info { display: flex; flex-direction: column; gap: 8rpx; color: rgba(45, 62, 80, 0.68); font-size: 23rpx; }
.plan-edit-btn { width: 150rpx; height: 66rpx; line-height: 66rpx; font-size: 22rpx; }
.date-picker { height: 82rpx; line-height: 82rpx; padding: 0 20rpx; }

.checkin-metrics { display: grid; grid-template-columns: repeat(2, 1fr); gap: 16rpx; }
.metric-card { min-height: 112rpx; display: flex; flex-direction: column; justify-content: center; gap: 6rpx; }
.metric-card text:first-child { color: #236894; font-size: 34rpx; font-weight: 900; }
.metric-card text:last-child { color: rgba(45, 62, 80, 0.58); font-size: 21rpx; }
.checkin-card { margin-top: 20rpx; display: flex; flex-direction: column; gap: 18rpx; }
.checkin-title-row, .history-head { display: flex; align-items: center; justify-content: space-between; gap: 18rpx; }
.checkin-content { width: 100%; min-height: 150rpx; }
.checkin-fields { display: grid; grid-template-columns: repeat(2, 1fr); gap: 14rpx; }
.checkin-fields .glass-input { width: 100%; height: 78rpx; padding: 18rpx 20rpx; }
.checkin-btn { width: 100%; min-height: 82rpx; font-size: 24rpx; }
.checkin-btn[disabled] { opacity: 0.55; }
.seven-day-card { display: flex; justify-content: space-between; gap: 8rpx; padding: 24rpx 18rpx; }
.day-item { flex: 1; display: flex; flex-direction: column; align-items: center; gap: 8rpx; color: rgba(45, 62, 80, 0.52); font-size: 18rpx; }
.day-dot { width: 38rpx; height: 38rpx; border-radius: 50%; display: flex; align-items: center; justify-content: center; background: rgba(255,255,255,.38); }
.day-item.checked { color: #236894; font-weight: 800; }
.day-item.checked .day-dot { background: rgba(107,190,255,.5); }
.checkin-history { display: flex; flex-direction: column; gap: 16rpx; }
.history-item { display: flex; flex-direction: column; gap: 12rpx; }
.history-head { color: #236894; font-weight: 800; }
.history-content { color: rgba(45, 62, 80, 0.78); font-size: 24rpx; line-height: 1.55; }

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
  grid-template-columns: 28px minmax(0, 1fr);
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
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

.subtask-title {
  overflow-wrap: anywhere;
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
  grid-column: 2;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.subtask-ops .glass-button {
  min-width: 68px;
  min-height: 38px;
  height: auto;
  padding: 8px 14px;
  line-height: 1.4;
  font-size: 14px;
  flex-shrink: 0;
}
.step-delete-btn { color: #b42318; }
.plan-info, .progress-copy, .detail-top > view { min-width: 0; overflow-wrap: anywhere; }
.plan-edit-btn { flex-shrink: 0; min-width: 90px; }
.plan-info .status-badge { align-self: flex-start; }
.edit-dialog { max-width: 560px; max-height: calc(100dvh - 48px); overflow-y: auto; padding: 24px; }
.modal-mask { position: fixed; inset: 0; z-index: 50; padding: 24px 16px; box-sizing: border-box; display: flex; align-items: center; justify-content: center; background: rgba(25, 63, 88, .35); backdrop-filter: blur(8px); }
.modal-head { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.modal-title { flex: 1; min-width: 0; font-size: 20px; font-weight: 800; }
.close-btn { width: 36px; height: 36px; margin: 0; padding: 0; display: flex; align-items: center; justify-content: center; line-height: 1; font-size: 28px; flex-shrink: 0; border: 0; }
.close-btn::after { border: 0; }
.add-step-btn { margin: 0; padding: 8px 14px; line-height: 1.4; font-size: 14px; flex-shrink: 0; }
.form-block { display: flex; flex-direction: column; gap: 8px; }
.field-label { font-size: 14px; font-weight: 700; color: #236894; }
@media screen and (min-width: 700px) {
  .subtask-card { grid-template-columns: 28px minmax(0, 1fr) auto; }
  .subtask-ops { grid-column: 3; }
  .checkin-metrics { grid-template-columns: repeat(4, minmax(0, 1fr)); }
}
@media screen and (max-width: 420px) {
  .plan-card { align-items: flex-start; flex-wrap: wrap; }
  .checkin-fields { grid-template-columns: minmax(0, 1fr); }
  .progress-card { gap: 16px; }
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
