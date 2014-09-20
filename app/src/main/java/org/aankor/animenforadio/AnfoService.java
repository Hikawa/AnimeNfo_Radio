package org.aankor.animenforadio;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.widget.Toast;

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

public class AnfoService extends Service implements AudioManager.OnAudioFocusChangeListener {
    public static final String START_PLAYBACK_ACTION = "org.aankor.animenforadio.AnfoService.startPlayback";
    public static final String REFRESH_WIDGET_ACTION = "org.aankor.animenforadio.AnfoService.stopPlayback";
    public static final String KEY_STOP = "org.aankor.animenforadio.AnfoService.stopRadio";
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor();
    boolean fetchingCompletionNotified;
    ScheduledFuture processorHandle = null;
    SortedMap<WebsiteGate.Subscription, Long> refreshSchedule = new TreeMap<WebsiteGate.Subscription, Long>();
    WifiManager.WifiLock wifiLock = null;
    AudioManager audioManager = null;
    private PlayerState currentState = PlayerState.STOPPED;
    private WebsiteGate gate = new WebsiteGate();
    private CommandReceiver commandReceiver;
    private MediaPlayer mediaPlayer = new MediaPlayer();
    private RadioNotification notification;
    private ArrayList<OnSongChangeListener> onSongChangeListeners = new ArrayList<OnSongChangeListener>();
    private ArrayList<OnSongPosChangedListener> onSongPosChangedListeners = new ArrayList<OnSongPosChangedListener>();
    private ArrayList<OnPlayerStateChangedListener> onPlayerStateChangedListeners = new ArrayList<OnPlayerStateChangedListener>();
    private boolean isSchedulerActive = false;
    private boolean isPaused = false;
    private long playerStartedTime = 0;
    private ScheduledFuture<?> stopBufferingTask = null;
    private BroadcastReceiver systemReceiver = null;


    public AnfoService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new AnfoInterface();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (!isOnline())
            currentState = PlayerState.NO_NETWORK;
        systemReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                final String action = intent.getAction();
                if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                    final boolean online = !intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, false);
                    if ((currentState == PlayerState.NO_NETWORK) && online) {
                        notifyPlayerStateChanged(PlayerState.STOPPED);
                    } else if (!online) {
                        interruptPlayback(PlayerState.NO_NETWORK);
                    }
                } else if (action.equals(AudioManager.ACTION_AUDIO_BECOMING_NOISY)) {
                    if (mediaPlayer.isPlaying()) {
                        pausePlayback();
                        notifyPlayerStateChanged(PlayerState.HEADSET_REMOVED);
                    }
                } else if (action.equals(Intent.ACTION_HEADSET_PLUG)) {
                    if ((intent.getIntExtra("state", 0) == 1) && (currentState == PlayerState.HEADSET_REMOVED)) {
                        resumePlayback();
                    }
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        filter.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        filter.addAction(Intent.ACTION_HEADSET_PLUG);
        registerReceiver(systemReceiver, filter);
        wifiLock = ((WifiManager) getSystemService(Context.WIFI_SERVICE))
                .createWifiLock(WifiManager.WIFI_MODE_FULL, "anfo_stream_lock");
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        notification = new RadioNotification(this);
        commandReceiver = new CommandReceiver(getApplicationContext()) {
            @Override
            public void onStop(Context context) {
                stopPlayback();
            }
        };
        mediaPlayer.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
        mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                Toast.makeText(getApplicationContext(), "Radio player error", Toast.LENGTH_LONG).show();
                interruptPlayback(PlayerState.STOPPED);
                return false;
            }
        });
        mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                if (!mp.isPlaying()) {
                    playerStartedTime = new Date().getTime();
                    startPlayback();
                }
            }
        });

        mediaPlayer.setOnInfoListener(new MediaPlayer.OnInfoListener() {
            @Override
            public boolean onInfo(MediaPlayer mp, int what, int extra) {
                switch (what) {
                    case MediaPlayer.MEDIA_INFO_BUFFERING_START:
                        notifyPlayerStateChanged(PlayerState.CACHING);
                        break;
                    case MediaPlayer.MEDIA_INFO_BUFFERING_END:
                        notifyPlayerStateChanged(PlayerState.PLAYING);
                        break;
                }
                return false;
            }
        });
        /*
        mediaPlayer.setOnBufferingUpdateListener(new MediaPlayer.OnBufferingUpdateListener() {
            @Override
            public void onBufferingUpdate(MediaPlayer mp, int percent) {
                Log.i("BufferingUpdate", "" + percent);
                Log.i("Pos", "" + mp.getCurrentPosition());
            }
        });
        */
        PreferenceManager.setDefaultValues(getApplicationContext(), R.xml.prefs, false);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent.getAction();
        if (action.equals(START_PLAYBACK_ACTION)) {
            resumePlayback();
            notification.start();
            return START_NOT_STICKY;
        } else if (action.equals(REFRESH_WIDGET_ACTION)) {
            refreshWidgetCommand();
            return START_NOT_STICKY;
        }
        return START_NOT_STICKY;
    }

    private void refreshWidgetCommand() {
        if (dropCurrentSongTracking()) {
            RadioWidget.songUntracked(getApplicationContext());
            return;
        }
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
        interruptPlayback(PlayerState.STOPPED);
        mediaPlayer.release();
        commandReceiver.unregister(getApplicationContext());
        unregisterReceiver(systemReceiver);
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

    public boolean dropCurrentSongTracking() {
        return !mediaPlayer.isPlaying() &&
                !PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
                        .getBoolean("currentSongTracking", true);
    }

    private void process() {
        if (currentState == PlayerState.NO_NETWORK)
            return;
        long currentTime = new Date().getTime();
        EnumSet<WebsiteGate.Subscription> fetchNow = EnumSet.noneOf(WebsiteGate.Subscription.class);
        for (SortedMap.Entry<WebsiteGate.Subscription, Long> e : refreshSchedule.entrySet()) {
            long time = e.getValue().longValue();
            if ((time == 0l) || (time < currentTime)) {
                fetchNow.add(e.getKey());
            }
        }
        if (dropCurrentSongTracking()) {
            if (fetchNow.contains(WebsiteGate.Subscription.CURRENT_SONG) && gate.getCurrentSong() != null) {
                gate.unsetCurrentSong();
                notifySongUntracked();
            }
            fetchNow = EnumSet.noneOf(WebsiteGate.Subscription.class);
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

    private void notifySongUntracked() {
        synchronized (onSongChangeListeners) {
            for (OnSongChangeListener l : onSongChangeListeners) {
                l.onSongUntracked();
            }
        }
        RadioWidget.songUntracked(getApplicationContext());
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

    private void notifyPlayerStateChanged(PlayerState state) {
        if (currentState == state)
            return;
        currentState = state;
        synchronized (onPlayerStateChangedListeners) {
            for (OnPlayerStateChangedListener l : onPlayerStateChangedListeners)
                l.onPlayerStateChanged(state);
        }
        RadioWidget.onPlayerStateChanged(getApplicationContext(), state);
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

    private void addOnPlayerStateChangedListener(final OnPlayerStateChangedListener l) {
        synchronized (onPlayerStateChangedListeners) {
            onPlayerStateChangedListeners.add(l);
        }
    }

    private void removeOnPlayerStateChangedListener(final OnPlayerStateChangedListener l) {
        synchronized (onPlayerStateChangedListeners) {
            onPlayerStateChangedListeners.remove(l);
        }
    }

    private void resumePlayback() {
        synchronized (mediaPlayer) {
            if (!mediaPlayer.isPlaying()) {
                if (isPaused) {
                    isPaused = false;
                    stopBufferingTask.cancel(false); // synchronization here
                    long currentTime = new Date().getTime();
                    mediaPlayer.seekTo((int) (currentTime - playerStartedTime));
                    startPlayback();

                } else {
                    mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                    try {
                        mediaPlayer.setDataSource("http://itori.animenfo.com:443");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    mediaPlayer.prepareAsync();
                    notifyPlayerStateChanged(PlayerState.CACHING);
                }
            }
        }
    }

    private void pausePlayback() {
        audioManager.abandonAudioFocus(this);
        synchronized (mediaPlayer) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
                isPaused = true;
                stopBufferingTask = Executors.newSingleThreadScheduledExecutor()
                        .schedule(new Runnable() {
                            @Override
                            public void run() {
                                synchronized (mediaPlayer) {
                                    if (isPaused) {
                                        mediaPlayer.stop();
                                        mediaPlayer.reset();
                                        wifiLock.release();
                                        isPaused = false;
                                        // if hide notification when not playing
                                        notification.stop();
                                        AnfoService.this.stopSelf(); // make service bound
                                    }
                                }
                            }
                        }, 60, TimeUnit.SECONDS);
            } else {
                mediaPlayer.reset();
                isPaused = false;
                if (wifiLock.isHeld())
                    wifiLock.release();
                notification.stop();
                AnfoService.this.stopSelf(); // make service bound
            }
        }
    }

    public void startPlayback() {
        if (AudioManager.AUDIOFOCUS_REQUEST_FAILED ==
                audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)) {

            Toast.makeText(getApplicationContext(), "Your audio system is busy now. Try to start playing anfo radio later", Toast.LENGTH_LONG)
                    .show();
            synchronized (mediaPlayer) {
                mediaPlayer.reset();
                isPaused = false;
            }
            notifyPlayerStateChanged(PlayerState.STOPPED);
            notification.stop();
            AnfoService.this.stopSelf(); // make service bound
            return;
        }
        mediaPlayer.start();
        wifiLock.acquire();

        notifyPlayerStateChanged(PlayerState.PLAYING);
    }

    private void stopPlayback() {
        pausePlayback();
        notifyPlayerStateChanged(PlayerState.STOPPED);
        /*
        // if hide notification when not playing
        notification.stop();
        AnfoService.this.stopSelf(); // make service bound
        */
    }

    private void interruptPlayback(PlayerState state) {
        audioManager.abandonAudioFocus(AnfoService.this);
        if (wifiLock.isHeld())
            wifiLock.release();
        isPaused = false;
        mediaPlayer.reset();
        notifyPlayerStateChanged(state);
        notification.stop();
        AnfoService.this.stopSelf(); // make service bound
    }

    @Override
    public void onAudioFocusChange(int focus) {
        switch (focus) {
            case AudioManager.AUDIOFOCUS_GAIN:
                if (currentState == PlayerState.QUIET) {
                    notifyPlayerStateChanged(PlayerState.PLAYING);
                } else
                    resumePlayback();
                break;

            case AudioManager.AUDIOFOCUS_LOSS:
                Toast.makeText(getApplicationContext(), "Your audio system becomes busy for a long time. Try to start playing anfo radio later", Toast.LENGTH_LONG)
                        .show();
                stopPlayback(); // resume me manually
                break;

            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                pausePlayback();
                notifyPlayerStateChanged(PlayerState.NO_AUDIO_FOCUS);
                break;

            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                notifyPlayerStateChanged(PlayerState.QUIET);
                break;

        }
    }

    public boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo info = cm.getActiveNetworkInfo();
        return (info != null) && info.isConnected();
    }

    enum PlayerState {
        STOPPED,
        CACHING,
        PLAYING,
        QUIET,
        NO_NETWORK,
        NO_AUDIO_FOCUS,
        HEADSET_REMOVED
    }

    public interface OnSongChangeListener {
        void onFetchingStarted();

        void onSongChanged(SongInfo s, long songEndTime, int songPosTime, String songPosTimeStr, double nowPlayingPos);

        void onSongUnknown();

        void onSongUntracked();
    }

    public interface OnSongPosChangedListener {
        void onSongPosChanged(int songPosTime, String songPosTimeStr, double nowPlayingPos);
    }

    public interface OnPlayerStateChangedListener {
        void onPlayerStateChanged(PlayerState state);
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

        public void addOnPlayerStateChangedListener(OnPlayerStateChangedListener l) {
            AnfoService.this.addOnPlayerStateChangedListener(l);
        }

        public void removeOnPlayerStateChangedListener(OnPlayerStateChangedListener l) {
            AnfoService.this.removeOnPlayerStateChangedListener(l);
        }

        public void stopPlayback() {
            AnfoService.this.stopPlayback();
        }

        public PlayerState getCurrentState() {
            return currentState;
        }
    }

    private abstract class CommandReceiver extends BroadcastReceiver {

        CommandReceiver(Context context) {
            register(context);
        }

        public void register(Context context) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(KEY_STOP);
            context.registerReceiver(this, filter);
        }

        public void unregister(Context context) {
            context.unregisterReceiver(this);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action.equals(KEY_STOP)) {
                onStop(context);
            }
        }

        public abstract void onStop(Context context);
    }
}
