# Z-Flow 开放 API

[English](open-api.md)

外部应用可以让 Z-Flow 以自己的小窗打开指定应用。请求走一条带能力校验的广播：
调用方必须先在 Z-Flow 里被标记为「可信调用者」，再向授权 Provider 领取与自身 UID
绑定的 token，最后把 token 随广播一起发出。**只写包名或自定义 extra 冒充身份不会生效**
（校验只看 Binder UID，见 `security/BroadcastAuth.kt`）。

## 0. 把调用方加入白名单

设置 → 启动与触发 → 桌面与手势 → **可信调用者**，勾选你的应用。没有这一步，
下一步领取能力会直接抛 `SecurityException`。

## 1. 领取能力

```kotlin
val result = contentResolver.call(
    Uri.parse("content://io.relimus.zflow.request.auth"),
    "issueCapability", null, null
) ?: error("Z-Flow 未安装或不可用")

val authUid = result.getInt("io.relimus.zflow.extra.auth_uid")
val token = result.getString("io.relimus.zflow.extra.token")
```

token 是 HMAC-SHA256(密钥, "zflow.broadcast.v1:<uid>") 的十六进制串，与调用方 UID
绑定；把 token 转交给别的进程后对方也用不了。

## 2. 发送开窗请求

```kotlin
val intent = Intent("io.relimus.zflow.start_freeform").apply {
    // 必须与 Z-Flow 内部的 component 完全一致，否则接收器直接丢弃
    component = ComponentName(
        "io.relimus.zflow",
        "io.relimus.zflow.broadcast.FreeformRequestReceiver"
    )
    putExtra("packageName", "com.example.app")
    putExtra("activityName", "com.example.app.MainActivity") // 可选
    putExtra("userId", 0)          // 可选，默认 0；分身/工作资料用目标用户
    putExtra("mini_mode", false)   // 可选，true 直接开成迷你窗
    putExtra("source", "third_party") // 可选，日志标记，≤64 字符

    putExtra("io.relimus.zflow.extra.auth_uid", authUid)
    putExtra("io.relimus.zflow.extra.token", token)
}
context.sendBroadcast(intent)
```

| extra | 必填 | 说明 |
|---|---|---|
| `packageName` | 二者至少一个 | 目标应用包名 |
| `activityName` | 同上 | 目标 Activity；不填则由系统按桌面入口启动 |
| `Intent.EXTRA_INTENT` | 同上 | 直接给一个带 component 的 Intent，优先级高于上面两个 |
| `userId` | 否 | 默认 0，对应 `UserHandle` |
| `mini_mode` | 否 | 默认 false，true 时直接开成迷你窗 |
| `task_id` | 否 | 内部使用：最近任务上滑时由引擎认领该任务 |
| `source` | 否 | 日志标记 |
| `auth_uid` / `token` | 是 | 第 1 步领取到的能力 |

请求还会过一遍小窗黑名单（设置 → 运行规则与交互 → 小窗黑名单），命中就直接拒绝。

## 3. 打开应用选择界面

Z-Flow 的选择器 Activity 是 `io.relimus.zflow.ui.floating.FloatingActivity`
（`exported=true`，同时响应 `ACTION_ASSIST`）。直接 `startActivity` 即可，
它会列出全部已安装应用，选中后走与上面相同的开窗链路。
