package org.aankor.animenforadio;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import org.aankor.animenforadio.api.SongInfo;
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
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;

public class WebsiteService extends Service {
    private final Pattern mainNowPlayingPattern = Pattern.compile("^Artist: (.*) Title: (.*) Album: (.*) Album Type: (.*) Series: (.*) Genre\\(s\\): (.*)$");
    private final Pattern raitingNowPlayingPattern = Pattern.compile("Rating: (.*)\n");
    private final Pattern nowPlayingBarPattern = Pattern.compile("^left: ([\\d\\.]*)%$");
    SongInfo currentSong = null;
    long currentSongStartTime = 0;
    Thread worker = null;
    Handler handler = null;
    EnumSet<FetchPiece> subscripedPieces = EnumSet.noneOf(FetchPiece.class);
    private ArrayList<OnSongChangeListener> onSongChangeListeners = new ArrayList<OnSongChangeListener>();
    private String phpSessID = "";

    public WebsiteService() {
    }

    @Override
    public void onCreate() {
        worker = new Thread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare();
                handler = new Handler();
                Looper.loop();
            }
        });
        worker.start();
    }

    @Override
    public void onDestroy() {
        handler.getLooper().quit();
        worker = null;
        handler = null;
    }

    private void fetchCookies() throws IOException {
        if (phpSessID.isEmpty()) {
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

    // returns: song changed
    private void fetch(EnumSet<FetchPiece> pieces) {
        if (pieces.isEmpty())
            return;
        addSubscription(pieces);
        notifyFetchStarted(pieces);
        long currentTime = (new Date()).getTime();
        try {
            fetchCookies();

            // TODO: fetch peice by piece
            URL url = new URL("https://www.animenfo.com/radio/index.php?t=" + currentTime);
            // URL url = new URL("http://192.168.0.2:12345/");
            String body = "{\"ajaxcombine\":true,\"pages\":[{\"uid\":\"nowplaying\",\"page\":\"nowplaying.php\",\"args\":{\"mod\":\"playing\"}}" +
                    ",{\"uid\":\"queue\",\"page\":\"nowplaying.php\",\"args\":{\"mod\":\"queue\"}},{\"uid\":\"recent\",\"page\":\"nowplaying.php\",\"args\":{\"mod\":\"recent\"}}]}";
            HttpsURLConnection con = (HttpsURLConnection) url.openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("Accept", "application/json");
            con.setRequestProperty("Accept-Encoding", "gzip, deflate");
            con.setRequestProperty("Content-Type", "application/json");
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

            updateCookies(con);

            JSONObject res = new JSONObject(response.toString());
            String nowPlaying = res.getString("nowplaying");

            Document doc = Jsoup.parse(nowPlaying);

            Elements spans = doc.select("div .float-container .row .span6");

            Matcher matcher = mainNowPlayingPattern.matcher(spans.first().text());

            if (!matcher.find()) {
                notifySongUnknown();
                return; // wtf
            }
            SongInfo newSongInfo = new SongInfo(
                    matcher.group(1),
                    matcher.group(2),
                    matcher.group(3),
                    matcher.group(4),
                    matcher.group(5),
                    matcher.group(6)
            );

            String artUrl = doc.select("div img").attr("src");

            newSongInfo.setArtUrl(artUrl);

            long songPosTime = Long.valueOf(spans.get(1).select("#np_timer").attr("rel"));
            currentSongStartTime = currentTime - songPosTime;
            String songPosTimeStr = spans.get(1).select("#np_timer").text();
            newSongInfo.setDuration(Integer.valueOf(spans.get(1).select("#np_time").attr("rel")));
            newSongInfo.setDurationStr(spans.get(1).select("#np_time").text());

            matcher = raitingNowPlayingPattern.matcher(spans.get(1).html());

            if (matcher.find())
                newSongInfo.setRating(matcher.group(1));
            else
                newSongInfo.unsetRating();

            newSongInfo.setFavourites(Integer.valueOf(spans.get(1).select(".favourite-container span[data-favourite-count]").attr("data-favourite-count")));

            newSongInfo.setSongId(Integer.valueOf(spans.get(1).select("a[data-songinfo]").attr("data-songinfo")));

            matcher = nowPlayingBarPattern.matcher(doc.select("#nowPlayingBar").attr("style"));
            double nowPlayingPos = 0.0;
            if (matcher.find())
                nowPlayingPos = Double.valueOf(matcher.group(1));

            if (!newSongInfo.equals(currentSong)) {
                currentSong = newSongInfo;
                notifySongChanged(songPosTime, songPosTimeStr, nowPlayingPos);
            } else {
                notifySongRemained(songPosTime, songPosTimeStr, nowPlayingPos);
            }
            return;
        } catch (IOException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        } finally {
            currentTime = (new Date()).getTime();
            if (subscripedPieces.contains(FetchPiece.CURRENT_SONG) &&
                    (currentSong != null) && (currentSong.getDuration() > 0))
                handler.postAtTime(new Runnable() {
                    @Override
                    public void run() {
                        fetch(subscripedPieces); // Join requests for all pieces because this is slow request
                    }
                }, Math.max(currentTime + 500, Math.min(currentTime + 180000, currentSongStartTime + currentSong.getDuration() + 30)));
        }
        notifySongUnknown();
    }

    private void addSubscription(EnumSet<FetchPiece> pieces) {
        subscripedPieces.addAll(pieces);
    }

    private void updateSubscription() {
        subscripedPieces = EnumSet.noneOf(FetchPiece.class);
        if (!onSongChangeListeners.isEmpty())
            subscripedPieces.add(FetchPiece.CURRENT_SONG);
    }

    private void notifySongRemained(long songPosTime, String songPosTimeStr, double nowPlayingPos) {
        for (OnSongChangeListener l : onSongChangeListeners) {
            l.onSongRemained();
            l.onSongTimingRequested(songPosTime, songPosTimeStr, nowPlayingPos);
        }
    }

    private void notifySongChanged(long songPosTime, String songPosTimeStr, double nowPlayingPos) {
        for (OnSongChangeListener l : onSongChangeListeners) {
            l.onSongChanged(currentSong, currentSongStartTime);
            l.onSongTimingRequested(songPosTime, songPosTimeStr, nowPlayingPos);
        }
    }

    private void notifySongUnknown() {
        for (OnSongChangeListener l : onSongChangeListeners) {
            l.onSongUnknown();
        }
    }

    private void notifyFetchStarted(EnumSet<FetchPiece> pieces) {
        for (FetchPiece p : pieces) {
            switch (p) {
                case CURRENT_SONG:
                    for (OnSongChangeListener l : onSongChangeListeners)
                        l.onFetchingStarted();
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new WebsiteBinder();
    }

    enum FetchPiece {
        CURRENT_SONG,
        QUEUE
    }

    public interface OnSongChangeListener {
        void onFetchingStarted();

        void onSongChanged(SongInfo s, long songStartTime);

        void onSongRemained();

        void onSongUnknown();

        void onSongTimingRequested(long songPosTime, String songPosTimeStr, double nowPlayingPos);
    }

    public class WebsiteBinder extends Binder {
        public void addOnSongChangeListener(final OnSongChangeListener l) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    boolean first = onSongChangeListeners.isEmpty();
                    onSongChangeListeners.add(l);
                    if (first) {
                        fetch(EnumSet.of(FetchPiece.CURRENT_SONG));
                    } else
                        l.onSongChanged(currentSong, currentSongStartTime);
                }
            });
        }

        public void removeOnSongChangeListener(final OnSongChangeListener l) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    onSongChangeListeners.remove(l);
                    updateSubscription();
                }
            });
        }
    }
}
