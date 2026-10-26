<template>
  <view class="page-shell login-page">
    <view class="safe-area login-content">
      <view class="cloud cloud-left"></view>
      <view class="cloud cloud-right"></view>

      <view class="logo glass-card">
        <text class="logo-cloud">☁</text>
        <text class="logo-star">✦</text>
      </view>

      <text class="title-xl app-name">LifeTrack AI</text>
      <text class="slogan">把每天的努力，看得见地记录下来</text>
      <text class="sub-slogan">让 AI 帮你看见每一次小小进步</text>

      <view class="form">
        <text v-if="optionsLoading" class="sub-slogan">正在查询可用登录方式...</text>
        <view v-if="optionsError"><text>{{ optionsError }}</text><button class="glass-button" @tap="loadAuthOptions">重新加载登录方式</button></view>
        <text v-if="!optionsLoading && !optionsError && !authOptions.devLoginEnabled && !authOptions.demoPhoneEnabled" class="sub-slogan">当前环境未开放演示登录，请联系管理员配置正式认证。</text>
        <template v-if="authOptions.demoPhoneEnabled">
        <text class="sub-slogan">仅用于本地演示，不发送真实短信。</text>
        <input v-model="phone" class="glass-input input" type="number" placeholder="请输入手机号" />
        <view class="code-row glass-input">
          <input v-model="verifyCode" class="code-input" type="number" placeholder="请输入验证码" />
          <text class="code-action" @tap="sendCode">查看演示验证码</text>
        </view>

        <button class="glass-button primary-btn" :disabled="loading" :loading="loading" @tap="handlePhoneLogin">演示手机号登录 / 注册</button>
        </template>

        <view v-if="authOptions.devLoginEnabled && authOptions.demoPhoneEnabled" class="divider">
          <view></view>
          <text>其他登录方式</text>
          <view></view>
        </view>

        <button v-if="authOptions.devLoginEnabled" class="glass-button dev-btn" :disabled="loading" :loading="loading" @tap="handleDevLogin">开发环境一键登录</button>
      </view>

      <text class="agreement">登录即代表您已阅读并同意《用户协议》与《隐私政策》</text>
    </view>
  </view>
</template>

<script setup>
import { ref } from "vue";
import { onShow } from "@dcloudio/uni-app";
import { devLogin, phoneLogin, getAuthOptions } from "../../services/api";

const phone = ref("");
const verifyCode = ref("");
const loading = ref(false);
const authOptions = ref({ devLoginEnabled: false, demoPhoneEnabled: false });
const optionsLoading = ref(false);
const optionsError = ref("");
async function loadAuthOptions() {
  if (optionsLoading.value) return;
  optionsLoading.value = true; optionsError.value = "";
  authOptions.value = { devLoginEnabled: false, demoPhoneEnabled: false };
  try { authOptions.value = await getAuthOptions(); }
  catch { optionsError.value = "登录方式加载失败，请检查服务后重试"; }
  finally { optionsLoading.value = false; }
}
onShow(loadAuthOptions);

function sendCode() {
  if (!authOptions.value.demoPhoneEnabled || !authOptions.value.demoVerifyCode) return;
  verifyCode.value = authOptions.value.demoVerifyCode;
  uni.showToast({ title: `演示验证码：${verifyCode.value}`, icon: "none" });
}

async function loginWith(action) {
  if (loading.value) return;
  loading.value = true;
  try {
    await action();
    uni.redirectTo({ url: "/pages/dashboard/dashboard" });
  } catch (error) {
    if (error.code === 403) await loadAuthOptions();
  } finally {
    loading.value = false;
  }
}

function handlePhoneLogin() {
  if (!authOptions.value.demoPhoneEnabled) return;
  if (!/^1[3-9]\d{9}$/.test(phone.value) || !/^\d{6}$/.test(verifyCode.value)) {
    uni.showToast({ title: "请输入有效的11位手机号和6位数字验证码", icon: "none" });
    return;
  }
  return loginWith(() => phoneLogin(phone.value, verifyCode.value));
}

function handleDevLogin() {
  if (authOptions.value.devLoginEnabled) return loginWith(() => devLogin());
}
</script>

<style scoped>
.login-page {
  padding-top: 140rpx;
}

.login-content {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.cloud {
  position: absolute;
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.68);
  box-shadow: 48rpx -14rpx 0 rgba(255, 255, 255, 0.54), 96rpx 8rpx 0 rgba(255, 255, 255, 0.42);
}

.cloud-left {
  width: 148rpx;
  height: 72rpx;
  left: 18rpx;
  top: 130rpx;
}

.cloud-right {
  width: 126rpx;
  height: 64rpx;
  right: 48rpx;
  top: 258rpx;
  opacity: 0.72;
}

.logo {
  width: 150rpx;
  height: 150rpx;
  border-radius: 44rpx;
  padding: 0;
  margin-bottom: 36rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
}

.logo-cloud {
  color: #236894;
  font-size: 72rpx;
}

.logo-star {
  position: absolute;
  right: 34rpx;
  top: 28rpx;
  color: #c9b8ff;
  font-size: 30rpx;
}

.app-name {
  margin-bottom: 14rpx;
}

.slogan {
  color: #236894;
  font-size: 30rpx;
  font-weight: 700;
}

.sub-slogan {
  margin-top: 8rpx;
  color: rgba(45, 62, 80, 0.58);
  font-size: 26rpx;
}

.form {
  width: 100%;
  margin-top: 88rpx;
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}

.input {
  height: 96rpx;
}

.code-row {
  height: 96rpx;
  padding: 0 28rpx;
  display: flex;
  align-items: center;
}

.code-input {
  flex: 1;
  height: 92rpx;
  color: #2d3e50;
}

.code-action {
  color: #236894;
  font-size: 24rpx;
  font-weight: 700;
}

.primary-btn,
.dev-btn {
  height: 96rpx;
  line-height: 96rpx;
  font-size: 30rpx;
}

.divider {
  margin: 24rpx 0 4rpx;
  display: flex;
  align-items: center;
  gap: 24rpx;
  color: rgba(45, 62, 80, 0.5);
  font-size: 22rpx;
}

.divider view {
  flex: 1;
  height: 2rpx;
  background: rgba(255, 255, 255, 0.48);
}

.dev-btn {
  font-size: 26rpx;
}

.agreement {
  position: fixed;
  left: 52rpx;
  right: 52rpx;
  bottom: calc(34rpx + env(safe-area-inset-bottom));
  text-align: center;
  color: rgba(45, 62, 80, 0.48);
  font-size: 20rpx;
}
</style>
