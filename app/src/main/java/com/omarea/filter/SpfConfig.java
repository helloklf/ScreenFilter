package com.omarea.filter;

public class SpfConfig {
    public static String FILTER_SPF = "FILTER_SPF";

    // 亮度偏移量（1表示1%,100表示100%）
    public static String BRIGTHNESS_OFFSET = "BRIGTHNESS_OFFSET";
    public static int BRIGTHNESS_OFFSET_DEFAULT = 0;

    // 平滑亮度
    public static String SMOOTH_ADJUSTMENT = "SMOOTH_ADJUSTMENT";
    public static boolean SMOOTH_ADJUSTMENT_DEFAULT = true;

    // 深夜极暗光
    public static String SCREEN_OFF_PAUSE = "SCREEN_OFF_PAUSE";
    public static boolean SCREEN_OFF_PAUSE_DEFAULT = true;

    // 横屏优化
    public static String LANDSCAPE_OPTIMIZE = "LANDSCAPE_OPTIMIZE";
    public static boolean LANDSCAPE_OPTIMIZE_DEFAULT = true;

    // 自动启动服务
    public static String FILTER_AUTO_START = "FILTER_AUTO_START";
    public static boolean FILTER_AUTO_START_DEFAULT = false;

    // 屏幕最大亮度数值（通常是1-255，但是也有发现最大值可到2047甚至4095的）
    public static String SCREENT_MAX_LIGHT = "SCREENT_MAX_LIGHT";
    public static int SCREENT_MAX_LIGHT_DEFAULT = 255;

    // 屏幕最小亮度
    public static String SCREENT_MIN_LIGHT = "SCREENT_MAX_LIGHT";
    public static int SCREENT_MIN_LIGHT_DEFAULT = 255;

    // 隐藏最近任务
    public static String HIDE_IN_RECENT = "HIDE_IN_RECENT";
    public static boolean HIDE_IN_RECENT_DEFAULT = false;

    // 深夜极暗光
    public static String NIGHT_MODE = "NIGHT_MODE";
    public static boolean NIGHT_MODE_DEFAULT = true;
}
