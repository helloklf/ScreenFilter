package com.omarea.filter

import android.os.Bundle
import android.os.Handler
import android.support.v7.app.AppCompatActivity

class ScreenCapActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Handler().postDelayed({
            finish()
        }, 1000)
    }
}
