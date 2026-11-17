package io.relimus.zflow.app

import android.app.Application
import android.content.Context
import com.google.android.material.color.DynamicColors
import org.lsposed.hiddenapibypass.HiddenApiBypass

/**
 * Application class for Z-Flow.
 *
 * 小窗内核跑在 system_server / SystemUI（ncengine），应用进程不再持有任何
 * system_server binder，因此这里没有“服务是否连上”的状态需要维护。
 */
class ZFlow : Application() {

    companion object {
        const val PACKAGE_NAME = "io.relimus.zflow"
        const val VERSION_PRIVACY = 1
        const val APP_SETTINGS_NAME = "app_settings"

        // ===== 兼容性修复开关 =====
        // 说明：微信小窗标题栏重复、小窗顶部空白、Telegram 画中画三项修复
        // 已从本项目移出，改由独立模块 Z-Flow-Compat-Patch 提供。

        /** 桌面长按 → 打开小窗 入口开关 */
        const val KEY_POPUP_FREEFORM_ENABLE = "popup_freeform_enable"

        /** 屏蔽桌面长按 → 消息气泡（Android 17+）开关 */
        const val KEY_POPUP_BUBBLE_BLOCK = "popup_bubble_block"

        /**
         * 底角上滑轮盘开关。
         *
         * 轮盘主体跑在 Launcher 进程（那里才有手势输入与图标），
         * 因此这个开关必须通过 Provider 跨进程读取，见
         * [io.relimus.zflow.providers.NotificationSettingsProvider] 的白名单。
         */
        const val KEY_RADIAL_GESTURE_ENABLE = "radial_gesture_enable"
    }

    override fun onCreate() {
        super.onCreate()

        DynamicColors.applyToActivitiesIfAvailable(this)
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        HiddenApiBypass.addHiddenApiExemptions("")
    }
}
