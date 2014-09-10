package org.aankor.animenforadio;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.IBinder;

import java.io.IOException;

public class RadioService extends Service {
    PlayerStateReceiver playerStateReceiver;
    private MediaPlayer mediaPlayer = new MediaPlayer();
    private RadioNotification notification;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    } // Started service

    @Override
    public void onCreate() {
        notification = new RadioNotification(this);
        playerStateReceiver = new PlayerStateReceiver(getApplicationContext(), new PlayerStateReceiver.Listener() {
            @Override
            public void onStop(Context context) {
                RadioService.this.stopSelf();
            }
        });
        mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                return false;
            }
        });
        mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                if (!mp.isPlaying())
                    mp.start();
                notification.start();
            }
        });
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
    }

    @Override
    public void onDestroy() {
        if (mediaPlayer.isPlaying())
            mediaPlayer.stop();
        mediaPlayer.release();
        notification.stop();
        playerStateReceiver.unregister(getApplicationContext());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (mediaPlayer.isPlaying())
            return START_STICKY; // wtf

        try {
            mediaPlayer.setDataSource("http://itori.animenfo.com:443");
        } catch (IOException e) {

        }

        mediaPlayer.prepareAsync();
        return START_STICKY;
    }
}
