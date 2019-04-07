package com.omarea.filter;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

public class LightSensorManager {
    private static LightSensorManager instance;
    private SensorManager mSensorManager;
    private SensorEventListener mLightSensorListener;
    private boolean mHasStarted = false;

    public LightSensorManager() {
    }

    public static LightSensorManager getInstance() {
        if (instance == null) {
            instance = new LightSensorManager();
        }
        return instance;
    }

    public void start(Context context, SensorEventListener sensorEventListener) {
        if (mHasStarted) {
            stop();
        }
        mHasStarted = true;
        mSensorManager = (SensorManager) context.getApplicationContext().getSystemService(Context.SENSOR_SERVICE);
        Sensor lightSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT); // 获取光线传感器
        if (lightSensor != null) { // 光线传感器存在时
            mLightSensorListener = sensorEventListener;
            mSensorManager.registerListener(mLightSensorListener, lightSensor,
                    SensorManager.SENSOR_DELAY_NORMAL); // 注册事件监听
        }
    }

    public void stop() {
        if (!mHasStarted || mSensorManager == null) {
            return;
        }
        mHasStarted = false;
        mSensorManager.unregisterListener(mLightSensorListener);
        mLightSensorListener = null;
    }
}