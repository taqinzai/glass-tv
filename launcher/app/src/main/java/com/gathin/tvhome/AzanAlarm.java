package com.gathin.tvhome;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Calendar;

/**
 * Plays the azan at each prayer time: one alarm at a time, set for the next prayer from the cached
 * JAKIM month (PrayerTimes). When it rings, AzanReceiver opens AzanActivity and sets the next one.
 * Set again whenever Home shows, at boot, and after each azan, so a changed timetable is picked up.
 */
final class AzanAlarm {
    private static final String TAG = "TvHome.Azan";
    static final String ACTION_AZAN = "com.gathin.tvhome.AZAN";
    static final String ACTION_RESCHEDULE = "com.gathin.tvhome.AZAN_RESCHEDULE";
    static final String EXTRA_INDEX = "index";
    static final String EXTRA_AT = "at";
    /** The downloaded default azan; a file named azan.mp3 (or azan_subuh.mp3) pushed by the person wins. */
    static final String DEFAULT_FILE = "azan-default.audio";
    static final String[] OWN = {"azan.mp3", "azan.ogg", "azan.m4a", "azan.wav"};
    static final String[] OWN_SUBUH = {"azan_subuh.mp3", "azan_subuh.ogg", "azan_subuh.m4a", "azan_subuh.wav"};

    private AzanAlarm() {
    }

    /** Fetches the timetable and audio if needed, then sets the next alarm. Blocking: call off the main thread. */
    static void scheduleBlocking(Context ctx) {
        Config cfg = Config.load(ctx);
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent azan = pending(ctx, ACTION_AZAN, -1, 0);
        PendingIntent again = pending(ctx, ACTION_RESCHEDULE, -1, 0);
        am.cancel(azan);
        am.cancel(again);
        if (!cfg.azan || cfg.prayerZone.isEmpty()) return;
        File dir = Config.dir(ctx);
        if (dir == null) return;
        try {
            PrayerTimes.refreshIfNeeded(dir, cfg.prayerZone);
            ensureAudio(dir, cfg.azanUrl);
        } catch (Exception e) {
            Log.w(TAG, "prepare: " + e);
        }
        long now = System.currentTimeMillis();
        for (int d = 0; d < 2; d++) {
            Calendar day = Calendar.getInstance();
            day.add(Calendar.DAY_OF_MONTH, d);
            int[] t = PrayerTimes.day(dir, cfg.prayerZone, day);
            if (t == null) continue;
            for (int i = 0; i < t.length; i++) {
                Calendar at = (Calendar) day.clone();
                at.set(Calendar.HOUR_OF_DAY, t[i] / 60);
                at.set(Calendar.MINUTE, t[i] % 60);
                at.set(Calendar.SECOND, 0);
                at.set(Calendar.MILLISECOND, 0);
                long when = at.getTimeInMillis();
                if (when <= now + 5000) continue;
                set(am, when, pending(ctx, ACTION_AZAN, i, when));
                Log.i(TAG, "next azan " + PrayerTimes.LABELS[i] + " at " + at.getTime());
                return;
            }
        }
        // Tomorrow is not in this month's timetable (or nothing was fetched): try again after midnight,
        // or in an hour when the fetch failed.
        Calendar retry = Calendar.getInstance();
        if (PrayerTimes.today(dir, cfg.prayerZone) != null) {
            retry.add(Calendar.DAY_OF_MONTH, 1);
            retry.set(Calendar.HOUR_OF_DAY, 0);
            retry.set(Calendar.MINUTE, 5);
        } else {
            retry.add(Calendar.HOUR_OF_DAY, 1);
        }
        set(am, retry.getTimeInMillis(), again);
    }

    /** Off the main thread. */
    static void schedule(Context ctx) {
        final Context app = ctx.getApplicationContext();
        new Thread(() -> {
            try {
                scheduleBlocking(app);
            } catch (Exception e) {
                Log.w(TAG, "schedule: " + e);
            }
        }, "azan-schedule").start();
    }

    private static void set(AlarmManager am, long when, PendingIntent pi) {
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
        }
    }

    private static PendingIntent pending(Context ctx, String action, int index, long at) {
        Intent i = new Intent(ctx, AzanReceiver.class).setAction(action)
                .putExtra(EXTRA_INDEX, index).putExtra(EXTRA_AT, at);
        return PendingIntent.getBroadcast(ctx, action.hashCode(), i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** The sound to play for that prayer: the person's own file first, else the downloaded one, else null. */
    static File audioFor(File dir, int index) {
        if (dir == null) return null;
        if (index == 0) {
            for (String n : OWN_SUBUH) {
                File f = new File(dir, n);
                if (f.isFile() && f.length() > 0) return f;
            }
        }
        for (String n : OWN) {
            File f = new File(dir, n);
            if (f.isFile() && f.length() > 0) return f;
        }
        File f = new File(dir, DEFAULT_FILE);
        return f.isFile() && f.length() > 0 ? f : null;
    }

    /** Downloads the default azan once (and again when azan_url changes). */
    private static void ensureAudio(File dir, String url) throws Exception {
        if (url == null || url.isEmpty()) return;
        File f = new File(dir, DEFAULT_FILE);
        File src = new File(dir, DEFAULT_FILE + ".url");
        String had = src.isFile() ? new String(java.nio.file.Files.readAllBytes(src.toPath()), "UTF-8") : "";
        if (f.isFile() && f.length() > 0 && had.equals(url)) return;
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setRequestProperty("User-Agent", PlaceWallpapers.WIKIMEDIA_UA);
        File tmp = new File(dir, DEFAULT_FILE + ".tmp");
        try {
            if (c.getResponseCode() != 200) throw new java.io.IOException("HTTP " + c.getResponseCode());
            try (InputStream in = c.getInputStream(); FileOutputStream out = new FileOutputStream(tmp)) {
                byte[] buf = new byte[16 * 1024];
                long total = 0;
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    total += n;
                    if (total > 12L * 1024 * 1024) throw new java.io.IOException("too large");
                }
            }
            if (!tmp.renameTo(f)) throw new java.io.IOException("rename");
            try (FileOutputStream out = new FileOutputStream(src)) {
                out.write(url.getBytes("UTF-8"));
            }
        } finally {
            c.disconnect();
            tmp.delete();
        }
    }
}
