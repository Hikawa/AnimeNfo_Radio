package org.aankor.animenforadio.api;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Created by aankor on 04.09.14.
 */
public class SongInfo {
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

    public SongInfo(String artist, String title, String album, String albumType, String series, String genre) {
        this.artist = artist;
        this.title = title;
        this.album = album;
        this.albumType = albumType;
        this.series = series;
        this.genre = genre;
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

    public void setArtBmp(Bitmap artBmp) {
        this.artBmp = artBmp;
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

    @Override
    public boolean equals(Object o) {
        SongInfo other = (SongInfo) o;
        if (other == null)
            return false;
        return other.artist.equals(artist) &&
                other.title.equals(title) &&
                other.album.equals(album) &&
                other.albumType.equals(albumType) &&
                other.series.equals(series) &&
                other.genre.equals(genre) &&
                other.artUrl.equals(artUrl);
    }

    public void fetchAlbumArt() {
        try {
            if (artUrl.isEmpty())
                return;
            URL url = new URL(artUrl);
            artBmp = BitmapFactory.decodeStream(url.openConnection().getInputStream());
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void unsetArtUrl() {
        artUrl = "";
        artBmp = null;
    }
}
