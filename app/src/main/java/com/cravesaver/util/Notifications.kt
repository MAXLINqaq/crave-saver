package com.cravesaver.util

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.cravesaver.R

/** 识别结果本地通知：渠道、运行时权限、发送 */
object Notifications {

    private const val CHANNEL_ID = "recognize_result"
    private const val PREFS = "app_prefs"
    private const val KEY_PERMISSION_REQUESTED = "notification_permission_requested"

    /** 创建通知渠道（幂等） */
    fun ensureChannel(context: Context) {
        val channel = NotificationChannelCompat
            .Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
            .setName("识别结果")
            .setDescription("截图识别的入账/失败通知")
            .build()
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    /** 是否需要请求通知权限：API 33+、未授权、且之前没请求过（只在主页首次启动问一次） */
    fun shouldRequestPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return !context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_PERMISSION_REQUESTED, false)
    }

    /** 记录"已请求过"，之后不再主动弹权限 */
    fun markPermissionRequested(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_PERMISSION_REQUESTED, true).apply()
    }

    /** 发本地通知（点击打开 App）；用户关了通知权限就静默跳过 */
    fun notify(context: Context, title: String, text: String) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        ensureChannel(context)
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val contentIntent = launchIntent?.let {
            PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        runCatching {
            NotificationManagerCompat.from(context)
                .notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
        }
    }
}
