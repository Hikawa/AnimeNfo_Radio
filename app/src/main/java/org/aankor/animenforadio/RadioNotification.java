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
import android.widget.RemoteViews;

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
    private int lastPos;
    private RemoteViews views;
    private SongInfo currentSong;

    public RadioNotification(final Context context) {
        this.context = context;
        Intent main = new Intent(context, Main.class);
        main.putExtra("isPlaying", true);
        main.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        views = new RemoteViews(context.getPackageName(), R.layout.radio_notification);
        views.setOnClickPendingIntent(R.id.playStopButton,
                PendingIntent.getBroadcast(context, 0, new Intent(PlayerStateReceiver.KEY_STOP), 0));

        builder = new Notification.Builder(context)
                .setContent(views)
                .setContentIntent(PendingIntent.getActivity(context, 0, main, 0))
                .setTicker("AnimeNfo Radio Player")
                .setSmallIcon(R.drawable.ic_launcher)
                .setOngoing(true);
    }

    public void start() {
        lastPos = -200;
        context.bindService(new Intent(context, WebsiteService.class), this, Context.BIND_AUTO_CREATE);
    }

    public void stop() {
        website.removeOnSongPosChangeListener(this);
        website.removeOnSongChangeListener(this);
        context.unbindService(this);
        website = null;
        cancel();
    }

    private void show(final Notification notification) {
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
        website = (WebsiteService.WebsiteBinder) iBinder;
        website.addOnSongPosChangeListener(this);
        website.addOnSongChangeListener(this);
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {
        website = null;
    }
}