package org.aankor.animenforadio;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

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
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;

public class WebsiteService extends Service {
    private final Pattern mainNowPlayingPattern = Pattern.compile("^Artist: (.*) Title: (.*) Album: (.*) Album Type: (.*) Series: (.*) Genre\\(s\\): (.*)$");
    private final Pattern raitingNowPlayingPattern = Pattern.compile("Rating: (.*)\n");
    private final Pattern nowPlayingBarPattern = Pattern.compile("^left: ([\\d\\.]*)%$");
    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(1);
    SongInfo currentSong = null;
    long currentSongEndTime = 0;
    boolean fetchingCompletionNotified;
    SongPos currentSongPos = null;
    ScheduledFuture processorHandle = null;
    EnumSet<Subscription> subscripedPieces = EnumSet.noneOf(Subscription.class);
    SortedMap<Subscription, Long> refreshSchedule = new TreeMap<Subscription, Long>();
    private ArrayList<OnSongChangeListener> onSongChangeListeners = new ArrayList<OnSongChangeListener>();
    private ArrayList<OnSongPosChangedListener> onSongPosChangedListeners = new ArrayList<OnSongPosChangedListener>();
    private String phpSessID = "";
    private boolean isActive = false;

    public WebsiteService() {
    }

    public void activateScheduler() {
        if (isActive)
            return;
        isActive = true;
        processorHandle = scheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                process();
            }
        }, 0, 1, TimeUnit.SECONDS);
    }

    private void process() {
        long currentTime = new Date().getTime();
        EnumSet<Subscription> fetchNow = EnumSet.noneOf(Subscription.class);
        for (SortedMap.Entry<Subscription, Long> e : refreshSchedule.entrySet()) {
            long time = e.getValue().longValue();
            if ((time == 0l) || (time < currentTime)) {
                fetchNow.add(e.getKey());
            }
        }
        fetch(fetchNow);
        if (currentSongPos != null)
            for (OnSongPosChangedListener l : onSongPosChangedListeners)
                l.onSongPosChanged(currentSongPos.time, currentSongPos.timeStr, currentSongPos.percent);
    }

    @Override
    public void onDestroy() {
        if (processorHandle != null)
            processorHandle.cancel(false);
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

    // return currentSongPos updated
    private boolean updateNowPlaying(String nowPlaying) {
        Document doc = Jsoup.parse(nowPlaying);

        Elements spans = doc.select("div .float-container .row .span6");

        Matcher matcher = mainNowPlayingPattern.matcher(spans.first().text());

        if (!matcher.find()) {
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
        notifySongChanged(songPosTime, songPosTimeStr, nowPlayingPos);
        currentSongPos = new SongPos(songPosTime, songPosTimeStr, nowPlayingPos);
        currentTime = (new Date()).getTime();
        refreshSchedule.put(Subscription.CURRENT_SONG, Math.max(currentTime + 5000, Math.min(currentSongEndTime + 30, currentTime + 180000)));
        return true;
    }

    private JSONObject request(EnumSet<Subscription> subscriptions) throws IOException, JSONException {
        fetchCookies();

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
        return new JSONObject(response.toString());

    }

    // returns: song changed
    private void fetch(EnumSet<Subscription> subscriptions) {
        boolean currentSongPosUpdated = false;
        if (!subscriptions.isEmpty()) {
            addSubscription(subscriptions);
            notifyFetchStarted(subscriptions);
            try {
                JSONObject res = request(subscriptions);
                if (subscriptions.contains(Subscription.CURRENT_SONG))
                    currentSongPosUpdated = updateNowPlaying(res.getString("nowplaying"));
            } catch (IOException e) {
                e.printStackTrace();
            } catch (JSONException e) {
                e.printStackTrace();
            }

            // remove waiting state if some error
            if (subscriptions.contains(Subscription.CURRENT_SONG) && !fetchingCompletionNotified)
                notifySongUnknown();
        }
        if (!currentSongPosUpdated) {
            if (currentSong == null)
                currentSongPos = null;
            else
                currentSongPos.estimate(currentSongEndTime, currentSong.getDuration());
        }
    }

    private void addSubscription(EnumSet<Subscription> subscriptions) {
        subscripedPieces.addAll(subscriptions);
    }

    private void updateSubscription() {
        subscripedPieces = EnumSet.noneOf(Subscription.class);
        if (!onSongChangeListeners.isEmpty())
            subscripedPieces.add(Subscription.CURRENT_SONG);
    }

    private void notifySongChanged(int songPosTime, String songPosTimeStr, double nowPlayingPos) {
        for (OnSongChangeListener l : onSongChangeListeners) {
            l.onSongChanged(currentSong, currentSongEndTime, songPosTime, songPosTimeStr, nowPlayingPos);
        }
        fetchingCompletionNotified = true;
    }

    private void notifySongUnknown() {
        for (OnSongChangeListener l : onSongChangeListeners) {
            l.onSongUnknown();
        }
        fetchingCompletionNotified = true;
    }

    private void notifyFetchStarted(EnumSet<Subscription> subscriptions) {
        for (Subscription s : subscriptions) {
            switch (s) {
                case CURRENT_SONG:
                    for (OnSongChangeListener l : onSongChangeListeners)
                        l.onFetchingStarted();
            }
        }
        fetchingCompletionNotified = false;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new WebsiteBinder();
    }

    enum Subscription {
        CURRENT_SONG,
        QUEUE
    }

    public interface OnSongChangeListener {
        void onFetchingStarted();

        void onSongChanged(SongInfo s, long songEndTime, int songPosTime, String songPosTimeStr, double nowPlayingPos);

        // void onSongRemained();

        void onSongUnknown();
    }

    public interface OnSongPosChangedListener {
        void onSongPosChanged(int songPosTime, String songPosTimeStr, double nowPlayingPos);
    }

    private static class SongPos {
        public int time;
        public String timeStr;
        public double percent;

        private SongPos(int time, String timeStr, double percent) {
            this.time = time;
            this.timeStr = timeStr;
            this.percent = percent;
        }

        public void estimate(long currentSongEndTime, int currentSongDuration) {
            long currentTime = new Date().getTime();
            time = (int) ((currentSongEndTime - currentTime) / 1000);
            final int secs = (time % 60);
            timeStr = (time / 60) + ":" + ((secs < 10) ? "0" : "") + secs;
            percent = (100.0 * (currentSongDuration - time)) / currentSongDuration;
        }
    }

    public class WebsiteBinder extends Binder {
        public void addOnSongChangeListener(final OnSongChangeListener l) {
            scheduler.execute(new Runnable() {
                @Override
                public void run() {
                    boolean first = onSongChangeListeners.isEmpty();
                    onSongChangeListeners.add(l);
                    if (first) {
                        refreshSchedule.put(Subscription.CURRENT_SONG, 0l); // schedule now
                    } else if (currentSong != null)
                        l.onSongChanged(currentSong, currentSongEndTime, currentSongPos.time, currentSongPos.timeStr, currentSongPos.percent);
                }
            });
            activateScheduler();
        }

        public void removeOnSongChangeListener(final OnSongChangeListener l) {
            scheduler.execute(new Runnable() {
                @Override
                public void run() {
                    onSongChangeListeners.remove(l);
                    updateSubscription();
                }
            });
        }

        public void addOnSongPosChangeListener(final OnSongPosChangedListener l) {
            scheduler.execute(new Runnable() {
                @Override
                public void run() {
                    onSongPosChangedListeners.add(l);
                }
            });
        }

        public void removeOnSongPosChangeListener(final OnSongPosChangedListener l) {
            scheduler.execute(new Runnable() {
                @Override
                public void run() {
                    onSongPosChangedListeners.remove(l);
                }
            });
        }
    }
}
