package com.winter.muplayer.core

import android.util.Log

/**
 * 应用内日志器 — 对 android.util.Log 的轻量封装，统一 TAG 前缀。
 * Release 构建中日志调用可被 R8 编译期消除，零运行时开销。
 */
object AppLogger {

    private const val TAG_PREFIX = "WMPlayer"

    fun d(tag: String, msg: String) = Log.d("$TAG_PREFIX-$tag", msg)
    fun i(tag: String, msg: String) = Log.i("$TAG_PREFIX-$tag", msg)
    fun w(tag: String, msg: String) = Log.w("$TAG_PREFIX-$tag", msg)
    fun e(tag: String, msg: String) = Log.e("$TAG_PREFIX-$tag", msg)
}
