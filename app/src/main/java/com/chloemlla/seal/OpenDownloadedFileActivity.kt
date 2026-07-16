package com.chloemlla.seal

import android.app.Activity
import android.os.Bundle
import com.chloemlla.seal.util.FileUtil
import com.chloemlla.seal.util.NotificationUtil
import com.chloemlla.seal.util.ToastUtil

/**
 * Explicit trampoline for notification "open file" actions.
 *
 * Android 14 rejects PendingIntents created from package-less implicit intents. Notification
 * content intents therefore target this activity (explicit component), which then launches the
 * real ACTION_VIEW intent while the app is in the foreground transition path.
 */
class OpenDownloadedFileActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val notificationId =
            intent.getIntExtra(NotificationActionReceiver.NOTIFICATION_ID_KEY, 0)
        if (notificationId != 0) {
            NotificationUtil.cancelNotification(notificationId)
        }
        val path = intent.getStringExtra(NotificationActionReceiver.FILE_PATH_KEY)
        if (!path.isNullOrBlank()) {
            FileUtil.openFile(path) {
                ToastUtil.makeToastSuspend(getString(R.string.file_unavailable))
            }
        }
        finish()
    }
}
