package org.aankor.animenforadio;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

public class PlayerStateReceiver extends BroadcastReceiver {
    public static final String KEY_STOP = "org.aankor.animenforadio.RadioService.stopRadio";
    public static final String KEY_PLAY = "org.aankor.animenforadio.RadioService.playRadio";
    private Listener listener;

    public PlayerStateReceiver() {

    }

    public PlayerStateReceiver(Listener listener) {
        this.listener = listener;
    }

    public PlayerStateReceiver(Context context, Listener listener) {
        this.listener = listener;
        register(context);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void register(Context context) {
        IntentFilter filter = new IntentFilter();
        filter.addAction(PlayerStateReceiver.KEY_PLAY);
        filter.addAction(PlayerStateReceiver.KEY_STOP);
        context.registerReceiver(this, filter);
    }

    public void unregister(Context context) {
        context.unregisterReceiver(this);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        if (action.equals(PlayerStateReceiver.KEY_PLAY)) {
            listener.onPlay(context);
        } else if (action.equals(PlayerStateReceiver.KEY_STOP)) {
            listener.onStop(context);
        }
    }

    public interface Listener {
        void onStop(Context context);

        void onPlay(Context context);
    }
}
