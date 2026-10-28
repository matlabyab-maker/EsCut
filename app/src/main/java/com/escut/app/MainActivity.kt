package com.escut.app

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.VideoView
import android.widget.TextView
import android.widget.Toast
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private val pickVideoCode = 101
    private var selectedUri: Uri? = null
    private var originalVideoName = "Video"
    private lateinit var videoView: VideoView
    private lateinit var playbackSeekBar: SeekBar
    private lateinit var playbackTime: TextView
    private lateinit var playPauseButton: Button
    private var durationMs = 0
    private var userSeeking = false
    private val handler = Handler(Looper.getMainLooper())
    private val progressUpdater = object : Runnable {
        override fun run() {
            if (selectedUri != null && durationMs > 0) {
                if (!userSeeking) {
                    val position = videoView.currentPosition.coerceAtLeast(0)
                    playbackSeekBar.progress = (position.toLong() * 1000 / durationMs).toInt().coerceIn(0, 1000)
                    playbackTime.text = "${formatTime(position)} / ${formatTime(durationMs)}"
                }
                handler.postDelayed(this, 250)
            }
        }
    }
    private lateinit var selectedLabel: TextView
    private lateinit var startInput: EditText
    private lateinit var endInput: EditText
    private lateinit var cutButton: Button
    private lateinit var statusLabel: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
    }

    override fun onDestroy() {
        handler.removeCallbacks(progressUpdater)
        if (::videoView.isInitialized) { try { videoView.stopPlayback() } catch (_: Exception) {} }
        super.onDestroy()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 24, 22, 24)
            setBackgroundColor(0xFFF5F6F8.toInt())
        }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val title = TextView(this).apply {
            text = "EsCut"
            textSize = 30f
            setTextColor(0xFF172554.toInt())
            gravity = Gravity.CENTER
        }
        val subtitle = TextView(this).apply {
            text = "برش زمانی فیلم با حفظ کدک اصلی"
            textSize = 17f
            setPadding(0, 8, 0, 22)
            gravity = Gravity.CENTER
            setTextColor(0xFF334155.toInt())
        }
        videoView = VideoView(this).apply {
            setBackgroundColor(0xFF111827.toInt())
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(220))
        }
        playbackTime = TextView(this).apply {
            text = "00:00 / 00:00"
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, 6, 0, 0)
            setTextColor(0xFF0F172A.toInt())
        }
        playbackSeekBar = SeekBar(this).apply {
            max = 1000
            progress = 0
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(seekBar: SeekBar?) { userSeeking = true }
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser && durationMs > 0) {
                        val target = (durationMs.toLong() * progress / 1000).toInt()
                        playbackTime.text = "${formatTime(target)} / ${formatTime(durationMs)}"
                    }
                }
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    if (durationMs > 0) videoView.seekTo((durationMs.toLong() * playbackSeekBar.progress / 1000).toInt())
                    userSeeking = false
                }
            })
        }
        playPauseButton = Button(this).apply {
            text = "پخش / توقف"
            isEnabled = false
            setOnClickListener {
                if (videoView.isPlaying) {
                    videoView.pause(); text = "ادامه پخش"
                } else {
                    videoView.start(); text = "توقف پخش"
                }
            }
        }
        val choose = Button(this).apply {
            text = "انتخاب فیلم"
            setOnClickListener { openVideoPicker() }
        }
        selectedLabel = TextView(this).apply {
            text = "هنوز فیلمی انتخاب نشده است"
            textSize = 14f
            setPadding(0, 14, 0, 18)
        }
        startInput = field("شروع برش (ثانیه)، مثال: 10")
        endInput = field("پایان برش (ثانیه)، مثال: 60")
        val hint = TextView(this).apply {
            text = "نسخه ۱: یک بازه را جدا می‌کند. جریان‌های صوتی/تصویری بدون رمزگذاری مجدد کپی می‌شوند؛ برش ممکن است به فریم کلیدی نزدیک شود."
            textSize = 13f
            setTextColor(0xFF475569.toInt())
            setPadding(0, 12, 0, 14)
        }
        cutButton = Button(this).apply {
            text = "برش و ذخیره"
            isEnabled = false
            setOnClickListener { startCut() }
        }
        statusLabel = TextView(this).apply {
            text = "خروجی در پوشه Movies/EsCut ذخیره می‌شود."
            textSize = 14f
            setPadding(0, 16, 0, 8)
        }
        listOf(title, subtitle, videoView, playbackTime, playbackSeekBar, playPauseButton, choose, selectedLabel, startInput, endInput, hint, cutButton, statusLabel)
            .forEach { content.addView(it, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)) }
        scroll.addView(content)
        root.addView(scroll)
        setContentView(root)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun formatTime(milliseconds: Int): String {
        val total = milliseconds.coerceAtLeast(0) / 1000
        val hours = total / 3600
        val minutes = (total % 3600) / 60
        val seconds = total % 60
        return if (hours > 0) String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        else String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    private fun displayName(uri: Uri): String {
        var name = "Video"
        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) name = cursor.getString(index) ?: name
            }
        } catch (_: Exception) { } finally { cursor?.close() }
        return name
    }

    private fun field(hintText: String): EditText = EditText(this).apply {
        hint = hintText
        textSize = 16f
        singleLine = true
        inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        setPadding(12, 8, 12, 8)
    }

    private fun openVideoPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "video/*"
        }
        startActivityForResult(intent, pickVideoCode)
    }

    @Deprecated("Deprecated by platform, retained for broad device compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == pickVideoCode && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            selectedUri = uri
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {}
            val fullName = displayName(uri)
            originalVideoName = fullName.substringBeforeLast('.', fullName).ifBlank { "Video" }
            selectedLabel.text = "فیلم انتخاب شد:\n$fullName"
            cutButton.isEnabled = false
            playPauseButton.isEnabled = false
            durationMs = 0
            playbackSeekBar.progress = 0
            playbackTime.text = "00:00 / 00:00"
            try {
                videoView.setVideoURI(uri)
                videoView.setOnPreparedListener { player ->
                    durationMs = player.duration.coerceAtLeast(0)
                    playbackTime.text = "${formatTime(videoView.currentPosition)} / ${formatTime(durationMs)}"
                    playbackSeekBar.progress = 0
                    playPauseButton.isEnabled = true
                    cutButton.isEnabled = true
                    statusLabel.text = "فیلم آماده است؛ نوار زمان و زمان جاری پخش نمایش داده می‌شود."
                    handler.removeCallbacks(progressUpdater)
                    handler.post(progressUpdater)
                }
                videoView.setOnCompletionListener {
                    playbackSeekBar.progress = 1000
                    playbackTime.text = "${formatTime(durationMs)} / ${formatTime(durationMs)}"
                    playPauseButton.text = "پخش دوباره"
                }
                videoView.setOnErrorListener { _, what, extra ->
                    statusLabel.text = "پیش‌نمایش این فرمت پشتیبانی نشد ($what/$extra)."
                    true
                }
            } catch (e: Exception) {
                statusLabel.text = "پیش‌نمایش باز نشد: ${e.localizedMessage ?: "خطا"}"
            }
        }
    }

    private fun startCut() {
        val uri = selectedUri ?: return
        val startSec = startInput.text.toString().toDoubleOrNull()
        val endSec = endInput.text.toString().toDoubleOrNull()
        if (startSec == null || endSec == null || startSec < 0 || endSec <= startSec) {
            toast("زمان شروع و پایان را درست وارد کنید.")
            return
        }
        cutButton.isEnabled = false
        statusLabel.text = "در حال برش… لطفاً برنامه را باز نگه دارید."
        thread {
            try {
                val outputUri = trimWithoutReencode(uri, startSec * 1_000_000L, endSec * 1_000_000L)
                runOnUiThread {
                    statusLabel.text = "برش تمام شد.\nنام فایل: ${displayName(outputUri)}\nمسیر: Movies/EsCut"
                    cutButton.isEnabled = true
                    toast("برش با موفقیت انجام شد.")
                }
            } catch (e: Exception) {
                runOnUiThread {
                    statusLabel.text = "خطا: ${e.localizedMessage ?: "پردازش انجام نشد"}"
                    cutButton.isEnabled = true
                    toast("برش ناموفق بود.")
                }
            }
        }
    }

    private fun trimWithoutReencode(inputUri: Uri, startUs: Long, endUs: Long): Uri {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var outputUri: Uri? = null
        try {
            extractor.setDataSource(this, inputUri, null)
            val createdUri = createOutputUri(buildOutputName())
            outputUri = createdUri
            val outputFd = contentResolver.openFileDescriptor(createdUri, "rw")
                ?: throw IllegalStateException("فایل خروجی باز نشد")
            outputFd.use { parcel ->
                muxer = MediaMuxer(parcel.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                val trackMap = mutableMapOf<Int, Int>()
                for (i in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                    if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                        trackMap[i] = muxer!!.addTrack(format)
                    }
                }
                if (trackMap.isEmpty()) throw IllegalStateException("ردیف صوتی یا تصویری قابل‌استفاده پیدا نشد")
                muxer!!.start()
                trackMap.keys.forEach { extractor.selectTrack(it) }
                extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                val maxSize = (1 shl 20) * 8
                val buffer = ByteBuffer.allocateDirect(maxSize)
                val info = android.media.MediaCodec.BufferInfo()
                while (true) {
                    val sampleTrack = extractor.sampleTrackIndex
                    if (sampleTrack < 0) break
                    val sampleTime = extractor.sampleTime
                    if (sampleTime >= endUs) break
                    if (trackMap.containsKey(sampleTrack) && sampleTime >= startUs) {
                        buffer.clear()
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) break
                        info.offset = 0
                        info.size = size
                        info.presentationTimeUs = sampleTime - startUs
                        info.flags = extractor.sampleFlags
                        muxer!!.writeSampleData(trackMap[sampleTrack]!!, buffer, info)
                    }
                    if (!extractor.advance()) break
                }
                muxer!!.stop()
            }
            muxer?.release()
            muxer = null
            val done = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
            contentResolver.update(createdUri, done, null, null)
            return createdUri
        } catch (e: Exception) {
            outputUri?.let { try { contentResolver.delete(it, null, null) } catch (_: Exception) {} }
            throw e
        } finally {
            try { extractor.release() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
        }
    }

    private fun buildOutputName(): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val safeName = originalVideoName.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().trim('.').ifBlank { "Video" }.take(100)
        return "${safeName}_cut_${stamp}.mp4"
    }

    private fun createOutputUri(fileName: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/EsCut")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        return contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("ساخت فایل خروجی ممکن نشد")
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
