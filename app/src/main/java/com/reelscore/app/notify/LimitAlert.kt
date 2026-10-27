package com.reelscore.app.notify

import android.content.Context
import com.reelscore.app.settings.AppSettings
import java.time.LocalDate

/**
 * Decides when the daily-limit pop-up shows. The limit counts the whole day's reels, including
 * any watched before the limit was turned on. It fires once per (day, limit): raising the limit
 * later the same day can alert again at the new number.
 */
object LimitAlert {
    /** Returns the limit to announce if this reel just reached it, otherwise null. */
    fun check(context: Context, todayCount: Int): Int? {
        val limit = AppSettings.limit(context)
        if (!limit.on || todayCount < limit.count) return null
        if (!AppSettings.markLimitNotified(context, LocalDate.now().toString(), limit.count)) return null
        return limit.count
    }
}
