package org.aankor.animenforadio;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import org.aankor.animenforadio.api.SongInfo;


/**
 * Implementation of App Widget functionality.
 */
public class RadioWidget extends AppWidgetProvider {

    // called from service
    public static void updateWidget(Context context, SongInfo s, long songEndTime, int songPosTime, String songPosTimeStr, double nowPlayingPos) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.radio_widget);
        views.setTextViewText(R.id.titleView, s.getTitle());
        views.setTextViewText(R.id.artistView, s.getArtist());
        views.setImageViewBitmap(R.id.albumMiniArtView, s.getArtBmp());
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        int[] ids = appWidgetManager.getAppWidgetIds(new ComponentName(context, RadioWidget.class));
        appWidgetManager.updateAppWidget(ids, views);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        // There may be multiple widgets active, so update all of them
        // appWidgetManager.updateAppWidget(appWidgetIds, views);
    }

    @Override
    public void onEnabled(Context context) {
        context.startService(new Intent(context, RadioWidgetService.class));
        super.onEnabled(context);
    }

    @Override
    public void onDisabled(Context context) {
        context.stopService(new Intent(context, RadioWidgetService.class));
        super.onDisabled(context);
    }
}


