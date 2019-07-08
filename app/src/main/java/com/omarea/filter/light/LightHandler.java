package com.omarea.filter.light;

public interface LightHandler {
    void onLuxChange(float lux);

    void onBrightnessChange(int brightness);

    void onModeChange(boolean auto);
}
