package org.aankor.animenforadio;

import android.app.Fragment;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import org.aankor.animenforadio.api.RadioState;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

/**
 *
 */
public class NowPlaying extends Fragment {

    private boolean isPlaying = false;
    private RadioState radioState = new RadioState();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        final View rootView = inflater.inflate(R.layout.now_playing, container, false);
        final ImageButton playStopButton = (ImageButton) rootView.findViewById(R.id.playStopButton);
        final TextView artistView = (TextView) rootView.findViewById(R.id.artistView);
        final TextView titleView = (TextView) rootView.findViewById(R.id.titleView);
        final TextView albumView = (TextView) rootView.findViewById(R.id.albumView);
        final TextView albumTypeView = (TextView) rootView.findViewById(R.id.albumTypeView);
        final TextView seriesView = (TextView) rootView.findViewById(R.id.seriesView);
        final TextView genreView = (TextView) rootView.findViewById(R.id.genreView);

        final ImageView albumArtView = (ImageView) rootView.findViewById(R.id.albumArtView);

        playStopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                isPlaying = !isPlaying;
                playStopButton.setBackgroundResource(isPlaying? R.drawable.player_stop: R.drawable.player_play);
                if (isPlaying) {
                    getActivity().startService(new Intent(getActivity(), RadioService.class));
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            radioState.fetch();
                            try {
                                URL url = new URL(radioState.currentSong.getArtUrl());
                                final Bitmap bmp = BitmapFactory.decodeStream(url.openConnection().getInputStream());

                                getActivity().runOnUiThread(
                                        new Runnable() {
                                            @Override
                                            public void run() {
                                                if (radioState.currentSong == null)
                                                    return;

                                                artistView.setText(radioState.currentSong.getArtist());
                                                titleView.setText(radioState.currentSong.getTitle());
                                                albumView.setText(radioState.currentSong.getAlbum());
                                                albumTypeView.setText(radioState.currentSong.getAlbumType());
                                                seriesView.setText(radioState.currentSong.getSeries());
                                                genreView.setText(radioState.currentSong.getGenre());

                                                albumArtView.setImageBitmap(bmp);
                                            }
                                        });

                            } catch (MalformedURLException e) {
                                e.printStackTrace();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }

                        }
                    }).start();
                }
                else {
                    getActivity().stopService(new Intent(getActivity(), RadioService.class));
                    RadioNotification.cancel(getActivity());
                }
            }
        });
        return rootView;
    }
}
