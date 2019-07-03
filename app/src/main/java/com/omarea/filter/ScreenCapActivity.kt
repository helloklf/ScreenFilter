package com.omarea.filter

import android.app.Activity
import android.os.Bundle
import android.widget.Toast

class ScreenCapActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val screenCap = GlobalStatus.screenCap
        if (screenCap != null) {
            screenCap.run()
        } else {
            Toast.makeText(this, "滤镜服务未启动", Toast.LENGTH_LONG).show()
        }

        finish()
    }
}
