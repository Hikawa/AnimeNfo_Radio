package org.aankor.animenforadio.api;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;

/**
 * Created by aankor on 04.09.14.
 */
public class RadioState {
    public SongInfo currentSong;
    private String phpSessID = "";
    private Pattern nowPlayingPattern1 = Pattern.compile("^Artist: (.*) Title: (.*) Album: (.*) Album Type: (.*) Series: (.*) Genre\\(s\\): (.*)$");

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

    public void fetch() {
        try {
            fetchCookies();
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

            JSONObject res = new JSONObject(response.toString());
            String nowPlaying = res.getString("nowplaying");

            Document doc = Jsoup.parse(nowPlaying);

            Matcher matcher = nowPlayingPattern1.matcher(doc.select("div .float-container .row .span6").first().text());
            if (matcher.find()) {
                Log.i("regexp", "found");
                currentSong = new SongInfo(
                        matcher.group(1),
                        matcher.group(2),
                        matcher.group(3),
                        matcher.group(4),
                        matcher.group(5),
                        matcher.group(6)
                );
            }

            currentSong.setArtUrl(doc.select("div img").first().attr("src"));
        } catch (IOException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        // Document doc = Jsoup.connect(url).data()
    }

}

