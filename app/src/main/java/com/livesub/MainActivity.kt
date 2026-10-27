package com.livesub

import android.Manifest
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private val projectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
            if (r.resultCode == RESULT_OK && r.data != null) {
                val i = Intent(this, CaptureService::class.java)
                    .putExtra("code", r.resultCode).putExtra("data", r.data)
                ContextCompat.startForegroundService(this, i)
                moveTaskToBack(true) // go play your video
            }
        }

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { next() }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
        }
        root.addView(TextView(this).apply {
            text = "الترجمة الفورية\nشغّل الفيديو من أي تطبيق ثم ستظهر الترجمة فوقه"
            textSize = 18f; gravity = Gravity.CENTER
        })
        root.addView(Button(this).apply {
            text = "ابدأ الترجمة"
            setOnClickListener { next() }
        })
        root.addView(Button(this).apply {
            text = "إيقاف"
            setOnClickListener { stopService(Intent(this@MainActivity, CaptureService::class.java)) }
        })
        setContentView(root)
    }

    private fun next() {
        val perms = arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
        if (perms.any { ContextCompat.checkSelfPermission(this, it) != 0 }) {
            permLauncher.launch(perms); return
        }
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionLauncher.launch(mpm.createScreenCaptureIntent())
    }
}
