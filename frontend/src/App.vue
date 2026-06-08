<script>
export default {
  onLaunch() {
    console.log("LifeTrack AI launched");
  },
};
</script>

<style>
page {
  min-height: 100%;
  color: #2d3e50;
  font-family: -apple-system, BlinkMacSystemFont, "Helvetica Neue", Arial, sans-serif;
  background: #8fd3ff;
}

button {
  margin: 0;
  padding: 0;
  background: transparent;
  border: 0;
  line-height: normal;
}

button::after {
  border: 0;
}

.page-shell {
  min-height: 100vh;
  box-sizing: border-box;
  padding: 96rpx 40rpx 160rpx;
  position: relative;
  overflow: hidden;
  isolation: isolate;
  background:
    radial-gradient(circle at 18% 12%, rgba(255, 255, 255, 0.72) 0 8%, transparent 19%),
    radial-gradient(circle at 82% 20%, rgba(255, 255, 255, 0.48) 0 6%, transparent 17%),
    radial-gradient(circle at 28% 82%, rgba(255, 255, 255, 0.36) 0 9%, transparent 22%),
    linear-gradient(160deg, #78caff 0%, #9dddff 42%, #d9f2ff 100%);
}

.page-shell::before {
  content: "";
  position: absolute;
  inset: 0;
  z-index: -2;
  opacity: 0.58;
  background-image: url("data:image/svg+xml,%3Csvg width='360' height='220' viewBox='0 0 360 220' xmlns='http://www.w3.org/2000/svg'%3E%3Cg fill='white' fill-opacity='.62'%3E%3Cpath d='M75 95c-24 0-43-18-43-40s19-40 43-40c11 0 21 4 29 11C113 15 127 9 143 9c29 0 53 21 56 48 5-2 10-3 16-3 21 0 38 15 38 34s-17 34-38 34H75V95Z'/%3E%3Cpath d='M254 185c-16 0-29-12-29-27s13-27 29-27c7 0 14 2 19 7 7-9 18-14 31-14 23 0 41 16 44 37 4-1 8-2 12-2 15 0 27 11 27 24s-12 24-27 24H254v-22Z'/%3E%3C/g%3E%3C/svg%3E");
  background-repeat: repeat;
  background-size: 360rpx 220rpx;
  background-position: 20rpx 8rpx;
}

.page-shell::after {
  content: "";
  position: absolute;
  inset: 0;
  z-index: -1;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.2), rgba(198, 235, 255, 0.22)),
    radial-gradient(circle at 50% 100%, rgba(255, 255, 255, 0.54), transparent 45%);
}

.safe-area {
  position: relative;
  z-index: 1;
}

.glass-card {
  box-sizing: border-box;
  border-radius: 40rpx;
  padding: 32rpx;
  background:
    linear-gradient(135deg, rgba(255, 255, 255, 0.56), rgba(255, 255, 255, 0.24)),
    linear-gradient(180deg, rgba(177, 225, 255, 0.22), rgba(255, 255, 255, 0.06));
  border: 2rpx solid rgba(255, 255, 255, 0.66);
  box-shadow:
    inset 0 2rpx 0 rgba(255, 255, 255, 0.72),
    0 24rpx 60rpx rgba(45, 137, 196, 0.18);
  backdrop-filter: blur(24rpx);
}

.glass-button {
  border-radius: 999rpx;
  background:
    linear-gradient(135deg, rgba(255, 255, 255, 0.72), rgba(255, 255, 255, 0.28)),
    linear-gradient(135deg, rgba(167, 216, 255, 0.62), rgba(107, 190, 255, 0.28));
  border: 2rpx solid rgba(255, 255, 255, 0.72);
  box-shadow:
    inset 0 2rpx 0 rgba(255, 255, 255, 0.85),
    0 16rpx 32rpx rgba(70, 154, 210, 0.22);
  color: #236894;
  font-weight: 700;
}

.glass-input {
  box-sizing: border-box;
  border-radius: 28rpx;
  padding: 26rpx 28rpx;
  background: rgba(255, 255, 255, 0.46);
  border: 2rpx solid rgba(255, 255, 255, 0.68);
  box-shadow:
    inset 0 2rpx 0 rgba(255, 255, 255, 0.62),
    0 10rpx 28rpx rgba(80, 160, 220, 0.12);
  color: #2d3e50;
}

.title-xl {
  font-size: 48rpx;
  line-height: 1.25;
  color: #2d3e50;
  font-weight: 800;
}

.title-lg {
  font-size: 40rpx;
  line-height: 1.25;
  color: #2d3e50;
  font-weight: 800;
}

.text-muted {
  color: rgba(45, 62, 80, 0.58);
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 42rpx 0 20rpx;
}

.section-title {
  color: #2d3e50;
  font-size: 32rpx;
  font-weight: 800;
}

.pill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 999rpx;
  padding: 8rpx 20rpx;
  background: rgba(255, 255, 255, 0.38);
  border: 2rpx solid rgba(255, 255, 255, 0.52);
  color: #236894;
  font-size: 22rpx;
  font-weight: 700;
}

.progress-track {
  height: 16rpx;
  border-radius: 999rpx;
  overflow: hidden;
  background: rgba(255, 255, 255, 0.42);
}

.progress-fill {
  height: 100%;
  border-radius: 999rpx;
  background: linear-gradient(90deg, #a7d8ff, #6bbeff);
}

.tabbar-space {
  height: 136rpx;
}

.custom-tabbar {
  position: fixed;
  left: 32rpx;
  right: 32rpx;
  bottom: calc(24rpx + env(safe-area-inset-bottom));
  z-index: 20;
  height: 112rpx;
  border-radius: 36rpx;
  display: flex;
  align-items: center;
  justify-content: space-around;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.58), rgba(255, 255, 255, 0.28));
  border: 2rpx solid rgba(255, 255, 255, 0.72);
  box-shadow:
    inset 0 2rpx 0 rgba(255, 255, 255, 0.75),
    0 18rpx 48rpx rgba(45, 137, 196, 0.25);
  backdrop-filter: blur(26rpx);
}

.tab-item {
  width: 148rpx;
  height: 82rpx;
  border-radius: 28rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4rpx;
  color: rgba(45, 102, 140, 0.62);
  font-size: 22rpx;
  font-weight: 600;
}

.tab-item.active {
  color: #236894;
  background: rgba(255, 255, 255, 0.42);
  box-shadow: inset 0 2rpx 0 rgba(255, 255, 255, 0.72);
}

.tab-icon {
  font-size: 34rpx;
  line-height: 1;
}
</style>
