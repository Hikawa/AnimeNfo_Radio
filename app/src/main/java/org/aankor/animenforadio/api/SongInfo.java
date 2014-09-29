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

package org.aankor.animenforadio.api;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

public class SongInfo {
    private static int miniArtSize = 0;
    private String artist = "";
    private String title = "";
    private String album = "";
    private String albumType = "";
    private String series = "";
    private String genre = "";
    private int duration = 0;
    private String durationStr = "";
    private String rating = "";
    private int favourites = 0;
    private int songId;
    private String artUrl = "";
    private Bitmap artBmp = null;
    private Bitmap miniArtBmp = null;

    public SongInfo(String artist, String title, String album, String albumType, String series, String genre) {
        this.artist = artist;
        this.title = title;
        this.album = album;
        this.albumType = albumType;
        this.series = series;
        this.genre = genre;
    }

    private static int getMiniArtSize(Context context) {
        if (miniArtSize == 0) {
            DisplayMetrics metrics = new DisplayMetrics();
            WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            wm.getDefaultDisplay().getMetrics(metrics);
            miniArtSize = (int) (64 * metrics.density);
        }
        return miniArtSize;
    }

    public String getArtist() {
        return artist;
    }

    public String getTitle() {
        return title;
    }

    public String getAlbum() {
        return album;
    }

    public String getAlbumType() {
        return albumType;
    }

    public String getSeries() {
        return series;
    }

    public String getGenre() {
        return genre;
    }

    public String getArtUrl() {
        return artUrl;
    }

    public void setArtUrl(String artUrl) {
        this.artUrl = artUrl;
    }

    public Bitmap getArtBmp() {
        return artBmp;
    }

    public void setArtBmp(Bitmap artBmp, Bitmap miniArtBmp) {
        this.artBmp = artBmp;
        this.miniArtBmp = miniArtBmp;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    public String getDurationStr() {
        return durationStr;
    }

    public void setDurationStr(String durationStr) {
        this.durationStr = durationStr;
    }

    public String getRating() {
        return rating;
    }

    public void setRating(String rating) {
        this.rating = rating;
    }

    public void unsetRating() {
        this.rating = "N/A";
    }

    public int getFavourites() {
        return favourites;
    }

    public void setFavourites(int favourites) {
        this.favourites = favourites;
    }

    public int getSongId() {
        return songId;
    }

    public void setSongId(int songId) {
        this.songId = songId;
    }

    public Bitmap getMiniArtBmp() {
        return miniArtBmp;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof SongInfo))
            return false;
        SongInfo other = (SongInfo) o;
        return other != null
                && other.artist.equals(artist)
                && other.title.equals(title)
                && other.album.equals(album)
                && other.albumType.equals(albumType)
                && other.series.equals(series)
                && other.genre.equals(genre)
                && other.artUrl.equals(artUrl);
    }

    public void fetchAlbumArt(Context context) {
        try {
            if (artUrl.equals(""))
                return;
            URL url = new URL(artUrl.replace(" ", "%20"));
            artBmp = BitmapFactory.decodeStream(url.openConnection().getInputStream());
            if (artBmp != null)
                miniArtBmp = Bitmap.createScaledBitmap(artBmp, getMiniArtSize(context), getMiniArtSize(context), false);
            else
                miniArtBmp = null;
        } catch (MalformedURLException e) {
        } catch (IOException e) {
        } // FileNotFoundException
    }

    public void unsetArtUrl() {
        artUrl = "";
        artBmp = null;
    }
}
