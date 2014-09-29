/*
 * This file is part of AnimeNfoRadio.
 * Copyright (C) 2014  aankor
 *
 * AnimeNfoRadio is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * AnimeNfoRadio is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with AnimeNfoRadio.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.aankor.animenforadio;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.aankor.animenforadio.api.SongInfo;

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
        getActivity().bindService(new Intent(getActivity(), AnfoService.class), this, Context.BIND_AUTO_CREATE);

        return rootView;
    }

    @Override
    public void onDestroyView() {
        anfo.removeOnSongPosChangeListener(this);
        anfo.removeOnSongChangeListener(this);
        anfo.removeOnPlayerStateChangedListener(this);
        getActivity().unbindService(this);
        super.onDestroyView();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (anfo != null)
            anfo.activityResumed();
    }

    @Override
    public void onStop() {
        if (anfo != null)
            anfo.activityPaused();
        super.onStop();
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
        if (s.getMiniArtBmp() != null)
            albumMiniArtView.setImageBitmap(s.getMiniArtBmp());
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
    public void onAlbumArtLoaded(final Bitmap artBmp, final Bitmap miniArtBmp) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (miniArtBmp != null)
                    albumMiniArtView.setImageBitmap(miniArtBmp);
                else
                    albumMiniArtView.setImageResource(R.drawable.image_not_found);
            }
        });
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        anfo = (AnfoService.AnfoInterface) iBinder;
        anfo.addOnSongPosChangeListener(this);
        anfo.addOnSongChangeListener(this);
        anfo.addOnPlayerStateChangedListener(this);
        anfo.activityResumed();
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
