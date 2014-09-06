package org.aankor.animenforadio.api;

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

    private String artUrl = "";

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
}
