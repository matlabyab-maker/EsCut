package com.escut.app

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.VideoView
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
    private lateinit var storyboard: GridLayout
    private lateinit var videoView: VideoView
    private lateinit var playButton: Button
    private lateinit var seekBar: SeekBar
    private lateinit var timeLabel: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var durationMs = 0
    private var userSeeking = false
    private var prepared = false

    private val progressTask = object : Runnable {
        override fun run() {
            if (prepared && !userSeeking && videoView.isPlaying) {
                val position = videoView.currentPosition.coerceAtLeast(0)
                if (durationMs > 0) seekBar.progress = position.coerceAtMost(durationMs)
                timeLabel.text = "${formatMs(position)} / ${formatMs(durationMs)}"
            }
            handler.postDelayed(this, 250)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        handler.post(progressTask)
    }

    override fun onDestroy() {
        handler.removeCallbacks(progressTask)
        try { videoView.stopPlayback() } catch (_: Exception) {}
        super.onDestroy()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 18, 18, 18)
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
            text = "پخش، برش زمانی و حفظ کدک اصلی"
            textSize = 17f
            setPadding(0, 6, 0, 14)
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
            setPadding(0, 10, 0, 8)
        }
        videoView = VideoView(this).apply {
            setBackgroundColor(0xFF111827.toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(220)
            )
            setOnPreparedListener { mp ->
                prepared = true
                durationMs = mp.duration.coerceAtLeast(0)
                seekBar.max = durationMs.coerceAtLeast(1)
                seekBar.progress = 0
                timeLabel.text = "${formatMs(0)} / ${formatMs(durationMs)}"
                playButton.isEnabled = true
                mp.isLooping = false
            }
            setOnCompletionListener {
                playButton.text = "پخش"
                if (durationMs > 0) seekBar.progress = durationMs
                timeLabel.text = "${formatMs(durationMs)} / ${formatMs(durationMs)}"
            }
            setOnErrorListener { _, what, extra ->
                statusLabel.text = "پخش این فرمت توسط پلیر داخلی دستگاه پشتیبانی نشد ($what/$extra)."
                true
            }
        }
        playButton = Button(this).apply {
            text = "پخش"
            isEnabled = false
            setOnClickListener {
                if (!prepared) return@setOnClickListener
                if (videoView.isPlaying) {
                    videoView.pause()
                    text = "ادامه پخش"
                } else {
                    if (durationMs > 0 && videoView.currentPosition >= durationMs) videoView.seekTo(0)
                    videoView.start()
                    text = "مکث"
                }
            }
        }
        seekBar = SeekBar(this).apply {
            max = 1
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(seekBar: SeekBar?) { userSeeking = true }
                override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) timeLabel.text = "${formatMs(progress)} / ${formatMs(durationMs)}"
                }
                override fun onStopTrackingTouch(bar: SeekBar?) {
                    val target = bar?.progress ?: 0
                    if (prepared && durationMs > 0) videoView.seekTo(target.coerceIn(0, durationMs))
                    userSeeking = false
                }
            })
        }
        timeLabel = TextView(this).apply {
            text = "00:00 / 00:00"
            gravity = Gravity.CENTER
            textSize = 13f
            setTextColor(0xFF334155.toInt())
        }
        startInput = field("شروع برش (ثانیه)، مثال: 10")
        endInput = field("پایان برش (ثانیه)، مثال: 60")
        val storyboardTitle = TextView(this).apply {
            text = "نوار فریم‌ها — برای انتخاب شروع و پایان، دو فریم را لمس کن"
            textSize = 14f
            setTextColor(0xFF172554.toInt())
            setPadding(0, 14, 0, 6)
        }
        storyboard = GridLayout(this).apply {
            columnCount = 15
            rowCount = 3
            setPadding(2, 2, 2, 2)
            useDefaultMargins = false
        }
        val storyboardScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = true
            addView(storyboard, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }
        val hint = TextView(this).apply {
            text = "حالت بدون رمزگذاری مجدد: جریان صوتی و تصویری اصلی کپی می‌شود؛ برش ممکن است به فریم کلیدی نزدیک شود. تغییر رزولوشن یا حجم هدف در این حالت ممکن نیست."
            textSize = 13f
            setTextColor(0xFF475569.toInt())
            setPadding(0, 10, 0, 12)
        }
        cutButton = Button(this).apply {
            text = "برش و ذخیره بدون Encoding مجدد"
            isEnabled = false
            setOnClickListener { startCut() }
        }
        statusLabel = TextView(this).apply {
            text = "خروجی در Movies/EsCut ذخیره می‌شود."
            textSize = 14f
            setPadding(0, 12, 0, 8)
        }
        listOf<View>(title, subtitle, choose, selectedLabel, videoView, playButton, seekBar,
            timeLabel, startInput, endInput, storyboardTitle, storyboardScroll, hint, cutButton, statusLabel)
            .forEach { content.addView(it, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )) }
        scroll.addView(content)
        root.addView(scroll)
        setContentView(root)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun field(hintText: String): EditText = EditText(this).apply {
        hint = hintText
        textSize = 16f
        setSingleLine(true)
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        setPadding(12, 5, 12, 5)
    }

    private fun openVideoPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "video/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, pickVideoCode)
    }

    @Deprecated("Deprecated by platform, retained for broad device compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == pickVideoCode && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            selectedUri = uri
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            selectedLabel.text = "فیلم انتخاب شد:\n${uri.lastPathSegment ?: uri}"
            cutButton.isEnabled = true
            startInput.tag = null
            prepared = false
            durationMs = 0
            seekBar.progress = 0
            seekBar.max = 1
            timeLabel.text = "00:00 / 00:00"
            playButton.isEnabled = false
            playButton.text = "پخش"
            try {
                videoView.stopPlayback()
                videoView.setVideoURI(uri)
                videoView.setOnPreparedListener { mp ->
                    prepared = true
                    durationMs = mp.duration.coerceAtLeast(0)
                    seekBar.max = durationMs.coerceAtLeast(1)
                    seekBar.progress = 0
                    timeLabel.text = "${formatMs(0)} / ${formatMs(durationMs)}"
                    playButton.isEnabled = true
                    statusLabel.text = "پلیر آماده است. نوار زمان با موقعیت واقعی پخش به‌روز می‌شود."
                }
                videoView.setOnCompletionListener {
                    playButton.text = "پخش"
                    if (durationMs > 0) seekBar.progress = durationMs
                    timeLabel.text = "${formatMs(durationMs)} / ${formatMs(durationMs)}"
                }
                videoView.start()
                playButton.text = "مکث"
            } catch (e: Exception) {
                statusLabel.text = "پخش ناموفق: ${e.localizedMessage ?: "فایل باز نشد"}"
            }
            statusLabel.text = "در حال آماده‌سازی پلیر و نوار فریم‌ها…"
            loadStoryboard(uri)
        }
    }

    private fun loadStoryboard(uri: Uri) {
        storyboard.removeAllViews()
        thread {
            var retriever: MediaMetadataRetriever? = null
            try {
                retriever = MediaMetadataRetriever()
                contentResolver.openFileDescriptor(uri, "r").use { pfd ->
                    if (pfd == null) throw IllegalStateException("فایل ورودی باز نشد")
                    retriever.setDataSource(pfd.fileDescriptor)
                }
                val lengthMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                    ?: throw IllegalStateException("مدت فیلم خوانده نشد")
                if (lengthMs <= 0) throw IllegalStateException("مدت فیلم نامعتبر است")
                // Exactly 45 representative thumbnails: 3 rows x 15 columns.
                // Sample evenly across the full duration so long videos are covered too.
                val frames = mutableListOf<Pair<Long, android.graphics.Bitmap>>()
                val sampleCount = 45
                for (index in 0 until sampleCount) {
                    val timeMs = if (sampleCount == 1) 0L else
                        (lengthMs - 1L).coerceAtLeast(0L) * index / (sampleCount - 1)
                    try {
                        val bitmap = retriever.getFrameAtTime(timeMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        if (bitmap != null) frames.add(Pair(timeMs, bitmap))
                    } catch (_: Exception) {}
                }
                runOnUiThread {
                    storyboard.removeAllViews()
                    frames.forEach { (time, bitmap) ->
                        val card = LinearLayout(this).apply {
                            orientation = LinearLayout.VERTICAL
                            gravity = Gravity.CENTER
                            setPadding(1, 1, 1, 1)
                            setBackgroundColor(0xFFE2E8F0.toInt())
                            layoutParams = GridLayout.LayoutParams().apply {
                                width = dp(43)
                                height = dp(48)
                                setMargins(dp(1), dp(1), dp(1), dp(1))
                            }
                        }
                        val image = ImageView(this).apply {
                            setImageBitmap(bitmap)
                            scaleType = ImageView.ScaleType.CENTER_CROP
                            layoutParams = LinearLayout.LayoutParams(dp(39), dp(27))
                        }
                        val label = TextView(this).apply {
                            text = formatMs(time.toInt())
                            textSize = 7f
                            gravity = Gravity.CENTER
                            setTextColor(0xFF0F172A.toInt())
                        }
                        card.addView(image)
                        card.addView(label)
                        card.setOnClickListener {
                            if (startInput.tag != "start_set") {
                                startInput.setText(String.format(Locale.US, "%.3f", time / 1000.0))
                                startInput.tag = "start_set"
                                toast("شروع برش تنظیم شد؛ فریم پایان را انتخاب کن.")
                            } else {
                                endInput.setText(String.format(Locale.US, "%.3f", time / 1000.0))
                                startInput.tag = null
                                toast("پایان برش تنظیم شد.")
                            }
                        }
                        storyboard.addView(card)
                    }
                    statusLabel.text = "۴۵ فریم کوچک در ۳ ردیف ۱۵تایی آماده است؛ برای دیدن همه فریم‌ها نوار را افقی حرکت بده. دو فریم را برای شروع و پایان انتخاب کن."
                }
            } catch (e: Exception) {
                runOnUiThread { statusLabel.text = "ساخت نوار فریم‌ها ناموفق بود: ${e.localizedMessage ?: "خطای خواندن فیلم"}" }
            } finally { try { retriever?.release() } catch (_: Exception) {} }
        }
    }

    private fun formatMs(ms: Int): String {
        val total = (ms.coerceAtLeast(0) / 1000)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%02d:%02d", m, s)
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
        statusLabel.text = "در حال برش بدون رمزگذاری مجدد…"
        thread {
            try {
                trimWithoutReencode(uri, (startSec * 1_000_000).toLong(), (endSec * 1_000_000).toLong())
                runOnUiThread {
                    statusLabel.text = "برش تمام شد؛ فایل در Movies/EsCut ذخیره شد."
                    cutButton.isEnabled = true
                    toast("برش تمام شد.")
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
        val outputUri = createOutputUri()
        try {
            contentResolver.openFileDescriptor(inputUri, "r").use { sourceFd ->
                if (sourceFd == null) throw IllegalStateException("به فایل انتخاب‌شده دسترسی نداریم؛ دوباره فیلم را انتخاب کن.")
                extractor.setDataSource(sourceFd.fileDescriptor)
            }
            val outputFd = contentResolver.openFileDescriptor(outputUri, "rw")
                ?: throw IllegalStateException("فایل خروجی باز نشد")
            outputFd.use { parcel ->
                muxer = MediaMuxer(parcel.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                val trackMap = mutableMapOf<Int, Int>()
                for (i in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                    if (mime.startsWith("video/") || mime.startsWith("audio/")) trackMap[i] = muxer!!.addTrack(format)
                }
                if (trackMap.isEmpty()) throw IllegalStateException("ردیف صوتی یا تصویری پیدا نشد")
                muxer!!.start()
                trackMap.keys.forEach { extractor.selectTrack(it) }
                extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                val actualStartUs = extractor.sampleTime.takeIf { it >= 0 } ?: startUs
                val buffer = ByteBuffer.allocateDirect(8 * 1024 * 1024)
                val info = MediaCodec.BufferInfo()
                var written = 0
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
                        written++
                    }
                    if (!extractor.advance()) break
                }
                if (written == 0) throw IllegalStateException("در این محدوده نمونه قابل‌نوشتن پیدا نشد؛ زمان‌ها را تغییر بده.")
                muxer!!.stop()
            }
            val done = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
            contentResolver.update(outputUri, done, null, null)
            return outputUri
        } catch (e: Exception) {
            try { contentResolver.delete(outputUri, null, null) } catch (_: Exception) {}
            throw e
        } finally {
            try { extractor.release() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
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

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
