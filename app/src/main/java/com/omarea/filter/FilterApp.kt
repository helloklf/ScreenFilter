package com.omarea.filter

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper

public class FilterApp : Application() {
    companion object {
        public lateinit var context: Context
        public val handler = Handler(Looper.getMainLooper())
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        context = this
    }
}