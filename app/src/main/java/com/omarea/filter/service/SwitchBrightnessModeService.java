package com.omarea.filter.service;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.support.annotation.RequiresApi;
import android.widget.Toast;

import com.omarea.filter.R;
import com.omarea.filter.SpfConfig;

@RequiresApi(api = Build.VERSION_CODES.N)
public class SwitchBrightnessModeService extends TileService {

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
        SharedPreferences config = getSharedPreferences(SpfConfig.FILTER_SPF, Context.MODE_PRIVATE);

        int toggleState = getQsTile().getState();

        // 如果磁贴为激活状态 被点击 则动作为关闭滤镜
        if (toggleState == Tile.STATE_ACTIVE) {
            if (Settings.System.canWrite(getApplicationContext())) {
                ContentResolver contentResolver = getApplicationContext().getContentResolver();
                Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
                getQsTile().setState(Tile.STATE_INACTIVE);
                getQsTile().updateTile(); //更新Tile
            } else {
                Toast.makeText(this, getString(R.string.write_settings_unallowed), Toast.LENGTH_LONG).show();
            }
        }
        // 如果磁贴为未激活状态 被点击 则动作为开启滤镜
        else if (toggleState == Tile.STATE_INACTIVE) {
            if (Settings.System.canWrite(getApplicationContext())) {
                ContentResolver contentResolver = getApplicationContext().getContentResolver();
                Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC);
                getQsTile().setState(Tile.STATE_ACTIVE);
                getQsTile().updateTile(); //更新Tile
            } else {
                Toast.makeText(this, getString(R.string.write_settings_unallowed), Toast.LENGTH_LONG).show();
            }
        }

        // Icon icon = Icon.createWithResource(getApplicationContext(), R.drawable.filter);
        // getQsTile().setIcon(icon); //设置图标
        getQsTile().updateTile(); //更新Tile
    }

    // 打开下拉菜单的时候调用,当快速设置按钮并没有在编辑栏拖到设置栏中不会调用
    //在TleAdded之后会调用一次
    @Override
    public void onStartListening() {
        try {
            ContentResolver contentResolver = getApplicationContext().getContentResolver();
            if (Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) {
                getQsTile().setState(Tile.STATE_ACTIVE);
            } else {
                getQsTile().setState(Tile.STATE_INACTIVE);
            }
            getQsTile().updateTile(); //更新Tile
        } catch (Exception ex) {
        }
    }

    // 关闭下拉菜单的时候调用,当快速设置按钮并没有在编辑栏拖到设置栏中不会调用
    // 在onTileRemoved移除之前也会调用移除
    @Override
    public void onStopListening() {
    }
}