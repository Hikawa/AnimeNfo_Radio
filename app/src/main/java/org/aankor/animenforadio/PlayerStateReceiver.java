package org.aankor.animenforadio;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

public abstract class PlayerStateReceiver extends BroadcastReceiver {
    public static final String KEY_STOP = "org.aankor.animenforadio.RadioService.stopRadio";

    public PlayerStateReceiver(Context context) {
        register(context);
    }

    public void register(Context context) {
        IntentFilter filter = new IntentFilter();
        filter.addAction(PlayerStateReceiver.KEY_STOP);
        context.registerReceiver(this, filter);
    }

    public void unregister(Context context) {
        context.unregisterReceiver(this);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(PlayerStateReceiver.KEY_STOP)) {
            onStop(context);
        }
    }

    public abstract void onStop(Context context);
}
