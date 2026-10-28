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
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
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
        listOf(title, subtitle, choose, selectedLabel, startInput, endInput, hint, cutButton, statusLabel)
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
            statusLabel.text = "زمان شروع و پایان را بر حسب ثانیه وارد کنید."
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
                    statusLabel.text = "برش تمام شد.\nفایل در Movies/EsCut ذخیره شد."
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
        val outputUri = createOutputUri()
        try {
            extractor.setDataSource(this, inputUri, null)
            val outputFd = contentResolver.openFileDescriptor(outputUri, "rw")
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
        val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("ساخت فایل خروجی ممکن نشد")
        val done = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
        contentResolver.update(uri, done, null, null)
        return uri
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
