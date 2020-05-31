package com.mabeijianxi.smallvideorecord2

import android.app.Application
import android.content.Context

/**
 * Created by wangzhx on 2017/08/14.
 */

object VideoAppLike {

    private const val TAG = "AppCommon"

    private lateinit var mApplication: Application

    val app: Context
        get() = mApplication

    fun init(app: Application) {
        mApplication = app
    }

}
