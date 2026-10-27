package com.securenet.vpn.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.securenet.vpn.MainActivity
import com.securenet.vpn.R
import com.securenet.vpn.guide.GuideSignalsData
import com.securenet.vpn.guide.GuideSmart
import com.securenet.vpn.guide.GuideStoreProvider
import com.securenet.vpn.guide.GuideWidgetModel

/**
 * v1.8.11 — home-screen "Smart picks" widget: the guide's recommendation
 * engine, glanceable without opening the app.
 *
 * Up to three rows (source name + the same reason chip the picks card
 * uses — "matches your most-engaged category" / "Editor's pick you haven't
 * rated yet" / "trusted starting point" / "similar to what you're
 * viewing"), each tapping straight into that source's detail page via
 * [MainActivity] and its EXTRA_OPEN_GUIDE_DETAIL contract; the title opens
 * the app itself. When nothing scores (every source bookmarked or already
 * engaged) the rows swap for a "rate sources" hint — the widget is never
 * blank, and it never invents picks.
 *
 * Refresh paths: the system's [onUpdate] + periodic schedule (30 min
 * floor), and [pushUpdate] — called from [MainActivity.onResume] so any
 * signal change (stars, favorites, notes, backup restore) repaints every
 * placed widget the moment the user returns to the app. Rendering is a
 * thin shell over the pure [GuideWidgetModel]; the content decision runs
 * on the JVM and is unit-tested there.
 */
class GuideSmartWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        render(context, appWidgetManager, appWidgetIds)
    }

    companion object {

        /** Request codes must differ per tap target inside one provider. */
        private const val REQ_TITLE = 200
        private const val REQ_ROW_BASE = 201

        /** Called after any user-signal change so widgets stay honest. */
        fun pushUpdate(context: Context) {
            runCatching {
                val manager = AppWidgetManager.getInstance(context) ?: return
                val ids = manager.getAppWidgetIds(
                    ComponentName(context, GuideSmartWidgetProvider::class.java)
                )
                if (ids.isNotEmpty()) render(context, manager, ids)
            }
        }

        private fun render(
            context: Context,
            manager: AppWidgetManager,
            ids: IntArray
        ) {
            val content = GuideWidgetModel.build(
                GuideSignalsData.snapshot(GuideStoreProvider.get(context))
            )
            for (id in ids) {
                manager.updateAppWidget(id, viewsFor(context, content))
            }
        }

        private fun viewsFor(
            context: Context,
            content: GuideWidgetModel.Content
        ): RemoteViews = RemoteViews(
            context.packageName, R.layout.widget_smart_picks
        ).apply {
            setTextViewText(
                R.id.widgetSmartTitle,
                "✦ " + context.getString(R.string.guide_smart_title)
            )
            setOnClickPendingIntent(
                R.id.widgetSmartTitle,
                activityPendingIntent(context, REQ_TITLE) { Intent(it, MainActivity::class.java) }
            )

            val names = intArrayOf(
                R.id.widgetPick1Name, R.id.widgetPick2Name, R.id.widgetPick3Name
            )
            val reasons = intArrayOf(
                R.id.widgetPick1Reason, R.id.widgetPick2Reason, R.id.widgetPick3Reason
            )
            val wraps = intArrayOf(
                R.id.widgetPick1Wrap, R.id.widgetPick2Wrap, R.id.widgetPick3Wrap
            )

            content.rows.forEachIndexed { index, row ->
                setTextViewText(names[index], row.name)
                setTextViewText(reasons[index], context.getString(reasonRes(row.reason)))
                setOnClickPendingIntent(
                    wraps[index],
                    activityPendingIntent(context, REQ_ROW_BASE + index) { app ->
                        Intent(app, MainActivity::class.java)
                            .putExtra(MainActivity.EXTRA_OPEN_GUIDE_DETAIL, row.sourceId)
                    }
                )
                setViewVisibility(wraps[index], android.view.View.VISIBLE)
            }
            // Unused row slots (fewer than three picks) stay hidden.
            for (index in content.rows.size until 3) {
                setViewVisibility(wraps[index], android.view.View.GONE)
            }

            setViewVisibility(
                R.id.widgetSmartEmpty,
                if (content.empty) android.view.View.VISIBLE else android.view.View.GONE
            )
            if (content.empty) {
                setTextViewText(
                    R.id.widgetSmartEmpty,
                    context.getString(R.string.widget_smart_empty)
                )
            }
        }

        private fun activityPendingIntent(
            context: Context,
            requestCode: Int,
            intent: (Context) -> Intent
        ): PendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            intent(context).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        /** Same mapping as the guide screens — reason enum to chip text. */
        private fun reasonRes(reason: GuideSmart.Reason): Int = when (reason) {
            GuideSmart.Reason.TOP_CATEGORY -> R.string.guide_smart_reason_top
            GuideSmart.Reason.EDITORS_CHOICE -> R.string.guide_smart_reason_editors
            GuideSmart.Reason.COLD_START -> R.string.guide_smart_reason_cold
            GuideSmart.Reason.SIMILAR -> R.string.guide_smart_reason_similar
        }
    }
}
