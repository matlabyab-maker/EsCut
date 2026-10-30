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
import android.os.Build
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
    private lateinit var intervalInput: EditText
    private lateinit var storyboardStatus: TextView
    private lateinit var selectDeleteButton: Button
    private lateinit var deleteSelectedButton: Button
    private var storyboardMode: StoryboardMode = StoryboardMode.TIME
    private var selectionMode = false
    private var storyboardDurationMs = 0L
    private val storyboardTimes = mutableListOf<Long>()
    private val selectedFrameTimes = linkedSetOf<Long>()
    private val frameCards = mutableMapOf<Long, View>()

    private enum class StoryboardMode { TIME, SCENE }
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
        val modeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val timeModeButton = Button(this).apply {
            text = "حالت زمانی"
            setOnClickListener {
                storyboardMode = StoryboardMode.TIME
                selectedFrameTimes.clear()
                selectedUri?.let { loadStoryboard(it) }
            }
        }
        val sceneModeButton = Button(this).apply {
            text = "حالت صحنه"
            setOnClickListener {
                storyboardMode = StoryboardMode.SCENE
                selectedFrameTimes.clear()
                selectedUri?.let { loadStoryboard(it) }
            }
        }
        modeRow.addView(timeModeButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        modeRow.addView(sceneModeButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        intervalInput = field("فاصلهٔ فریم‌ها به ثانیه؛ مثلاً 2 یا 0.5").apply { setText("2") }
        val rebuildButton = Button(this).apply {
            text = "ساخت دوبارهٔ اسلایدشو"
            setOnClickListener {
                selectedUri?.let {
                    selectedFrameTimes.clear()
                    loadStoryboard(it)
                } ?: toast("ابتدا یک فیلم انتخاب کنید.")
            }
        }
        storyboard = GridLayout(this).apply {
            columnCount = 50
            rowCount = 6
            setPadding(2, 2, 2, 2)
            useDefaultMargins = false
        }
        selectDeleteButton = Button(this).apply {
            text = "انتخاب فریم‌ها برای حذف: خاموش"
            setOnClickListener {
                selectionMode = !selectionMode
                text = if (selectionMode) "انتخاب فریم‌ها برای حذف: روشن" else "انتخاب فریم‌ها برای حذف: خاموش"
                if (!selectionMode) selectedFrameTimes.clear()
                refreshCardSelection()
            }
        }
        deleteSelectedButton = Button(this).apply {
            text = "حذف فریم‌ها/صحنه‌های انتخاب‌شده"
            isEnabled = false
            setOnClickListener { deleteSelectedFrames() }
        }
        storyboardStatus = TextView(this).apply {
            text = "حالت زمانی: فاصله را به ثانیه وارد کنید؛ حالت صحنه تغییرات تصویری احتمالی را پیدا می‌کند."
            textSize = 12f
            setTextColor(0xFF475569.toInt())
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
            timeLabel, startInput, endInput, storyboardTitle, modeRow, intervalInput, rebuildButton,
            storyboardStatus, storyboardScroll, selectDeleteButton, deleteSelectedButton, hint, cutButton, statusLabel)
            .forEach { view ->
                val height = if (view === videoView) dp(220) else ViewGroup.LayoutParams.WRAP_CONTENT
                content.addView(view, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, height
                ))
            }
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
            selectedFrameTimes.clear()
            selectionMode = false
            if (::selectDeleteButton.isInitialized) selectDeleteButton.text = "انتخاب فریم‌ها برای حذف: خاموش"
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
            attachVideoToPlayer(uri)
            statusLabel.text = "در حال آماده‌سازی پلیر و نوار فریم‌ها…"
            loadStoryboard(uri)
        }
    }

    private fun attachVideoToPlayer(uri: Uri) {
        prepared = false
        durationMs = 0
        playButton.isEnabled = false
        playButton.text = "در حال آماده‌سازی…"
        videoView.setOnPreparedListener { mp ->
            prepared = true
            durationMs = mp.duration.coerceAtLeast(0)
            seekBar.max = durationMs.coerceAtLeast(1)
            seekBar.progress = 0
            timeLabel.text = "${formatMs(0)} / ${formatMs(durationMs)}"
            playButton.isEnabled = true
            playButton.text = "مکث"
            mp.isLooping = false
            // Start only after the media is prepared; this makes the first frame visible.
            videoView.start()
            statusLabel.text = "پلیر آماده است."
        }
        videoView.setOnCompletionListener {
            playButton.text = "پخش"
            if (durationMs > 0) seekBar.progress = durationMs
            timeLabel.text = "${formatMs(durationMs)} / ${formatMs(durationMs)}"
        }
        videoView.setOnErrorListener { _, what, extra ->
            prepared = false
            playButton.isEnabled = false
            playButton.text = "پخش"
            statusLabel.text = "پخش ناموفق؛ فرمت یا کدک این ویدیو پشتیبانی نمی‌شود ($what/$extra)."
            true
        }
        try {
            videoView.stopPlayback()
            videoView.setVideoURI(uri)
            // Do not call start before onPrepared; it can race on some Android versions.
        } catch (e: Exception) {
            statusLabel.text = "پخش ناموفق: ${e.localizedMessage ?: "فایل باز نشد"}"
        }
    }

    private fun loadStoryboard(uri: Uri) {
        storyboard.removeAllViews()
        frameCards.clear()
        storyboardTimes.clear()
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
                val requestedIntervalSec = intervalInput.text.toString().toDoubleOrNull()?.takeIf { it > 0.0 } ?: 2.0
                val maxFrames = 180
                val frameTimes = mutableListOf<Long>()
                val frames = mutableListOf<Pair<Long, android.graphics.Bitmap>>()
                if (storyboardMode == StoryboardMode.TIME) {
                    val effectiveIntervalMs = maxOf(1L, (requestedIntervalSec * 1000.0).toLong())
                    val intervalMs = maxOf(effectiveIntervalMs, (lengthMs / maxFrames).coerceAtLeast(1L))
                    var timeMs = 0L
                    while (timeMs < lengthMs && frameTimes.size < maxFrames) {
                        frameTimes.add(timeMs)
                        timeMs += intervalMs
                    }
                } else {
                    // Approximate scene-change detection: compare small frames at regular probes.
                    val probeStepMs = maxOf(1000L, lengthMs / maxFrames)
                    var previousSmall: android.graphics.Bitmap? = null
                    var timeMs = 0L
                    while (timeMs < lengthMs && frameTimes.size < maxFrames) {
                        val small = try { getScaledFrame(retriever, timeMs * 1000L, 32, 18) } catch (_: Exception) { null }
                        if (small != null) {
                            val previous = previousSmall
                            if (previous == null || frameDifference(previous, small) >= 42.0) frameTimes.add(timeMs)
                            previousSmall?.recycle()
                            previousSmall = small
                        }
                        timeMs += probeStepMs
                    }
                    previousSmall?.recycle()
                    if (frameTimes.isEmpty()) frameTimes.add(0L)
                }
                frameTimes.distinct().forEach { timeMs ->
                    try {
                        val thumbnail = getScaledFrame(retriever, timeMs * 1000L, 160, 90, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        if (thumbnail != null) frames.add(Pair(timeMs, thumbnail))
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
                            if (selectionMode) {
                                if (!selectedFrameTimes.add(time)) selectedFrameTimes.remove(time)
                                refreshCardSelection()
                            } else if (startInput.tag != "start_set") {
                                startInput.setText(String.format(Locale.US, "%.3f", time / 1000.0))
                                startInput.tag = "start_set"
                                toast("شروع برش تنظیم شد؛ فریم پایان را انتخاب کن.")
                            } else {
                                endInput.setText(String.format(Locale.US, "%.3f", time / 1000.0))
                                startInput.tag = null
                                toast("پایان برش تنظیم شد.")
                            }
                        }
                        storyboardTimes.add(time)
                        frameCards[time] = card
                        storyboard.addView(card)
                    }
                    storyboardDurationMs = lengthMs
                    refreshCardSelection()
                    val modeText = if (storyboardMode == StoryboardMode.TIME) "زمانی" else "صحنه‌ای (تشخیص تقریبی)"
                    storyboardStatus.text = "حالت $modeText؛ ${frames.size} فریم در ۶ ردیف. در حالت زمانی فاصلهٔ واردشده رعایت می‌شود، مگر آنکه تعداد از ۱۸۰ بیشتر شود."
                    statusLabel.text = "اسلایدشو آماده است؛ برای حذف چند بخش، حالت انتخاب را روشن و فریم‌ها را لمس کنید."
                }
            } catch (e: Exception) {
                runOnUiThread { statusLabel.text = "ساخت نوار فریم‌ها ناموفق بود: ${e.localizedMessage ?: "خطای خواندن فیلم"}" }
            } finally { try { retriever?.release() } catch (_: Exception) {} }
        }
    }

    private fun getScaledFrame(
        retriever: MediaMetadataRetriever,
        timeUs: Long,
        width: Int,
        height: Int,
        option: Int = MediaMetadataRetriever.OPTION_CLOSEST
    ): android.graphics.Bitmap? {
        // Scaled decoding avoids decoding full-resolution frames for tiny storyboard cards.
        return if (Build.VERSION.SDK_INT >= 27) {
            retriever.getScaledFrameAtTime(timeUs, option, width, height)
        } else {
            val raw = retriever.getFrameAtTime(timeUs, option) ?: return null
            try {
                android.graphics.Bitmap.createScaledBitmap(raw, width, height, true)
            } finally {
                raw.recycle()
            }
        }
    }

    private fun frameDifference(a: android.graphics.Bitmap, b: android.graphics.Bitmap): Double {
        val width = minOf(a.width, b.width)
        val height = minOf(a.height, b.height)
        if (width == 0 || height == 0) return 0.0
        var sum = 0L
        var count = 0
        for (y in 0 until height step 2) for (x in 0 until width step 2) {
            val ca = a.getPixel(x, y)
            val cb = b.getPixel(x, y)
            sum += kotlin.math.abs(android.graphics.Color.red(ca) - android.graphics.Color.red(cb))
            sum += kotlin.math.abs(android.graphics.Color.green(ca) - android.graphics.Color.green(cb))
            sum += kotlin.math.abs(android.graphics.Color.blue(ca) - android.graphics.Color.blue(cb))
            count += 3
        }
        return if (count == 0) 0.0 else sum.toDouble() / count
    }

    private fun refreshCardSelection() {
        frameCards.forEach { (time, view) ->
            view.setBackgroundColor(if (time in selectedFrameTimes) 0xFFFFC107.toInt() else 0xFFE2E8F0.toInt())
        }
        if (::deleteSelectedButton.isInitialized) deleteSelectedButton.isEnabled = selectedFrameTimes.isNotEmpty()
    }

    private fun deleteSelectedFrames() {
        val uri = selectedUri ?: return
        val points = storyboardTimes.sorted()
        if (selectedFrameTimes.isEmpty() || points.isEmpty()) {
            toast("ابتدا یک یا چند فریم را انتخاب کنید.")
            return
        }
        // Storyboard timestamps and duration are milliseconds; MediaExtractor sampleTime is microseconds.
        // Convert deletion ranges to microseconds before comparing them with sample timestamps.
        val ranges = selectedFrameTimes.mapNotNull { selected ->
            val index = points.indexOf(selected)
            if (index < 0) null else {
                val startMs = selected.coerceIn(0L, storyboardDurationMs)
                val endMs = (if (index + 1 < points.size) points[index + 1] else storyboardDurationMs)
                    .coerceIn(0L, storyboardDurationMs)
                if (endMs > startMs) (startMs * 1000L) to (endMs * 1000L) else null
            }
        }.sortedBy { it.first }
        if (ranges.isEmpty()) {
            toast("بازهٔ قابل حذفی پیدا نشد.")
            return
        }
        deleteSelectedButton.isEnabled = false
        statusLabel.text = "در حال حذف ${ranges.size} بازه بدون رمزگذاری مجدد…"
        thread {
            try {
                removeRangesWithoutReencode(uri, ranges)
                runOnUiThread {
                    statusLabel.text = "بازه‌های انتخاب‌شده از نسخهٔ خروجی حذف شدند؛ فایل جدید در Movies/EsCut ذخیره شد و فایل اصلی دست‌نخورده ماند."
                    deleteSelectedButton.isEnabled = true
                    toast("نسخهٔ خروجی بدون بازه‌های انتخاب‌شده ساخته شد.")
                }
            } catch (e: Exception) {
                runOnUiThread {
                    statusLabel.text = "خطای حذف: ${e.localizedMessage ?: "پردازش انجام نشد"}"
                    deleteSelectedButton.isEnabled = true
                    toast("حذف بخش‌ها ناموفق بود.")
                }
            }
        }
    }

    private fun removeRangesWithoutReencode(inputUri: Uri, rawRanges: List<Pair<Long, Long>>): Uri {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        val outputUri = createOutputUri()
        val ranges = rawRanges.sortedBy { it.first }.fold(mutableListOf<Pair<Long, Long>>()) { acc, range ->
            if (acc.isNotEmpty() && range.first <= acc.last().second) {
                val previous = acc.removeAt(acc.lastIndex)
                acc.add(previous.first to maxOf(previous.second, range.second))
            } else acc.add(range)
            acc
        }
        try {
            contentResolver.openFileDescriptor(inputUri, "r").use { fd ->
                if (fd == null) throw IllegalStateException("فایل ورودی باز نشد")
                extractor.setDataSource(fd.fileDescriptor)
            }
            val outputFd = contentResolver.openFileDescriptor(outputUri, "rw") ?: throw IllegalStateException("فایل خروجی باز نشد")
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
                val buffer = ByteBuffer.allocateDirect(8 * 1024 * 1024)
                val info = MediaCodec.BufferInfo()
                var written = 0
                while (true) {
                    val track = extractor.sampleTrackIndex
                    if (track < 0) break
                    val sampleTime = extractor.sampleTime
                    val deleted = ranges.any { sampleTime >= it.first && sampleTime < it.second }
                    if (!deleted && trackMap.containsKey(track)) {
                        // Shift retained samples left by the exact duration of all deleted ranges
                        // that have ended before this sample. Keep timestamps monotonic per track.
                        val removedBefore = ranges.asSequence()
                            .filter { it.second <= sampleTime }
                            .sumOf { it.second - it.first }
                        buffer.clear()
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) break
                        info.offset = 0
                        info.size = size
                        info.presentationTimeUs = (sampleTime - removedBefore).coerceAtLeast(0L)
                        info.flags = extractor.sampleFlags
                        muxer!!.writeSampleData(trackMap[track]!!, buffer, info)
                        written++
                    }
                    if (!extractor.advance()) break
                }
                if (written == 0) throw IllegalStateException("تمام بازه‌های فیلم انتخاب شده‌اند؛ بخشی از فیلم را باقی بگذارید.")
                muxer!!.stop()
            }
            contentResolver.update(outputUri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
            return outputUri
        } catch (e: Exception) {
            try { contentResolver.delete(outputUri, null, null) } catch (_: Exception) {}
            throw e
        } finally {
            try { extractor.release() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
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
