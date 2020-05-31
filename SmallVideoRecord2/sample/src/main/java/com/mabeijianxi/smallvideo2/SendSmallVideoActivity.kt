package com.mabeijianxi.smallvideo2

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.mabeijianxi.smallvideorecord2.MediaRecorderActivity
import kotlinx.android.synthetic.main.smallvideo_text_edit_activity.*
import java.io.File

/**
 * Created by jian on 2016/7/21 15:52
 * mabeijianxi@gmail.com
 */
class SendSmallVideoActivity : AppCompatActivity(), View.OnClickListener {

    private var videoUri: String? = null
    private var videoScreenshot: String? = null
    private var dialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initView()
        initData()
        initEvent()
    }

    private fun initEvent() {
        tv_cancel?.setOnClickListener(this)
        tv_send?.setOnClickListener(this)
        et_send_content?.setOnClickListener(this)
        iv_video_screenshot?.setOnClickListener(this)
    }

    private fun initData() {
        val intent = intent
        videoUri = intent.getStringExtra(MediaRecorderActivity.VIDEO_URI)
        videoScreenshot = intent.getStringExtra(MediaRecorderActivity.VIDEO_SCREENSHOT)
        val bitmap = BitmapFactory.decodeFile(videoScreenshot)
        iv_video_screenshot!!.setImageBitmap(bitmap)
        val file = File(videoUri)
        et_send_content?.hint = """
            Video Uri:
            $videoUri
            Size: ${file.length() / 1024f / 1024f}MB
        """.trimIndent()
    }

    private fun initView() {
        setContentView(R.layout.smallvideo_text_edit_activity)
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.tv_cancel -> hesitate()
            R.id.tv_send -> {
            }
            R.id.iv_video_screenshot -> startActivity(Intent(this, VideoPlayerActivity::class.java).putExtra(
                    "path", videoUri))
        }
    }

    override fun onBackPressed() {
        hesitate()
    }

    private fun hesitate() {
        if (dialog == null) {
            dialog = AlertDialog.Builder(this)
                    .setTitle(R.string.hint)
                    .setMessage(R.string.record_camera_exit_dialog_message)
                    .setNegativeButton(
                            R.string.record_camera_cancel_dialog_yes
                    ) { dialog, which ->
                        finish()

//                                    FileUtils.deleteDir(getIntent().getStringExtra(MediaRecorderActivity.OUTPUT_DIRECTORY));
                    }
                    .setPositiveButton(R.string.record_camera_cancel_dialog_no,
                            null).setCancelable(false).show()
        } else {
            dialog!!.show()
        }
    }
}