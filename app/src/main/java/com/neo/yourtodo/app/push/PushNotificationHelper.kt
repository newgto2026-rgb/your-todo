package com.neo.yourtodo.app.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.neo.yourtodo.R
import com.neo.yourtodo.app.MainActivity

object PushNotificationHelper {
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            PushNotificationContract.CHANNEL_ID,
            context.getString(R.string.push_notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.push_notification_channel_description)
        }
        manager.createNotificationChannel(channel)
    }

    fun show(
        context: Context,
        title: String,
        body: String,
        data: Map<String, String>
    ) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        ensureChannel(context)
        val requestCode = data[PushNotificationContract.EXTRA_NOTIFICATION_EVENT_ID]
            ?.hashCode()
            ?: System.currentTimeMillis().toInt()
        val notification = NotificationCompat.Builder(context, PushNotificationContract.CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .apply {
                if (shouldOpenPushNotificationInApp(data)) {
                    setContentIntent(contentIntent(context, requestCode, data))
                }
            }
            .build()

        NotificationManagerCompat.from(context).notify(requestCode, notification)
    }

    private fun contentIntent(
        context: Context,
        requestCode: Int,
        data: Map<String, String>
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = PushNotificationContract.ACTION_OPEN_PUSH_NOTIFICATION
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            data[PushNotificationContract.EXTRA_DEEP_LINK]
                ?.takeIf { it.isNotBlank() }
                ?.let { deepLink -> setData(deepLink.toUri()) }
            data.forEach { (key, value) -> putExtra(key, value) }
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
