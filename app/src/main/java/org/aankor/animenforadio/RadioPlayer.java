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
        AnfoService.OnSongPosChangedListener,
        AnfoService.OnSongChangeListener,
        AnfoService.OnPlayerStateChangedListener {
    private AnfoService.PlayerState currentState;
    private ImageView albumMiniArtView;
    private TextView songNameView;
    private ProgressBar progressView;
    private TextView progressTextView;
    private SongInfo currentSong;
    private AnfoService.AnfoInterface anfo;
    private ImageButton playStopButton = null;


    public RadioPlayer() {
        // Required empty public constructor
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Intent main = getActivity().getIntent();
        currentState = AnfoService.PlayerState.STOPPED;
        // Inflate the layout for this fragment
        final View rootView = inflater.inflate(R.layout.radio_player, container, false);
        playStopButton = (ImageButton) rootView.findViewById(R.id.playStopButton);
        albumMiniArtView = (ImageView) rootView.findViewById(R.id.albumMiniArtView);
        songNameView = (TextView) rootView.findViewById(R.id.songNameView);
        progressView = (ProgressBar) rootView.findViewById(R.id.progressView);
        progressTextView = (TextView) rootView.findViewById(R.id.progressTextView);
        updatePlayButton();
        playStopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                switch (currentState) {
                    case STOPPED:
                        getActivity().startService(new Intent(getActivity(), AnfoService.class).setAction(AnfoService.START_PLAYBACK_ACTION));
                        break;
                    case CACHING:
                        anfo.stopPlayback();
                        break;
                    case PLAYING:
                        anfo.stopPlayback();
                        break;
                    case QUIET:
                        anfo.stopPlayback();
                        break;
                    case NO_AUDIO_FOCUS:
                        anfo.stopPlayback();
                        break;
                    case HEADSET_REMOVED:
                        getActivity().startService(new Intent(getActivity(), AnfoService.class).setAction(AnfoService.START_PLAYBACK_ACTION));
                        break;
                }
                updatePlayButton();
            }
        });

        return rootView;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
    }

    @Override
    public void onStart() {
        super.onStart();
        getActivity().bindService(new Intent(getActivity(), AnfoService.class), this, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onStop() {
        super.onStop();
        anfo.removeOnSongPosChangeListener(this);
        anfo.removeOnSongChangeListener(this);
        anfo.removeOnPlayerStateChangedListener(this);
        getActivity().unbindService(this);
    }

    private void updatePlayButton() {
        if (playStopButton == null)
            return;
        playStopButton.setEnabled(true);
        switch (currentState) {
            case STOPPED:
                playStopButton.setBackgroundResource(R.drawable.button_play);
                break;
            case CACHING:
                playStopButton.setBackgroundResource(R.drawable.button_caching);
                break;
            case PLAYING:
                playStopButton.setBackgroundResource(R.drawable.button_stop);
                break;
            case QUIET:
                playStopButton.setBackgroundResource(R.drawable.button_stop);
                break;
            case NO_AUDIO_FOCUS:
                playStopButton.setBackgroundResource(R.drawable.button_no_focus);
                break;
            case NO_NETWORK:
                playStopButton.setBackgroundResource(R.drawable.button_play);
                playStopButton.setEnabled(false);
                break;
            case HEADSET_REMOVED:
                playStopButton.setBackgroundResource(R.drawable.button_no_headset);
                break;
        }
    }

    private void updateSong(final SongInfo s, final long songEndTime) {
        if (s.getArtBmp() != null)
            albumMiniArtView.setImageBitmap(s.getArtBmp());
        else
            albumMiniArtView.setImageResource(R.drawable.image_not_found);
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
    public void onSongUntracked() {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                albumMiniArtView.setImageResource(R.drawable.image_not_found);
                songNameView.setText(getResources().getText(R.string.unknown));
                progressView.setProgress(0);
                progressTextView.setText("");
            }
        });
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        anfo = (AnfoService.AnfoInterface) iBinder;
        anfo.addOnSongPosChangeListener(this);
        anfo.addOnSongChangeListener(this);
        anfo.addOnPlayerStateChangedListener(this);
        if (currentState != anfo.getCurrentState()) {
            currentState = anfo.getCurrentState();
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updatePlayButton();
                }
            });
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {
        anfo = null;
    }

    @Override
    public void onPlayerStateChanged(AnfoService.PlayerState state) {
        currentState = state;
        updatePlayButton();
    }
}
