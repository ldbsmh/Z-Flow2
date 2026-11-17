package dev.local.sunwindowdemo;

import android.content.Context;
import android.content.SharedPreferences;

public final class Config {
    static final int DEFAULT_WINDOW_DIM=20;
    public static final String HOST_PREFERENCES="nativecomplements_corner";
    final SharedPreferences prefs;
    static volatile SharedPreferences hostPreferences;
    public Config(Context c) {
        prefs = hostPreferences!=null?hostPreferences:c.getSharedPreferences("corner", Context.MODE_PRIVATE);
    }
    int number(String key, int def, int min, int max) { return Rules.clamp(prefs.getInt(key, def), min, max); }
    boolean hideRecents() { return prefs.getBoolean("hideRecents", false); }
    // Keep the 0.9.7 automatic-mini behavior for existing installations without this key.
    boolean autoMinimizeOnOpen() { return prefs.getBoolean("autoMinimizeOnOpen", true); }
    boolean endOnClose() { return prefs.getBoolean("endOnClose", false); }
    boolean keepEdgeAwake() { return prefs.getBoolean("keepEdgeAwake", false); }
    boolean doubleClose() { return prefs.getBoolean("doubleClose", false); }
    boolean landscapeShareWindow() { return prefs.getBoolean("landscapeShareWindow", true); }
    int windowDim() { return number("windowDim",DEFAULT_WINDOW_DIM,0,100); }
    /** 贴边把手露出比例（百分比）。28% 只露一条边，100% 完整露出图标。 */
    int dockStyle() { return number("dockStyle",100,28,100); }
}
