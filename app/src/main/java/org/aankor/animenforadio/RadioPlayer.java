package org.aankor.animenforadio;

import android.app.Fragment;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import org.aankor.animenforadio.api.SongInfo;


/**
 * A simple {@link Fragment} subclass.
 */
public class RadioPlayer extends Fragment implements ServiceConnection {
    private boolean isPlaying = false;
    private ImageView albumMiniArtView;
    private TextView songNameView;
    private WebsiteService.WebsiteBinder website;


    public RadioPlayer() {
        // Required empty public constructor
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        final View rootView = inflater.inflate(R.layout.radio_player, container, false);
        final ImageButton playStopButton = (ImageButton) rootView.findViewById(R.id.playStopButton);
        albumMiniArtView = (ImageView) rootView.findViewById(R.id.albumMiniArtView);
        songNameView = (TextView) rootView.findViewById(R.id.songNameView);
        playStopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                isPlaying = !isPlaying;
                playStopButton.setBackgroundResource(isPlaying ? R.drawable.player_stop : R.drawable.player_play);
                if (isPlaying) {
                    getActivity().startService(new Intent(getActivity(), RadioService.class));
                } else {
                    getActivity().stopService(new Intent(getActivity(), RadioService.class));
                    RadioNotification.cancel(getActivity());
                }
            }
        });
        return rootView;
    }

    @Override
    public void onStart() {
        super.onStart();
        getActivity().bindService(new Intent(getActivity(), WebsiteService.class), this, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onStop() {
        super.onStop();
        getActivity().unbindService(this);
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        website = (WebsiteService.WebsiteBinder) iBinder;
        website.addOnSongChangeListener(new WebsiteService.OnSongChangeListener() {
            @Override
            public void onFetchingStarted() {

            }

            @Override
            public void onSongChanged(final SongInfo s, final long songStartTime) {
                getActivity().runOnUiThread(
                        new Runnable() {
                            @Override
                            public void run() {
                                albumMiniArtView.setImageBitmap(s.getArtBmp());
                                songNameView.setText(s.getArtist() + " - " + s.getTitle());
                            }
                        });
            }

            @Override
            public void onSongRemained() {

            }

            @Override
            public void onSongUnknown() {

            }

            @Override
            public void onSongTimingRequested(long songPosTime, String songPosTimeStr, double nowPlayingPos) {

            }
        });
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {
        website = null;
    }
}
