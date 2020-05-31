package com.mabeijianxi.smallvideorecord2

import android.app.Activity
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.core.view.updateLayoutParams
import com.mabeijianxi.smallvideorecord2.MediaRecorderBase.OnEncodeListener
import com.mabeijianxi.smallvideorecord2.model.MediaRecorderConfig
import com.mabeijianxi.smallvideorecord2.utils.DeviceUtils
import com.mabeijianxi.smallvideorecord2.utils.FileUtils
import com.mabeijianxi.smallvideorecord2.utils.StringUtils
import com.mabeijianxi.smallvideorecord2.view.RecordButtonView
import kotlinx.android.synthetic.main.activity_media_recorder.*
import java.io.File

/**
 * 视频录制
 */
class MediaRecorderActivity : Activity(), MediaRecorderBase.OnErrorListener, View.OnClickListener, MediaRecorderBase.OnPreparedListener, OnEncodeListener {

    companion object {
        private const val TAG = "MediaRecorderActivity"

        /**
         * 刷新进度条
         */
        private const val HANDLE_INVALIDATE_PROGRESS = 1

        /**
         * 视频地址
         */
        const val VIDEO_URI = "video_uri"

        /**
         * 本次视频保存的文件夹地址
         */
        const val OUTPUT_DIRECTORY = "output_directory"

        /**
         * 视屏截图地址
         */
        const val VIDEO_SCREENSHOT = "video_screenshot"

        /**
         * 录制完成后需要跳转的activity
         */
        const val OVER_ACTIVITY_NAME = "over_activity_name"

        /**
         * 录制配置key
         */
        const val MEDIA_RECORDER_CONFIG_KEY = "media_recorder_config_key"

        /**
         * @param context
         * @param overGOActivityName 录制结束后需要跳转的Activity全类名
         */
        fun goSmallVideoRecorder(context: Activity, overGOActivityName: String?, mediaRecorderConfig: MediaRecorderConfig?) {
            context.startActivity(Intent(context, MediaRecorderActivity::class.java).putExtra(OVER_ACTIVITY_NAME, overGOActivityName).putExtra(MEDIA_RECORDER_CONFIG_KEY, mediaRecorderConfig))
        }
    }

    /**
     * 最小录制时长(默认3s)
     */
    private var recordTimeMin = (3f * 1000).toInt()

    /**
     * 最大录制时长(默认60s)
     */
    private var recordTimeMax = 60 * 1000

    /**
     * SDK视频录制对象
     */
    private val mMediaRecorder: MediaRecorderBase by lazy {
        MediaRecorderNative().apply {
            setOnErrorListener(this@MediaRecorderActivity)
            setOnEncodeListener(this@MediaRecorderActivity)
            setOnPreparedListener(this@MediaRecorderActivity)
            val f = File(JianXiCamera.getVideoCachePath())
            if (!FileUtils.checkFile(f)) {
                f.mkdirs()
            }
            val key = System.currentTimeMillis().toString()
            setOutputDirectory(key, JianXiCamera.getVideoCachePath() + key)
            setSurfaceHolder(record_preview?.holder)
        }
    }

    /**
     * 是否是点击状态
     */
    @Volatile
    private var mPressedStatus = false

    private var GO_HOME = false
    private var startState = false
    private var NEED_FULL_SCREEN = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) // 防止锁屏
        initData()
        loadViews()
    }

    private fun initData() {
        val intent = intent
        val mediaRecorderConfig: MediaRecorderConfig = intent.getParcelableExtra(MEDIA_RECORDER_CONFIG_KEY)
                ?: return
        NEED_FULL_SCREEN = mediaRecorderConfig.fullScreen
        recordTimeMax = mediaRecorderConfig.recordTimeMax
        recordTimeMin = mediaRecorderConfig.recordTimeMin
        MediaRecorderBase.MAX_FRAME_RATE = mediaRecorderConfig.maxFrameRate
        MediaRecorderBase.NEED_FULL_SCREEN = NEED_FULL_SCREEN
        MediaRecorderBase.MIN_FRAME_RATE = mediaRecorderConfig.minFrameRate
        MediaRecorderBase.SMALL_VIDEO_HEIGHT = mediaRecorderConfig.smallVideoHeight
        MediaRecorderBase.SMALL_VIDEO_WIDTH = mediaRecorderConfig.smallVideoWidth
        MediaRecorderBase.mVideoBitrate = mediaRecorderConfig.videoBitrate
        MediaRecorderBase.CAPTURE_THUMBNAILS_TIME = mediaRecorderConfig.captureThumbnailsTime
        GO_HOME = mediaRecorderConfig.isGO_HOME
        Log.d(TAG, "config: ${MediaRecorderBase.SMALL_VIDEO_HEIGHT}*${MediaRecorderBase.SMALL_VIDEO_WIDTH}")
    }

    /**
     * 加载视图
     */
    private fun loadViews() {
        setContentView(R.layout.activity_media_recorder)
        // ~~~ 绑定事件
        /*if (DeviceUtils.hasICS())
            mSurfaceView.setOnTouchListener(mOnSurfaveViewTouchListener);*/
        iv_next?.setOnClickListener(this)
        title_back.setOnClickListener(this)
        /**
         * 回删按钮、延时按钮、滤镜按钮
         */
        record_delete?.setOnClickListener(this)
        /**
         * 拍摄按钮
         */
        record_controller.setOnGestureListener(object : RecordButtonView.OnGestureListener {
            override fun onClick() {
            }

            override fun onDown() {
                // 检测是否手动对焦
                // 判断是否已经超时
                mMediaRecorder.mMediaObject?.let {
                    if (it.duration >= recordTimeMax) {
                        return
                    }

                    // 取消回删
                    if (cancelDelete()) return
                    if (!startState) {
                        startState = true
                        startRecord()
                    } else {
                        it.buildMediaPart(mMediaRecorder.mCameraId)
                        record_progress?.setData(it)
                        setStartUI()
                        mMediaRecorder.recordState = true
                    }
                }
            }

            override fun onUp() {
                mMediaRecorder.mMediaObject?.let {
                    mMediaRecorder.recordState = false
                    if (it.duration >= recordTimeMax) {
                        iv_next?.performClick()
                    } else {
                        mMediaRecorder.setStopDate()
                        setStopUI()
                    }
                }
            }

        })

        // ~~~ 设置数据

        // 是否支持前置摄像头
        if (MediaRecorderBase.isSupportFrontCamera()) {
            record_camera_switcher?.setOnClickListener(this)
        } else {
            record_camera_switcher?.visibility = View.GONE
        }
        // 是否支持闪光灯
        if (DeviceUtils.isSupportCameraLedFlash(packageManager)) {
            record_camera_led?.setOnClickListener(this)
        } else {
            record_camera_led?.visibility = View.GONE
        }
        record_progress?.setMaxDuration(recordTimeMax)
        record_progress?.setMinTime(recordTimeMin)
    }

    /**
     * 初始化画布
     */
    private fun initSurfaceView() {
        if (NEED_FULL_SCREEN) {
            bottom_layout.setBackgroundColor(0)
            title_layout?.setBackgroundColor(resources.getColor(R.color.full_title_color))
            record_progress?.setBackgroundColor(resources.getColor(R.color.full_progress_color))
            //根据 camera previewSize 重置预览框尺寸
            val w = DeviceUtils.getScreenWidth(this)
            val height = (w * (MediaRecorderBase.mSupportedPreviewWidth * 1.0f / MediaRecorderBase.SMALL_VIDEO_HEIGHT)).toInt()
            record_preview?.updateLayoutParams<RelativeLayout.LayoutParams> {
                this.width = w
                this.height = height
            }
        } else {
            val w = DeviceUtils.getScreenWidth(this)
            (bottom_layout.layoutParams as RelativeLayout.LayoutParams).topMargin = (w / (MediaRecorderBase.SMALL_VIDEO_HEIGHT / (MediaRecorderBase.SMALL_VIDEO_WIDTH * 1.0f))).toInt()
            val height = (w * (MediaRecorderBase.mSupportedPreviewWidth * 1.0f / MediaRecorderBase.SMALL_VIDEO_HEIGHT)).toInt()

            record_preview?.updateLayoutParams<RelativeLayout.LayoutParams> {
                this.width = w
                this.height = height
            }
        }
    }

    /*@Override
    public void onPause() {
        super.onPause();
        stopRecord();
        if (!mReleased) {
            if (mMediaRecorder != null)
                mMediaRecorder.release();
        }
        mReleased = false;
    }*/

    override fun onResume() {
        super.onResume()
        record_camera_led?.isChecked = false
        mMediaRecorder.prepare()
        record_progress?.setData(mMediaRecorder.mMediaObject)
    }

    /**
     * 开始录制
     */
    private fun startRecord() {
        val part = mMediaRecorder.startRecord() ?: return
        record_progress?.setData(mMediaRecorder.mMediaObject)
        setStartUI()
    }

    private fun setStartUI() {
        mPressedStatus = true
        mHandler.removeMessages(HANDLE_INVALIDATE_PROGRESS)
        mHandler.sendEmptyMessage(HANDLE_INVALIDATE_PROGRESS)

        record_delete?.visibility = View.GONE
        record_camera_switcher?.isEnabled = false
    }

    override fun onBackPressed() {
        /*if (mRecordDelete != null && mRecordDelete.isChecked()) {
            cancelDelete();
            return;
        }*/
        mMediaRecorder.mMediaObject?.let {
            if (it.duration > 1) {
                // 未转码
                AlertDialog.Builder(this)
                        .setTitle(R.string.hint)
                        .setMessage(R.string.record_camera_exit_dialog_message)
                        .setNegativeButton(
                                R.string.record_camera_cancel_dialog_yes
                        ) { dialog, which ->
                            it.delete()
                            finish()
                        }
                        .setPositiveButton(R.string.record_camera_cancel_dialog_no,
                                null).setCancelable(false).show()
                return
            }
            it.delete()
        }
        finish()
    }

    /**
     * 停止录制
     */
    private fun stopRecord() {
        mMediaRecorder.stopRecord()
        setStopUI()
    }

    private fun setStopUI() {
        mPressedStatus = false
        record_delete?.visibility = View.VISIBLE
        record_camera_switcher?.isEnabled = true
        record_controller?.initState()
        checkStatus()
    }

    override fun onClick(v: View) {
        val id = v.id
        // 处理开启回删后其他点击操作
        if (id != R.id.record_delete) {
            mMediaRecorder.mMediaObject?.currentPart?.let { part ->
                if (part.remove) {
                    part.remove = false
                    record_delete?.isChecked = false
                    record_progress?.invalidate()
                }
            }
        }
        if (id == R.id.title_back) {
            onBackPressed()
        } else if (id == R.id.record_camera_switcher) { // 前后摄像头切换
            if (record_camera_led.isChecked) {
                mMediaRecorder.toggleFlashMode()
                record_camera_led?.isChecked = false
            }
            mMediaRecorder.switchCamera()
            record_camera_led?.isEnabled = !mMediaRecorder.isFrontCamera
        } else if (id == R.id.record_camera_led) { // 闪光灯
            // 开启前置摄像头以后不支持开启闪光灯
            if (mMediaRecorder.isFrontCamera) {
                return
            }
            mMediaRecorder.toggleFlashMode()
        } else if (id == R.id.iv_next) { // 停止录制
            stopRecord()
            /*finish();
            overridePendingTransition(R.anim.push_bottom_in,
					R.anim.push_bottom_out);*/
        } else if (id == R.id.record_delete) {
            // 取消回删
            mMediaRecorder.mMediaObject?.let {
                val part = it.currentPart
                if (part != null) {
                    if (part.remove) {
                        part.remove = false
                        it.removePart(part, true)
                        record_delete?.isChecked = false
                    } else {
                        part.remove = true
                        record_delete?.isChecked = true
                    }
                }
                record_progress?.invalidate()

                // 检测按钮状态
                checkStatus()
            }
        }
    }

    /**
     * 取消回删
     */
    private fun cancelDelete(): Boolean {
        mMediaRecorder.mMediaObject?.currentPart?.let { part ->
            if (part.remove) {
                part.remove = false
                record_delete?.isChecked = false
                record_progress?.invalidate()
                return true
            }
        }
        return false
    }

    /**
     * 检查录制时间，显示/隐藏下一步按钮
     */
    private fun checkStatus(): Int {
        mMediaRecorder.mMediaObject?.duration?.let { duration ->
            if (!isFinishing) {
                if (duration < recordTimeMin) {
                    if (duration == 0) {
                        record_camera_switcher?.visibility = View.VISIBLE
                        record_delete?.visibility = View.GONE
                    } else {
                        record_camera_switcher?.visibility = View.GONE
                    }
                    // 视频必须大于3秒
                    if (iv_next?.visibility != View.INVISIBLE) iv_next?.visibility = View.INVISIBLE
                } else {
                    // 下一步
                    if (iv_next?.visibility != View.VISIBLE) {
                        iv_next?.visibility = View.VISIBLE
                    }
                }
            }
        }
        return 0
    }

    private val mHandler: Handler = object : Handler() {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                HANDLE_INVALIDATE_PROGRESS -> if (!isFinishing) {
                    mMediaRecorder.mMediaObject?.let {
                        if (it.medaParts != null && it.duration >= recordTimeMax) {
                            iv_next?.performClick()
                            return
                        }
                    }
                    record_progress?.invalidate()
                    // if (mPressedStatus)
                    // titleText.setText(String.format("%.1f",
                    // mMediaRecorder.getDuration() / 1000F));
                    if (mPressedStatus) sendEmptyMessageDelayed(HANDLE_INVALIDATE_PROGRESS, 30)
                }
            }
        }
    }

    override fun onEncodeStart() {
        Log.d(TAG, "onEncodeStart")
        showProgress("", getString(R.string.record_camera_progress_message))
    }

    override fun onEncodeProgress(progress: Int) {
        Log.d(TAG, "onEncodeProgress $progress")
    }

    /**
     * 转码完成
     */
    override fun onEncodeComplete() {
        Log.d(TAG, "onEncodeComplete")
        hideProgress()
        try {
            mMediaRecorder.mMediaObject?.let {
                val intent = Intent(this, Class.forName(intent.getStringExtra(OVER_ACTIVITY_NAME))).apply {
                    putExtra(OUTPUT_DIRECTORY, it.outputDirectory)
                    putExtra(VIDEO_URI, it.outputTempTranscodingVideoPath)
                    putExtra(VIDEO_SCREENSHOT, it.outputVideoThumbPath)
                    putExtra("go_home", GO_HOME)
                }
                startActivity(intent)
            }
        } catch (e: ClassNotFoundException) {
            throw IllegalArgumentException("需要传入录制完成后跳转的Activity的全类名")
        }
        finish()
    }

    /**
     * 转码失败 检查sdcard是否可用，检查分块是否存在
     */
    override fun onEncodeError() {
        Log.d(TAG, "onEncodeError")
        hideProgress()
        Toast.makeText(this, R.string.record_video_transcoding_faild,
                Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onVideoError(what: Int, extra: Int) {}
    override fun onAudioError(what: Int, message: String) {}

    override fun onPrepared() {
        initSurfaceView()
    }

    private var mProgressDialog: ProgressDialog? = null

    @JvmOverloads
    fun showProgress(title: String?, message: String?, theme: Int = -1): ProgressDialog {
        if (mProgressDialog == null) {
            mProgressDialog = (if (theme > 0) ProgressDialog(this, theme) else ProgressDialog(this)).apply {
                setProgressStyle(ProgressDialog.STYLE_SPINNER)
                requestWindowFeature(Window.FEATURE_NO_TITLE)
                setCanceledOnTouchOutside(false) // 不能取消
                setCancelable(false)
                isIndeterminate = true // 设置进度条是否不明确
            }
        }
        if (!StringUtils.isEmpty(title)) mProgressDialog!!.setTitle(title)
        mProgressDialog!!.setMessage(message)
        mProgressDialog!!.show()
        return mProgressDialog!!
    }

    private fun hideProgress() {
        mProgressDialog?.dismiss()
    }

    override fun onStop() {
        super.onStop()
        if (mMediaRecorder is MediaRecorderNative) {
            (mMediaRecorder as MediaRecorderNative).activityStop()
        }
        hideProgress()
        mProgressDialog = null
    }

    override fun onDestroy() {
        super.onDestroy()
        mMediaRecorder.release()
    }
}