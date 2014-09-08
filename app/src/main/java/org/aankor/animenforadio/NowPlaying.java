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
import android.widget.ImageView;
import android.widget.TextView;

import org.aankor.animenforadio.api.SongInfo;

/**
 *
 */
public class NowPlaying extends Fragment implements ServiceConnection {
    private TextView artistView;
    private TextView titleView;
    private TextView albumView;
    private TextView albumTypeView;
    private TextView seriesView;
    private TextView genreView;
    private ImageView albumArtView;
    private TextView ratingTextView;

    private WebsiteService.WebsiteBinder website;

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
            public void onSongChanged(final SongInfo s, final long songEndTime) {
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        artistView.setText(s.getArtist());
                        titleView.setText(s.getTitle());
                        albumView.setText(s.getAlbum() + " (" + s.getAlbumType() + ")");
                        seriesView.setText(s.getSeries());
                        genreView.setText(s.getGenre());

                        if (s.getArtBmp() != null)
                            albumArtView.setImageBitmap(s.getArtBmp());
                        else
                            albumArtView.setImageResource(R.drawable.example_picture);

                        ratingTextView.setText("Rating: " + s.getRating() + ".\n" + s.getFavourites() + " users have added this song to their favourites.");
                    }
                });
            }

            @Override
            public void onSongUnknown() {

            }
        });
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {
        website = null;
    }
}
