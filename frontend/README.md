# LifeTrack Frontend

LifeTrack 前端使用 uni-app + Vue3。这个目录可以在 macOS 和 Windows 上用同一套 npm 命令运行。

## 环境要求

- Node.js：建议使用 LTS 版本
- npm：随 Node.js 安装
- 编辑器：WebStorm / VS Code / HBuilderX 都可以

Windows 同学不需要安装 Xcode。只看 H5 或安卓模拟器时，按下面命令即可。

## 安装依赖

```bash
npm install
```

如果 Windows 上提示 `uni` 不是命令，通常是依赖没装好，重新执行 `npm install` 后用 npm scripts 运行，不要直接在终端输入 `uni`。

## 本机浏览器预览

```bash
npm run dev:h5
```

打开：

```text
http://127.0.0.1:5173/#/
```

## 安卓模拟器或局域网预览

```bash
npm run dev:h5:lan
```

Android 模拟器 Chrome 打开：

```text
http://10.0.2.2:5173/#/
```

同一局域网手机打开：

```text
http://你的电脑IP:5173/#/
```

## 后端联调

后端默认端口是 `8081`，API 前缀为：

```text
http://localhost:8081/api/v1
```

前端会自动处理这些场景：

- 电脑浏览器：请求 `localhost:8081`
- Android 模拟器：请求 `10.0.2.2:8081`
- 局域网手机：请求当前电脑 IP 的 `8081`

更完整的接口说明见：

```text
FRONTEND_BACKEND_INTEGRATION.md
```

## 常用命令

```bash
npm run test:contracts
npm run build:h5
npm run verify
```

`npm run verify` 会先检查接口契约，再构建 H5，适合提交前跑一次。

## 常见问题

- 页面打不开：确认 `npm run dev:h5:lan` 没有关闭。
- 安卓模拟器打不开 `127.0.0.1`：模拟器访问电脑要用 `10.0.2.2`。
- 登录后接口 401：先点“开发环境一键登录”，确认后端返回 token。
- 任务分类报错：只能使用后端支持的 `学习 / 娱乐 / 休息 / 运动 / 琐事`。
- H5 跨域：请让后端允许 `localhost:5173`、`127.0.0.1:5173`、`10.0.2.2:5173`。

## 不要提交

这些目录不需要提交到 Git：

- `node_modules`
- `dist`
- `unpackage`
