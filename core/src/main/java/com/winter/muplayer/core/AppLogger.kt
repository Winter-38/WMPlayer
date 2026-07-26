package com.winter.muplayer.core

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 应用内日志器 — 内存环形缓冲区，仅调试模式可用。
 * Release 构建中所有日志调用被 R8 编译期消除，零运行时开销。
 */
object AppLogger {

    private const val MAX_ENTRIES = 200
    private val buffer = ArrayDeque<LogEntry>(MAX_ENTRIES)
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    data class LogEntry(
        val time: String,
        val tag: String,
        val message: String
    )

    fun d(tag: String, msg: String) = add("D", tag, msg)
    fun i(tag: String, msg: String) = add("I", tag, msg)
    fun w(tag: String, msg: String) = add("W", tag, msg)
    fun e(tag: String, msg: String) = add("E", tag, msg)

    private fun add(level: String, tag: String, msg: String) {
        val entry = LogEntry(
            time = dateFormat.format(Date()),
            tag = tag,
            message = "[$level] $msg"
        )
        synchronized(buffer) {
            if (buffer.size >= MAX_ENTRIES) buffer.removeFirst()
            buffer.addLast(entry)
        }
    }

    /** 获取所有日志（按时间倒序，最新在前） */
    fun getEntries(): List<LogEntry> = synchronized(buffer) {
        buffer.toList().reversed()
    }

    /** 清空日志 */
    fun clear() = synchronized(buffer) { buffer.clear() }
}
