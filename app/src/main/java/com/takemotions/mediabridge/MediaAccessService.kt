package com.takemotions.mediabridge

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * A deliberately EMPTY notification listener.
 *
 * Android only lets an app enumerate active media sessions
 * (`MediaSessionManager.getActiveSessions`) if it owns an *enabled* notification
 * listener — that is why this class must exist and why the user has to grant
 * "Notification access".
 *
 * This service is the access handle ONLY. It never reads, stores, or transmits any
 * notification content: [onNotificationPosted] / [onNotificationRemoved] are
 * intentional no-ops. Media Bridge does not touch your messages.
 */
class MediaAccessService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        // Intentionally ignored. Media Bridge does not read notifications.
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Intentionally ignored. Media Bridge does not read notifications.
    }
}
