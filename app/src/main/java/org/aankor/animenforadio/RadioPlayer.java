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
import android.widget.ProgressBar;
import android.widget.TextView;

import org.aankor.animenforadio.api.SongInfo;


/**
 * A simple {@link Fragment} subclass.
 */
public class RadioPlayer extends Fragment implements
        ServiceConnection,
        WebsiteService.OnSongPosChangedListener,
        WebsiteService.OnSongChangeListener {
    PlayerStateReceiver playerStateReceiver;
    private boolean isPlaying = false;
    private ImageView albumMiniArtView;
    private TextView songNameView;
    private ProgressBar progressView;
    private TextView progressTextView;
    private SongInfo currentSong;
    private WebsiteService.WebsiteBinder website;


    public RadioPlayer() {
        // Required empty public constructor
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Intent main = getActivity().getIntent();
        isPlaying = main.getBooleanExtra("isPlaying", false);
        // Inflate the layout for this fragment
        final View rootView = inflater.inflate(R.layout.radio_player, container, false);
        final ImageButton playStopButton = (ImageButton) rootView.findViewById(R.id.playStopButton);
        albumMiniArtView = (ImageView) rootView.findViewById(R.id.albumMiniArtView);
        songNameView = (TextView) rootView.findViewById(R.id.songNameView);
        progressView = (ProgressBar) rootView.findViewById(R.id.progressView);
        progressTextView = (TextView) rootView.findViewById(R.id.progressTextView);
        playStopButton.setBackgroundResource(isPlaying ? R.drawable.player_stop : R.drawable.player_play);
        playStopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                isPlaying = !isPlaying;
                playStopButton.setBackgroundResource(isPlaying ? R.drawable.player_stop : R.drawable.player_play);
                if (isPlaying) {
                    getActivity().startService(new Intent(getActivity(), RadioService.class));
                } else {
                    getActivity().stopService(new Intent(getActivity(), RadioService.class));
                }
            }
        });
        playerStateReceiver = new PlayerStateReceiver(getActivity()) {
            @Override
            public void onStop(Context context) {
                isPlaying = false;
                playStopButton.setBackgroundResource(R.drawable.player_play);
            }
        };
        return rootView;
    }

    @Override
    public void onDestroyView() {
        playerStateReceiver.unregister(getActivity());
        super.onDestroyView();
    }

    @Override
    public void onStart() {
        super.onStart();
        getActivity().bindService(new Intent(getActivity(), WebsiteService.class), this, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onStop() {
        super.onStop();
        website.addOnSongPosChangeListener(this);
        website.addOnSongChangeListener(this);
        getActivity().unbindService(this);
    }

    private void updateSong(final SongInfo s, final long songEndTime) {
        if (s.getArtBmp() != null)
            albumMiniArtView.setImageBitmap(s.getArtBmp());
        else
            albumMiniArtView.setImageResource(R.drawable.example_picture);
        songNameView.setText(s.getArtist() + " - " + s.getTitle());
    }

    private void updateTiming(int songPosTime, String songPosTimeStr, double nowPlayingPos) {
        progressView.setProgress((int) nowPlayingPos);
        progressTextView.setText(songPosTimeStr + " / " + currentSong.getDurationStr());
    }

    @Override
    public void onSongPosChanged(final int songPosTime, final String songPosTimeStr, final double nowPlayingPos) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updateTiming(songPosTime, songPosTimeStr, nowPlayingPos);
            }
        });
    }

    @Override
    public void onFetchingStarted() {

    }

    @Override
    public void onSongChanged(final SongInfo s, final long songEndTime,
                              final int songPosTime, final String songPosTimeStr, final double nowPlayingPos) {
        currentSong = s;
        getActivity().runOnUiThread(
                new Runnable() {
                    @Override
                    public void run() {
                        updateSong(s, songEndTime);
                        updateTiming(songPosTime, songPosTimeStr, nowPlayingPos);
                    }
                });
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
