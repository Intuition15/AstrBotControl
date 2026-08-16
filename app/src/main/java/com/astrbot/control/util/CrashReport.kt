package com.astrbot.control.util

import android.content.Context
import com.astrbot.control.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 全局崩溃捕获：把崩溃堆栈写入本地文件，供下次启动时展示，便于定位问题 */
object CrashReport {

    private const val FILE_NAME = "crash_log.txt"

    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                write(context, thread, throwable)
            } catch (_: Throwable) {
            }
            // 继续默认行为（结束进程）
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun write(context: Context, thread: Thread, throwable: Throwable) {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val sb = StringBuilder()
        sb.appendLine("=== AstrBot 控制台 崩溃日志 ===")
        sb.appendLine("时间: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
        sb.appendLine("版本: v${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE})")
        sb.appendLine("设备: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
        sb.appendLine("系统: Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
        sb.appendLine("线程: ${thread.name}")
        sb.appendLine()
        sb.appendLine(sw.toString())
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        val f = File(dir, FILE_NAME)
        f.writeText(sb.toString())
    }

    fun read(context: Context): String? {
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        val f = File(dir, FILE_NAME)
        return if (f.exists() && f.length() > 0) f.readText() else null
    }

    fun clear(context: Context) {
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        File(dir, FILE_NAME).delete()
    }
}
