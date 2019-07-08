package com.omarea.filter;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.support.annotation.RequiresApi;
import android.widget.Toast;

@RequiresApi(api = Build.VERSION_CODES.N)
public class QuickSettingService extends TileService {

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
            if (GlobalStatus.INSTANCE.getFilterClose() != null) {
                GlobalStatus.INSTANCE.getFilterClose().run();
                config.edit().putBoolean(SpfConfig.FILTER_AUTO_START, false).apply();

                Toast.makeText(this, R.string.quick_tile_off, Toast.LENGTH_SHORT).show();
            }
            getQsTile().setState(Tile.STATE_INACTIVE);
        }
        // 如果磁贴为未激活状态 被点击 则动作为开启滤镜
        else if (toggleState == Tile.STATE_INACTIVE) {
            // 如果服务没启动
            if (GlobalStatus.INSTANCE.getFilterOpen() == null) {
                Toast.makeText(this, R.string.accessibility_service_required, Toast.LENGTH_SHORT).show();
                try {
                    Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                    startActivity(intent);
                } catch (java.lang.Exception ignored) {
                }
            }
            // 正常开启滤镜
            else {
                config.edit().putBoolean(SpfConfig.FILTER_AUTO_START, true).apply();
                GlobalStatus.INSTANCE.getFilterOpen().run();

                getQsTile().setState(Tile.STATE_ACTIVE);

                Toast.makeText(this, R.string.quick_tile_on, Toast.LENGTH_SHORT).show();
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
        getQsTile().setState(GlobalStatus.INSTANCE.getFilterEnabled() ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        getQsTile().updateTile(); //更新Tile
    }

    // 关闭下拉菜单的时候调用,当快速设置按钮并没有在编辑栏拖到设置栏中不会调用
    // 在onTileRemoved移除之前也会调用移除
    @Override
    public void onStopListening() {
    }
}