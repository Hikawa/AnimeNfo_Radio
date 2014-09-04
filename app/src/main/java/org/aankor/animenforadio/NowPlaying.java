package org.aankor.animenforadio;

import android.app.Fragment;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;

/**
 *
 */
public class NowPlaying extends Fragment {

    private boolean isPlaying = false;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        final View rootView = inflater.inflate(R.layout.now_playing, container, false);
        final ImageButton playStopButton = (ImageButton) rootView.findViewById(R.id.playStopButton);
        playStopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                isPlaying = !isPlaying;
                playStopButton.setBackgroundResource(isPlaying? R.drawable.player_stop: R.drawable.player_play);
                if (isPlaying)
                    getActivity().startService(new Intent(getActivity(), RadioService.class));
                else {
                    getActivity().stopService(new Intent(getActivity(), RadioService.class));
                    RadioNotification.cancel(getActivity());
                }
            }
        });
        return rootView;
    }
}
