<template>
  <view class="page-shell">
    <view class="safe-area">
      <view class="top-row">
        <view>
          <text class="text-muted">任务中心</text>
          <text class="title-xl">拆小一点，更容易完成</text>
        </view>
        <button class="glass-button add-btn" @tap="openCreator">新建任务</button>
      </view>

      <view class="filter-group">
        <text class="filter-label">任务类型</text>
        <view class="category-row">
          <text v-for="item in categories" :key="item" class="pill category-pill"
            :class="{ active: activeCategory === item }" @tap="activeCategory = item">
            {{ item === "全部" ? "全部类型" : item }}
          </text>
        </view>
      </view>
      <view class="filter-group">
        <text class="filter-label">任务状态</text>
        <view class="category-row status-row">
          <text v-for="item in statusOptions" :key="item.value" class="pill category-pill"
            :class="{ active: activeStatus === item.value }" @tap="selectStatus(item.value)">{{ item.label }}</text>
        </view>
      </view>

      <view class="filter-group date-filter">
        <text class="filter-label">按计划周期筛选</text>
        <text class="text-muted">显示与日期范围有交集的计划，可只选开始或结束日期。</text>
        <view class="date-filter-row">
          <picker mode="date" :value="filterStartDate" @change="filterStartDate = $event.detail.value"><view class="glass-input">{{ filterStartDate || "选择开始日期" }}</view></picker>
          <picker mode="date" :value="filterEndDate" @change="filterEndDate = $event.detail.value"><view class="glass-input">{{ filterEndDate || "选择结束日期" }}</view></picker>
          <button class="glass-button" :disabled="loading || deleting" @tap="applyDateFilters">筛选计划</button>
          <button class="glass-button" :disabled="loading || deleting" @tap="resetDateFilters">清除日期</button>
        </view>
        <text v-if="dateFilterError" class="error-state">{{ dateFilterError }}</text>
      </view>

      <view class="section-head">
        <text class="section-title">我的任务</text>
        <button class="glass-button batch-toggle" :disabled="deleting" @tap="toggleBatchMode">{{ batchMode ? "完成" : "批量管理" }}</button>
      </view>

      <view v-if="batchMode" class="batch-toolbar glass-card">
        <button class="glass-button select-all-btn" :disabled="deleting || loading || !filteredTasks.length" @tap="toggleSelectAll">{{ allVisibleSelected ? "取消全选" : "全选当前列表" }}</button>
        <text>已选 {{ selectedIds.length }} 项</text>
        <button class="glass-button delete-selected" :disabled="!selectedIds.length || deleting" :loading="deleting" @tap="confirmBatchDelete">删除所选</button>
      </view>

      <view v-if="loading" class="glass-card empty-card">
        <text>正在加载任务...</text>
      </view>

      <view v-else-if="loadError" class="glass-card empty-card error-state">
        <text>{{ loadError }}</text>
        <button class="glass-button retry-btn" @tap="loadTasks">重新加载</button>
      </view>

      <view v-else-if="filteredTasks.length === 0" class="glass-card empty-card">
        <text>当前筛选条件下暂无计划</text>
        <text class="text-muted">可以清除日期、切换状态或分类，也可点右上角新建计划。</text>
      </view>

      <view v-else class="task-list">
        <view
          v-for="task in filteredTasks"
          :key="task.id"
          class="glass-card task-card"
          :class="{ selected: selectedIds.includes(task.id) }"
          @tap="openTask(task)"
        >
          <view class="task-head">
            <text v-if="batchMode" class="selection-mark" :aria-label="selectedIds.includes(task.id) ? '已选择' : '未选择'">{{ selectedIds.includes(task.id) ? "☑" : "☐" }}</text>
            <view>
              <text class="task-title">{{ task.title }}</text>
              <text class="text-muted">{{ task.category }} · {{ formatUpdatedAt(task.updatedAt) }}</text>
              <text class="text-muted">{{ task.startDate }} 至 {{ task.endDate }} · 目标 {{ task.targetDays }} 天</text>
            </view>
            <text class="pill status-badge" :class="'status-' + task.status">{{ task.statusDescription || "未知状态" }}</text>
          </view>
          <view class="task-bottom">
            <view class="progress-track">
              <view class="progress-fill" :style="{ width: clampProgress(task.totalProgress) + '%' }"></view>
            </view>
            <text>{{ formatProgress(task.totalProgress) }}%</text>
          </view>
        </view>
      </view>

      <view class="tabbar-space"></view>
    </view>

    <view v-if="showCreator" class="modal-mask" @tap="closeCreator">
      <view class="creator-dialog glass-card" @tap.stop>
        <view class="modal-head">
          <view>
            <text class="modal-eyebrow">新建任务</text>
            <text class="modal-title">{{ creatorMode === "ai" ? "让 AI 拆解目标" : "手动添加任务" }}</text>
          </view>
          <button class="close-btn" @tap="closeCreator">×</button>
        </view>

        <view class="mode-switch">
          <view
            class="mode-item"
            :class="{ active: creatorMode === 'ai' }"
            @tap="!creating && (creatorMode = 'ai')"
          >
            <text>AI 拆解</text>
            <text>自动生成子任务</text>
          </view>
          <view
            class="mode-item"
            :class="{ active: creatorMode === 'manual' }"
            @tap="!creating && (creatorMode = 'manual')"
          >
            <text>手动添加</text>
            <text>自己填写任务</text>
          </view>
        </view>

        <view class="form-block">
          <text class="field-label">{{ creatorMode === "ai" ? "想完成什么长期目标？" : "任务名称" }}</text>
          <input
            v-model="newTitle"
            class="glass-input creator-input"
            maxlength="100"
            :placeholder="creatorMode === 'ai' ? '例如：系统学习 Spring Boot' : '例如：完成数据库课设答辩'"
          />
        </view>

        <view class="period-grid">
          <view class="form-block">
            <text class="field-label">开始日期</text>
            <picker mode="date" :disabled="creating" :value="newStartDate" @change="newStartDate = $event.detail.value">
              <view class="glass-input date-picker">{{ newStartDate }}</view>
            </picker>
          </view>
          <view class="form-block">
            <text class="field-label">结束日期</text>
            <picker mode="date" :disabled="creating" :value="newEndDate" @change="newEndDate = $event.detail.value">
              <view class="glass-input date-picker">{{ newEndDate }}</view>
            </picker>
          </view>
        </view>
        <view class="form-block">
          <text class="field-label">目标打卡天数</text>
          <input v-model="newTargetDays" :disabled="creating" type="number" class="glass-input creator-input" placeholder="例如：20" />
          <text class="field-help">计划周期内希望坚持学习的天数；每天最多计1天，与子计划数量无关。</text>
        </view>

        <view class="form-block">
          <text class="field-label">选择分类</text>
          <view class="select-row">
            <text
              v-for="item in selectableCategories"
              :key="item"
              class="pill"
              :class="{ active: newCategory === item }"
              @tap="newCategory = item"
            >
              {{ item }}
            </text>
          </view>
        </view>

        <view class="backend-note">
          <text v-if="creatorMode === 'ai'">
            先预览拆解，可填写调整建议重新生成，确认后保存。
          </text>
          <text v-else>
            手动模式下，你可以自定义主任务和每一个具体的执行步骤。
          </text>
        </view>

        <view v-if="creatorMode === 'manual'" class="manual-subtasks-editor">
          <view class="field-label-row">
            <text class="field-label">拆解步骤 (至少一个)</text>
            <text class="add-subtask-link" @tap="addManualSubtask">+ 添加步骤</text>
          </view>
          <view
            v-for="(st, index) in manualSubtasks"
            :key="index"
            class="manual-subtask-row"
          >
            <view class="st-description-field">
              <text class="step-field-label">步骤 {{ index + 1 }}</text>
              <input v-model="st.content" class="glass-input st-content-input" placeholder="步骤描述" />
            </view>
            <view class="st-weight-field">
              <text class="step-field-label">权重（%）</text>
              <input v-model="st.weight" type="number" class="glass-input st-weight-input" placeholder="自动" />
            </view>
            <button class="remove-st-icon" :disabled="manualSubtasks.length <= 1" :aria-label="`删除步骤${index + 1}`" @tap="removeManualSubtask(index)">×</button>
          </view>
        </view>

        <view v-if="creatorMode === 'ai'" class="form-block">
          <text class="field-label">拆解要求与调整建议（可选）</text>
          <textarea v-model="decompositionGuidance" class="glass-input guidance-input" maxlength="1000" placeholder="例如：已有Java基础，重点练习接口和数据库，最后完成可运行的应用并通过测试" />
          <text class="field-label">步骤数量</text>
          <picker :range="stepCountOptions" :value="stepCountIndex" @change="stepCountIndex = Number($event.detail.value)">
            <view class="glass-input date-picker">{{ stepCountOptions[stepCountIndex] }}</view>
          </picker>
        </view>

        <view v-if="aiResult && creatorMode === 'ai'" class="ai-result">
          <text class="ai-title">拆解预览（尚未保存）</text>
          <view class="mini-subtasks">
            <text v-for="(item, index) in aiResult.subTasks" :key="item.content">{{ index + 1 }}. {{ item.content }}（{{ formatProgress(item.weight * 100) }}%）</text>
          </view>
          <button class="glass-button create-btn regenerate-btn" :disabled="creating" @tap="generatePreview">按当前建议重新生成</button>
        </view>

        <button class="glass-button create-btn" :disabled="creating" :loading="creating" @tap="createTask">
          {{ creating ? "正在处理，请稍候" : creatorMode === "ai" ? (aiResult ? "确认方案并保存" : "生成拆解预览") : "保存任务" }}
        </button>
      </view>
    </view>

    <LifeTabBar current="tasks" />
  </view>
</template>

<script setup>
import { computed, ref, watch } from "vue";
import { onShow } from "@dcloudio/uni-app";
import LifeTabBar from "../../components/LifeTabBar.vue";
import { previewTaskByAI, createTaskManually, getTasks, deleteTasks } from "../../services/api";
import { TASK_CATEGORIES } from "../../services/config";
import { validatePlanPeriod } from "../../services/planRules";

const categories = ["全部", ...TASK_CATEGORIES];
const selectableCategories = TASK_CATEGORIES;
const activeCategory = ref("全部");
const activeStatus = ref("");
const filterStartDate = ref("");
const filterEndDate = ref("");
const dateFilterError = ref("");
let appliedDateFilters = {};
let tasksVersion = 0;
function applyDateFilters() {
  if (loading.value || deleting.value) return;
  if (filterStartDate.value && filterEndDate.value && filterStartDate.value > filterEndDate.value) {
    dateFilterError.value = "筛选开始日期不能晚于结束日期";
    return;
  }
  dateFilterError.value = "";
  appliedDateFilters = { ...(filterStartDate.value ? { startDate: filterStartDate.value } : {}), ...(filterEndDate.value ? { endDate: filterEndDate.value } : {}) };
  selectedIds.value = [];
  return loadTasks();
}
function resetDateFilters() {
  if (loading.value || deleting.value) return;
  filterStartDate.value = ""; filterEndDate.value = "";
  return applyDateFilters();
}
const statusOptions = [
  { value: "", label: "全部状态" },
  { value: "NOT_STARTED", label: "未开始" },
  { value: "IN_PROGRESS", label: "进行中" },
  { value: "COMPLETED", label: "已完成" },
  { value: "ENDED", label: "人工结束" },
  { value: "CANCELLED", label: "已取消" },
  { value: "EXPIRED", label: "已过期" },
];
const tasks = ref([]);
const loading = ref(false);
const loadError = ref("");
const showCreator = ref(false);
const creatorMode = ref("ai");
const creating = ref(false);
const newTitle = ref("");
const newCategory = ref("学习");
const today = new Date();
const formatDate = (date) => {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
};
const defaultEnd = new Date(today);
defaultEnd.setDate(defaultEnd.getDate() + 29);
const newStartDate = ref(formatDate(today));
const newEndDate = ref(formatDate(defaultEnd));
const newTargetDays = ref("20");
const aiResult = ref(null);
const decompositionGuidance = ref("");
const stepCountOptions = ["自动决定", "2步", "3步", "4步", "5步", "6步", "7步", "8步", "9步", "10步"];
const stepCountIndex = ref(0);
let previewTitle = "";
let previewGuidance = "";
let previewStepCount = 0;
watch(newTitle, () => { aiResult.value = null; });

function formatUpdatedAt(value) {
  if (!value) return "更新时间未知";
  return `${String(value).replace("T", " ").slice(0, 16)} 更新`;
}

function validateCreatorForm() {
  const title = newTitle.value.trim();
  const targetDays = Number(newTargetDays.value);
  const error = !title ? "先写一个任务名称"
    : !TASK_CATEGORIES.includes(newCategory.value) ? "请选择有效分类"
    : validatePlanPeriod(newStartDate.value, newEndDate.value, targetDays);
  if (error) { uni.showToast({ title: error, icon: "none" }); return null; }
  return { title, startDate: newStartDate.value, endDate: newEndDate.value, targetDays };
}

async function generatePreview() {
  if (creating.value) return;
  const form = validateCreatorForm();
  if (!form) return;
  const title = form.title;
  const guidance = decompositionGuidance.value.trim();
  const countIndex = stepCountIndex.value;
  creating.value = true;
  try {
    const result = await previewTaskByAI({ ...form, guidance,
      stepCount: countIndex ? countIndex + 1 : null,
      previousSteps: aiResult.value?.subTasks.map(step => step.content) || [] });
    aiResult.value = result;
    previewTitle = title;
    previewGuidance = guidance;
    previewStepCount = countIndex;
  } catch (error) {
    uni.showToast({ title: error.message || "拆解失败，请重试或手动创建", icon: "none" });
  } finally { creating.value = false; }
}
const manualSubtasks = ref([{ content: "", weight: "" }]);

const statusOrder = { IN_PROGRESS: 0, NOT_STARTED: 1, COMPLETED: 2, ENDED: 3, CANCELLED: 4, EXPIRED: 5 };
const batchMode = ref(false);
const selectedIds = ref([]);
const deleting = ref(false);
watch(activeCategory, () => { selectedIds.value = []; });
const filteredTasks = computed(() => tasks.value
  .filter(task => activeCategory.value === "全部" || task.category === activeCategory.value)
  .sort((a, b) => (statusOrder[a.status] ?? 5) - (statusOrder[b.status] ?? 5)));
const allVisibleSelected = computed(() => filteredTasks.value.length > 0 && filteredTasks.value.every(task => selectedIds.value.includes(task.id)));

function toggleBatchMode() {
  if (deleting.value) return;
  batchMode.value = !batchMode.value;
  selectedIds.value = [];
}
function toggleSelectAll() {
  if (deleting.value) return;
  selectedIds.value = allVisibleSelected.value ? [] : filteredTasks.value.map(task => task.id);
}
function confirmBatchDelete() {
  if (deleting.value || !selectedIds.value.length) return;
  if (selectedIds.value.length > 100) {
    uni.showToast({ title: "每次最多删除100个任务，请减少选择", icon: "none" });
    return;
  }
  const ids = [...selectedIds.value];
  uni.showModal({
    title: "删除任务",
    content: `确定删除选中的 ${ids.length} 个任务及其打卡、学习记录？此操作无法撤销。`,
    confirmText: "删除",
    confirmColor: "#b42318",
    success: async ({ confirm }) => {
      if (!confirm || deleting.value) return;
      deleting.value = true;
      try {
        await deleteTasks(ids);
        selectedIds.value = [];
        await loadTasks();
        uni.showToast({ title: `已删除 ${ids.length} 个任务`, icon: "none" });
      } catch (error) {
        console.warn("batch delete failed", error);
      } finally {
        deleting.value = false;
      }
    },
  });
}

function formatProgress(value) {
  const actual = Math.max(0, Math.min(100, Number(value) || 0));
  const display = actual < 100 ? Math.min(actual, 99.9) : 100;
  return display.toFixed(display % 1 === 0 ? 0 : 1);
}

function clampProgress(value) {
  return Math.max(0, Math.min(100, Number(value || 0)));
}

async function loadTasks() {
  const version = ++tasksVersion;
  loading.value = true;
  loadError.value = "";
  try {
    const data = await getTasks({ ...appliedDateFilters, ...(activeStatus.value ? { status: activeStatus.value } : {}) });
    if (version !== tasksVersion) return;
    tasks.value = Array.isArray(data) ? data : [];
    selectedIds.value = selectedIds.value.filter(id => tasks.value.some(task => task.id === id));
  } catch (error) {
    if (version !== tasksVersion) return;
    console.warn("load tasks failed", error);
    tasks.value = [];
    loadError.value = "计划加载失败，请稍后重试";
  } finally {
    if (version === tasksVersion) loading.value = false;
  }
}

function selectStatus(status) {
  if (deleting.value) return;
  selectedIds.value = [];
  activeStatus.value = status;
  loadTasks();
}

function openTask(task) {
  if (batchMode.value) {
    if (deleting.value) return;
    selectedIds.value = selectedIds.value.includes(task.id)
      ? selectedIds.value.filter(id => id !== task.id) : [...selectedIds.value, task.id];
    return;
  }
  const query = `id=${task.id}&title=${encodeURIComponent(task.title)}&category=${encodeURIComponent(task.category)}&progress=${task.totalProgress || 0}`;
  uni.navigateTo({ url: `/pages/task-detail/task-detail?${query}` });
}

function openCreator() {
  aiResult.value = null;
  decompositionGuidance.value = "";
  showCreator.value = true;
}

function closeCreator() {
  if (creating.value) return;
  showCreator.value = false;
  manualSubtasks.value = [{ content: "", weight: "" }];
}

function addManualSubtask() {
  manualSubtasks.value.push({ content: "", weight: "" });
}

function removeManualSubtask(index) {
  if (manualSubtasks.value.length <= 1) {
    uni.showToast({ title: "至少需要一个步骤", icon: "none" });
    return;
  }
  manualSubtasks.value.splice(index, 1);
}

async function createTask() {
  if (creating.value) return;
  const form = validateCreatorForm();
  if (!form) return;
  if (creatorMode.value === "ai" && (!aiResult.value || previewTitle !== newTitle.value.trim()
      || previewGuidance !== decompositionGuidance.value.trim() || previewStepCount !== stepCountIndex.value)) {
    return generatePreview();
  }
  const { title, targetDays } = form;

  creating.value = true;
  try {
    let result;
    if (creatorMode.value === "ai") {
      result = await createTaskManually({ title, category: newCategory.value, startDate: newStartDate.value,
        endDate: newEndDate.value, targetDays, subTasks: aiResult.value.subTasks });
    } else {
      // 校验手动输入的子任务
      const validSubtasks = manualSubtasks.value.filter(st => st.content.trim());
      if (validSubtasks.length === 0) {
        uni.showToast({ title: "请至少填写一个子任务步骤", icon: "none" });
        creating.value = false;
        return;
      }
      
      const data = {
        title,
        category: newCategory.value,
        startDate: newStartDate.value,
        endDate: newEndDate.value,
        targetDays,
        subTasks: validSubtasks.map(st => ({
          content: st.content.trim(),
          weight: st.weight ? Number(st.weight) / 100 : null // 转换为 0-1 的小数
        }))
      };
      result = await createTaskManually(data);
    }
    
    showCreator.value = false;
    aiResult.value = null;
    manualSubtasks.value = [{ content: "", weight: "" }];
    activeStatus.value = "";
    await loadTasks();
    uni.showToast({
      title: "任务已保存",
      icon: "none",
    });
    newTitle.value = "";
    activeCategory.value = "全部";
  } catch (error) {
    console.warn("create task failed", error);
    if (creatorMode.value === "ai" && error.code === 503) {
      creatorMode.value = "manual";
      aiResult.value = null;
      uni.showToast({ title: "AI 暂不可用，已切换为手动创建", icon: "none", duration: 2500 });
    } else if (creatorMode.value === "ai") {
      uni.showToast({ title: error.message || "AI 请求未完成，请重试", icon: "none" });
    }
  } finally {
    creating.value = false;
  }
}

onShow(loadTasks);
</script>

<style scoped>
.filter-group.date-filter { display: flex; flex-direction: column; align-items: stretch; gap: 10px; }
.date-filter-row { display: flex; flex-wrap: wrap; gap: 10px; align-items: center; }
.date-filter-row picker { flex: 1 1 160px; }
.date-filter-row button { margin: 0; padding: 8px 14px; line-height: 1.5; font-size: 13px; }
.batch-toggle { width: 170rpx; font-size: 24rpx; margin: 0; }
.batch-toolbar { display: flex; align-items: center; gap: 12px; margin-bottom: 20rpx; flex-wrap: wrap; font-size: 14px; }
.select-all-btn, .delete-selected { padding: 10px 16px; min-height: 42px; line-height: 1.4; flex-shrink: 0; font-size: 14px; }
.delete-selected { margin: 0 0 0 auto; color: #b42318; font-size: 24rpx; }
.task-card.selected { outline: 2px solid #2378ad; }
.selection-mark { color: #176895; font-size: 36rpx; }
.creator-dialog { max-width: 960px; box-sizing: border-box; }
.creator-dialog > * { flex-shrink: 0; }
.guidance-input { width: 100%; min-height: 100px; box-sizing: border-box; padding: 16px; margin-bottom: 16px; }
/* H5 日期选择器切换视口时，关闭态的选项不应进入表单文档流。 */
.creator-dialog :deep(.uni-picker-custom:not(.uni-picker-toggle)) { display: none; }
.st-content-input { min-width: 0; }
.st-weight-input { flex-shrink: 0; }

.top-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24rpx;
}
.error-state { flex-direction: column; gap: 18rpx; color: #a33b3b; text-align: center; }
.retry-btn { width: 200rpx; height: 68rpx; line-height: 68rpx; font-size: 23rpx; }

.top-row view {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.add-btn {
  width: 174rpx;
  height: 72rpx;
  line-height: 72rpx;
  font-size: 24rpx;
}

.category-row,
.select-row {
  margin-top: 34rpx;
  display: flex;
  gap: 14rpx;
  flex-wrap: wrap;
}
.status-row {
  margin-top: 16rpx;
}

.period-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16rpx;
}

.date-picker {
  height: 82rpx;
  line-height: 82rpx;
  padding: 0 20rpx;
}

.category-pill.active,
.select-row .active {
  background: rgba(255, 255, 255, 0.66);
  color: #145f8e;
}

.small {
  font-size: 22rpx;
}

.task-list {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

.task-card {
  display: flex;
  flex-direction: column;
  gap: 26rpx;
}

.task-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24rpx;
}

.task-head view {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 10rpx;
}

.task-title {
  color: #2d3e50;
  font-size: 30rpx;
  font-weight: 900;
  line-height: 1.35;
}

.task-bottom {
  display: grid;
  grid-template-columns: 1fr 84rpx;
  align-items: center;
  gap: 18rpx;
  color: #236894;
  font-size: 26rpx;
  font-weight: 900;
}

.empty-card {
  min-height: 180rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12rpx;
  text-align: center;
}

.modal-mask {
  position: fixed;
  inset: 0;
  z-index: 40;
  padding: 160rpx 34rpx 48rpx;
  box-sizing: border-box;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  background: rgba(42, 137, 196, 0.24);
  backdrop-filter: blur(12rpx);
}

.creator-dialog {
  width: 100%;
  max-height: 82vh;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}

.modal-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24rpx;
}

.modal-head view {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.modal-eyebrow {
  color: rgba(45, 62, 80, 0.56);
  font-size: 22rpx;
  font-weight: 700;
}

.modal-title {
  color: #2d3e50;
  font-size: 38rpx;
  font-weight: 900;
  line-height: 1.25;
}

.close-btn {
  margin: 0;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 62rpx;
  height: 62rpx;
  border-radius: 50%;
  color: #236894;
  background: rgba(255, 255, 255, 0.42);
  font-size: 42rpx;
  line-height: 56rpx;
}

.mode-switch {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 14rpx;
}

.mode-item {
  min-height: 108rpx;
  padding: 20rpx;
  border-radius: 28rpx;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 8rpx;
  color: rgba(45, 62, 80, 0.58);
  background: rgba(255, 255, 255, 0.28);
  border: 2rpx solid rgba(255, 255, 255, 0.42);
}

.mode-item.active {
  color: #236894;
  background: rgba(255, 255, 255, 0.62);
  box-shadow: inset 0 2rpx 0 rgba(255, 255, 255, 0.78);
}

.mode-item text:first-child {
  font-size: 27rpx;
  font-weight: 900;
}

.mode-item text:last-child {
  font-size: 21rpx;
}

.form-block {
  display: flex;
  flex-direction: column;
  gap: 14rpx;
}

.field-label {
  color: #236894;
  font-size: 24rpx;
  font-weight: 900;
}

.creator-input {
  height: 92rpx;
}

.backend-note {
  padding: 0;
  border: 0;
  border-radius: 0;
  background: transparent;
  box-shadow: none;
  color: rgba(45, 62, 80, 0.62);
  font-size: 13px;
  line-height: 1.6;
}

.create-btn {
  height: 88rpx;
  line-height: 88rpx;
  font-size: 26rpx;
}

.ai-result {
  padding-top: 20rpx;
  border-top: 2rpx solid rgba(255, 255, 255, 0.38);
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

.ai-title {
  color: #236894;
  font-size: 26rpx;
  font-weight: 900;
}

.ai-copy {
  font-size: 24rpx;
  line-height: 1.5;
}

.mini-subtasks {
  display: flex;
  flex-direction: column;
  gap: 8rpx;
  color: rgba(45, 62, 80, 0.66);
  font-size: 23rpx;
  line-height: 1.45;
}
.field-label-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.add-subtask-link {
  font-size: 24rpx;
  color: #236894;
  font-weight: 700;
}

.manual-subtasks-editor {
  display: flex;
  flex-direction: column;
  gap: 18rpx;
}

.manual-subtask-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 112px 36px;
  align-items: end;
  gap: 10px;
}
.st-description-field, .st-weight-field { min-width: 0; display: flex; flex-direction: column; gap: 6px; }
.step-field-label { font-size: 13px; color: #236894; line-height: 1.4; }
.manual-subtask-row .glass-input { width: 100%; height: 46px; box-sizing: border-box; padding: 10px 12px; font-size: 14px; }
.st-weight-input { text-align: center; }
.remove-st-icon { margin: 0; padding: 0; width: 36px; height: 46px; display: flex; align-items: center; justify-content: center; font-size: 26px; color: #d33; }
.remove-st-icon[disabled] { opacity: 0.35; }
.filter-group { display: grid; grid-template-columns: 72px minmax(0, 1fr); align-items: start; gap: 12px; margin-top: 24px; }
.filter-group + .filter-group { margin-top: 12px; }
.filter-group .category-row { margin: 0; }
.filter-label { padding-top: 6px; font-size: 14px; font-weight: 700; color: #236894; }
.field-help { font-size: 12px; color: rgba(45, 62, 80, 0.68); line-height: 1.5; }
.form-block .select-row { margin-top: 0; }
@media screen and (max-width: 500px) {
  .manual-subtask-row { grid-template-columns: minmax(0, 1fr) 84px 30px; gap: 8px; }
  .remove-st-icon { width: 30px; }
}
</style>
