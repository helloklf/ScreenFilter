package com.omarea.filter

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_guide.*

class GuideActivity : AppCompatActivity() {
    private val OVERLAY_PERMISSION_REQ_CODE = 0
    private val mHidePart2Runnable = Runnable {
        fullscreen_content.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LOW_PROFILE or
                        View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_guide)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        mHidePart2Runnable.run()
        askForPermission()
    }

    public fun askForPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, getString(R.string.permission_required), Toast.LENGTH_LONG).show()
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()))
                startActivityForResult(intent, OVERLAY_PERMISSION_REQ_CODE);
                return
            }
        }
        start()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_PERMISSION_REQ_CODE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!Settings.canDrawOverlays(this)) {
                    Toast.makeText(this, getString(R.string.get_permission_fail), Toast.LENGTH_LONG).show()
                    finishAndRemoveTask()
                } else {
                    start()
                }
            }
        }
    }

    private fun start() {
        val it = Intent()
        it.setClass(this.applicationContext, MainActivity::class.java)
        it.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK// Intent.FLAG_ACTIVITY_CLEAR_TOP
        startActivity(it)
        finish()
    }
}
