# Z-Flow Open API

[简体中文](open-api_zh-Hans.md)

External apps can ask Z-Flow to open a package in a freeform window. Every request is
a broadcast carrying a capability: the caller must first be whitelisted as a trusted
caller, then fetch a token bound to its own UID from the auth provider, and finally
attach that token to the broadcast. **Package names and custom extras are never used as
identity** — authorization is based on the Binder UID (see `security/BroadcastAuth.kt`).

## 0. Whitelist the caller

Settings → Launch & triggers → Desktop & gestures → **Trusted callers**, and tick your app.
Without it, the capability request in the next step throws `SecurityException`.

## 1. Obtain a capability

```kotlin
val result = contentResolver.call(
    Uri.parse("content://io.relimus.zflow.request.auth"),
    "issueCapability", null, null
) ?: error("Z-Flow is not available")

val authUid = result.getInt("io.relimus.zflow.extra.auth_uid")
val token = result.getString("io.relimus.zflow.extra.token")
```

The token is the hex HMAC-SHA256 of `"zflow.broadcast.v1:<uid>"` and is bound to the
caller's UID; forwarding it to another process is useless.

## 2. Send the request

```kotlin
val intent = Intent("io.relimus.zflow.start_freeform").apply {
    // Must match Z-Flow's internal component exactly, otherwise it is dropped.
    component = ComponentName(
        "io.relimus.zflow",
        "io.relimus.zflow.broadcast.FreeformRequestReceiver"
    )
    putExtra("packageName", "com.example.app")
    putExtra("activityName", "com.example.app.MainActivity") // optional
    putExtra("userId", 0)             // optional, defaults to 0
    putExtra("mini_mode", false)      // optional, true opens a mini window
    putExtra("source", "third_party") // optional log tag, <= 64 chars

    putExtra("io.relimus.zflow.extra.auth_uid", authUid)
    putExtra("io.relimus.zflow.extra.token", token)
}
context.sendBroadcast(intent)
```

| extra | Required | Meaning |
|---|---|---|
| `packageName` | one of these | Target package |
| `activityName` | one of these | Target activity; omitted means the launcher entry |
| `Intent.EXTRA_INTENT` | one of these | A component-bearing Intent, takes precedence |
| `userId` | no | Defaults to 0 (`UserHandle`) |
| `mini_mode` | no | Defaults to false; true opens a mini window directly |
| `task_id` | no | Internal: claims a task swiped away from Recents |
| `source` | no | Log tag |
| `auth_uid` / `token` | yes | Capability fetched in step 1 |

Requests also pass the freeform blacklist (Settings → Rules & interaction → Freeform
blacklist); a hit is rejected.

## 3. Open the app picker

The picker activity is `io.relimus.zflow.ui.floating.FloatingActivity`
(`exported=true`, also handles `ACTION_ASSIST`). Start it directly; the selected app
goes through the same path as above.
