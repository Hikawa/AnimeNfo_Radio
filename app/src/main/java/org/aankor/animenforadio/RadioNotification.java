package org.aankor.animenforadio;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.IBinder;

import org.aankor.animenforadio.api.SongInfo;


/**
 * Helper class for showing and canceling radio
 * notifications.
 */
public class RadioNotification implements
        ServiceConnection,
        WebsiteService.OnSongPosChangedListener,
        WebsiteService.OnSongChangeListener {
    /**
     * The unique identifier for this type of notification.
     */
    private static final String NOTIFICATION_TAG = "AnfoRadio";
    private final Context context;
    private Notification.Builder builder;
    private WebsiteService.WebsiteBinder website;
    private volatile boolean isStarted;
    private int lastPos;

    public RadioNotification(final Context context) {
        this.context = context;
        builder = new Notification.Builder(context)
                .setContentTitle("AnimeNfo Radio")
                .setSmallIcon(R.drawable.ic_launcher)
                .setTicker("AnimeNfo Radio Player")
                .setOngoing(true)
                .addAction(R.drawable.player_stop, null,
                        PendingIntent.getBroadcast(context, 0, new Intent(PlayerStateReceiver.KEY_STOP), 0));
    }

    public void start() {
        isStarted = true;
        lastPos = -200;
        context.bindService(new Intent(context, WebsiteService.class), this, Context.BIND_AUTO_CREATE);
    }

    public void stop() {
        isStarted = false;
        website.removeOnSongPosChangeListener(this);
        website.removeOnSongChangeListener(this);
        context.unbindService(this);
        website = null;
        cancel();
    }

    private void show(final Notification notification) {
        // if there will be updates after stopping process is started
        if (!isStarted)
            return;
        final NotificationManager nm = (NotificationManager) context
                .getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ECLAIR) {
            nm.notify(NOTIFICATION_TAG, 0, notification);
        } else {
            nm.notify(NOTIFICATION_TAG.hashCode(), notification);
        }
    }

    public void cancel() {
        final NotificationManager nm = (NotificationManager) context
                .getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ECLAIR) {
            nm.cancel(NOTIFICATION_TAG, 0);
        } else {
            nm.cancel(NOTIFICATION_TAG.hashCode());
        }
    }

    void updateSong(SongInfo s, long songEndTime) {
        builder = builder.setContentText(s.getArtist() + " - " + s.getTitle())
                .setLargeIcon(s.getArtBmp());
    }

    void updateTiming(int songPosTime, String songPosTimeStr, int pos) {
        builder = builder.setProgress(100, pos, false);
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
        updateSong(s, songEndTime);
        updateTiming(songPosTime, songPosTimeStr, lastPos = (int) nowPlayingPos);
        show(builder.build());
    }

    @Override
    public void onSongUnknown() {

    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        website = (WebsiteService.WebsiteBinder) iBinder;
        website.addOnSongPosChangeListener(this);
        website.addOnSongChangeListener(this);
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {
        website = null;
        cancel();
    }
}