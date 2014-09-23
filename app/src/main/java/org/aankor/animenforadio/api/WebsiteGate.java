package org.aankor.animenforadio.api;

import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Date;
import java.util.EnumSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;

/**
 * Created by aankor on 11.09.14.
 */
public class WebsiteGate {
    private final Pattern mainNowPlayingPattern = Pattern.compile("^Artist: (.*) Title: (.*) Album: (.*) Album Type: (.*) Series: (.*) Genre\\(s\\): (.*)$");
    private final Pattern raitingNowPlayingPattern = Pattern.compile("Rating: (.*)\n");
    private final Pattern nowPlayingBarPattern = Pattern.compile("^left: ([\\d\\.]*)%$");
    private String phpSessID = "";
    private SongInfo currentSong = null;
    private long currentSongEndTime = 0;
    private SongPos currentSongPos = null;

    public SongInfo getCurrentSong() {
        return currentSong;
    }

    public long getCurrentSongEndTime() {
        return currentSongEndTime;
    }

    public int getCurrentSongPosTime() {
        return currentSongPos.time;
    }

    public String getCurrentSongPosTimeStr() {
        return currentSongPos.timeStr;
    }

    public double getCurrentSongPosPercent() {
        return currentSongPos.percent;
    }

    private void fetchCookies() throws IOException {
        if (phpSessID.equals("")) {
            URL url = new URL("https://www.animenfo.com/radio/index.php");
            HttpsURLConnection con = (HttpsURLConnection) url.openConnection();
            con.setRequestMethod("GET");
            con.connect();
            updateCookies(con);
        }
    }

    private void updateCookies(HttpsURLConnection con) {
        Pattern titlePattern = Pattern.compile("PHPSESSID=([^;]*);");
        Matcher matcher = titlePattern.matcher(con.getHeaderField("Set-Cookie"));
        if (matcher.find())
            phpSessID = matcher.group(1);
    }

    // return currentSongPos updated
    private boolean updateNowPlaying(String nowPlaying) {
        Document doc = Jsoup.parse(nowPlaying);

        Elements spans = doc.select("div .float-container .row .span6");

        Matcher matcher = mainNowPlayingPattern.matcher(spans.first().text());

        if (!matcher.find()) {
            currentSong = null;
            return false;
        }
        SongInfo newSongInfo = new SongInfo(
                matcher.group(1),
                matcher.group(2),
                matcher.group(3),
                matcher.group(4),
                matcher.group(5),
                matcher.group(6)
        );

        Elements e = doc.select("div img");
        if (!e.isEmpty()) {
            String artUrl = e.attr("src");
            newSongInfo.setArtUrl(artUrl);
        } else
            newSongInfo.unsetArtUrl();

        newSongInfo.setSongId(Integer.valueOf(spans.get(1).select("a[data-songinfo]").attr("data-songinfo")));

        int songPosTime = Integer.valueOf(spans.get(1).select("#np_timer").attr("rel"));
        long currentTime = (new Date()).getTime();
        currentSongEndTime = currentTime + songPosTime * 1000l;
        String songPosTimeStr = spans.get(1).select("#np_timer").text();
        newSongInfo.setDuration(Integer.valueOf(spans.get(1).select("#np_time").attr("rel")));
        newSongInfo.setDurationStr(spans.get(1).select("#np_time").text());

        matcher = raitingNowPlayingPattern.matcher(spans.get(1).html());

        if (matcher.find())
            newSongInfo.setRating(matcher.group(1));
        else
            newSongInfo.unsetRating();

        newSongInfo.setFavourites(Integer.valueOf(spans.get(1).select(".favourite-container span[data-favourite-count]").attr("data-favourite-count")));

        matcher = nowPlayingBarPattern.matcher(doc.select("#nowPlayingBar").attr("style"));
        double nowPlayingPos = 0.0;
        if (matcher.find())
            nowPlayingPos = Double.valueOf(matcher.group(1));

        if ((currentSong == null) || !newSongInfo.getArtUrl().equals(currentSong.getArtUrl()))
            newSongInfo.fetchAlbumArt(); // don't refresh image if song remains the same
        else
            newSongInfo.setArtBmp(currentSong.getArtBmp());
        currentSong = newSongInfo;
        currentSongPos = new SongPos(songPosTime, songPosTimeStr, nowPlayingPos);
        return true;
    }

    private JSONObject request(EnumSet<Subscription> subscriptions) throws IOException, JSONException {
        // fetchCookies();

        // TODO: fetch peice by piece
        URL url = new URL("https://www.animenfo.com/radio/index.php?t=" + (new Date()).getTime());
        // URL url = new URL("http://192.168.0.2:12345/");
        String body = "{\"ajaxcombine\":true,\"pages\":[{\"uid\":\"nowplaying\",\"page\":\"nowplaying.php\",\"args\":{\"mod\":\"playing\"}}" +
                ",{\"uid\":\"queue\",\"page\":\"nowplaying.php\",\"args\":{\"mod\":\"queue\"}},{\"uid\":\"recent\",\"page\":\"nowplaying.php\",\"args\":{\"mod\":\"recent\"}}]}";
        HttpsURLConnection con = (HttpsURLConnection) url.openConnection();
        con.setRequestMethod("POST");
        con.setRequestProperty("Accept", "application/json");
        con.setRequestProperty("Accept-Encoding", "gzip, deflate");
        con.setRequestProperty("Content-Type", "application/json");
        if (!phpSessID.equals(""))
            con.setRequestProperty("Cookie", "PHPSESSID=" + phpSessID);
        con.setRequestProperty("Host", "www.animenfo.com");
        con.setRequestProperty("Referer", "https://www.animenfo.com/radio/nowplaying.php");
        con.setRequestProperty("User-Agent", "Mozilla/5.0 (X11; Linux x86_64; rv:31.0) Gecko/20100101 Firefox/31.0 Iceweasel/31.1.0");
        // con.setUseCaches (false);
        con.setDoInput(true);
        con.setDoOutput(true);

        //Send request

        DataOutputStream wr = new DataOutputStream(con.getOutputStream());
        wr.writeBytes(body);
        wr.flush();
        wr.close();
        InputStream is = con.getInputStream();
        BufferedReader rd = new BufferedReader(new InputStreamReader(is));
        String line;
        StringBuilder response = new StringBuilder();
        while ((line = rd.readLine()) != null) {
            response.append(line);
        }
        rd.close();

        // updateCookies(con);
        return new JSONObject(response.toString());

    }

    public void fetch(EnumSet<Subscription> subscriptions) {
        boolean currentSongPosUpdated = false;
        if (!subscriptions.isEmpty()) {
            try {
                JSONObject res = request(subscriptions);
                if (subscriptions.contains(Subscription.CURRENT_SONG))
                    currentSongPosUpdated = updateNowPlaying(res.getString("nowplaying"));
            } catch (Exception e) {
                currentSong = null;
                // e.printStackTrace();
            }
        }

        if (!currentSongPosUpdated) {
            if (currentSong == null)
                currentSongPos = null;
            else
                currentSongPos = new SongPos(currentSongEndTime, currentSong.getDuration());
        }
    }

    public void unsetCurrentSong() {
        currentSong = null;
    }

    public enum Subscription {
        CURRENT_SONG,
        QUEUE
    }

    private static class SongPos {
        public int time;
        public String timeStr;
        public double percent;

        private SongPos(int time, String timeStr, double percent) {
            this.time = time;
            this.timeStr = timeStr;
            this.percent = percent;
            fix();
        }

        private SongPos(long currentSongEndTime, int currentSongDuration) {
            long currentTime = new Date().getTime();
            time = (int) ((currentSongEndTime - currentTime) / 1000);
            final int secs = (time % 60);
            timeStr = (time / 60) + ":" + ((secs < 10) ? "0" : "") + secs;
            percent = (100.0 * (currentSongDuration - time)) / currentSongDuration;
            fix();
        }

        private void fix() {
            if (time < 0) {
                time = 0;
                timeStr = "0:00";
            }
            if (percent < 0)
                percent = 0;
            else if (percent > 100)
                percent = 100;
        }
    }

}
