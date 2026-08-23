package com.winter.muplayer.core

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 崩溃日志管理器：捕获全局未捕获异常，将堆栈写入应用专属日志目录。
 *
 * 日志路径：`{外部存储}/Android/data/<package>/files/logs/`
 * （即 [Context.getExternalFilesDir] null + "logs"，Android 11+ 用户可用文件管理器查看，
 * 无需任何存储权限；极端情况下外部目录不可用时回退内部 [Context.filesDir]）
 *
 * 文件名：`crash_yyyyMMdd_HHmmss.log`，每次崩溃一个文件，最多保留 [MAX_LOG_FILES] 个（自动清理最旧的）。
 */
object CrashLogManager {

    private const val TAG = "CrashLog"
    private const val LOG_DIR_NAME = "logs"
    private const val MAX_LOG_FILES = 30

    /** 崩溃日志目录（应用专属目录，无权限要求） */
    fun logDir(context: Context): File {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        return File(base, LOG_DIR_NAME)
    }

    /**
     * 安装全局崩溃处理器。必须在 [android.app.Application.onCreate] 或 Activity 创建早期调用。
     * 崩溃发生时先写入日志文件，再交给原有处理器（系统默认行为：弹"应用已停止"并结束进程）。
     */
    fun install(context: Context) {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeCrashLog(context, thread, throwable)
            } catch (e: Exception) {
                android.util.Log.w(TAG, "Failed to write crash log: ${e.message}")
            }
            prev?.uncaughtException(thread, throwable)
        }
    }

    /**
     * 写入一次崩溃日志，返回日志文件；失败返回 null。
     * 写入前自动清理超出上限的最旧日志。
     */
    fun writeCrashLog(context: Context, thread: Thread, throwable: Throwable): File? {
        val dir = logDir(context)
        if (!dir.exists() && !dir.mkdirs()) return null
        trimOldLogs(dir)

        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir, "crash_$stamp.log")

        val stack = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val content = buildString {
            appendLine("=== WMPlayer Crash Log ===")
            appendLine("time: $time")
            appendLine("thread: ${thread.name}")
            appendLine("process: ${android.os.Process.myPid()}")
            appendLine()
            append(stack)
        }
        file.writeText(content)
        return file
    }

    /**
     * 写入一次业务错误日志（被捕获的非崩溃异常，如配置解析失败），返回日志文件；失败返回 null。
     * 文件名：`error_yyyyMMdd_HHmmss.log`。
     */
    fun writeErrorLog(
        context: Context,
        tag: String,
        message: String,
        throwable: Throwable? = null,
    ): File? {
        val dir = logDir(context)
        if (!dir.exists() && !dir.mkdirs()) return null
        trimOldLogs(dir)

        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir, "error_$stamp.log")
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val content = buildString {
            appendLine("=== WMPlayer Error Log ===")
            appendLine("time: $time")
            appendLine("tag: $tag")
            appendLine("message: $message")
            if (throwable != null) {
                appendLine()
                append(StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString())
            }
        }
        file.writeText(content)
        return file
    }

    /** 最新一份日志文件（定位/查看用），目录无日志时返回 null */
    fun latestLogFile(context: Context): File? =
        listLogFiles(logDir(context)).maxByOrNull { it.lastModified() }

    /** 删除全部日志（crash_*.log 与 error_*.log），返回删除的文件数 */
    fun clearLogs(context: Context): Int {
        val dir = logDir(context)
        if (!dir.isDirectory) return 0
        val files = listLogFiles(dir)
        var removed = 0
        for (f in files) {
            if (f.delete()) removed++
        }
        return removed
    }

    /** 只保留最近 [MAX_LOG_FILES] 个日志文件（按修改时间） */
    private fun trimOldLogs(dir: File) {
        val files = listLogFiles(dir)
        if (files.size <= MAX_LOG_FILES) return
        files.sortedBy { it.lastModified() }
            .take(files.size - MAX_LOG_FILES)
            .forEach { it.delete() }
    }

    private fun listLogFiles(dir: File): List<File> =
        dir.listFiles { f -> f.isFile && f.extension == "log" }
            ?.toList() ?: emptyList()
}
