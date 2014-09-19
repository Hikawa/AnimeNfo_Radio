package org.aankor.animenforadio;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.widget.RemoteViews;

import org.aankor.animenforadio.api.SongInfo;


/**
 * Helper class for showing and canceling radio
 * notifications.
 */
public class RadioNotification implements
        ServiceConnection,
        AnfoService.OnSongPosChangedListener,
        AnfoService.OnSongChangeListener,
        AnfoService.OnPlayerStateChangedListener {
    /**
     * The unique identifier for this type of notification.
     */
    private static final String NOTIFICATION_TAG = "AnfoRadio";
    private final Context context;
    private final Service service;
    private Notification.Builder builder;
    private AnfoService.AnfoInterface anfo;
    private int lastPos;
    private RemoteViews views;
    private SongInfo currentSong;

    public RadioNotification(final Service s) {
        this.service = s;
        this.context = s.getApplicationContext();
        Intent main = new Intent(context, Main.class);
        main.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        views = new RemoteViews(context.getPackageName(), R.layout.radio_notification);
        // default action
        views.setOnClickPendingIntent(R.id.playStopButton,
                PendingIntent.getBroadcast(context, 0, new Intent(AnfoService.KEY_STOP), 0));

        builder = new Notification.Builder(context)
                .setContent(views)
                .setContentIntent(PendingIntent.getActivity(context, 0, main, 0))
                .setTicker("AnimeNfo Radio Player")
                .setSmallIcon(R.drawable.ic_launcher)
                .setOngoing(true);
    }

    public void start() {
        lastPos = -200;
        context.bindService(new Intent(context, AnfoService.class), this, Context.BIND_AUTO_CREATE);
    }

    public void stop() {
        // TODO: why am I called when I am not started
        if (anfo != null) {
            anfo.removeOnSongPosChangeListener(this);
            anfo.removeOnSongChangeListener(this);
            anfo.removeOnPlayerStateChangedListener(this);
            context.unbindService(this);
            anfo = null;
        }
        cancel();
    }

    private void show(final Notification notification) {
        service.startForeground(NOTIFICATION_TAG.hashCode(), notification);
    }

    private void cancel() {
        service.stopForeground(true);
    }

    void updateSong(SongInfo s, long songEndTime) {
        views.setTextViewText(R.id.songNameView, s.getArtist() + " - " + s.getTitle());
        views.setImageViewBitmap(R.id.albumMiniArtView, s.getArtBmp());
    }

    void updateTiming(int songPosTime, String songPosTimeStr, int pos) {
        views.setProgressBar(R.id.progressView, 100, pos, false);
        views.setTextViewText(R.id.progressTextView, songPosTimeStr + " / " + currentSong.getDurationStr());
    }

    @Override
    public void onSongPosChanged(int songPosTime, String songPosTimeStr, double nowPlayingPos) {
        int pos = (int) nowPlayingPos;
        if (pos == lastPos)
            return; // don't notify if nothing is changed
        updateTiming(songPosTime, songPosTimeStr, pos);
        show(builder.build());
    }

    @Override
    public void onFetchingStarted() {

    }

    @Override
    public void onSongChanged(SongInfo s, long songEndTime, int songPosTime, String songPosTimeStr, double nowPlayingPos) {
        currentSong = s;
        updateSong(s, songEndTime);
        updateTiming(songPosTime, songPosTimeStr, lastPos = (int) nowPlayingPos);
        show(builder.build());
    }

    @Override
    public void onSongUnknown() {

    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        anfo = (AnfoService.AnfoInterface) iBinder;
        anfo.addOnSongPosChangeListener(this);
        anfo.addOnSongChangeListener(this);
        anfo.addOnPlayerStateChangedListener(this);
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {
        anfo = null;
    }

    @Override
    public void onPlayerStateChanged(AnfoService.PlayerState state) {
        PendingIntent intent = null;
        views.setBoolean(R.id.playStopButton, "setEnabled", true);
        switch (state) {
            case STOPPED:
                views.setInt(R.id.playStopButton, "setBackgroundResource", R.drawable.button_play);
                intent = PendingIntent.getService(context, 0,
                        new Intent(context, AnfoService.class).setAction(AnfoService.START_PLAYBACK_ACTION), 0);
                builder.setOngoing(false);
                break;
            case CACHING:
                views.setInt(R.id.playStopButton, "setBackgroundResource", R.drawable.button_caching);
                intent = PendingIntent.getBroadcast(context, 0,
                        new Intent(AnfoService.KEY_STOP), 0);
                builder.setOngoing(true);
                break;
            case PLAYING:
                views.setInt(R.id.playStopButton, "setBackgroundResource", R.drawable.button_stop);
                intent = PendingIntent.getBroadcast(context, 0,
                        new Intent(AnfoService.KEY_STOP), 0);
                builder.setOngoing(true);
                break;
            case QUIET:
                views.setInt(R.id.playStopButton, "setBackgroundResource", R.drawable.button_stop);
                intent = PendingIntent.getBroadcast(context, 0,
                        new Intent(AnfoService.KEY_STOP), 0);
                builder.setOngoing(true);
                break;
            case NO_AUDIO_FOCUS:
                views.setInt(R.id.playStopButton, "setBackgroundResource", R.drawable.button_no_focus);
                intent = PendingIntent.getBroadcast(context, 0,
                        new Intent(AnfoService.KEY_STOP), 0);
                builder.setOngoing(true);
                break;
            case NO_NETWORK:
                views.setInt(R.id.playStopButton, "setBackgroundResource", R.drawable.button_play);
                views.setBoolean(R.id.playStopButton, "setEnabled", false);
                builder.setOngoing(false);
                break;
            case HEADSET_REMOVED:
                views.setInt(R.id.playStopButton, "setBackgroundResource", R.drawable.button_no_headset);
                intent = PendingIntent.getService(context, 0,
                        new Intent(context, AnfoService.class).setAction(AnfoService.START_PLAYBACK_ACTION), 0);
                builder.setOngoing(false);
                break;
        }
        views.setOnClickPendingIntent(R.id.playStopButton, intent);
        show(builder.build());
    }
}