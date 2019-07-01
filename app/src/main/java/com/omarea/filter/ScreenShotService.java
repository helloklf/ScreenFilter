package com.omarea.filter;

import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.service.quicksettings.TileService;
import android.support.annotation.RequiresApi;
import android.widget.Toast;

@RequiresApi(api = Build.VERSION_CODES.N)
public class ScreenShotService extends TileService {
    private final Handler handler = new Handler();

    // 点击的时候
    @Override
    public void onClick() {
        Runnable screenCap = GlobalStatus.INSTANCE.getScreenCap();
        Runnable close = GlobalStatus.INSTANCE.getFilterClose();
        Runnable open = GlobalStatus.INSTANCE.getFilterOpen();
        boolean isEnabled = GlobalStatus.INSTANCE.getFilterEnabled();
        if  (screenCap != null) {
            if (isEnabled) {
                if (close != null) {
                    close.run();
                }
                try {
                    startActivityAndCollapse(new Intent(this, ScreenCapActivity.class));
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
                handler.postDelayed(screenCap, 500);
                if (open != null) {
                    handler.postDelayed(open, 5000);
                }
            } else {
                screenCap.run();
            }
        } else {
            Toast.makeText(this, "滤镜服务未启动", Toast.LENGTH_LONG).show();
        }
    }
}