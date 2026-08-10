package com.armsone.stand.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import androidx.core.net.toUri
import com.armsone.stand.R

/** A compact home-screen shortcut equivalent to the iOS circular launch widget. */
class STandLaunchWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { appWidgetId ->
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        updateWidget(context, appWidgetManager, appWidgetId)
    }

    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_stand_launch).apply {
            setOnClickPendingIntent(R.id.widget_root, launchPendingIntent(context))
            setContentDescription(
                R.id.widget_root,
                "${context.getString(R.string.app_name)} 열기",
            )
        }
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun launchPendingIntent(context: Context): PendingIntent {
        val launchIntent = Intent(Intent.ACTION_VIEW, OPEN_URI).apply {
            setPackage(context.packageName)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
        }
        return PendingIntent.getActivity(
            context,
            OPEN_REQUEST_CODE,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private companion object {
        val OPEN_URI = "stand://open".toUri()
        const val OPEN_REQUEST_CODE = 10_001
    }
}
