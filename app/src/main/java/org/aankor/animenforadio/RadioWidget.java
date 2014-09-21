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
    private static volatile AlarmManager alarmManager = null;
    private static volatile PendingIntent anfoIntent = null;
    private static long songEndTime;
    private static volatile RemoteViews views;
    private static volatile AnfoService.PlayerState currentState = AnfoService.PlayerState.STOPPED;
    private static volatile AppWidgetManager appWidgetManager = null;

    private static AppWidgetManager getAppWidgetManager(Context context) {
        if (appWidgetManager == null)
            appWidgetManager = AppWidgetManager.getInstance(context);
        return appWidgetManager;
    }

    private static PendingIntent makeAnfoIntent(Context context) {
        return anfoIntent = PendingIntent.getService(context, 0,
                new Intent(context, AnfoService.class).setAction(AnfoService.REFRESH_WIDGET_ACTION), 0);
    }

    private static int[] getWidgetIds(Context context) {
        return getAppWidgetManager(context).getAppWidgetIds(new ComponentName(context, RadioWidget.class));
    }

    private static boolean isEnabled(Context context) {
        return getWidgetIds(context).length > 0;
    }

    // called from service
    public static void updateWidget(Context context, SongInfo s,
                                    long songEndTime, int songPosTime, String songPosTimeStr, double nowPlayingPos) {
        RadioWidget.songEndTime = songEndTime;
        views = new RemoteViews(context.getPackageName(), R.layout.radio_widget);
        views.setTextViewText(R.id.titleView, s.getTitle());
        views.setTextViewText(R.id.artistView, s.getArtist());
        views.setImageViewBitmap(R.id.albumMiniArtView, s.getArtBmp());
        updateWidget(context, getWidgetIds(context));
        if (isEnabled(context) && !doAnfoSendsUpdates)
            startAlarm(context);
    }

    private static void updateWidget(Context context, int[] ids) {
        // TODO: multithreading issues
        if (views == null)
            views = new RemoteViews(context.getPackageName(), R.layout.radio_widget);
        PendingIntent intent = null;
        views.setBoolean(R.id.playStopButton, "setEnabled", true);
        switch (currentState) {
            case STOPPED:
                views.setInt(R.id.playStopButton, "setBackgroundResource", R.drawable.button_play);
                intent = PendingIntent.getService(context, 0,
                        new Intent(context, AnfoService.class).setAction(AnfoService.START_PLAYBACK_ACTION), 0);
                break;
            case CACHING:
                views.setInt(R.id.playStopButton, "setBackgroundResource", R.drawable.button_caching);
                intent = PendingIntent.getBroadcast(context, 0,
                        new Intent(AnfoService.KEY_STOP), 0);
                break;
            case PLAYING:
                views.setInt(R.id.playStopButton, "setBackgroundResource", R.drawable.button_stop);
                intent = PendingIntent.getBroadcast(context, 0,
                        new Intent(AnfoService.KEY_STOP), 0);
                break;
            case QUIET:
                views.setInt(R.id.playStopButton, "setBackgroundResource", R.drawable.button_stop);
                intent = PendingIntent.getBroadcast(context, 0,
                        new Intent(AnfoService.KEY_STOP), 0);
                break;
            case NO_AUDIO_FOCUS:
                views.setInt(R.id.playStopButton, "setBackgroundResource", R.drawable.button_no_focus);
                intent = PendingIntent.getBroadcast(context, 0,
                        new Intent(AnfoService.KEY_STOP), 0);
                break;
            case NO_NETWORK:
                views.setInt(R.id.playStopButton, "setBackgroundResource", R.drawable.button_play);
                views.setBoolean(R.id.playStopButton, "setEnabled", false);
                break;
            case HEADSET_REMOVED:
                views.setInt(R.id.playStopButton, "setBackgroundResource", R.drawable.button_no_headset);
                intent = PendingIntent.getService(context, 0,
                        new Intent(context, AnfoService.class).setAction(AnfoService.START_PLAYBACK_ACTION), 0);
                break;
        }

        views.setOnClickPendingIntent(R.id.playStopButton, intent);
        getAppWidgetManager(context).updateAppWidget(ids, views);
    }

    public static void notifyAnfoStartsToSendUpdates() {
        doAnfoSendsUpdates = true;
        stopAlarm();
    }

    public static void notifyAnfoStopsToSendUpdates(Context context) {
        doAnfoSendsUpdates = false;
        if (isEnabled(context))
            startAlarm(context);
    }

    private static void startAlarm(Context context) {
        if (alarmManager == null)
            alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        if (anfoIntent != null)
            alarmManager.cancel(anfoIntent);

        alarmManager.set(AlarmManager.RTC, Math.max(songEndTime + 500, new Date().getTime() + 30000),
                makeAnfoIntent(context));
    }

    private static void stopAlarm() {
        if (alarmManager == null)
            return;
        alarmManager.cancel(anfoIntent);
        anfoIntent = null;
    }

    public static void onPlayerStateChanged(Context context, AnfoService.PlayerState state) {
        currentState = state;
        updateWidget(context, getWidgetIds(context));
    }

    public static void songUntracked(Context context) {
        stopAlarm();
        views.setTextViewText(R.id.titleView, context.getResources().getText(R.string.unknown));
        views.setTextViewText(R.id.artistView, context.getResources().getText(R.string.unknown));
        views.setImageViewResource(R.id.albumMiniArtView, R.drawable.image_not_found);
        getAppWidgetManager(context).updateAppWidget(getWidgetIds(context), views);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        if (doAnfoSendsUpdates) {
            updateWidget(context, appWidgetIds);
        } else
            try {
                makeAnfoIntent(context).send();
            } catch (PendingIntent.CanceledException e) {
                e.printStackTrace();
            }
    }

    @Override
    public void onDisabled(Context context) {
        stopAlarm();
        super.onDisabled(context);
    }
}


