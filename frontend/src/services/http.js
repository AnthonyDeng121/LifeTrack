import { API_BASE_STORAGE_KEY, TOKEN_KEY, USER_KEY, resolveApiBaseUrl } from "./config";

function getStorage(key) {
  try {
    return uni.getStorageSync(key);
  } catch (error) {
    return "";
  }
}

function setStorage(key, value) {
  try {
    uni.setStorageSync(key, value);
  } catch (error) {
    console.warn("storage failed", key, error);
  }
}

function removeStorage(key) {
  try {
    uni.removeStorageSync(key);
  } catch (error) {
    console.warn("remove storage failed", key, error);
  }
}

export function getToken() {
  return getStorage(TOKEN_KEY);
}

export function saveLogin(loginResponse) {
  if (!loginResponse) return;
  if (loginResponse.token) setStorage(TOKEN_KEY, loginResponse.token);
  setStorage(USER_KEY, {
    userId: loginResponse.userId,
    username: loginResponse.username,
    avatar: loginResponse.avatar,
  });
}

export function getUser() {
  return getStorage(USER_KEY) || {};
}

export function logout() {
  removeStorage(TOKEN_KEY);
  removeStorage(USER_KEY);
  uni.reLaunch({ url: "/pages/login/login" });
}

export function getApiBaseUrl() {
  return resolveApiBaseUrl({
    storedBase: getStorage(API_BASE_STORAGE_KEY),
    runtimeHost: getRuntimeHost(),
  });
}

export function buildUrl(path) {
  const base = getApiBaseUrl();
  return `${base}${path}`;
}

function getRuntimeHost() {
  // #ifdef H5
  if (typeof window !== "undefined" && window.location) {
    return window.location.hostname;
  }
  // #endif
  return "";
}

export function request({ method = "GET", path, data, mock, showError = true }) {
  const token = getToken();
  return new Promise((resolve, reject) => {
    uni.request({
      url: buildUrl(path),
      method,
      data,
      timeout: 8000,
      header: {
        "content-type": "application/json",
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      success: (res) => {
        const body = res.data || {};
        if (res.statusCode >= 200 && res.statusCode < 300 && body.code === 200) {
          resolve(body.data);
          return;
        }
        if (res.statusCode === 401 || body.code === 401) {
          logout();
          return;
        }
        const message = body.message || `请求失败：${res.statusCode}`;
        if (showError) uni.showToast({ title: message, icon: "none" });
        reject(new Error(message));
      },
      fail: (error) => {
        if (mock !== undefined) {
          resolve(mock);
          return;
        }
        if (showError) uni.showToast({ title: "后端服务暂不可用", icon: "none" });
        reject(error);
      },
    });
  });
}
