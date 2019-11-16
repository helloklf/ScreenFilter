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
import android.util.Log
import android.widget.RemoteViews
import com.omarea.filter.R
import com.omarea.filter.SpfConfig

class NotificationHelper(private var context: Context) {
    private val channelId = context.getString(R.string.channel_brightness_controller)
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private var channelCreated = false
    private val uniqueID = 101

    fun updateNotification() {
        val minus = PendingIntent.getBroadcast(context, 0, Intent(context.getString(R.string.action_minus)), 0);
        val plus = PendingIntent.getBroadcast(context, 0, Intent(context.getString(R.string.action_plus)), 0);

        /*
         * 通知布局如果使用自定义布局文件中的话要通过RemoteViews类来实现，
         * 其实无论是使用系统提供的布局还是自定义布局，都是通过RemoteViews类实现，如果使用系统提供的布局，
         * 系统会默认提供一个RemoteViews对象。如果使用自定义布局的话这个RemoteViews对象需要我们自己创建，
         * 并且加入我们需要的对应的控件事件处理，然后通过setContent(RemoteViews remoteViews)方法传参实现
         */
        val remoteViews = RemoteViews(context.getPackageName(), R.layout.notification);
        /*
         * 对于自定义布局文件中的控件通过RemoteViews类的对象进行事件处理
         */
        remoteViews.setOnClickPendingIntent(R.id.brightness_minus, minus);
        remoteViews.setOnClickPendingIntent(R.id.brightness_plus, plus);
        var current = 0
        val config = context.getSharedPreferences(SpfConfig.FILTER_SPF, Context.MODE_PRIVATE)
        var max = config.getInt(SpfConfig.SCREENT_MAX_LIGHT, SpfConfig.SCREENT_MAX_LIGHT_DEFAULT)

        try {
            val contentResolver = context.contentResolver
            current = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        } catch (ex: Exception) {
        }

        if (current > max && current < 2048) {
            max = 2047
            config.edit().putInt(SpfConfig.SCREENT_MAX_LIGHT, max).apply()
        }

        remoteViews.setProgressBar(R.id.brightness_current, if (current > max) current else max, current, false)

        val notificationBuilder = NotificationCompat.Builder(context)
                .setContentTitle(context.getString(R.string.app_name)) // 创建通知的标题
                .setContentText(context.getString(R.string.brightness_control)) // 创建通知的内容
                .setSmallIcon(R.mipmap.ic_launcher) // 创建通知的小图标
                .setLargeIcon(BitmapFactory.decodeResource(context.getResources(), R.mipmap.ic_launcher)) // 创建通知的大图标
                /*
                 * 是使用自定义视图还是系统提供的视图，上面4的属性一定要设置，不然这个通知显示不出来
                  */
                //.setDefaults(Notification.DEFAULT_ALL)  // 设置通知提醒方式为系统默认的提醒方式
                .setAutoCancel(true)
                .setWhen(System.currentTimeMillis())
                .setContent(remoteViews) // 通过设置RemoteViews对象来设置通知的布局，这里我们设置为自定义布局


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
        Log.d("bNotification", "---" + max + "  " + current)
    }

    fun cancelNotification() {
        notificationManager.cancel(uniqueID)
    }
}
