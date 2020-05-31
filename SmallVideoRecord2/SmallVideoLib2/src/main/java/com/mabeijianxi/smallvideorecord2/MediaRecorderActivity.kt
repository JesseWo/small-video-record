package com.mabeijianxi.smallvideorecord2

import android.app.Activity
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.core.view.updateLayoutParams
import com.mabeijianxi.smallvideorecord2.MediaRecorderBase.OnEncodeListener
import com.mabeijianxi.smallvideorecord2.R.id
import com.mabeijianxi.smallvideorecord2.model.MediaObject
import com.mabeijianxi.smallvideorecord2.model.MediaRecorderConfig
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
        private const val HANDLE_INVALIDATE_PROGRESS = 0

        /**
         * 延迟拍摄停止
         */
        private const val HANDLE_STOP_RECORD = 1

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
         * 最大录制时间的key
         */
        const val MEDIA_RECORDER_MAX_TIME_KEY = "media_recorder_max_time_key"

        /**
         * 最小录制时间的key
         */
        const val MEDIA_RECORDER_MIN_TIME_KEY = "media_recorder_min_time_key"

        /**
         * 录制配置key
         */
        const val MEDIA_RECORDER_CONFIG_KEY = "media_recorder_config_key"

        /**
         * @param context
         * @param overGOActivityName 录制结束后需要跳转的Activity全类名
         */
        @JvmStatic
        fun goSmallVideoRecorder(context: Activity, overGOActivityName: String?, mediaRecorderConfig: MediaRecorderConfig?) {
            context.startActivity(Intent(context, MediaRecorderActivity::class.java).putExtra(OVER_ACTIVITY_NAME, overGOActivityName).putExtra(MEDIA_RECORDER_CONFIG_KEY, mediaRecorderConfig))
        }
    }

    private var RECORD_TIME_MIN = (1.5f * 1000).toInt()

    /**
     * 录制最长时间
     */
    private var RECORD_TIME_MAX = 6 * 1000

    /**
     * SDK视频录制对象
     */
    private var mMediaRecorder: MediaRecorderBase? = null

    /**
     * 视频信息
     */
    private var mMediaObject: MediaObject? = null

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
        RECORD_TIME_MAX = mediaRecorderConfig.recordTimeMax
        RECORD_TIME_MIN = mediaRecorderConfig.recordTimeMin
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
        title_next?.setOnClickListener(this)
        findViewById<View>(id.title_back).setOnClickListener(this)
        /**
         * 回删按钮、延时按钮、滤镜按钮
         */
        record_delete?.setOnClickListener(this)
        /**
         * 拍摄按钮
         */
        record_controller?.setOnTouchListener(mOnVideoControllerTouchListener)

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
        record_progress?.setMaxDuration(RECORD_TIME_MAX)
        record_progress?.setMinTime(RECORD_TIME_MIN)
    }

    /**
     * 初始化画布
     */
    private fun initSurfaceView() {
        if (NEED_FULL_SCREEN) {
            bottom_layout.setBackgroundColor(0)
            title_layout?.setBackgroundColor(resources.getColor(R.color.full_title_color))
            record_preview?.updateLayoutParams<FrameLayout.LayoutParams> {
                setMargins(0, 0, 0, 0)

            }
            record_progress?.setBackgroundColor(resources.getColor(R.color.full_progress_color))
        } else {
            val w = DeviceUtils.getScreenWidth(this)
            (bottom_layout.layoutParams as RelativeLayout.LayoutParams).topMargin = (w / (MediaRecorderBase.SMALL_VIDEO_HEIGHT / (MediaRecorderBase.SMALL_VIDEO_WIDTH * 1.0f))).toInt()
            val height = (w * (MediaRecorderBase.mSupportedPreviewWidth * 1.0f / MediaRecorderBase.SMALL_VIDEO_HEIGHT)).toInt()

            record_preview?.updateLayoutParams<FrameLayout.LayoutParams> {
                this.width = w
                this.height = height
            }
        }
    }

    /**
     * 初始化拍摄SDK
     */
    private fun initMediaRecorder() {
        mMediaRecorder = MediaRecorderNative().apply {
            setOnErrorListener(this@MediaRecorderActivity)
            setOnEncodeListener(this@MediaRecorderActivity)
            setOnPreparedListener(this@MediaRecorderActivity)
            val f = File(JianXiCamera.getVideoCachePath())
            if (!FileUtils.checkFile(f)) {
                f.mkdirs()
            }
            val key = System.currentTimeMillis().toString()
            this@MediaRecorderActivity.mMediaObject = setOutputDirectory(key, JianXiCamera.getVideoCachePath() + key)
            setSurfaceHolder(record_preview?.holder)
        }.also { it.prepare() }
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

    /**
     * 点击屏幕录制
     */
    private val mOnVideoControllerTouchListener = OnTouchListener { v, event ->
        if (mMediaRecorder == null) {
            return@OnTouchListener false
        }
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // 检测是否手动对焦
                // 判断是否已经超时
                if (mMediaObject!!.duration >= RECORD_TIME_MAX) {
                    return@OnTouchListener true
                }

                // 取消回删
                if (cancelDelete()) return@OnTouchListener true
                if (!startState) {
                    startState = true
                    startRecord()
                } else {
                    mMediaObject!!.buildMediaPart(mMediaRecorder!!.mCameraId)
                    record_progress?.setData(mMediaObject)
                    setStartUI()
                    mMediaRecorder!!.recordState = true
                }
            }
            MotionEvent.ACTION_UP -> {
                mMediaRecorder!!.recordState = false
                if (mMediaObject!!.duration >= RECORD_TIME_MAX) {
                    title_next?.performClick()
                } else {
                    mMediaRecorder!!.setStopDate()
                    setStopUI()
                }
            }
        }
        true
    }

    override fun onResume() {
        super.onResume()
        if (mMediaRecorder == null) {
            initMediaRecorder()
        } else {
            record_camera_led?.isChecked = false
            mMediaRecorder!!.prepare()
            record_progress?.setData(mMediaObject)
        }
    }

    /**
     * 开始录制
     */
    private fun startRecord() {
        if (mMediaRecorder != null) {
            val part = mMediaRecorder!!.startRecord() ?: return
            record_progress?.setData(mMediaObject)
        }
        setStartUI()
    }

    private fun setStartUI() {
        mPressedStatus = true
        //		TODO 开始录制的图标
        record_controller.animate().scaleX(0.8f).scaleY(0.8f).setDuration(500).start()
        if (mHandler != null) {
            mHandler.removeMessages(HANDLE_INVALIDATE_PROGRESS)
            mHandler.sendEmptyMessage(HANDLE_INVALIDATE_PROGRESS)
            mHandler.removeMessages(HANDLE_STOP_RECORD)
            mHandler.sendEmptyMessageDelayed(HANDLE_STOP_RECORD,
                    RECORD_TIME_MAX - mMediaObject!!.duration.toLong())
        }
        record_delete?.visibility = View.GONE
        record_camera_switcher?.isEnabled = false
        record_camera_led?.isEnabled = false
    }

    override fun onBackPressed() {
        /*if (mRecordDelete != null && mRecordDelete.isChecked()) {
            cancelDelete();
            return;
        }*/
        if (mMediaObject != null && mMediaObject!!.duration > 1) {
            // 未转码
            AlertDialog.Builder(this)
                    .setTitle(R.string.hint)
                    .setMessage(R.string.record_camera_exit_dialog_message)
                    .setNegativeButton(
                            R.string.record_camera_cancel_dialog_yes
                    ) { dialog, which ->
                        mMediaObject!!.delete()
                        finish()
                    }
                    .setPositiveButton(R.string.record_camera_cancel_dialog_no,
                            null).setCancelable(false).show()
            return
        }
        if (mMediaObject != null) mMediaObject!!.delete()
        finish()
    }

    /**
     * 停止录制
     */
    private fun stopRecord() {
        if (mMediaRecorder != null) {
            mMediaRecorder!!.stopRecord()
        }
        setStopUI()
    }

    private fun setStopUI() {
        mPressedStatus = false
        record_controller.animate().scaleX(1f).scaleY(1f).setDuration(500).start()
        record_delete?.visibility = View.VISIBLE
        record_camera_switcher?.isEnabled = true
        record_camera_led?.isEnabled = true
        mHandler.removeMessages(HANDLE_STOP_RECORD)
        checkStatus()
    }

    override fun onClick(v: View) {
        val id = v.id
        if (mHandler.hasMessages(HANDLE_STOP_RECORD)) {
            mHandler.removeMessages(HANDLE_STOP_RECORD)
        }

        // 处理开启回删后其他点击操作
        if (id != R.id.record_delete) {
            if (mMediaObject != null) {
                val part = mMediaObject!!.currentPart
                if (part != null) {
                    if (part.remove) {
                        part.remove = false
                        record_delete?.isChecked = false
                        record_progress?.invalidate()
                    }
                }
            }
        }
        if (id == R.id.title_back) {
            onBackPressed()
        } else if (id == R.id.record_camera_switcher) { // 前后摄像头切换
            if (record_camera_led.isChecked) {
                if (mMediaRecorder != null) {
                    mMediaRecorder!!.toggleFlashMode()
                }
                record_camera_led?.isChecked = false
            }
            if (mMediaRecorder != null) {
                mMediaRecorder!!.switchCamera()
            }
            record_camera_led?.isEnabled = !mMediaRecorder!!.isFrontCamera
        } else if (id == R.id.record_camera_led) { // 闪光灯
            // 开启前置摄像头以后不支持开启闪光灯
            if (mMediaRecorder != null) {
                if (mMediaRecorder!!.isFrontCamera) {
                    return
                }
            }
            if (mMediaRecorder != null) {
                mMediaRecorder!!.toggleFlashMode()
            }
        } else if (id == R.id.title_next) { // 停止录制
            stopRecord()
            /*finish();
            overridePendingTransition(R.anim.push_bottom_in,
					R.anim.push_bottom_out);*/
        } else if (id == R.id.record_delete) {
            // 取消回删
            if (mMediaObject != null) {
                val part = mMediaObject!!.currentPart
                if (part != null) {
                    if (part.remove) {
                        part.remove = false
                        mMediaObject!!.removePart(part, true)
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
        if (mMediaObject != null) {
            val part = mMediaObject!!.currentPart
            if (part != null && part.remove) {
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
        if (!isFinishing && mMediaObject != null) {
            val duration = mMediaObject!!.duration
            if (duration < RECORD_TIME_MIN) {
                if (duration == 0) {
                    record_camera_switcher?.visibility = View.VISIBLE
                    record_delete?.visibility = View.GONE
                } else {
                    record_camera_switcher?.visibility = View.GONE
                }
                // 视频必须大于3秒
                if (title_next?.visibility != View.INVISIBLE) title_next?.visibility = View.INVISIBLE
            } else {
                // 下一步
                if (title_next?.visibility != View.VISIBLE) {
                    title_next?.visibility = View.VISIBLE
                }
            }
        }
        return 0
    }

    private val mHandler: Handler = object : Handler() {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                HANDLE_INVALIDATE_PROGRESS -> if (mMediaRecorder != null && !isFinishing) {
                    if (mMediaObject != null && mMediaObject!!.medaParts != null && mMediaObject!!.duration >= RECORD_TIME_MAX) {
                        title_next?.performClick()
                        return
                    }
                    record_progress?.invalidate()
                    // if (mPressedStatus)
                    // titleText.setText(String.format("%.1f",
                    // mMediaRecorder.getDuration() / 1000F));
                    if (mPressedStatus) sendEmptyMessageDelayed(0, 30)
                }
            }
        }
    }

    override fun onEncodeStart() {
        showProgress("", getString(R.string.record_camera_progress_message))
    }

    override fun onEncodeProgress(progress: Int) {}

    /**
     * 转码完成
     */
    override fun onEncodeComplete() {
        hideProgress()
        try {
            val intent = Intent(this, Class.forName(intent.getStringExtra(OVER_ACTIVITY_NAME))).apply {
                putExtra(OUTPUT_DIRECTORY, mMediaObject!!.outputDirectory)
                putExtra(VIDEO_URI, mMediaObject!!.outputTempTranscodingVideoPath)
                putExtra(VIDEO_SCREENSHOT, mMediaObject!!.outputVideoThumbPath)
                putExtra("go_home", GO_HOME)
            }
            startActivity(intent)
        } catch (e: ClassNotFoundException) {
            throw IllegalArgumentException("需要传入录制完成后跳转的Activity的全类名")
        }
        finish()
    }

    /**
     * 转码失败 检查sdcard是否可用，检查分块是否存在
     */
    override fun onEncodeError() {
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
        mMediaRecorder?.release()
    }
}