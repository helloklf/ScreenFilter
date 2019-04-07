package com.omarea.filter

import android.content.Context
import android.provider.Settings

object Utils {
    internal fun getSystemBrightness(context: Context): Int {
        var systemBrightness = 0;
        try {
            systemBrightness = Settings.System.getInt(context.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS);
        } catch (e: Settings.SettingNotFoundException) {
            e.printStackTrace();
        }
        return systemBrightness;
    }
}
