package com.jantursky.streamtracktv.collector

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import com.jantursky.streamtracktv.core.insights.GoalTarget
import com.jantursky.streamtracktv.core.insights.Reminder
import kotlin.math.ceil

/**
 * Draws a short reminder over the current app. The window is an accessibility overlay: it takes no focus,
 * no touches and no keys, and it leaves after [DISMISS_MS]. If the window cannot be added, a toast shows the
 * same text instead.
 */
class ReminderBanner(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private val windows = context.getSystemService(WindowManager::class.java)
    private var current: View? = null
    private val dismiss = Runnable { remove() }

    fun show(text: String) {
        handler.post {
            remove()
            try {
                val banner = bannerView(text)
                windows.addView(banner, layoutParams())
                current = banner
                handler.postDelayed(dismiss, DISMISS_MS)
            } catch (refused: RuntimeException) {
                Toast.makeText(context, text, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun remove() {
        handler.removeCallbacks(dismiss)
        current?.let { runCatching { windows.removeView(it) } }
        current = null
    }

    private fun bannerView(text: String): View =
        TextView(context).apply {
            this.text = text
            textSize = TEXT_SP
            setTextColor(TEXT_COLOR)
            setBackgroundColor(BACKGROUND_COLOR)
            val pad = (PADDING_DP * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }

    private fun layoutParams() =
        WindowManager
            .LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = (TOP_OFFSET_DP * context.resources.displayMetrics.density).toInt()
            }

    private companion object {
        const val DISMISS_MS = 10_000L
        const val TEXT_SP = 20f
        const val PADDING_DP = 16
        const val TOP_OFFSET_DP = 32
        const val TEXT_COLOR = 0xFFFFFFFF.toInt()
        const val BACKGROUND_COLOR = 0xE6000000.toInt()
    }
}

/** The on-screen text of one reminder, in the app's language. [appLabel] names an app limit. */
fun reminderText(context: Context, reminder: Reminder, appLabel: (String) -> String): String =
    when (reminder) {
        Reminder.Bedtime -> {
            context.getString(R.string.reminder_bedtime)
        }

        is Reminder.Limit -> {
            val left = remainingText(context, reminder.limitMs - reminder.usedMs)
            val reached = reminder.threshold >= FULL_PERCENT
            when (val target = reminder.target) {
                GoalTarget.ScreenTime -> {
                    if (reached) {
                        context.getString(
                            R.string.reminder_screen_full,
                        )
                    } else {
                        context.getString(R.string.reminder_screen_near, left)
                    }
                }

                is GoalTarget.App -> {
                    val name = appLabel(target.packageName)
                    if (reached) {
                        context.getString(
                            R.string.reminder_app_full,
                            name,
                        )
                    } else {
                        context.getString(R.string.reminder_app_near, name, left)
                    }
                }
            }
        }
    }

private const val FULL_PERCENT = 100

private fun remainingText(context: Context, remainingMs: Long): String {
    val minutes = ceil(remainingMs.coerceAtLeast(0) / 60_000.0).toLong()
    return if (minutes >= 60) {
        context.getString(R.string.reminder_duration_hours, minutes / 60, minutes % 60)
    } else {
        context.getString(R.string.reminder_duration_minutes, minutes)
    }
}
