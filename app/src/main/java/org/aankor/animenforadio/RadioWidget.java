package org.aankor.animenforadio;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import org.aankor.animenforadio.api.SongInfo;

import java.util.Date;


/**
 * Implementation of App Widget functionality.
 */
public class RadioWidget extends AppWidgetProvider {

    private static volatile boolean doAnfoSendsUpdates = false;
    private static volatile boolean isEnabled = false;
    private static AlarmManager alarmManager = null;
    private static PendingIntent anfoIntent;
    private static long songEndTime;
    private static RemoteViews views;
    private static boolean isPlaying = false;

    // called from service
    public static void updateWidget(Context context, SongInfo s, long songEndTime, int songPosTime, String songPosTimeStr, double nowPlayingPos) {
        RadioWidget.songEndTime = songEndTime;
        views = new RemoteViews(context.getPackageName(), R.layout.radio_widget);
        views.setTextViewText(R.id.titleView, s.getTitle());
        views.setTextViewText(R.id.artistView, s.getArtist());
        views.setImageViewBitmap(R.id.albumMiniArtView, s.getArtBmp());
        updateWidget(context);
        if (isEnabled && !doAnfoSendsUpdates)
            startAlarm();
    }

    private static void updateWidget(Context context) {
        // TODO: multithreading issues
        if (views == null)
            views = new RemoteViews(context.getPackageName(), R.layout.radio_widget);
        views.setImageViewResource(R.id.playStopButton, isPlaying ? R.drawable.player_stop : R.drawable.player_play);
        views.setOnClickPendingIntent(R.id.playStopButton, PendingIntent.getBroadcast(context, 0,
                new Intent(isPlaying ? PlayerStateReceiver.KEY_STOP : PlayerStateReceiver.KEY_PLAY), 0));
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        int[] ids = appWidgetManager.getAppWidgetIds(new ComponentName(context, RadioWidget.class));
        appWidgetManager.updateAppWidget(ids, views);
    }

    public static void notifyAnfoStartsToSendUpdates() {
        doAnfoSendsUpdates = true;
        stopAlarm();
    }

    public static void notifyAnfoStopsToSendUpdates() {
        doAnfoSendsUpdates = false;
        if (isEnabled)
            startAlarm();
    }

    public static void startAlarm() {
        alarmManager.set(AlarmManager.RTC, Math.max(songEndTime + 500, new Date().getTime() + 30000), anfoIntent);
    }

    public static void stopAlarm() {
        if (alarmManager == null)
            return;
        alarmManager.cancel(anfoIntent);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        // There may be multiple widgets active, so update all of them
        // appWidgetManager.updateAppWidget(appWidgetIds, views);
    }

    @Override
    public void onEnabled(Context context) {
        isEnabled = true;
        super.onEnabled(context);

        alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, AnfoService.class);
        intent.putExtra(AnfoService.ACTION_KEY, AnfoService.REFRESH_WIDGET_ACTION);
        anfoIntent = PendingIntent.getService(context, 0, intent, 0);
        try {
            if (doAnfoSendsUpdates) // Has updated earlier
                updateWidget(context);
            else
                anfoIntent.send();
        } catch (PendingIntent.CanceledException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDisabled(Context context) {
        isEnabled = false;
        stopAlarm();
        super.onDisabled(context);
    }

    public void onPlay(Context context) {
        isPlaying = true;
        updateWidget(context);
    }

    public void onStop(Context context) {
        isPlaying = false;
        updateWidget(context);
    }
}


