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
        <input v-model="phone" class="glass-input input" type="number" placeholder="请输入手机号" />
        <view class="code-row glass-input">
          <input v-model="verifyCode" class="code-input" type="number" placeholder="请输入验证码" />
          <text class="code-action" @tap="sendCode">获取验证码</text>
        </view>

        <button class="glass-button primary-btn" :loading="loading" @tap="handlePhoneLogin">登录 / 注册</button>

        <view class="divider">
          <view></view>
          <text>其他登录方式</text>
          <view></view>
        </view>

        <button class="glass-button dev-btn" :loading="loading" @tap="handleDevLogin">开发环境一键登录</button>
      </view>

      <text class="agreement">登录即代表您已阅读并同意《用户协议》与《隐私政策》</text>
    </view>
  </view>
</template>

<script setup>
import { ref } from "vue";
import { devLogin, phoneLogin } from "../../services/api";

const phone = ref("");
const verifyCode = ref("");
const loading = ref(false);

function sendCode() {
  uni.showToast({ title: "演示验证码：123456", icon: "none" });
  verifyCode.value = "123456";
}

async function loginWith(action) {
  if (loading.value) return;
  loading.value = true;
  try {
    await action();
    uni.redirectTo({ url: "/pages/dashboard/dashboard" });
  } catch (error) {
    console.warn("login failed", error);
  } finally {
    loading.value = false;
  }
}

function handlePhoneLogin() {
  if (!phone.value || !verifyCode.value) {
    uni.showToast({ title: "请填写手机号和验证码", icon: "none" });
    return;
  }
  loginWith(() => phoneLogin(phone.value, verifyCode.value));
}

function handleDevLogin() {
  loginWith(() => devLogin());
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
