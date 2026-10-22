package com.gathin.tvhome;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Today's five prayer times for one JAKIM zone (e.g. TRG01), from JAKIM's e-Solat service.
 *
 * The month's timetable is fetched once and kept in the launcher's files folder, so the network is
 * used about once a month; a failed fetch is retried at most every 30 minutes. Parsing is plain
 * regular expressions (no org.json), so it runs in the JVM unit tests too.
 */
final class PrayerTimes {
    private static final String TAG = "TvHome.Prayer";
    private static final String URL_FMT =
            "https://www.e-solat.gov.my/index.php?r=esolatApi/TakwimSolat&period=month&zone=%s";
    /** JAKIM's keys, in the order shown on Home. */
    static final String[] KEYS = {"fajr", "dhuhr", "asr", "maghrib", "isha"};
    static final String[] LABELS = {"Subuh", "Zohor", "Asar", "Maghrib", "Isyak"};
    /** JAKIM writes dates as 22-Okt-2026, with Malay month abbreviations. */
    private static final String[] MONTHS = {"jan", "feb", "mac", "apr", "mei", "jun", "jul", "ogo", "sep", "okt", "nov", "dis"};
    private static final Pattern DATE = Pattern.compile("\"date\"\\s*:\\s*\"(\\d{1,2})-([A-Za-z]{3})-(\\d{4})\"");
    private static final long RETRY_MS = 30 * 60_000L;

    private static long lastAttempt;

    private PrayerTimes() {
    }

    /** Minutes after midnight for the five prayers on that day, or null when the timetable lacks it. */
    static int[] parseDay(String json, int year, int month1, int day) {
        if (json == null) return null;
        // Each day is one {...} object; look at them one by one.
        for (String obj : json.split("\\{")) {
            Matcher d = DATE.matcher(obj);
            if (!d.find()) continue;
            if (Integer.parseInt(d.group(1)) != day || Integer.parseInt(d.group(3)) != year) continue;
            if (monthOf(d.group(2)) != month1) continue;
            int[] out = new int[KEYS.length];
            for (int i = 0; i < KEYS.length; i++) {
                Matcher t = Pattern.compile("\"" + KEYS[i] + "\"\\s*:\\s*\"(\\d{1,2}):(\\d{2})").matcher(obj);
                if (!t.find()) return null;
                out[i] = Integer.parseInt(t.group(1)) * 60 + Integer.parseInt(t.group(2));
            }
            return out;
        }
        return null;
    }

    /** 1 to 12 for a Malay (or English) month abbreviation, 0 when unknown. */
    static int monthOf(String abbr) {
        String a = abbr.toLowerCase(java.util.Locale.ROOT);
        for (int i = 0; i < MONTHS.length; i++) if (MONTHS[i].equals(a)) return i + 1;
        String[] en = {"jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec"};
        for (int i = 0; i < en.length; i++) if (en[i].equals(a)) return i + 1;
        return 0;
    }

    /** The prayer still to come today; after Isyak, tomorrow's Subuh (index 0). */
    static int nextIndex(int[] mins, int nowMin) {
        for (int i = 0; i < mins.length; i++) if (nowMin < mins[i]) return i;
        return 0;
    }

    /** Today's times from the cached month, or null when the cache does not have today. */
    static int[] today(File dir, String zone) {
        Calendar c = Calendar.getInstance();
        return parseDay(read(cacheFile(dir, zone)),
                c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    /**
     * Fetches this month's timetable when the cache lacks today. Call off the main thread.
     * Returns true when a new timetable was saved.
     */
    static boolean refreshIfNeeded(File dir, String zone) {
        if (dir == null || zone.isEmpty() || today(dir, zone) != null) return false;
        long now = System.currentTimeMillis();
        synchronized (PrayerTimes.class) {
            if (now - lastAttempt < RETRY_MS) return false;
            lastAttempt = now;
        }
        try {
            String json = fetch(String.format(java.util.Locale.ROOT, URL_FMT, zone));
            Calendar c = Calendar.getInstance();
            if (parseDay(json, c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH)) == null) {
                Log.w(TAG, "timetable for " + zone + " has no entry for today");
                return false;
            }
            File f = cacheFile(dir, zone);
            File tmp = new File(f.getPath() + ".tmp");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(json.getBytes(StandardCharsets.UTF_8));
            }
            return tmp.renameTo(f);
        } catch (Exception e) {
            Log.w(TAG, "fetch failed: " + e);
            return false;
        }
    }

    private static File cacheFile(File dir, String zone) {
        return new File(dir, "prayer-" + zone.replaceAll("[^A-Za-z0-9]", "") + ".json");
    }

    private static String read(File f) {
        if (f == null || !f.isFile() || f.length() > 256 * 1024) return null;
        try (InputStream in = new FileInputStream(f)) {
            return new String(readAll(in), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    private static String fetch(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; TV) GlassTV");
        try {
            if (c.getResponseCode() != 200) throw new java.io.IOException("HTTP " + c.getResponseCode());
            try (InputStream in = c.getInputStream()) {
                return new String(readAll(in), StandardCharsets.UTF_8);
            }
        } finally {
            c.disconnect();
        }
    }

    private static byte[] readAll(InputStream in) throws java.io.IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
            if (out.size() > 256 * 1024) throw new java.io.IOException("too large");
        }
        return out.toByteArray();
    }
}
