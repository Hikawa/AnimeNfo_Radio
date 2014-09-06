package org.aankor.animenforadio;

import android.app.Fragment;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.aankor.animenforadio.api.SongInfo;

/**
 *
 */
public class NowPlaying extends Fragment implements RadioPlayer.OnSongChangeListener {
    private TextView artistView;
    private TextView titleView;
    private TextView albumView;
    private TextView albumTypeView;
    private TextView seriesView;
    private TextView genreView;
    private ImageView albumArtView;

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

        return rootView;
    }

    @Override
    public void onSongChange(final SongInfo songInfo, final Bitmap bmp) {
        artistView.setText(songInfo.getArtist());
        titleView.setText(songInfo.getTitle());
        albumView.setText(songInfo.getAlbum() + " (" + songInfo.getAlbumType() + ")");
        seriesView.setText(songInfo.getSeries());
        genreView.setText(songInfo.getGenre());

        albumArtView.setImageBitmap(bmp);
    }
}
