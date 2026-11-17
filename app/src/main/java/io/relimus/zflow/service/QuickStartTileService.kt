package io.relimus.zflow.service

import android.content.Intent
import android.service.quicksettings.TileService
import io.relimus.zflow.ui.floating.FloatingActivity

/**
 * @author sunshine
 * @date 2021/2/27
 * 通知栏快捷磁贴服务
 */
class QuickStartTileService : TileService() {

    override fun onClick() {
        super.onClick()
        // 磁贴拉起 Activity 并收起通知栏：startActivityAndCollapse 会一并折叠
        // 快捷设置面板，不必再借道 system_server 的 StatusBarManager。
        startActivityAndCollapse(
            Intent(this, FloatingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
