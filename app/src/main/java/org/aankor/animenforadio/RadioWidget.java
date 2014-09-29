package org.aankor.animenforadio;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.widget.RemoteViews;

import org.aankor.animenforadio.api.SongInfo;

import java.util.ArrayList;
import java.util.Date;


/**
 * Implementation of App Widget functionality.
 */
public class RadioWidget extends AppWidgetProvider {

    private static volatile AlarmManager alarmManager = null;
    private static volatile PendingIntent anfoIntent = null;
    private static long songEndTime = 0;
    private static volatile SongInfo currentSong = null;
    private static volatile AnfoService.PlayerState currentState = AnfoService.PlayerState.STOPPED;
    private static volatile AppWidgetManager appWidgetManager = null;
    private static volatile ArrayList<Integer> pendingIds = new ArrayList<Integer>();

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

    public static boolean isEnabled(Context context) {
        return getWidgetIds(context).length > 0;
    }

    // called from service
    public static void updateWidget(Context context, SongInfo s,
                                    long songEndTime, int songPosTime, String songPosTimeStr, double nowPlayingPos) {
        RadioWidget.songEndTime = songEndTime;
        currentSong = s;
        updateWidget(context, getWidgetIds(context));
        if (isEnabled(context))
            startAlarm(context);
    }

    private static void updateWidget(Context context, int[] ids) {
        // TODO: multithreading issues
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.radio_widget);
        views = new RemoteViews(context.getPackageName(), R.layout.radio_widget);
        if (currentSong != null) {
            views.setTextViewText(R.id.titleView, currentSong.getTitle());
            views.setTextViewText(R.id.artistView, currentSong.getArtist());
            if (currentSong.getMiniArtBmp() != null)
                views.setImageViewBitmap(R.id.albumMiniArtView, currentSong.getMiniArtBmp());
            else
                views.setImageViewResource(R.id.albumMiniArtView, R.drawable.image_not_found);

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
                    intent = PendingIntent.getActivity(context, 0, new Intent(), 0);
                    break;
                case HEADSET_REMOVED:
                    views.setInt(R.id.playStopButton, "setBackgroundResource", R.drawable.button_no_headset);
                    intent = PendingIntent.getService(context, 0,
                            new Intent(context, AnfoService.class).setAction(AnfoService.START_PLAYBACK_ACTION), 0);
                    break;
            }

            views.setOnClickPendingIntent(R.id.playStopButton, intent);
        } else {
            views.setTextViewText(R.id.titleView, context.getResources().getText(R.string.unknown));
            views.setTextViewText(R.id.artistView, context.getResources().getText(R.string.unknown));
            views.setImageViewResource(R.id.albumMiniArtView, R.drawable.image_not_found);
        }
        getAppWidgetManager(context).updateAppWidget(ids, views);
    }

    private static synchronized void startAlarm(Context context) {
        if (alarmManager == null)
            alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        if (anfoIntent != null)
            alarmManager.cancel(anfoIntent);

        alarmManager.set(AlarmManager.RTC, Math.max(songEndTime + 500, new Date().getTime() + 30000),
                makeAnfoIntent(context));
    }

    private static synchronized void stopAlarm() {
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
        currentSong = null;
        updateWidget(context, getWidgetIds(context));
        stopAlarm();
    }

    static public void serviceCommandExecuted(Context context) {
        synchronized (pendingIds) {
            int[] ids = new int[pendingIds.size()];
            for (int i = 0; i < ids.length; i++) {
                ids[i] = pendingIds.get(i);
            }
            updateWidget(context, ids);
            pendingIds.clear();
        }
    }

    public static void updateAlbumArt(Context context, Bitmap artBmp) {
        updateWidget(context, getWidgetIds(context));
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        try {
            synchronized (pendingIds) {
                for (int i : appWidgetIds)
                    pendingIds.add(i);
            }
            makeAnfoIntent(context).send();
        } catch (PendingIntent.CanceledException e) {
        }
    }

    @Override
    public void onDisabled(Context context) {
        stopAlarm();
        super.onDisabled(context);
    }
}


