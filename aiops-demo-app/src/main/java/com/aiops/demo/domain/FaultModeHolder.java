package com.aiops.demo.domain;

/**
 * 故障模式共享状态。
 *
 * <p>控制器注入故障、上报任务读取故障，两者通过这个持有者解耦，
 * 避免控制器直接依赖定时任务。
 */
public final class FaultModeHolder {

    private static volatile String mode = "none";

    private static volatile double intensity = 0.9;

    private FaultModeHolder() {
    }

    public static String get() {
        return mode;
    }

    public static double intensity() {
        return intensity;
    }

    public static void set(String newMode, double newIntensity) {
        mode = newMode == null || newMode.isBlank() ? "none" : newMode.trim();
        intensity = Math.max(0, Math.min(1, newIntensity));
    }

    public static boolean isHealthy() {
        return "none".equals(mode);
    }
}
