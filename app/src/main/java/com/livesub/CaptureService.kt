package com.livesub

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.media.*
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.google.mlkit.nl.translate.*
import com.google.mlkit.common.model.DownloadConditions
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import kotlin.concurrent.thread

class CaptureService : Service() {

    // لغة الكلام في الفيديو (يجب أن تطابق نموذج Vosk في assets) ← لغة الترجمة
    private val sourceLang = TranslateLanguage.ENGLISH
    private val targetLang = TranslateLanguage.ARABIC
    private val modelAsset = "model"   // app/src/main/assets/model

    @Volatile private var running = false
    private var projection: MediaProjection? = null
    private var record: AudioRecord? = null
    private lateinit var wm: WindowManager
    private lateinit var box: LinearLayout
    private lateinit var original: TextView
    private lateinit var translated: TextView
    private lateinit var translator: Translator

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, f: Int, id: Int): Int {
        startForeground(1, notification())
        val code = intent?.getIntExtra("code", 0) ?: 0
        val data = intent?.getParcelableExtra<Intent>("data")
        if (data == null) { stopSelf(); return START_NOT_STICKY }
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = mpm.getMediaProjection(code, data)
        projection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { stopSelf() }
        }, null)

        translator = Translation.getClient(
            TranslatorOptions.Builder().setSourceLanguage(sourceLang).setTargetLanguage(targetLang).build()
        )
        translator.downloadModelIfNeeded(DownloadConditions.Builder().build())
        showOverlay("جارِ التحضير…")
        startRecognition()
        return START_NOT_STICKY
    }

    private fun startRecognition() {
        running = true
        thread {
            val modelDir = File(filesDir, "vosk-model")
            if (!modelDir.exists()) copyAssets(modelAsset, modelDir)
            val recognizer = Recognizer(Model(modelDir.absolutePath), 16000f)

            val cfg = AudioPlaybackCaptureConfiguration.Builder(projection!!)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build()
            val fmt = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(16000)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO).build()
            val buf = ShortArray(4096)
            record = AudioRecord.Builder()
                .setAudioFormat(fmt)
                .setBufferSizeInBytes(buf.size * 4)
                .setAudioPlaybackCaptureConfig(cfg).build()
            record!!.startRecording()
            runOnUi { original.text = ""; translated.text = "في انتظار الصوت…" }

            while (running) {
                val n = record!!.read(buf, 0, buf.size)
                if (n <= 0) continue
                if (recognizer.acceptWaveForm(buf, n)) {
                    val text = JSONObject(recognizer.result).optString("text")
                    if (text.isNotBlank()) translate(text)
                } else {
                    val p = JSONObject(recognizer.partialResult).optString("partial")
                    if (p.isNotBlank()) runOnUi { original.text = p }
                }
            }
            recognizer.close()
        }
    }

    private fun translate(text: String) {
        translator.translate(text)
            .addOnSuccessListener { t -> runOnUi { original.text = text; translated.text = t } }
            .addOnFailureListener { runOnUi { translated.text = "(حمّل نموذج الترجمة: يلزم إنترنت أول مرة)" } }
    }

    private fun showOverlay(msg: String) {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        original = TextView(this).apply { setTextColor(Color.LTGRAY); textSize = 13f; gravity = Gravity.CENTER }
        translated = TextView(this).apply { setTextColor(Color.WHITE); textSize = 18f; gravity = Gravity.CENTER; text = msg }
        box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xB3000000.toInt())
            setPadding(24, 16, 24, 16)
            addView(original); addView(translated)
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.BOTTOM; y = 160 }
        wm.addView(box, lp)
    }

    private fun runOnUi(r: () -> Unit) = box.post(r)

    private fun copyAssets(src: String, dst: File) {
        val list = assets.list(src) ?: return
        if (list.isEmpty()) {
            dst.parentFile?.mkdirs()
            assets.open(src).use { i -> dst.outputStream().use { o -> i.copyTo(o) } }
        } else {
            dst.mkdirs()
            list.forEach { copyAssets("$src/$it", File(dst, it)) }
        }
    }

    private fun notification(): Notification {
        val ch = "live"
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(ch, "LiveSub", NotificationManager.IMPORTANCE_LOW))
        return Notification.Builder(this, ch)
            .setContentTitle("الترجمة الفورية تعمل")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now).build()
    }

    override fun onDestroy() {
        running = false
        record?.run { stop(); release() }
        projection?.stop()
        if (::box.isInitialized) wm.removeView(box)
        if (::translator.isInitialized) translator.close()
        super.onDestroy()
    }
}
