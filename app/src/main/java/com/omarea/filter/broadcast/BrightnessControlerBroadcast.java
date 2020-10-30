package com.omarea.filter.broadcast;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

import com.omarea.filter.GlobalStatus;
import com.omarea.filter.R;
import com.omarea.filter.SpfConfig;

@TargetApi(Build.VERSION_CODES.M)
public class BrightnessControlerBroadcast extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null) {
            String action = intent.getAction();
            if (action == null) {
                return;
            }

            if (!checkWriteSettings(context)) {
                Toast.makeText(context, "没有“修改系统设置”权限，请先为应用授权", Toast.LENGTH_LONG).show();
                return;
            }

            try {
                if (Settings.System.getInt(context.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE) == Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL) {
                    if (action.equals(context.getString(R.string.action_minus))) {
                        brightnessMinus(context);
                    } else if (action.equals(context.getString(R.string.action_plus))) {
                        brightnessPlus(context);
                    } else if (action.equals(context.getString(R.string.action_auto))) {
                        brightnessAutoMode(context);
                    }
                } else {
                    if (action.equals(context.getString(R.string.action_minus))) {
                        offsetMinus(context);
                    } else if (action.equals(context.getString(R.string.action_plus))) {
                        offsetPlus(context);
                    } else if (action.equals(context.getString(R.string.action_manual))) {
                        brightnessManualMode(context);
                    }
                }
            } catch (Exception ignored) {
            }
        }
    }

    private boolean checkWriteSettings(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.System.canWrite(context)) {
            return true;
        }
        return false;
    }

    private void brightnessMinus(Context context) {
        ContentResolver contentResolver = context.getContentResolver();
        try {
            SharedPreferences config = context.getSharedPreferences(SpfConfig.FILTER_SPF, Context.MODE_PRIVATE);
            int max = config.getInt(SpfConfig.SCREENT_MAX_LIGHT, SpfConfig.SCREENT_MAX_LIGHT_DEFAULT);
            int current = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS);
            if (current > max) {
                config.edit().putInt(SpfConfig.SCREENT_MAX_LIGHT, current).apply();
                max = current;
            }

            if (current > max * 0.9) {
                Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, current - (int) (max * 0.1));
            } else if (current > max * 0.5) {
                Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, current - (int) (max * 0.075));
            } else if (current > max * 0.2) {
                Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, current - (int) (max * 0.0255));
            } else if (current > 10) {
                Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, current - (int) (max * 0.0125));
            } else if (current > 1) {
                Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, current - 1);
            }
        } catch (Exception ex) {
        }
    }

    private void brightnessPlus(Context context) {
        ContentResolver contentResolver = context.getContentResolver();
        try {
            SharedPreferences config = context.getSharedPreferences(SpfConfig.FILTER_SPF, Context.MODE_PRIVATE);

            int max = config.getInt(SpfConfig.SCREENT_MAX_LIGHT, SpfConfig.SCREENT_MAX_LIGHT_DEFAULT);
            int current = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS);
            if (current > max) {
                config.edit().putInt(SpfConfig.SCREENT_MAX_LIGHT, current).apply();
                max = current;
            }

            if (current < 10) {
                Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, current + 1);
            } else if (current < max * 0.1) {
                Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, current + (int) (max * 0.0125));
            } else if (current < max * 0.2) {
                Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, current + (int) (max * 0.0255));
            } else if (current < max * 0.5) {
                Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, current + (int) (max * 0.075));
            } else if (current < max * 0.9) {
                Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, current + (int) (max * 0.1));
            } else {
                Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, max);
            }
        } catch (Exception ignored) {
        }
    }

    private void brightnessManualMode(Context context) {
        ContentResolver contentResolver = context.getContentResolver();
        try {
            Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
        } catch (Exception ignored) {
        }
    }

    private void brightnessAutoMode(Context context) {
        ContentResolver contentResolver = context.getContentResolver();
        try {
            Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC);
        } catch (Exception ex) {
        }
    }

    private void offsetMinus(Context context) {
        SharedPreferences config = context.getSharedPreferences(SpfConfig.FILTER_SPF, Context.MODE_PRIVATE);
        int currentValue = config.getInt(SpfConfig.BRIGTHNESS_OFFSET, SpfConfig.BRIGTHNESS_OFFSET_DEFAULT);
        if (currentValue > SpfConfig.BRIGTHNESS_OFFSET_LEVELS / -2) {
            config.edit().putInt(SpfConfig.BRIGTHNESS_OFFSET, currentValue - 1).apply();
            Runnable runnable = GlobalStatus.INSTANCE.getFilterRefresh();
            if (runnable != null) {
                runnable.run();
            }
        }
    }

    private void offsetPlus(Context context) {
        SharedPreferences config = context.getSharedPreferences(SpfConfig.FILTER_SPF, Context.MODE_PRIVATE);
        int currentValue = config.getInt(SpfConfig.BRIGTHNESS_OFFSET, SpfConfig.BRIGTHNESS_OFFSET_DEFAULT);
        if (currentValue < SpfConfig.BRIGTHNESS_OFFSET_LEVELS / 2) {
            config.edit().putInt(SpfConfig.BRIGTHNESS_OFFSET, currentValue + 1).apply();
            Runnable runnable = GlobalStatus.INSTANCE.getFilterRefresh();
            if (runnable != null) {
                runnable.run();
            }
        }
    }
}
