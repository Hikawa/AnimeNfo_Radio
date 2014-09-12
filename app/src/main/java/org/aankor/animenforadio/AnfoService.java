package org.aankor.animenforadio;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.IBinder;

import org.aankor.animenforadio.api.SongInfo;
import org.aankor.animenforadio.api.WebsiteGate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class AnfoService extends Service {
    public static final String START_PLAYBACK_ACTION = "org.aankor.animenforadio.AnfoService.startPlayback";
    public static final String REFRESH_WIDGET_ACTION = "org.aankor.animenforadio.AnfoService.stopPlayback";
    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(1);
    boolean fetchingCompletionNotified;
    ScheduledFuture processorHandle = null;
    SortedMap<WebsiteGate.Subscription, Long> refreshSchedule = new TreeMap<WebsiteGate.Subscription, Long>();
    private WebsiteGate gate = new WebsiteGate();
    private PlayerStateReceiver playerStateReceiver;
    private MediaPlayer mediaPlayer = new MediaPlayer();
    private RadioNotification notification;
    private ArrayList<OnSongChangeListener> onSongChangeListeners = new ArrayList<OnSongChangeListener>();
    private ArrayList<OnSongPosChangedListener> onSongPosChangedListeners = new ArrayList<OnSongPosChangedListener>();
    private boolean isSchedulerActive = false;

    public AnfoService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new AnfoInterface();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        notification = new RadioNotification(this);
        playerStateReceiver = new PlayerStateReceiver(getApplicationContext(), new PlayerStateReceiver.Listener() {
            @Override
            public void onStop(Context context) {
                stopPlayback();
            }

            @Override
            public void onPlay(Context context) {
                /*Intent intent = new Intent(context, AnfoService.class);
                intent.putExtra(ACTION_KEY, START_PLAYBACK_ACTION);
                startService(intent);
                if (!mediaPlayer.isPlaying())
                    mediaPlayer.prepareAsync();
                */
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
                RadioWidget.onPlay(getApplicationContext());
            }
        });
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        try {
            mediaPlayer.setDataSource("http://itori.animenfo.com:443");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent.getAction();
        if (action.equals(START_PLAYBACK_ACTION)) {
            if (!mediaPlayer.isPlaying())
                mediaPlayer.prepareAsync();
            return START_STICKY;
        } else if (action.equals(REFRESH_WIDGET_ACTION)) {
                refreshWidgetCommand();
                return START_NOT_STICKY;
        }
        return START_NOT_STICKY;
    }

    private void refreshWidgetCommand() {
        scheduler.execute(new Runnable() {
            @Override
            public void run() {
                gate.fetch(EnumSet.of(WebsiteGate.Subscription.CURRENT_SONG));
                if (gate.getCurrentSong() != null) {
                    RadioWidget.updateWidget(
                            getApplicationContext(),
                            gate.getCurrentSong(),
                            gate.getCurrentSongEndTime(),
                            gate.getCurrentSongPosTime(),
                            gate.getCurrentSongPosTimeStr(),
                            gate.getCurrentSongPosPercent());
                }
                stopSelf();
            }
        });
    }

    @Override
    public void onDestroy() {
        if (processorHandle != null)
            processorHandle.cancel(false);
        stopPlayback();
        mediaPlayer.release();
        playerStateReceiver.unregister(getApplicationContext());
        RadioWidget.notifyAnfoStopsToSendUpdates(getApplicationContext());

        super.onDestroy();
    }

    public void activateScheduler() {
        if (isSchedulerActive)
            return;
        isSchedulerActive = true;
        processorHandle = scheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                process();
            }
        }, 0, 1, TimeUnit.SECONDS);
    }

    private void process() {
        long currentTime = new Date().getTime();
        EnumSet<WebsiteGate.Subscription> fetchNow = EnumSet.noneOf(WebsiteGate.Subscription.class);
        for (SortedMap.Entry<WebsiteGate.Subscription, Long> e : refreshSchedule.entrySet()) {
            long time = e.getValue().longValue();
            if ((time == 0l) || (time < currentTime)) {
                fetchNow.add(e.getKey());
            }
        }
        notifyFetchStarted(fetchNow);
        gate.fetch(fetchNow);
        currentTime = (new Date()).getTime();

        if (fetchNow.contains(WebsiteGate.Subscription.CURRENT_SONG))
            refreshSchedule.put(WebsiteGate.Subscription.CURRENT_SONG,
                    Math.max(currentTime + 5000,
                            Math.min(gate.getCurrentSongEndTime() + 30, currentTime + 180000)));

        notifyFetchResult(fetchNow);

        // SongPosChanged is not subscribed by enumset
        if (gate.getCurrentSong() != null) {
            notifySongPosChanged();
        }
    }

    private void notifyFetchResult(EnumSet<WebsiteGate.Subscription> subscriptions) {
        for (WebsiteGate.Subscription s : subscriptions) {
            switch (s) {
                case CURRENT_SONG:
                    synchronized (onSongChangeListeners) {
                        if (gate.getCurrentSong() != null) {
                            for (OnSongChangeListener l : onSongChangeListeners) {
                                l.onSongChanged(
                                        gate.getCurrentSong(),
                                        gate.getCurrentSongEndTime(),
                                        gate.getCurrentSongPosTime(),
                                        gate.getCurrentSongPosTimeStr(),
                                        gate.getCurrentSongPosPercent());
                            }
                        } else {
                            for (OnSongChangeListener l : onSongChangeListeners) {
                                l.onSongUnknown();
                            }
                        }
                    }
                    RadioWidget.updateWidget(
                            getApplicationContext(),
                            gate.getCurrentSong(),
                            gate.getCurrentSongEndTime(),
                            gate.getCurrentSongPosTime(),
                            gate.getCurrentSongPosTimeStr(),
                            gate.getCurrentSongPosPercent());
                    break;
            }
        }
        fetchingCompletionNotified = true;
    }

    private void notifySongPosChanged() {
        synchronized (onSongPosChangedListeners) {
            for (OnSongPosChangedListener l : onSongPosChangedListeners)
                l.onSongPosChanged(
                        gate.getCurrentSongPosTime(),
                        gate.getCurrentSongPosTimeStr(),
                        gate.getCurrentSongPosPercent());
        }
    }

    private void notifyFetchStarted(EnumSet<WebsiteGate.Subscription> subscriptions) {
        for (WebsiteGate.Subscription s : subscriptions) {
            switch (s) {
                case CURRENT_SONG:
                    synchronized (onSongChangeListeners) {
                        for (OnSongChangeListener l : onSongChangeListeners)
                            l.onFetchingStarted();
                    }
                    break;
            }
        }
        fetchingCompletionNotified = false;
    }

    private void addOnSongChangeListener(final OnSongChangeListener l) {
        boolean first;
        synchronized (onSongChangeListeners) {
            first = onSongChangeListeners.isEmpty();
            onSongChangeListeners.add(l);
        }
        if (first)
            RadioWidget.notifyAnfoStartsToSendUpdates();
        final boolean wasFirst = first;
        scheduler.execute(new Runnable() {
            @Override
            public void run() {
                if (wasFirst) {
                    refreshSchedule.put(WebsiteGate.Subscription.CURRENT_SONG, 0l); // schedule now
                } else if (gate.getCurrentSong() != null)
                    l.onSongChanged(
                            gate.getCurrentSong(),
                            gate.getCurrentSongEndTime(),
                            gate.getCurrentSongPosTime(),
                            gate.getCurrentSongPosTimeStr(),
                            gate.getCurrentSongPosPercent());
            }
        });
        activateScheduler();
    }

    private void removeOnSongChangeListener(final OnSongChangeListener l) {
        boolean last = false;
        synchronized (onSongChangeListeners) {
            onSongChangeListeners.remove(l);
            last = onSongChangeListeners.isEmpty();
        }
        if (last)
            RadioWidget.notifyAnfoStopsToSendUpdates(getApplicationContext());
    }

    private void addOnSongPosChangeListener(final OnSongPosChangedListener l) {
        synchronized (onSongPosChangedListeners) {
            onSongPosChangedListeners.add(l);
        }
    }

    private void removeOnSongPosChangeListener(final OnSongPosChangedListener l) {
        synchronized (onSongPosChangedListeners) {
            onSongPosChangedListeners.remove(l);
        }
    }

    private void stopPlayback() {
        if (mediaPlayer.isPlaying())
            mediaPlayer.stop();
        // if hide notification when not playing
        notification.stop();
        RadioWidget.onStop(getApplicationContext());
        AnfoService.this.stopSelf(); // make service bound
    }

    public interface OnSongChangeListener {
        void onFetchingStarted();

        void onSongChanged(SongInfo s, long songEndTime, int songPosTime, String songPosTimeStr, double nowPlayingPos);

        void onSongUnknown();
    }

    public interface OnSongPosChangedListener {
        void onSongPosChanged(int songPosTime, String songPosTimeStr, double nowPlayingPos);
    }

    public class AnfoInterface extends Binder {
        public void addOnSongChangeListener(final OnSongChangeListener l) {
            AnfoService.this.addOnSongChangeListener(l);
        }

        public void removeOnSongChangeListener(final OnSongChangeListener l) {
            AnfoService.this.removeOnSongChangeListener(l);
        }

        public void addOnSongPosChangeListener(final OnSongPosChangedListener l) {
            AnfoService.this.addOnSongPosChangeListener(l);
        }

        public void removeOnSongPosChangeListener(final OnSongPosChangedListener l) {
            AnfoService.this.removeOnSongPosChangeListener(l);
        }

        public void stopPlayback() {
            AnfoService.this.stopPlayback();
        }
    }
}
