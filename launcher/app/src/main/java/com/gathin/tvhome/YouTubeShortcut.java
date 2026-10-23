package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A tile that plays a YouTube channel straight away (youtube_shortcuts): the channel's newest video
 * that is not a rerun, then its other uploads one after another, in the YouTube app. One press
 * instead of finding the channel and a video in YouTube, for someone who cannot see the screen well.
 */
final class YouTubeShortcut {
    private static final String TAG = "TvHome.YtShortcut";
    static final String PREFIX = "youtube:";
    static final String YOUTUBE_TV = "com.google.android.youtube.tv";
    private static final Pattern ENTRY = Pattern.compile(
            "<entry>.*?<yt:videoId>([^<]+)</yt:videoId>.*?<title>([^<]*)</title>", Pattern.DOTALL);

    private YouTubeShortcut() {
    }

    /** The newest video in the channel's feed whose title does not mark it a rerun, else the newest; null if none. */
    static String newestVideo(String feed) {
        if (feed == null) return null;
        Matcher m = ENTRY.matcher(feed);
        String first = null;
        while (m.find()) {
            String id = m.group(1).trim(), title = m.group(2);
            if (first == null) first = id;
            if (!title.toLowerCase(java.util.Locale.ROOT).contains("ulangan")
                    && !title.toLowerCase(java.util.Locale.ROOT).contains("rerun")) return id;
        }
        return first;
    }

    /** Fetches the channel's feed off the main thread, then opens YouTube on the newest video. */
    static void play(Context ctx, String channelId, String label) {
        final Context app = ctx.getApplicationContext();
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            String video = null;
            try {
                video = newestVideo(fetch("https://www.youtube.com/feeds/videos.xml?channel_id=" + Uri.encode(channelId)));
            } catch (Exception e) {
                Log.w(TAG, "feed: " + e);
            }
            final String v = video;
            main.post(() -> open(app, channelId, v, label));
        }, "yt-shortcut").start();
    }

    private static void open(Context ctx, String channelId, String video, String label) {
        // A channel's uploads playlist is its id with UC changed to UU: the videos after this one play on.
        String uploads = channelId.startsWith("UC") ? "UU" + channelId.substring(2) : null;
        Uri uri = video != null
                ? Uri.parse("https://www.youtube.com/watch?v=" + Uri.encode(video) + (uploads != null ? "&list=" + uploads : ""))
                : Uri.parse("https://www.youtube.com/channel/" + Uri.encode(channelId));
        Intent i = new Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            ctx.startActivity(new Intent(i).setPackage(YOUTUBE_TV));
        } catch (Exception e) {
            try {
                ctx.startActivity(i);
            } catch (Exception e2) {
                android.widget.Toast.makeText(ctx, ctx.getString(R.string.cant_open, label), android.widget.Toast.LENGTH_SHORT).show();
            }
        }
    }

    private static String fetch(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        try {
            if (c.getResponseCode() != 200) throw new java.io.IOException("HTTP " + c.getResponseCode());
            try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    if (out.size() > 1024 * 1024) break;
                }
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally {
            c.disconnect();
        }
    }
}
