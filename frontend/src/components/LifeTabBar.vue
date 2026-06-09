<template>
  <view class="custom-tabbar">
    <view
      v-for="item in items"
      :key="item.path"
      class="tab-item"
      :class="{ active: current === item.key }"
      @tap="go(item.path)"
    >
      <text class="tab-icon">{{ item.icon }}</text>
      <text>{{ item.label }}</text>
    </view>
  </view>
</template>

<script setup>
defineProps({
  current: {
    type: String,
    required: true,
  },
});

const items = [
  { key: "dashboard", label: "首页", icon: "⌂", path: "/pages/dashboard/dashboard" },
  { key: "tasks", label: "任务", icon: "✓", path: "/pages/tasks/tasks" },
  { key: "record", label: "记录", icon: "✎", path: "/pages/record/record" },
  { key: "profile", label: "我的", icon: "♡", path: "/pages/profile/profile" },
];

function go(path) {
  uni.redirectTo({ url: path });
}
</script>
