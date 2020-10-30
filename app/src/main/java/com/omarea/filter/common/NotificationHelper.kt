package com.omarea.filter.common

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.provider.Settings
import android.support.v4.app.NotificationCompat
import android.view.View
import android.widget.RemoteViews
import com.omarea.filter.GlobalStatus
import com.omarea.filter.R
import com.omarea.filter.SpfConfig

class NotificationHelper(private var context: Context) {
    private val channelId = context.getString(R.string.channel_brightness_controller)
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private var channelCreated = false
    private val uniqueID = 101

    companion object {
        private var visible = false
    }

    fun updateNotification(autoMode: Boolean = true) {
        val config = context.getSharedPreferences(SpfConfig.FILTER_SPF, Context.MODE_PRIVATE)

        val minus = PendingIntent.getBroadcast(context, 0, Intent(context.getString(R.string.action_minus)), 0);
        val plus = PendingIntent.getBroadcast(context, 0, Intent(context.getString(R.string.action_plus)), 0);
        val on = PendingIntent.getBroadcast(context, 0, Intent(context.getString(R.string.action_on)), 0);
        val off = PendingIntent.getBroadcast(context, 0, Intent(context.getString(R.string.action_off)), 0);

        val remoteViews = RemoteViews(context.getPackageName(), R.layout.notification);
        remoteViews.setOnClickPendingIntent(R.id.brightness_minus, minus);
        remoteViews.setOnClickPendingIntent(R.id.brightness_plus, plus);

        if (autoMode) {
            val brightnessManual = PendingIntent.getBroadcast(context, 0, Intent(context.getString(R.string.action_manual)), 0);
            remoteViews.setOnClickPendingIntent(R.id.brightness_auto_manual, brightnessManual)
            val levels = SpfConfig.BRIGTHNESS_OFFSET_LEVELS
            remoteViews.setProgressBar(R.id.brightness_current, levels, (config.getInt(SpfConfig.BRIGTHNESS_OFFSET, SpfConfig.BRIGTHNESS_OFFSET_DEFAULT) + (levels / 2)), false)
            remoteViews.setImageViewResource(R.id.brightness_auto_manual, R.drawable.icon_brightness_auto)
        } else {
            val brightnessAuto = PendingIntent.getBroadcast(context, 0, Intent(context.getString(R.string.action_auto)), 0);
            remoteViews.setOnClickPendingIntent(R.id.brightness_auto_manual, brightnessAuto)
            remoteViews.setImageViewResource(R.id.brightness_auto_manual, R.drawable.icon_brightness_manual)

            var current = 0
            var max = config.getInt(SpfConfig.SCREENT_MAX_LIGHT, SpfConfig.SCREENT_MAX_LIGHT_DEFAULT)

            try {
                val contentResolver = context.contentResolver
                current = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS)
            } catch (ex: Exception) {
            }

            if (current > max) {
                config.edit().putInt(SpfConfig.SCREENT_MAX_LIGHT, current).apply()
                max = current
            }

            remoteViews.setProgressBar(R.id.brightness_current, if (current > max) current else max, current, false)
        }

        if (GlobalStatus.filterEnabled) {
            remoteViews.setOnClickPendingIntent(R.id.brightness_power_switch, off)
            remoteViews.setImageViewResource(R.id.brightness_power_switch, R.drawable.icon_power_on)
        } else {
            remoteViews.setOnClickPendingIntent(R.id.brightness_power_switch, on)
            remoteViews.setImageViewResource(R.id.brightness_power_switch, R.drawable.icon_power_off)
            remoteViews.setViewVisibility(R.id.brightness_controller, View.GONE)
        }

        val notificationBuilder = NotificationCompat.Builder(context)
                .setContentTitle(context.getString(R.string.app_name)) // 创建通知的标题
                .setContentText(context.getString(R.string.brightness_control)) // 创建通知的内容
                .setSmallIcon(R.drawable.ic_lightbulb) // 创建通知的小图标
                .setLargeIcon(BitmapFactory.decodeResource(context.getResources(), R.mipmap.ic_launcher)) // 创建通知的大图标
                /*
                 * 是使用自定义视图还是系统提供的视图，上面4的属性一定要设置，不然这个通知显示不出来
                  */
                //.setDefaults(Notification.DEFAULT_ALL)  // 设置通知提醒方式为系统默认的提醒方式
                .setAutoCancel(true)
                .setWhen(System.currentTimeMillis())
                .setContent(remoteViews) // 通过设置RemoteViews对象来设置通知的布局，这里我们设置为自定义布局
                .setPriority(NotificationCompat.PRIORITY_MIN)


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!channelCreated) {
                val channel = NotificationChannel(channelId, context.getString(R.string.channel_brightness_controller_text), NotificationManager.IMPORTANCE_DEFAULT)
                channel.enableVibration(false)
                channel.enableLights(false)
                channel.setSound(null, null)
                notificationManager.createNotificationChannel(channel)
            }
            channelCreated = true
            notificationBuilder.setChannelId(channelId)
        }

        val notification = notificationBuilder.build(); // 创建通知（每个通知必须要调用这个方法来创建）
        notification!!.flags = Notification.FLAG_NO_CLEAR or Notification.FLAG_ONGOING_EVENT

        notificationManager.notify(uniqueID, notification); // 发送通知

        visible = true
    }

    fun cancelNotification() {
        if (visible) {
            notificationManager.cancel(uniqueID)
            visible = false
        }
    }
}
