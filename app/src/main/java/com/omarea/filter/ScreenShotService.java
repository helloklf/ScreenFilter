package com.omarea.filter;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.widget.Toast;

import java.io.OutputStream;
import java.nio.charset.Charset;

@RequiresApi(api = Build.VERSION_CODES.N)
public class ScreenShotService extends TileService {
    private final String LOG_TAG = "ScreenShotService";

    //当用户从Edit栏添加到快速设定中调用
    @Override
    public void onTileAdded() {
    }

    //当用户从快速设定栏中移除的时候调用
    @Override
    public void onTileRemoved() {
    }

    // 点击的时候
    @Override
    public void onClick() {
        getQsTile().setState(Tile.STATE_ACTIVE);
        getQsTile().updateTile();

        Runnable close = GlobalStatus.INSTANCE.getFilterOpen();
        if (close != null) {
            close.run();
        }

        // 没有root或adb权限无法执行
        String output = Environment.getExternalStorageDirectory().getAbsolutePath() + "/Pictures/" + System.currentTimeMillis() + ".png";
        Log.d(LOG_TAG, output);

        try {
            Handler handler = new Handler();
            String cmd = "screencap -p \"" + output + "\"";
            final Process process = Runtime.getRuntime().exec("sh");
            if (process != null) {
                OutputStream outputStream = process.getOutputStream();
                Log.d(LOG_TAG, cmd);
                outputStream.write(cmd.getBytes(Charset.defaultCharset()));
                outputStream.flush();
            } else {
                throw new Exception("");
            }
            Runnable open = GlobalStatus.INSTANCE.getFilterOpen();
            if (open != null) {
                handler.postDelayed(open, 5000);
            }
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    process.destroy();
                }
            }, 5000);
            Toast.makeText(this, getString(R.string.screenshot_tile_output) + "\n" + output, Toast.LENGTH_LONG).show();
        } catch (Exception ex) {
            Log.d(LOG_TAG, ex.getMessage());
            Toast.makeText(this, R.string.screenshot_tile_fail, Toast.LENGTH_SHORT).show();
        }
    }

    // 打开下拉菜单的时候调用,当快速设置按钮并没有在编辑栏拖到设置栏中不会调用
    //在TleAdded之后会调用一次
    @Override
    public void onStartListening() {
        getQsTile().setState(Tile.STATE_INACTIVE);
        getQsTile().updateTile();
    }

    // 关闭下拉菜单的时候调用,当快速设置按钮并没有在编辑栏拖到设置栏中不会调用
    // 在onTileRemoved移除之前也会调用移除
    @Override
    public void onStopListening() {
    }
}