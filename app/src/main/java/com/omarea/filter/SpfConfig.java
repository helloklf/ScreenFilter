package com.omarea.filter;

public class SpfConfig {
    public static String FILTER_SPF = "FILTER_SPF";

    // 亮度偏移量（1表示1%,100表示100%）
    public static String BRIGTHNESS_OFFSET = "BRIGTHNESS_OFFSET_V3";
    public static int BRIGTHNESS_OFFSET_DEFAULT = 0;
    public static int BRIGTHNESS_OFFSET_LEVELS = 50;

    // 平滑亮度
    public static String SMOOTH_ADJUSTMENT = "SMOOTH_ADJUSTMENT";
    public static boolean SMOOTH_ADJUSTMENT_DEFAULT = true;

    // 息屏暂停
    public static String SCREEN_OFF_PAUSE = "SCREEN_OFF_PAUSE";
    public static boolean SCREEN_OFF_PAUSE_DEFAULT = true;

    // 息屏关闭滤镜
    public static String SCREEN_OFF_CLOSE = "SCREEN_OFF_CLOSE";
    public static boolean SCREEN_OFF_CLOSE_DEFAULT = false;

    // 横屏优化
    public static String LANDSCAPE_OPTIMIZE = "LANDSCAPE_OPTIMIZE";
    public static boolean LANDSCAPE_OPTIMIZE_DEFAULT = true;

    // 自动启动服务
    public static String FILTER_AUTO_START = "FILTER_AUTO_START";
    public static boolean FILTER_AUTO_START_DEFAULT = false;

    public static String FILTER_ALIGN_START = "FILTER_ALIGN_START";
    public static boolean FILTER_ALIGN_START_DEFAULT = false;

    // 屏幕最大亮度数值（通常是1-255，但是也有发现最大值可到2047甚至4095的）
    public static String SCREENT_MAX_LIGHT = "SCREENT_MAX_LIGHT";
    public static int SCREENT_MAX_LIGHT_DEFAULT = 255;

    // 目标设备
    public static String TARGET_DEVICE = "TARGET_DEVICE";
    public static int TARGET_DEVICE_AMOLED = 1;
    public static int TARGET_DEVICE_LCD = 2;

    // 屏幕最小亮度
    public static String SCREENT_MIN_LIGHT = "SCREENT_MAX_LIGHT";
    public static int SCREENT_MIN_LIGHT_DEFAULT = 255;

    // 隐藏最近任务
    public static String HIDE_IN_RECENT = "HIDE_IN_RECENT";
    public static boolean HIDE_IN_RECENT_DEFAULT = false;

    // 动态优化（虚拟环境）
    public static String DYNAMIC_OPTIMIZE = "DYNAMIC_OPTIMIZE";
    public static boolean DYNAMIC_OPTIMIZE_DEFAULT = false;

    // 动态优化（虚拟环境） 亮度限制(Lux)
    public static String DYNAMIC_OPTIMIZE_LIMIT = "DYNAMIC_OPTIMIZE_LIMIT";
    public static float DYNAMIC_OPTIMIZE_LIMIT_DEFAULT = 10;

    // 通知中心显示亮度控制器
    public static String BRIGHTNESS_CONTROLLER = "BRIGHTNESS_CONTROLLER";
    public static boolean BRIGHTNESS_CONTROLLER_DEFAULT = false;

    // 纹理素材
    public static String TEXTURE = "TEXTURE";
    public static int TEXTURE_DEFAULT = 0;
}
