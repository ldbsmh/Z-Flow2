package dev.local.sunwindowdemo;

/** Pure geometry and validation, shared by UI and JVM checks. */
public final class Rules {
    public static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
}
