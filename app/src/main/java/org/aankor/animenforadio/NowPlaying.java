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
import android.widget.ImageView;
import android.widget.TextView;

import org.aankor.animenforadio.api.SongInfo;

public class NowPlaying extends Fragment implements ServiceConnection, AnfoService.OnSongChangeListener {
    private TextView artistView;
    private TextView titleView;
    private TextView albumView;
    private TextView seriesView;
    private TextView genreView;
    private ImageView albumArtView;
    private TextView ratingTextView;

    private AnfoService.AnfoInterface anfo;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        final View rootView = inflater.inflate(R.layout.now_playing, container, false);

        artistView = (TextView) rootView.findViewById(R.id.artistView);
        titleView = (TextView) rootView.findViewById(R.id.titleView);
        albumView = (TextView) rootView.findViewById(R.id.albumView);
        seriesView = (TextView) rootView.findViewById(R.id.seriesView);
        genreView = (TextView) rootView.findViewById(R.id.genreView);

        albumArtView = (ImageView) rootView.findViewById(R.id.albumArtView);
        ratingTextView = (TextView) rootView.findViewById(R.id.ratingTextView);

        getActivity().bindService(new Intent(getActivity(), AnfoService.class), this, Context.BIND_AUTO_CREATE);

        return rootView;
    }

    @Override
    public void onDestroyView() {
        if (anfo != null)
            anfo.removeOnSongChangeListener(this);

        getActivity().unbindService(this);
        super.onDestroyView();
    }

    void updateSong(final SongInfo s, final long songEndTime) {
        artistView.setText(s.getArtist());
        titleView.setText(s.getTitle());
        albumView.setText(s.getAlbum() + " (" + s.getAlbumType() + ")");
        seriesView.setText(s.getSeries());
        genreView.setText(s.getGenre());

        if (s.getArtBmp() != null)
            albumArtView.setImageBitmap(s.getArtBmp());
        else
            albumArtView.setImageResource(R.drawable.image_not_found);

        ratingTextView.setText("Rating: " + s.getRating() + ".\n" + s.getFavourites() + " users have added this song to their favourites.");
    }

    @Override
    public void onFetchingStarted() {

    }

    @Override
    public void onSongChanged(final SongInfo s, final long songEndTime, int songPosTime, String songPosTimeStr, double nowPlayingPos) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updateSong(s, songEndTime);
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
                artistView.setText(getResources().getText(R.string.unknown));
                titleView.setText(getResources().getText(R.string.unknown));
                albumView.setText(getResources().getText(R.string.unknown));
                seriesView.setText(getResources().getText(R.string.unknown));
                genreView.setText(getResources().getText(R.string.unknown));

                albumArtView.setImageResource(R.drawable.image_not_found);

                ratingTextView.setText("");

            }
        });
    }

    @Override
    public void onAlbumArtLoaded(final Bitmap artBmp, final Bitmap miniArtBmp) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (artBmp != null)
                    albumArtView.setImageBitmap(artBmp);
                else
                    albumArtView.setImageResource(R.drawable.image_not_found);

            }
        });
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
