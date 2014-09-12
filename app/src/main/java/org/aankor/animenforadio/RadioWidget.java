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
    private static PendingIntent anfoIntent = null;
    private static long songEndTime;
    private static RemoteViews views;
    private static boolean isPlaying = false;

    private static PendingIntent makeAnfoIntent(Context context) {
        return anfoIntent = PendingIntent.getService(context, 0,
                new Intent(context, AnfoService.class).setAction(AnfoService.REFRESH_WIDGET_ACTION), 0);
    }

    // called from service
    public static void updateWidget(Context context, SongInfo s, long songEndTime, int songPosTime, String songPosTimeStr, double nowPlayingPos) {
        RadioWidget.songEndTime = songEndTime;
        views = new RemoteViews(context.getPackageName(), R.layout.radio_widget);
        views.setTextViewText(R.id.titleView, s.getTitle());
        views.setTextViewText(R.id.artistView, s.getArtist());
        views.setImageViewBitmap(R.id.albumMiniArtView, s.getArtBmp());
        updateWidget(context);
        if (isEnabled && !doAnfoSendsUpdates)
            startAlarm(context);
    }

    private static void updateWidget(Context context) {
        // TODO: multithreading issues
        if (views == null)
            views = new RemoteViews(context.getPackageName(), R.layout.radio_widget);
        views.setImageViewResource(R.id.playStopButton, isPlaying ? R.drawable.player_stop : R.drawable.player_play);

        PendingIntent intent;
        if (isPlaying)
            intent = PendingIntent.getBroadcast(context, 0,
                    new Intent(PlayerStateReceiver.KEY_STOP), 0);
        else
            intent = PendingIntent.getService(context, 0,
                    new Intent(context, AnfoService.class).setAction(AnfoService.START_PLAYBACK_ACTION), 0);
        views.setOnClickPendingIntent(R.id.playStopButton, intent);

        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        int[] ids = appWidgetManager.getAppWidgetIds(new ComponentName(context, RadioWidget.class));
        appWidgetManager.updateAppWidget(ids, views);
    }

    public static void notifyAnfoStartsToSendUpdates() {
        doAnfoSendsUpdates = true;
        stopAlarm();
    }

    public static void notifyAnfoStopsToSendUpdates(Context context) {
        doAnfoSendsUpdates = false;
        if (isEnabled)
            startAlarm(context);
    }

    public static void startAlarm(Context context) {
        alarmManager.set(AlarmManager.RTC, Math.max(songEndTime + 500, new Date().getTime() + 30000),
                makeAnfoIntent(context));
    }

    public static void stopAlarm() {
        if (alarmManager == null)
            return;
        alarmManager.cancel(anfoIntent);
    }

    public static void onPlay(Context context) {
        isPlaying = true;
        updateWidget(context);
    }

    public static void onStop(Context context) {
        isPlaying = false;
        updateWidget(context);
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
        try {
            if (doAnfoSendsUpdates) // Has updated earlier
                updateWidget(context);
            else
                makeAnfoIntent(context).send();
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
}


