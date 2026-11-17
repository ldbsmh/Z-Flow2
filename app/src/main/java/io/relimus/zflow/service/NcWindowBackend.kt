package io.relimus.zflow.service

import android.content.Context
import android.content.Intent
import dev.local.sunwindowdemo.Engine
import io.relimus.zflow.xposed.hook.utils.XLog

/**
 * 小窗后端：把 Z-Flow 的入口接到 NativeComplements 内核。
 *
 * 内核固定为 NC：经典虚拟屏已整体移除，没有第二条开窗路径。
 * 该对象只负责“开哪个应用”，窗口控件、迷你窗、贴边停靠由 ncengine 的 SystemUI 宿主负责。
 */
object NcWindowBackend {

    private const val TAG = "NcWindowBackend"

    /** @return true 表示请求已交给引擎；false 表示引擎调用抛异常，调用方记日志即可。 */
    fun openApp(
        context: Context,
        packageName: String,
        miniMode: Boolean,
        taskId: Int = -1,
    ): Boolean {
        return try {
            Engine.openApp(context, packageName, taskId, miniMode)
            XLog.i("$TAG: OPEN via nc engine pkg=$packageName task=$taskId mini=$miniMode")
            // logcat 也能看到这一行：`adb logcat -s ZFlowMini`。
            // LSPosed 日志只有装模块的人看得见，排查「卡片上滑没反应」时两端都留个锚点。
            android.util.Log.i("ZFlowMini", "open pkg=$packageName task=$taskId mini=$miniMode")
            true
        } catch (t: Throwable) {
            XLog.e("$TAG: nc engine open failed for $packageName", t)
            android.util.Log.e("ZFlowMini", "open failed pkg=$packageName task=$taskId", t)
            false
        }
    }

    /** 通知整条 PendingIntent 开窗（保留着陆页）。 */
    fun openNotification(
        context: Context,
        packageName: String,
        launch: android.app.PendingIntent,
        fill: Intent?
    ): Boolean {
        return try {
            Engine.openNotification(context, packageName, launch, fill)
            XLog.i("$TAG: OPEN(notification) via nc engine pkg=$packageName")
            true
        } catch (t: Throwable) {
            XLog.e("$TAG: nc engine notification open failed for $packageName", t)
            false
        }
    }
}
