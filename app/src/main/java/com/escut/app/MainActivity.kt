package com.escut.app

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.view.ViewGroup
import android.os.Handler
import android.os.Looper
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
    private lateinit var selectedLabel: TextView
    private lateinit var startInput: EditText
    private lateinit var endInput: EditText
    private lateinit var cutButton: Button
    private lateinit var statusLabel: TextView
    private lateinit var preview: VideoView
    private lateinit var playbackButton: Button
    private lateinit var timeline: SeekBar
    private lateinit var positionLabel: TextView
    private lateinit var durationLabel: TextView
    private lateinit var startMarkLabel: TextView
    private var durationMs: Int = 0
    private var updatingTimeline = false
    private val handler = Handler(Looper.getMainLooper())
    private val timelineTicker = object : Runnable {
        override fun run() {
            if (::preview.isInitialized && preview.isPlaying && !updatingTimeline) {
                timeline.progress = preview.currentPosition.coerceIn(0, timeline.max.coerceAtLeast(0))
                positionLabel.text = formatTime(preview.currentPosition)
            }
            handler.postDelayed(this, 250)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
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
        val choose = Button(this).apply {
            text = "انتخاب فیلم"
            setOnClickListener { openVideoPicker() }
        }
        selectedLabel = TextView(this).apply {
            text = "هنوز فیلمی انتخاب نشده است"
            textSize = 14f
            setPadding(0, 14, 0, 10)
        }
        preview = VideoView(this).apply {
            setBackgroundColor(0xFF111827.toInt())
        }
        val previewParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 220)
        content.addView(preview, previewParams)
        playbackButton = Button(this).apply {
            text = "پخش فیلم"
            isEnabled = false
            setOnClickListener {
                if (preview.isPlaying) {
                    preview.pause()
                    text = "ادامه پخش"
                } else if (selectedUri != null && durationMs > 0) {
                    preview.start()
                    text = "توقف موقت"
                }
            }
        }
        content.addView(playbackButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val timelineTitle = TextView(this).apply {
            text = "نوار زمانی فیلم — برای دیدن صحنه حرکت بده"
            textSize = 15f
            setTextColor(0xFF172554.toInt())
            setPadding(0, 12, 0, 4)
        }
        timeline = SeekBar(this).apply {
            max = 1000
            progress = 0
            isEnabled = false
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser && durationMs > 0) {
                        val target = (durationMs.toLong() * progress / 1000L).toInt()
                        preview.seekTo(target)
                        positionLabel.text = formatTime(target)
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) { updatingTimeline = true }
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    updatingTimeline = false
                    if (durationMs > 0) preview.seekTo((durationMs.toLong() * timeline.progress / 1000L).toInt())
                }
            })
        }
        val timeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        positionLabel = TextView(this).apply { text = "00:00"; textSize = 13f }
        durationLabel = TextView(this).apply { text = "00:00"; textSize = 13f; gravity = Gravity.END }
        timeRow.addView(positionLabel, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        timeRow.addView(durationLabel, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val markRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val markStart = Button(this).apply {
            text = "شروع در این صحنه"
            setOnClickListener {
                if (durationMs > 0) {
                    startInput.setText("%.3f".format(Locale.US, preview.currentPosition / 1000.0))
                    startMarkLabel.text = "شروع: ${formatTime(preview.currentPosition)}"
                }
            }
        }
        val markEnd = Button(this).apply {
            text = "پایان در این صحنه"
            setOnClickListener {
                if (durationMs > 0) endInput.setText("%.3f".format(Locale.US, preview.currentPosition / 1000.0))
            }
        }
        markRow.addView(markStart, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        markRow.addView(markEnd, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        startMarkLabel = TextView(this).apply {
            text = "نقطهٔ شروع هنوز انتخاب نشده است"
            textSize = 13f
            setTextColor(0xFF475569.toInt())
        }
        content.addView(timelineTitle)
        content.addView(timeline, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        content.addView(timeRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        content.addView(markRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        content.addView(startMarkLabel, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        startInput = field("شروع برش (ثانیه)")
        endInput = field("پایان برش (ثانیه)، مثال: 60")
        val hint = TextView(this).apply {
            text = "با نوار زمانی صحنه را پیدا کن و شروع/پایان را علامت بزن. در حالت حفظ کدک اصلی، برش دقیق به فریم کلیدی وابسته است."
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
        val fullWidth = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        listOf(title, subtitle, choose, selectedLabel).forEachIndexed { index, view -> content.addView(view, index, fullWidth) }
        listOf(startInput, endInput, hint, cutButton, statusLabel)
            .forEach { content.addView(it, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)) }
        scroll.addView(content)
        root.addView(scroll)
        setContentView(root)
    }

    private fun field(hintText: String): EditText = EditText(this).apply {
        hint = hintText
        textSize = 16f
        setSingleLine(true)
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
            selectedLabel.text = "فیلم انتخاب شد:\n${uri.lastPathSegment ?: uri}"
            cutButton.isEnabled = true
            timeline.isEnabled = false
            durationMs = 0
            preview.stopPlayback()
            playbackButton.isEnabled = false
            playbackButton.text = "در حال آماده‌سازی فیلم…"
            preview.setOnPreparedListener { player ->
                durationMs = player.duration.coerceAtLeast(0)
                timeline.max = 1000
                timeline.progress = 0
                timeline.isEnabled = durationMs > 0
                positionLabel.text = "00:00:00"
                durationLabel.text = formatTime(durationMs)
                playbackButton.isEnabled = durationMs > 0
                playbackButton.text = "توقف موقت"
                // Starting playback forces VideoView to render decoded frames instead of
                // leaving some devices on a black surface until the first manual play.
                preview.start()
                handler.removeCallbacks(timelineTicker)
                handler.post(timelineTicker)
                statusLabel.text = "فیلم آماده است؛ پخش را می‌توانی متوقف کنی و روی نوار زمانی جابه‌جا شوی."
            }
            preview.setOnErrorListener { _, what, extra ->
                playbackButton.isEnabled = false
                playbackButton.text = "پخش در دسترس نیست"
                statusLabel.text = "پیش‌نمایش این فایل پخش نشد (کد $what/$extra). ممکن است کدک دستگاه پشتیبانی نشود."
                true
            }
            preview.setVideoURI(uri)
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
                val outputUri = trimWithoutReencode(uri, (startSec * 1_000_000L).toLong(), (endSec * 1_000_000L).toLong())
                runOnUiThread {
                    statusLabel.text = "فایل خروجی ساخته شد.\nبرای حفظ کدک اصلی، شروع برش ممکن است تا فریم کلیدی قبلی عقب‌تر باشد.\nمسیر: Movies/EsCut"
                    cutButton.isEnabled = true
                    toast("فایل خروجی ساخته شد.")
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
        var outputFd: android.os.ParcelFileDescriptor? = null
        var outputUri: Uri? = null
        var muxerStarted = false
        var success = false
        try {
            extractor.setDataSource(this, inputUri, null)

            var videoTrackIndex = -1
            for (i in 0 until extractor.trackCount) {
                val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/")) {
                    videoTrackIndex = i
                    break
                }
            }
            if (videoTrackIndex < 0) throw IllegalStateException("ردیف تصویری قابل‌استفاده پیدا نشد")

            // MediaExtractor.sampleTime is -1 until at least one track is selected.
            // Select the video track before seeking so the previous video keyframe can
            // be found and the output starts with a decodable frame.
            extractor.selectTrack(videoTrackIndex)
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val actualStartUs = extractor.sampleTime
            if (actualStartUs < 0L) throw IllegalStateException("برای این فیلم فریم کلیدی قابل‌استفاده پیدا نشد؛ زمان دیگری را انتخاب کن.")

            outputUri = createOutputUri()
            outputFd = contentResolver.openFileDescriptor(outputUri, "rw")
                ?: throw IllegalStateException("فایل خروجی باز نشد")
            muxer = MediaMuxer(outputFd!!.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val trackMap = mutableMapOf<Int, Int>()
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                    try {
                        trackMap[i] = muxer!!.addTrack(format)
                    } catch (e: Exception) {
                        if (mime.startsWith("video/")) throw IllegalStateException(
                            "کدک تصویری این فیلم برای کپی مستقیم در MP4 پشتیبانی نمی‌شود", e
                        )
                    }
                }
            }
            if (trackMap.keys.none { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true }) {
                throw IllegalStateException("ردیف تصویری این فیلم قابل‌کپی نیست")
            }

            trackMap.keys.forEach { if (it != videoTrackIndex) extractor.selectTrack(it) }
            // Re-seek after selecting the remaining tracks; samples before the chosen
            // video keyframe are ignored below to keep output timestamps non-negative.
            extractor.seekTo(actualStartUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            muxer!!.start()
            muxerStarted = true

            val buffer = ByteBuffer.allocateDirect(8 * 1024 * 1024)
            val info = android.media.MediaCodec.BufferInfo()
            var writtenSamples = 0
            while (true) {
                val sampleTrack = extractor.sampleTrackIndex
                if (sampleTrack < 0) break
                val sampleTime = extractor.sampleTime
                if (sampleTime >= endUs) break
                if (trackMap.containsKey(sampleTrack) && sampleTime >= actualStartUs) {
                    buffer.clear()
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) break
                    info.offset = 0
                    info.size = size
                    info.presentationTimeUs = sampleTime - actualStartUs
                    info.flags = extractor.sampleFlags
                    muxer!!.writeSampleData(trackMap[sampleTrack]!!, buffer, info)
                    writtenSamples++
                }
                if (!extractor.advance()) break
            }
            if (writtenSamples == 0) throw IllegalStateException("در این بازه نمونهٔ صوتی/تصویری قابل‌نوشتن پیدا نشد")

            muxer!!.stop()
            muxerStarted = false
            muxer!!.release()
            muxer = null
            outputFd!!.close()
            outputFd = null

            val done = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
            contentResolver.update(outputUri!!, done, null, null)
            success = true
            return outputUri!!
        } finally {
            if (muxerStarted) {
                try { muxer?.stop() } catch (_: Exception) {}
            }
            try { muxer?.release() } catch (_: Exception) {}
            try { outputFd?.close() } catch (_: Exception) {}
            try { extractor.release() } catch (_: Exception) {}
            if (!success && outputUri != null) {
                try { contentResolver.delete(outputUri!!, null, null) } catch (_: Exception) {}
            }
        }
    }

    private fun createOutputUri(): Uri {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "EsCut_$stamp.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/EsCut")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        return contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("ساخت فایل خروجی ممکن نشد")
    }

    private fun formatTime(ms: Int): String {
        val total = (ms.coerceAtLeast(0) / 1000)
        return String.format(Locale.US, "%02d:%02d:%02d", total / 3600, (total % 3600) / 60, total % 60)
    }

    override fun onDestroy() {
        handler.removeCallbacks(timelineTicker)
        if (::preview.isInitialized) preview.stopPlayback()
        super.onDestroy()
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
