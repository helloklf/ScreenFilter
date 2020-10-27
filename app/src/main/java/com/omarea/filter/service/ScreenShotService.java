package com.omarea.filter.service;

import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.TileService;
import android.support.annotation.RequiresApi;
import android.util.Log;

import com.omarea.filter.ScreenCapActivity;

@RequiresApi(api = Build.VERSION_CODES.N)
public class ScreenShotService extends TileService {
    // 点击的时候
    @Override
    public void onClick() {
        try {
            Intent intent = new Intent(this, ScreenCapActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivityAndCollapse(intent);
        } catch (Exception ex) {
            Log.e("ScreenCapActivity", "!!!" + ex.getMessage());
        }
    }

    @Override
    public void onStartListening() {
        getQsTile().updateTile(); //更新Tile
    }
}