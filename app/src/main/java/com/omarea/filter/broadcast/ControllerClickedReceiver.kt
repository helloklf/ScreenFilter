package com.omarea.filter.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import com.omarea.filter.PopupBrightnessController

class ControllerClickedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(context)) {
            //若没有权限，提示获取
            //val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
            //startActivity(intent);
            val overlayPermission = Intent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            overlayPermission.action = "android.settings.APPLICATION_DETAILS_SETTINGS"
            overlayPermission.data = Uri.fromParts("package", context.packageName, null)
            Toast.makeText(context, "为[屏幕滤镜]授权显示悬浮窗权限，从而使用更便捷的亮度控制器！", Toast.LENGTH_LONG).show();
        } else {
            PopupBrightnessController(context).open()
        }
    }
}