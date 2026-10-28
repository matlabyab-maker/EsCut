package com.escut.app

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.max

class MainActivity : Activity() {
    private val pickCode = 101
    private var videoUri: Uri? = null
    private var durationMs = 0
    private var playheadMs = 0
    private lateinit var preview: VideoView
    private lateinit var timeline: SeekBar
    private lateinit var startTime: EditText
    private lateinit var endTime: EditText
    private lateinit var selected: TextView
    private lateinit var status: TextView
    private lateinit var cut: Button
    private lateinit var thumbs: LinearLayout
    private lateinit var startMark: TextView
    private lateinit var endMark: TextView

    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); makeUi() }

    private fun makeUi() {
        val page = LinearLayout(this).apply { orientation=1; setPadding(16,16,16,16); setBackgroundColor(0xFFF4F6F8.toInt()) }
        val scroll = ScrollView(this)
        val body = LinearLayout(this).apply { orientation=1; gravity=Gravity.CENTER_HORIZONTAL }
        body.addView(TextView(this).apply { text="EsCut"; textSize=29f; gravity=Gravity.CENTER; setTextColor(0xFF172554.toInt()) }, match())
        body.addView(TextView(this).apply { text="پیش‌نمایش فیلم • نوار صحنه • No Encoding"; textSize=15f; gravity=Gravity.CENTER; setPadding(0,4,0,12) }, match())
        body.addView(Button(this).apply { text="انتخاب فیلم"; setOnClickListener { pickVideo() } }, match())
        selected = TextView(this).apply { text="هنوز فیلمی انتخاب نشده است"; setPadding(0,8,0,8) }
        body.addView(selected, match())
        preview = VideoView(this).apply { setBackgroundColor(0xFF111827.toInt()) }
        body.addView(preview, LinearLayout.LayoutParams(-1, dp(210)))
        val controls = LinearLayout(this).apply { orientation=0; gravity=Gravity.CENTER }
        controls.addView(Button(this).apply { text="پخش / توقف"; setOnClickListener { if(videoUri==null) toast("ابتدا فیلم را انتخاب کنید") else if(preview.isPlaying) preview.pause() else preview.start() } }, LinearLayout.LayoutParams(0,dp(48),1f))
        controls.addView(Button(this).apply { text="شروع = اینجا"; setOnClickListener { startTime.setText("%.3f".format(Locale.US,playheadMs/1000.0)); refreshMarks() } }, LinearLayout.LayoutParams(0,dp(48),1f))
        controls.addView(Button(this).apply { text="پایان = اینجا"; setOnClickListener { endTime.setText("%.3f".format(Locale.US,playheadMs/1000.0)); refreshMarks() } }, LinearLayout.LayoutParams(0,dp(48),1f))
        body.addView(controls, match())
        body.addView(TextView(this).apply { text="نوار زمان: بکشید یا روی تصویرهای پایین بزنید"; textSize=14f; setPadding(0,10,0,2) }, match())
        timeline = SeekBar(this).apply {
            max=1000; isEnabled=false
            setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s:SeekBar?, p:Int, user:Boolean) { if(user && durationMs>0) { playheadMs=(durationMs.toLong()*p/1000).toInt(); preview.seekTo(playheadMs) } }
                override fun onStartTrackingTouch(s:SeekBar?) {}
                override fun onStopTrackingTouch(s:SeekBar?) {}
            })
        }
        body.addView(timeline, match())
        val marks=LinearLayout(this).apply { orientation=0 }
        startMark=TextView(this).apply { text="شروع: —" }
        endMark=TextView(this).apply { text="پایان: —" }
        marks.addView(startMark,LinearLayout.LayoutParams(0,-2,1f)); marks.addView(endMark,LinearLayout.LayoutParams(0,-2,1f))
        body.addView(marks,match())
        val hs=HorizontalScrollView(this)
        thumbs=LinearLayout(this).apply { orientation=0 }
        hs.addView(thumbs); body.addView(hs,match())
        startTime=field("زمان شروع بر حسب ثانیه")
        endTime=field("زمان پایان بر حسب ثانیه")
        body.addView(startTime,match()); body.addView(endTime,match())
        body.addView(TextView(this).apply {
            text="نسخه ۱.۱: یک بازه را بدون رمزگذاری مجدد جدا می‌کند. برش مستقیم ممکن است به فریم کلیدی قبلی تنظیم شود؛ حذف چند بخش و اتصال آن‌ها در نسخه بعدی است."
            textSize=13f; setPadding(0,8,0,8); setTextColor(0xFF475569.toInt())
        },match())
        cut=Button(this).apply { text="برش و ذخیره بدون Encoding"; isEnabled=false; setOnClickListener { doCut() } }
        body.addView(cut,match())
        status=TextView(this).apply { text="خروجی در Movies/EsCut ذخیره می‌شود."; setPadding(0,10,0,8) }
        body.addView(status,match())
        scroll.addView(body); page.addView(scroll); setContentView(page)
    }

    private fun match()=LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT)
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
    private fun field(h:String)=EditText(this).apply { hint=h; singleLine=true; inputType=android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL }

    private fun pickVideo() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type="video/*" },pickCode)
    }

    @Deprecated("Compatibility with Android versions")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode!=pickCode || resultCode!=RESULT_OK) return
        val uri=data?.data ?: return
        videoUri=uri
        try { contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch(_:Exception){}
        selected.text="فیلم انتخاب‌شده:\n${uri.lastPathSegment ?: uri}"
        preview.setVideoURI(uri)
        preview.setOnPreparedListener { mp ->
            durationMs=mp.duration.coerceAtLeast(0); timeline.isEnabled=durationMs>0
            startTime.setText("0"); endTime.setText("%.3f".format(Locale.US,durationMs/1000.0))
            refreshMarks(); cut.isEnabled=true
            status.text="در حال آماده‌سازی نوار تصاویر…"
            makeThumbnails(uri,durationMs)
        }
    }

    private fun makeThumbnails(uri:Uri,duration:Int) {
        thread {
            val found=mutableListOf<Pair<Int,Bitmap>>()
            val r=MediaMetadataRetriever()
            try {
                r.setDataSource(this,uri)
                for(i in 0 until 10) {
                    val ms=if(duration>0) (duration.toLong()*i/10).toInt() else 0
                    r.getFrameAtTime(ms*1000L,MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { found.add(ms to it) }
                }
            } catch(_:Exception) {} finally { try { r.release() } catch(_:Exception){} }
            runOnUiThread {
                thumbs.removeAllViews()
                found.forEach { (ms,bmp) ->
                    val tile=LinearLayout(this).apply { orientation=1; gravity=Gravity.CENTER; setPadding(dp(3),dp(3),dp(3),dp(3)) }
                    tile.addView(ImageView(this).apply { setImageBitmap(bmp); scaleType=ImageView.ScaleType.CENTER_CROP },LinearLayout.LayoutParams(dp(112),dp(70)))
                    tile.addView(TextView(this).apply { text=clock(ms); textSize=11f; gravity=Gravity.CENTER },LinearLayout.LayoutParams(dp(112),-2))
                    tile.setOnClickListener { playheadMs=ms; preview.seekTo(ms); timeline.progress=if(durationMs>0) (ms.toLong()*1000/durationMs).toInt() else 0 }
                    thumbs.addView(tile)
                }
                status.text=if(found.isEmpty()) "پیش‌نمایش آماده است؛ تصویر بندانگشتی در دسترس نبود." else "روی هر تصویر بزنید تا به همان زمان بروید."
            }
        }
    }

    private fun refreshMarks() {
        startMark.text="شروع: ${startTime.text}"
        endMark.text="پایان: ${endTime.text}"
    }

    private fun doCut() {
        val uri=videoUri ?: return
        val a=startTime.text.toString().toDoubleOrNull()
        val b=endTime.text.toString().toDoubleOrNull()
        if(a==null || b==null || a<0 || b<=a || (durationMs>0 && b*1000>durationMs+500)) { toast("زمان شروع و پایان معتبر نیست"); return }
        cut.isEnabled=false; status.text="در حال برش بدون Encoding…"
        thread {
            try {
                copyTrim(uri,(a*1_000_000).toLong(),(b*1_000_000).toLong())
                runOnUiThread { status.text="برش انجام شد؛ فایل در Movies/EsCut ذخیره شد."; cut.isEnabled=true; toast("برش تمام شد") }
            } catch(e:Exception) {
                runOnUiThread { status.text="خطا: ${e.localizedMessage ?: "فرمت پشتیبانی نمی‌شود"}"; cut.isEnabled=true }
            }
        }
    }

    private fun copyTrim(uri:Uri,startUs:Long,endUs:Long) {
        val ex=MediaExtractor()
        var mux:MediaMuxer?=null
        var out:Uri?=null
        try {
            ex.setDataSource(this,uri,null)
            val stamp=SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(Date())
            val values=ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME,"EsCut_$stamp.mp4")
                put(MediaStore.Video.Media.MIME_TYPE,"video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH,Environment.DIRECTORY_MOVIES+"/EsCut")
                put(MediaStore.Video.Media.IS_PENDING,1)
            }
            out=contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,values) ?: error("ساخت فایل خروجی ناموفق بود")
            val pfd=contentResolver.openFileDescriptor(out,"rw") ?: error("فایل خروجی باز نشد")
            pfd.use { fd ->
                mux=MediaMuxer(fd.fileDescriptor,MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                val tracks=mutableMapOf<Int,Int>()
                for(i in 0 until ex.trackCount) {
                    val f=ex.getTrackFormat(i); val mime=f.getString(MediaFormat.KEY_MIME) ?: continue
                    if(mime.startsWith("video/") || mime.startsWith("audio/")) try { tracks[i]=mux!!.addTrack(f) } catch(_:Exception){}
                }
                if(tracks.isEmpty()) error("ردیف صوتی یا تصویری قابل‌استفاده پیدا نشد")
                mux!!.start(); tracks.keys.forEach { ex.selectTrack(it) }
                ex.seekTo(startUs,MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                val actualStart=ex.sampleTime.takeIf { it>=0 } ?: startUs
                val capacity=16*1024*1024
                val buffer=ByteBuffer.allocateDirect(capacity); val info=MediaCodec.BufferInfo()
                while(true) {
                    val track=ex.sampleTrackIndex
                    if(track<0 || ex.sampleTime>=endUs) break
                    if(tracks.containsKey(track)) {
                        buffer.clear(); val size=ex.readSampleData(buffer,0)
                        if(size<0) break
                        if(size>capacity) error("نمونه رسانه از حد بافر بزرگ‌تر است")
                        info.offset=0; info.size=size
                        info.presentationTimeUs=max(0L,ex.sampleTime-actualStart)
                        info.flags=ex.sampleFlags
                        mux!!.writeSampleData(tracks[track]!!,buffer,info)
                    }
                    if(!ex.advance()) break
                }
                mux!!.stop(); mux!!.release(); mux=null
            }
            contentResolver.update(out,ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING,0) },null,null)
        } catch(e:Exception) {
            try { mux?.release() } catch(_:Exception){}
            out?.let { try { contentResolver.delete(it,null,null) } catch(_:Exception){} }
            throw e
        } finally { try { ex.release() } catch(_:Exception){} }
    }

    private fun clock(ms:Int):String {
        val s=max(0,ms/1000)
        return String.format(Locale.US,"%02d:%02d:%02d",s/3600,(s%3600)/60,s%60)
    }
    private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_LONG).show()
}
