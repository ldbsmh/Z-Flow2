# Z-Flow2

> 单内核小窗模块：只保留 NativeComplements（NC）引擎

Z-Flow 是一个基于 Xposed 的自由窗口增强模块，让应用以真实 freeform 任务的形式
以小窗运行在桌面上。本仓库是 2026-11 精简后的版本：经典虚拟屏内核、AIDL 链路、
引擎总开关与「回退到旧内核」的路径全部删除，开窗只有一条路，失败就是失败。

> 应用名与包名仍是 **Z-Flow** / `io.relimus.zflow`，可以直接覆盖安装旧版。

## 功能

| 入口 | 说明 |
|---|---|
| 悬浮侧边栏 | 收藏应用一键开窗 |
| 桌面长按图标 | 无需打开 Z-Flow |
| 多任务卡片上滑 | 直接接管被上滑的任务 |
| 通知栏小窗按钮 | 受通知白名单约束 |
| 快捷磁贴 | 打开应用选择器 |
| 底角轮盘 | 左右下角斜向上滑，三环 3+4+6 最多 13 个应用 |

运行规则：小窗黑名单、横屏应用列表、开新窗时上一个收成迷你窗、关闭时结束应用、
贴边保活、双击关闭、隐藏最近任务、横屏分享、通知气泡拦截。

外部应用可以调用开放 API 开窗，见 [docs/open-api_zh-Hans.md](docs/open-api_zh-Hans.md)。

## 运行前提

- Android 13（API 33）及以上，**LSPosed** 可用（引擎用 libxposed API 101）
- LSPosed 里勾选：**系统框架**、**SystemUI**、**默认桌面**、Z-Flow 自身
- 修改引擎代码后必须重载 SystemUI 或重启

## 构建

```bash
./gradlew :app:assembleDebug          # app/build/outputs/apk/debug/
./gradlew :app:testDebugUnitTest      # JVM 单测
python3 tests/storage_audit_regression_test.py   # 静态回归（不需要 Android SDK）
```

release 签名从根目录 `keystore.properties` 读取（模板见 `keystore.properties.example`），
CI 用 `SIGNING_*` 环境变量注入。两种都没配时只产出未签名 APK。

## 文档

| 文档 | 内容 |
|---|---|
| [docs/nc-window-engine.md](docs/nc-window-engine.md) | 引擎实现细节：Binder 协议、贴边、迷你窗、设置镜像 |
| [docs/nc-integration-status.md](docs/nc-integration-status.md) | 哪些功能接进了引擎、哪些没有、为什么 |
| [docs/radial-gesture.md](docs/radial-gesture.md) | 底角轮盘的手势判定与布局 |
| [docs/open-api_zh-Hans.md](docs/open-api_zh-Hans.md) | 开放 API（[English](docs/open-api.md)） |
| [docs/faq_zh-Hans.md](docs/faq_zh-Hans.md) | 常见问题 |
| [docs/notes/duplicate-window.md](docs/notes/duplicate-window.md) | 一次上滑开出两个窗口的排查记录 |
| [docs/history/review-fixes-2026-11.md](docs/history/review-fixes-2026-11.md) | 2026-11 三轮代码审查的修复记录（历史） |

## 依赖库

| 库 | 说明 |
|---|---|
| [libxposed API](https://github.com/libxposed/api) | 模块框架（API 101） |
| [ezxhelper](https://github.com/KyuubiRan/EzXHelper) | Hook 工具 |
| [RikkaX](https://github.com/RikkaApps/RikkaX) | 系统 API 兼容 |
| [AppIconLoader](https://github.com/zhanghai/AppIconLoader) / [Glide](https://github.com/bumptech/glide) | 图标与图片加载 |
| [TinyPinyin](https://github.com/promeG/TinyPinyin) | 拼音排序 |
| [MoreBubbleButton](https://github.com/TYOPXN360/MoreBubbleButton) | 通知气泡按钮方案参考 |

## 许可

- Copyright (C) 2021-2022 sunshine0523
- Copyright (C) 2023 DtHnAme
- Copyright (C) 2024-2026 relimus

小窗引擎移植自 NativeComplements，本项目继续以 GNU 通用公共许可证第 3 版
（或更新版本）发布，完整条款见 [LICENSE](LICENSE)。
