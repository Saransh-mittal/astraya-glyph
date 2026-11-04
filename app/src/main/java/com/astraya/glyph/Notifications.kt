package com.astraya.glyph

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * How many notifications are waiting, for the Glyph. Only the COUNT is kept:
 * no titles, text or app names are read or stored.
 */
object Notifications {

    private const val PREFS = "astraya_notifications"

    fun hasAccess(context: Context): Boolean =
        context.getSystemService(NotificationManager::class.java)
            .isNotificationListenerAccessGranted(ComponentName(context, NotificationCounter::class.java))

    fun enabled(context: Context) = prefs(context).getBoolean("enabled", true)

    fun setEnabled(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("enabled", on).apply()
        AstrayaToyService.redraw(context)
    }

    /** What the Glyph should show: 0 when switched off or access is missing. */
    fun count(context: Context): Int =
        if (enabled(context) && hasAccess(context)) prefs(context).getInt("count", 0) else 0

    internal fun store(context: Context, count: Int) {
        val p = prefs(context)
        if (p.getInt("count", -1) == count) return
        p.edit().putInt("count", count).apply()
        AstrayaToyService.redraw(context)
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/**
 * Counts notifications a person would want to know about: not ongoing ones
 * (music, navigation, downloads), not silent ones, not group summaries
 * (they would count a conversation twice), and not our own.
 */
class NotificationCounter : NotificationListenerService() {

    override fun onListenerConnected() = recount()
    override fun onNotificationPosted(sbn: StatusBarNotification?) = recount()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = recount()

    override fun onListenerDisconnected() {
        Notifications.store(this, 0)
    }

    private fun recount() {
        val active = try {
            activeNotifications ?: emptyArray()
        } catch (_: SecurityException) {
            emptyArray()
        }
        val ranking = Ranking()
        val rankings = currentRanking
        val count = active.count { sbn ->
            val n = sbn.notification
            sbn.packageName != packageName &&
                sbn.isClearable &&
                !sbn.isOngoing &&
                n.flags and Notification.FLAG_GROUP_SUMMARY == 0 &&
                (rankings == null || !rankings.getRanking(sbn.key, ranking) ||
                    ranking.importance >= NotificationManager.IMPORTANCE_DEFAULT)
        }
        Notifications.store(this, count)
    }
}
