package com.omarea.filter

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler

/**
 * 监听屏幕开关事件
 * Created by Hello on 2018/01/23.
 */

class ReciverLock(private var callbacks: Handler) : BroadcastReceiver() {
    override fun onReceive(p0: Context?, p1: Intent?) {
        if (p1 == null) {
            return
        }
        when (p1.action) {
            Intent.ACTION_SCREEN_OFF -> {
                try {
                    callbacks.sendMessage(callbacks.obtainMessage(8))
                } catch (ex: Exception) {
                    System.out.print(">>>>>" + ex.message)
                }
            }
            Intent.ACTION_USER_PRESENT,
            Intent.ACTION_USER_UNLOCKED,
            Intent.ACTION_SCREEN_ON -> {
                try {
                    callbacks.sendMessage(callbacks.obtainMessage(7))
                } catch (ex: Exception) {
                    System.out.print(">>>>>" + ex.message)
                }
            }
        }
    }

    companion object {
        private var reciver: ReciverLock? = null
        public fun autoRegister(context: Context, callbacks: Handler): ReciverLock? {
            if (reciver != null) {
                unRegister(context)
            }

            reciver = ReciverLock(callbacks)

            context.applicationContext.registerReceiver(reciver, IntentFilter(Intent.ACTION_SCREEN_OFF))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                context.applicationContext.registerReceiver(reciver, IntentFilter(Intent.ACTION_USER_UNLOCKED))
            }
            context.applicationContext.registerReceiver(reciver, IntentFilter(Intent.ACTION_SCREEN_ON))
            context.applicationContext.registerReceiver(reciver, IntentFilter(Intent.ACTION_USER_PRESENT))

            return reciver
        }

         fun unRegister(context: Context) {
            if (reciver == null) {
                return
            }
            context.applicationContext.unregisterReceiver(reciver)
            reciver = null
        }
    }
}
