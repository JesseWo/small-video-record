package com.mabeijianxi.smallvideo2

import android.Manifest
import android.app.Activity
import android.app.ProgressDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Camera
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.TextUtils
import android.view.View
import android.view.Window
import android.widget.*
import android.widget.AdapterView.OnItemSelectedListener
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.mabeijianxi.smallvideorecord2.JianXiCamera
import com.mabeijianxi.smallvideorecord2.LocalMediaCompress
import com.mabeijianxi.smallvideorecord2.MediaRecorderActivity
import com.mabeijianxi.smallvideorecord2.MediaRecorderActivity.Companion.goSmallVideoRecorder
import com.mabeijianxi.smallvideorecord2.model.*
import com.mabeijianxi.smallvideorecord2.utils.FileUtils
import com.mabeijianxi.smallvideorecord2.utils.StringUtils
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {
    private val PERMISSION_REQUEST_CODE = 0x001
    private var sv: ScrollView? = null
    private var bt_start: Button? = null
    private var bt_choose: Button? = null
    private var et_maxtime: EditText? = null
    private var spinner_record: Spinner? = null
    private var et_maxframerate: EditText? = null
    private val CHOOSE_CODE = 0x000520
    private var rg_aspiration: RadioGroup? = null
    private var mProgressDialog: ProgressDialog? = null
    private var ll_only_compress: LinearLayout? = null
    private var rg_only_compress_mode: RadioGroup? = null
    private var ll_only_compress_crf: LinearLayout? = null
    private var et_only_compress_crfSize: EditText? = null
    private var ll_only_compress_bitrate: LinearLayout? = null
    private var et_only_compress_maxbitrate: EditText? = null
    private var tv_only_compress_maxbitrate: TextView? = null
    private var et_only_compress_bitrate: EditText? = null
    private var spinner_only_compress: Spinner? = null
    private var et_only_framerate: EditText? = null
    private var et_bitrate: EditText? = null
    private var et_only_scale: EditText? = null
    private var et_mintime: EditText? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initSmallVideo()
        initView()
        initEvent()
        permissionCheck()
    }

    private fun setSupportCameraSize() {
        val back = Camera.open(Camera.CameraInfo.CAMERA_FACING_BACK)
        val backSupportList = back.parameters.supportedPreviewSizes.map { "${it.height}x${it.width}" }
        back.release()
        spinner_support_preview_size.adapter = ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, backSupportList)

        val front = Camera.open(Camera.CameraInfo.CAMERA_FACING_FRONT)
        val frontSupportList = front.parameters.supportedPreviewSizes.map { "${it.height}x${it.width}" }
        front.release()
        spinner_front_support_preview_size.adapter = ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, frontSupportList)
    }

    private fun initEvent() {
        rg_only_compress_mode!!.setOnCheckedChangeListener { group, checkedId ->
            when (checkedId) {
                R.id.rb_auto -> {
                    ll_only_compress_crf!!.visibility = View.VISIBLE
                    ll_only_compress_bitrate!!.visibility = View.GONE
                }
                R.id.rb_vbr -> {
                    ll_only_compress_crf!!.visibility = View.GONE
                    ll_only_compress_bitrate!!.visibility = View.VISIBLE
                    tv_only_compress_maxbitrate!!.visibility = View.VISIBLE
                    et_only_compress_maxbitrate!!.visibility = View.VISIBLE
                }
                R.id.rb_cbr -> {
                    ll_only_compress_crf!!.visibility = View.GONE
                    ll_only_compress_bitrate!!.visibility = View.VISIBLE
                    tv_only_compress_maxbitrate!!.visibility = View.GONE
                    et_only_compress_maxbitrate!!.visibility = View.GONE
                }
            }
        }
        rg_aspiration!!.setOnCheckedChangeListener { group, checkedId ->
            when (checkedId) {
                R.id.rb_recorder -> {
                    sv!!.visibility = View.VISIBLE
                    ll_only_compress!!.visibility = View.GONE
                }
                R.id.rb_local -> {
                    sv!!.visibility = View.GONE
                    ll_only_compress!!.visibility = View.VISIBLE
                }
            }
        }

        spinner_support_preview_size?.onItemSelectedListener = object : OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View, position: Int, id: Long) {
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun initView() {
        rg_aspiration = findViewById<View>(R.id.rg_aspiration) as RadioGroup
        sv = findViewById<View>(R.id.sv) as ScrollView
        bt_choose = findViewById<View>(R.id.bt_choose) as Button
        ll_only_compress = findViewById<View>(R.id.ll_only_compress) as LinearLayout
        et_maxframerate = findViewById<View>(R.id.et_maxframerate) as EditText
        et_bitrate = findViewById<View>(R.id.et_record_bitrate) as EditText
        et_maxtime = findViewById<View>(R.id.et_maxtime) as EditText
        et_mintime = findViewById<View>(R.id.et_mintime) as EditText
        et_only_framerate = findViewById<View>(R.id.et_only_framerate) as EditText
        et_only_scale = findViewById<View>(R.id.et_only_scale) as EditText
        spinner_record = findViewById<View>(R.id.spinner_record) as Spinner
        rg_only_compress_mode = i_only_compress.findViewById<View>(R.id.rg_mode) as RadioGroup
        ll_only_compress_crf = i_only_compress.findViewById<View>(R.id.ll_crf) as LinearLayout
        et_only_compress_crfSize = i_only_compress.findViewById<View>(R.id.et_crfSize) as EditText
        ll_only_compress_bitrate = i_only_compress.findViewById<View>(R.id.ll_bitrate) as LinearLayout
        et_only_compress_maxbitrate = i_only_compress.findViewById<View>(R.id.et_maxbitrate) as EditText
        tv_only_compress_maxbitrate = i_only_compress.findViewById<View>(R.id.tv_maxbitrate) as TextView
        et_only_compress_bitrate = i_only_compress.findViewById<View>(R.id.et_bitrate) as EditText
        spinner_only_compress = findViewById<View>(R.id.spinner_only_compress) as Spinner
        bt_start = findViewById<View>(R.id.bt_start) as Button
    }

    /**
     * 选择本地视频，为了方便我采取了系统的API，所以也许在一些定制机上会取不到视频地址，
     * 所以选择手机里视频的代码根据自己业务写为妙。
     *
     * @param v
     */
    fun choose(v: View?) {
        val it = Intent(Intent.ACTION_GET_CONTENT,
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
        it.setDataAndType(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "video/*")
        startActivityForResult(it, CHOOSE_CODE)
    }

    fun go(c: View?) {
        val sizeArr = (spinner_support_preview_size?.selectedItem as String).split("x")
        val width = sizeArr[1]
        val height = sizeArr[0]
        val maxFramerate = et_maxframerate!!.text.toString()
        val bitrate = et_bitrate!!.text.toString()
        val maxTime = et_maxtime!!.text.toString()
        val minTime = et_mintime!!.text.toString()
        val needFull = switch_full_screen.isChecked
        val recordMode: BaseMediaBitrateConfig
        val compressMode: BaseMediaBitrateConfig? = null
        recordMode = AutoVBRMode()
        if (spinner_record!!.selectedItem.toString() != "none") {
            recordMode.setVelocity(spinner_record!!.selectedItem.toString())
        }
        if (!needFull && checkStrEmpty(width, "请输入宽度")) {
            return
        }
        if (checkStrEmpty(height, "请输入高度")
                || checkStrEmpty(maxFramerate, "请输入最高帧率")
                || checkStrEmpty(maxTime, "请输入最大录制时间")
                || checkStrEmpty(minTime, "请输小最大录制时间")
                || checkStrEmpty(bitrate, "请输入比特率")) {
            return
        }
        //      FFMpegUtils.captureThumbnails("/storage/emulated/0/DCIM/mabeijianxi/1496455533800/1496455533800.mp4", "/storage/emulated/0/DCIM/mabeijianxi/1496455533800/1496455533800.jpg", "1");
        val config = MediaRecorderConfig.Buidler()
                .fullScreen(needFull)
                .smallVideoWidth(if (needFull) 0 else Integer.valueOf(width))
                .smallVideoHeight(height.toInt())
                .recordTimeMax(maxTime.toInt())
                .recordTimeMin(minTime.toInt())
                .maxFrameRate(maxFramerate.toInt())
                .videoBitrate(bitrate.toInt())
                .captureThumbnailsTime(1)
                .build()
        goSmallVideoRecorder(this, SendSmallVideoActivity::class.java.name, config)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            for (i in permissions.indices) {
                if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                    if (Manifest.permission.CAMERA == permissions[i]) {
                        setSupportCameraSize()
                    } else if (Manifest.permission.RECORD_AUDIO == permissions[i]) {
                    }
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == CHOOSE_CODE) {
            //
            if (resultCode == Activity.RESULT_OK && data != null && data.data != null) {
                val uri = data.data
                val proj = arrayOf(MediaStore.Images.Media.DATA, MediaStore.Images.Media.MIME_TYPE)
                val cursor = contentResolver.query(uri!!, proj, null,
                        null, null)
                if (cursor != null && cursor.moveToFirst()) {
                    val _data_num = cursor.getColumnIndex(MediaStore.Images.Media.DATA)
                    val mime_type_num = cursor.getColumnIndex(MediaStore.Images.Media.MIME_TYPE)
                    val _data = cursor.getString(_data_num)
                    val mime_type = cursor.getString(mime_type_num)
                    if (!TextUtils.isEmpty(mime_type) && mime_type.contains("video") && !TextUtils.isEmpty(_data)) {
                        var compressMode: BaseMediaBitrateConfig? = null
                        val compressModeCheckedId = rg_only_compress_mode!!.checkedRadioButtonId
                        compressMode = if (compressModeCheckedId == R.id.rb_cbr) {
                            val bitrate = et_only_compress_bitrate!!.text.toString()
                            if (checkStrEmpty(bitrate, "请输入压缩额定码率")) {
                                return
                            }
                            CBRMode(166, Integer.valueOf(bitrate))
                        } else if (compressModeCheckedId == R.id.rb_auto) {
                            val crfSize = et_only_compress_crfSize!!.text.toString()
                            if (TextUtils.isEmpty(crfSize)) {
                                AutoVBRMode()
                            } else {
                                AutoVBRMode(Integer.valueOf(crfSize))
                            }
                        } else if (compressModeCheckedId == R.id.rb_vbr) {
                            val maxBitrate = et_only_compress_maxbitrate!!.text.toString()
                            val bitrate = et_only_compress_bitrate!!.text.toString()
                            if (checkStrEmpty(maxBitrate, "请输入压缩最大码率") || checkStrEmpty(bitrate, "请输入压缩额定码率")) {
                                return
                            }
                            VBRMode(Integer.valueOf(maxBitrate), Integer.valueOf(bitrate))
                        } else {
                            AutoVBRMode()
                        }
                        if (spinner_only_compress!!.selectedItem.toString() != "none") {
                            compressMode.velocity = spinner_only_compress!!.selectedItem.toString()
                        }
                        val sRate = et_only_framerate!!.text.toString()
                        val scale = et_only_scale!!.text.toString()
                        var iRate = 0
                        var fScale = 0f
                        if (!TextUtils.isEmpty(sRate)) {
                            iRate = Integer.valueOf(sRate)
                        }
                        if (!TextUtils.isEmpty(scale)) {
                            fScale = java.lang.Float.valueOf(scale)
                        }
                        val buidler = LocalMediaConfig.Buidler()
                        val config = buidler
                                .setVideoPath(_data)
                                .captureThumbnailsTime(1)
                                .doH264Compress(compressMode)
                                .setFramerate(iRate)
                                .setScale(fScale)
                                .build()
                        Thread(Runnable {
                            runOnUiThread { showProgress("", "压缩中...", -1) }
                            val onlyCompressOverBean = LocalMediaCompress(config).startCompress()
                            runOnUiThread { hideProgress() }
                            val intent = Intent(this@MainActivity, SendSmallVideoActivity::class.java)
                            intent.putExtra(MediaRecorderActivity.VIDEO_URI, onlyCompressOverBean.videoPath)
                            intent.putExtra(MediaRecorderActivity.VIDEO_SCREENSHOT, onlyCompressOverBean.picPath)
                            startActivity(intent)
                        }).start()
                    } else {
                        Toast.makeText(this, "选择的不是视频或者地址错误,也可能是这种方式定制神机取不到！", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun checkStrEmpty(str: String, display: String): Boolean {
        if (TextUtils.isEmpty(str)) {
            Toast.makeText(this, display, Toast.LENGTH_SHORT).show()
            return true
        }
        return false
    }

    private fun permissionCheck() {
        if (Build.VERSION.SDK_INT >= 23) {
            var permissionState = true
            for (permission in permissionManifest) {
                if (ContextCompat.checkSelfPermission(this, permission)
                        != PackageManager.PERMISSION_GRANTED) {
                    permissionState = false
                }
            }
            if (!permissionState) {
                ActivityCompat.requestPermissions(this, permissionManifest, PERMISSION_REQUEST_CODE)
            } else {
                setSupportCameraSize()
            }
        } else {
            setSupportCameraSize()
        }
    }

    private fun initSmallVideo() {
        // 设置拍摄视频缓存路径
        val cacheDir = FileUtils.getDiskCacheDir(this)
        JianXiCamera.setVideoCachePath("$cacheDir/video/")
        // 初始化拍摄
        JianXiCamera.initialize(false, null)
    }

    private fun showProgress(title: String, message: String, theme: Int) {
        if (mProgressDialog == null) {
            mProgressDialog = if (theme > 0) ProgressDialog(this, theme) else ProgressDialog(this)
            mProgressDialog!!.setProgressStyle(ProgressDialog.STYLE_SPINNER)
            mProgressDialog!!.requestWindowFeature(Window.FEATURE_NO_TITLE)
            mProgressDialog!!.setCanceledOnTouchOutside(false) // 不能取消
            mProgressDialog!!.setCancelable(false)
            mProgressDialog!!.isIndeterminate = true // 设置进度条是否不明确
        }
        if (!StringUtils.isEmpty(title)) mProgressDialog!!.setTitle(title)
        mProgressDialog!!.setMessage(message)
        mProgressDialog!!.show()
    }

    private fun hideProgress() {
        if (mProgressDialog != null) {
            mProgressDialog!!.dismiss()
        }
    }

    companion object {
        private val permissionManifest = arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    }
}