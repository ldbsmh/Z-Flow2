// IFreeformManager.aidl
package io.relimus.zflow.xposed;

import android.app.PendingIntent;
import android.content.ComponentName;
import io.relimus.zflow.bean.MotionEventBean;

interface IFreeformManager {
    // Version info
    String getVersionName();
    int getVersionCode();
    int getUid();

    // Window management
    // 外观参数（DPI/尺寸/透明度/旋转/输入法）已无设置入口，由内核侧取默认值，
    // 这里只保留随请求变化、无法在服务端推导的几项。
    void createWindow(in ComponentName componentName, in PendingIntent pendingIntent,
                      int userId, int taskId,
                      int sourceRotation, int sourceScreenWidth, int sourceScreenHeight,
                      int dockStyle);

    void createMiniWindow(in ComponentName componentName, in PendingIntent pendingIntent,
                          int userId, int taskId,
                          int sourceRotation, int sourceScreenWidth, int sourceScreenHeight,
                          int dockStyle);
                          
    void destroyWindow(int displayId);
    void destroyAllWindows();
    void moveWindowToTop(int displayId);

    // Input injection (runs in system_server)
    void injectMotionEvent(in MotionEventBean event, int displayId);
    void injectKeyEvent(int keyCode, int displayId);

    // Task management
    void moveTaskToDisplay(int taskId, int displayId);
    void startActivityOnDisplay(in ComponentName componentName, int userId, int displayId);
    void sendPendingIntentOnDisplay(in PendingIntent pendingIntent, int displayId, int taskId);

    // Status
    void collapseStatusBarPanel();
    int getOpenWindowCount();

    // Service status
    boolean isServiceReady();
}