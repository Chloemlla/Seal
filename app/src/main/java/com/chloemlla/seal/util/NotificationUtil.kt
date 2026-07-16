package com.chloemlla.seal.util

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE
import androidx.core.graphics.drawable.IconCompat
import com.chloemlla.seal.App.Companion.context
import com.chloemlla.seal.NotificationActionReceiver
import com.chloemlla.seal.NotificationActionReceiver.Companion.ACTION_CANCEL_TASK
import com.chloemlla.seal.NotificationActionReceiver.Companion.ACTION_ERROR_REPORT
import com.chloemlla.seal.NotificationActionReceiver.Companion.ACTION_KEY
import com.chloemlla.seal.NotificationActionReceiver.Companion.ERROR_REPORT_KEY
import com.chloemlla.seal.NotificationActionReceiver.Companion.FILE_PATH_KEY
import com.chloemlla.seal.NotificationActionReceiver.Companion.NOTIFICATION_ID_KEY
import com.chloemlla.seal.NotificationActionReceiver.Companion.TASK_ID_KEY
import com.chloemlla.seal.OpenDownloadedFileActivity
import com.chloemlla.seal.R
import com.chloemlla.seal.util.PreferenceUtil.getBoolean

private const val TAG = "NotificationUtil"

@SuppressLint("StaticFieldLeak")
object NotificationUtil {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private const val PROGRESS_MAX = 100
    private const val PROGRESS_INITIAL = 0
    private const val CHANNEL_ID = "download_notification"
    private const val SERVICE_CHANNEL_ID = "download_service"
    private const val NOTIFICATION_GROUP_ID = "seal.download.notification"
    private const val DEFAULT_NOTIFICATION_ID = 100
    const val SERVICE_NOTIFICATION_ID = 123
    private lateinit var serviceNotification: Notification

    //    private var builder =
    //        NotificationCompat.Builder(context, CHANNEL_ID).setSmallIcon(R.drawable.ic_stat_seal)
    private val commandNotificationBuilder =
        NotificationCompat.Builder(context, CHANNEL_ID).setSmallIcon(R.drawable.ic_stat_seal)

    @RequiresApi(Build.VERSION_CODES.O)
    fun createNotificationChannel() {
        val name = context.getString(R.string.channel_name)
        val descriptionText = context.getString(R.string.channel_description)
        val importance = NotificationManager.IMPORTANCE_LOW
        val channelGroup =
            NotificationChannelGroup(NOTIFICATION_GROUP_ID, context.getString(R.string.download))
        val channel =
            NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                group = NOTIFICATION_GROUP_ID
            }
        val serviceChannel =
            NotificationChannel(SERVICE_CHANNEL_ID, name, importance).apply {
                description = context.getString(R.string.service_title)
                group = NOTIFICATION_GROUP_ID
            }
        notificationManager.createNotificationChannelGroup(channelGroup)
        notificationManager.createNotificationChannel(channel)
        notificationManager.createNotificationChannel(serviceChannel)
    }

    fun notifyProgress(
        title: String,
        notificationId: Int = DEFAULT_NOTIFICATION_ID,
        progress: Int = PROGRESS_INITIAL,
        taskId: String? = null,
        text: String? = null,
    ) {
        if (!NOTIFICATION.getBoolean()) return
        val pendingIntent =
            taskId?.let {
                Intent(context.applicationContext, NotificationActionReceiver::class.java)
                    .putExtra(TASK_ID_KEY, taskId)
                    .putExtra(NOTIFICATION_ID_KEY, notificationId)
                    .putExtra(ACTION_KEY, ACTION_CANCEL_TASK)
                    .run {
                        PendingIntent.getBroadcast(
                            context.applicationContext,
                            notificationId,
                            this,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                        )
                    }
            }

        val clamped = progress.coerceIn(0, PROGRESS_MAX)
        val indeterminate = progress <= 0
        val builder =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_seal)
                .setContentTitle(title)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setProgress(PROGRESS_MAX, if (indeterminate) 0 else clamped, indeterminate)
        if (!text.isNullOrBlank()) {
            builder.setContentText(text)
            // Keep BigText only when ProgressStyle is unavailable; ProgressStyle replaces Style.
            if (Build.VERSION.SDK_INT < SdkLevels.BAKLAVA) {
                builder.setStyle(NotificationCompat.BigTextStyle().bigText(text))
            }
        }
        applyProgressStyle(
            builder = builder,
            progress = clamped,
            indeterminate = indeterminate,
        )
        pendingIntent?.let {
            builder.addAction(R.drawable.outline_cancel_24, context.getString(R.string.cancel), it)
        }
        notificationManager.notify(notificationId, builder.build())
    }

    fun finishNotification(
        notificationId: Int = DEFAULT_NOTIFICATION_ID,
        title: String? = null,
        text: String? = null,
        intent: PendingIntent? = null,
    ) {
        Log.d(TAG, "finishNotification: ")
        notificationManager.cancel(notificationId)
        if (!NOTIFICATION.getBoolean()) return

        val builder =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_seal)
                .setContentText(text)
                .setOngoing(false)
                .setAutoCancel(true)
        title?.let { builder.setContentTitle(title) }
        intent?.let { builder.setContentIntent(intent) }
        notificationManager.notify(notificationId, builder.build())
    }

    /**
     * Build an explicit PendingIntent that opens a finished download path.
     *
     * Android 14+ forbids creating PendingIntents from intents without a package/component.
     * Route through [com.chloemlla.seal.OpenDownloadedFileActivity] instead of a raw ACTION_VIEW
     * intent so the PendingIntent stays package/component-explicit.
     */
    fun createOpenFilePendingIntent(
        notificationId: Int,
        path: String?,
    ): PendingIntent? {
        if (path.isNullOrBlank()) return null
        val intent =
            Intent(context.applicationContext, OpenDownloadedFileActivity::class.java)
                .putExtra(NOTIFICATION_ID_KEY, notificationId)
                .putExtra(FILE_PATH_KEY, path)
        return PendingIntent.getActivity(
            context.applicationContext,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun finishNotificationForCustomCommands(
        notificationId: Int = DEFAULT_NOTIFICATION_ID,
        title: String? = null,
        text: String? = null,
    ) {
        //        notificationManager.cancel(notificationId)
        val builder =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_seal)
                .setContentText(text)
                .setProgress(0, 0, false)
                .setAutoCancel(true)
                .setOngoing(false)
                .setStyle(null)
        title?.let { builder.setContentTitle(title) }

        notificationManager.notify(notificationId, builder.build())
    }

    fun makeServiceNotification(intent: PendingIntent, text: String? = null): Notification {
        serviceNotification =
            NotificationCompat.Builder(context, SERVICE_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_seal)
                .setContentTitle(context.getString(R.string.service_title))
                .setContentText(text)
                .setOngoing(true)
                .setContentIntent(intent)
                .setForegroundServiceBehavior(FOREGROUND_SERVICE_IMMEDIATE)
                .build()
        return serviceNotification
    }

    fun updateServiceNotificationForPlaylist(index: Int, itemCount: Int) {
        serviceNotification =
            NotificationCompat.Builder(context, serviceNotification)
                .setContentTitle(context.getString(R.string.service_title) + " ($index/$itemCount)")
                .build()
        notificationManager.notify(SERVICE_NOTIFICATION_ID, serviceNotification)
    }

    fun cancelNotification(notificationId: Int) {
        notificationManager.cancel(notificationId)
    }

    fun notifyError(
        title: String,
        textId: Int = R.string.download_error_msg,
        notificationId: Int,
        report: String,
    ) {
        if (!NOTIFICATION.getBoolean()) return

        val intent =
            Intent()
                .setClass(context, NotificationActionReceiver::class.java)
                .putExtra(NOTIFICATION_ID_KEY, notificationId)
                .putExtra(ERROR_REPORT_KEY, report)
                .putExtra(ACTION_KEY, ACTION_ERROR_REPORT)

        val pendingIntent =
            PendingIntent.getBroadcast(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_ONE_SHOT or
                    PendingIntent.FLAG_IMMUTABLE or
                    PendingIntent.FLAG_UPDATE_CURRENT,
            )
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_seal)
            .setContentTitle(title)
            .setContentText(context.getString(textId))
            .setOngoing(false)
            .addAction(
                R.drawable.outline_content_copy_24,
                context.getString(R.string.copy_error_report),
                pendingIntent,
            )
            .run {
                notificationManager.cancel(notificationId)
                notificationManager.notify(notificationId, build())
            }
    }

    fun makeNotificationForCustomCommand(
        notificationId: Int,
        taskId: String,
        progress: Int,
        text: String? = null,
        templateName: String,
        taskUrl: String,
    ) {
        if (!NOTIFICATION.getBoolean()) return

        val intent =
            Intent(context.applicationContext, NotificationActionReceiver::class.java)
                .putExtra(TASK_ID_KEY, taskId)
                .putExtra(NOTIFICATION_ID_KEY, notificationId)
                .putExtra(ACTION_KEY, ACTION_CANCEL_TASK)

        val pendingIntent =
            PendingIntent.getBroadcast(
                context.applicationContext,
                notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val indeterminate = progress < 0
        val clamped = if (indeterminate) 0 else progress.coerceIn(0, PROGRESS_MAX)
        val title =
            "[${templateName}_${taskUrl}] " +
                context.getString(R.string.execute_command_notification)
        val builder =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_seal)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setProgress(PROGRESS_MAX, clamped, indeterminate)
                .addAction(
                    R.drawable.outline_cancel_24,
                    context.getString(R.string.cancel),
                    pendingIntent,
                )
        if (!text.isNullOrBlank()) {
            if (Build.VERSION.SDK_INT < SdkLevels.BAKLAVA) {
                builder.setStyle(NotificationCompat.BigTextStyle().bigText(text))
            }
        }
        applyProgressStyle(
            builder = builder,
            progress = clamped,
            indeterminate = indeterminate,
        )
        notificationManager.notify(notificationId, builder.build())
    }

    fun cancelAllNotifications() {
        notificationManager.cancelAll()
    }

    fun areNotificationsEnabled(): Boolean {
        return if (Build.VERSION.SDK_INT <= 24) true
        else notificationManager.areNotificationsEnabled()
    }

    /**
     * Attach Android 16+ progress-centric style when available.
     *
     * [NotificationCompat.ProgressStyle] is rendered on API 36+ and falls back to the default
     * notification style on older platforms; classic [NotificationCompat.Builder.setProgress]
     * remains as the cross-version progress indicator.
     */
    private fun applyProgressStyle(
        builder: NotificationCompat.Builder,
        progress: Int,
        indeterminate: Boolean,
    ) {
        // ProgressStyle is only meaningful on Android 16 / API 36+.
        if (Build.VERSION.SDK_INT < SdkLevels.BAKLAVA) return

        val style =
            NotificationCompat.ProgressStyle()
                .setStyledByProgress(true)
                .setProgressIndeterminate(indeterminate)
                .addProgressSegment(
                    NotificationCompat.ProgressStyle.Segment(PROGRESS_MAX)
                )
                .setProgress(if (indeterminate) 0 else progress)
                .setProgressTrackerIcon(
                    IconCompat.createWithResource(context, R.drawable.ic_stat_seal)
                )
        builder.setStyle(style)
    }
}
