package org.aankor.animenforadio;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import org.aankor.animenforadio.api.SongInfo;

public class RadioWidgetService extends Service implements ServiceConnection, AnfoService.OnSongChangeListener {
    AnfoService.AnfoInterface anfo;

    public RadioWidgetService() {
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        bindService(new Intent(getApplicationContext(), AnfoService.class), this, Context.BIND_AUTO_CREATE);

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        anfo.removeOnSongChangeListener(this);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onFetchingStarted() {

    }

    @Override
    public void onSongChanged(SongInfo s, long songEndTime, int songPosTime, String songPosTimeStr, double nowPlayingPos) {
        RadioWidget.updateWidget(getApplicationContext(), s, songEndTime, songPosTime, songPosTimeStr, nowPlayingPos);
    }

    @Override
    public void onSongUnknown() {

    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        anfo = (AnfoService.AnfoInterface) iBinder;
        anfo.addOnSongChangeListener(this);
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {
        anfo = null;
    }
}
