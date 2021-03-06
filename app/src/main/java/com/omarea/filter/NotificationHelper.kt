package com.omarea.filter

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.provider.Settings
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.omarea.filter.broadcast.ControllerClickedReceiver

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

        val minus = PendingIntent.getBroadcast(context, 11, Intent(context.getString(R.string.action_minus)), PendingIntent.FLAG_UPDATE_CURRENT)
        val plus = PendingIntent.getBroadcast(context, 12, Intent(context.getString(R.string.action_plus)), PendingIntent.FLAG_UPDATE_CURRENT)
        val on = PendingIntent.getBroadcast(context, 13, Intent(context.getString(R.string.action_on)), PendingIntent.FLAG_UPDATE_CURRENT)
        val off = PendingIntent.getBroadcast(context, 14, Intent(context.getString(R.string.action_off)), PendingIntent.FLAG_UPDATE_CURRENT)

        val remoteViews = RemoteViews(context.packageName, R.layout.notification)
        remoteViews.setOnClickPendingIntent(R.id.brightness_minus, minus)
        remoteViews.setOnClickPendingIntent(R.id.brightness_plus, plus)


        val clickIntent = if (GlobalStatus.filterEnabled) {
            PendingIntent.getBroadcast(
                    context,
                    0,
                    Intent(context, ControllerClickedReceiver::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT)
        } else {
            null
        }

        // 点击亮度条显示悬浮窗
        // remoteViews.setOnClickPendingIntent(R.id.brightness_current, clickIntent)

        if (autoMode) {
            val brightnessManual = PendingIntent.getBroadcast(context, 15, Intent(context.getString(R.string.action_manual)), PendingIntent.FLAG_UPDATE_CURRENT)
            remoteViews.setOnClickPendingIntent(R.id.brightness_auto_manual, brightnessManual)
            val levels = SpfConfig.BRIGTHNESS_OFFSET_LEVELS
            val offset = config.getInt(SpfConfig.BRIGTHNESS_OFFSET, SpfConfig.BRIGTHNESS_OFFSET_DEFAULT)
            remoteViews.setProgressBar(R.id.brightness_current, levels, offset + (levels / 2), false)
            remoteViews.setImageViewResource(R.id.brightness_auto_manual, R.drawable.icon_brightness_auto)
            remoteViews.setTextViewText(R.id.brightness_value, "" + (
                    if (offset > 0) {
                        "+$offset%"
                    } else if (offset < 0) {
                        "$offset%"
                    } else {
                        "--"
                    }
                    ))
        } else {
            val brightnessAuto = PendingIntent.getBroadcast(context, 16, Intent(context.getString(R.string.action_auto)), PendingIntent.FLAG_UPDATE_CURRENT)
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

            val valueMax = if (current > max) current else max
            remoteViews.setProgressBar(R.id.brightness_current, valueMax, current, false)
            remoteViews.setTextViewText(R.id.brightness_value, "" + (
                    String.format("%.1f%%", current * 100f / valueMax)
                    ))
        }

        if (GlobalStatus.filterEnabled) {
            remoteViews.setOnClickPendingIntent(R.id.brightness_power_switch, off)
            remoteViews.setImageViewResource(R.id.brightness_power_switch, R.drawable.icon_power_on)
            remoteViews.setViewVisibility(R.id.brightness_controller, View.VISIBLE)
        } else {
            remoteViews.setOnClickPendingIntent(R.id.brightness_power_switch, on)
            remoteViews.setImageViewResource(R.id.brightness_power_switch, R.drawable.icon_power_off)
            remoteViews.setViewVisibility(R.id.brightness_controller, View.GONE)
        }

        val notificationBuilder = NotificationCompat.Builder(context)
                .setContentTitle(context.getString(R.string.app_name)) // 创建通知的标题
                .setContentText(context.getString(R.string.brightness_control)) // 创建通知的内容
                .setSmallIcon(R.drawable.ic_lightbulb) // 创建通知的小图标
                .setLargeIcon(BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)) // 创建通知的大图标
                /*
                 * 是使用自定义视图还是系统提供的视图，上面4的属性一定要设置，不然这个通知显示不出来
                  */
                //.setDefaults(Notification.DEFAULT_ALL)  // 设置通知提醒方式为系统默认的提醒方式
                .setAutoCancel(true)
                .setWhen(System.currentTimeMillis())
                .setContentIntent(clickIntent)
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

        val notification = notificationBuilder.build() // 创建通知（每个通知必须要调用这个方法来创建）
        notification!!.flags = Notification.FLAG_NO_CLEAR or Notification.FLAG_ONGOING_EVENT

        notificationManager.notify(uniqueID, notification) // 发送通知

        visible = true
    }

    fun cancelNotification() {
        if (visible) {
            notificationManager.cancel(uniqueID)
            visible = false
        }
    }
}
