package com.gathin.tvhome;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.util.Calendar;

/**
 * The azan, over whatever is on screen: the prayer's name in Arabic and Malay, its time, and one
 * button to stop it. Whatever else is playing pauses while it plays (transient audio focus). Any
 * key that leaves (Back, Home) stops it too; it closes by itself a few seconds after the azan ends.
 */
public class AzanActivity extends Activity {
    private static final String TAG = "TvHome.Azan";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaPlayer player;
    private AudioManager audio;
    private AudioFocusRequest focus;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        int index = Math.max(0, Math.min(4, getIntent().getIntExtra(AzanAlarm.EXTRA_INDEX, 0)));
        Config cfg = Config.load(this);
        File dir = Config.dir(this);
        float d = getResources().getDisplayMetrics().density;

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding((int) (64 * d), (int) (40 * d), (int) (64 * d), (int) (36 * d));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xE6101418);
        bg.setCornerRadius(28 * d);
        bg.setStroke((int) Math.max(1, d), 0x40FFD54F);
        card.setBackground(bg);

        card.addView(text(PrayerTimes.LABELS_AR[index], 72, 0xFFFFD54F, true));
        card.addView(text("Waktu " + PrayerTimes.LABELS[index], 30, 0xFFFFFFFF, false), margins(8 * d));
        int[] t = dir != null ? PrayerTimes.today(dir, cfg.prayerZone) : null;
        if (t != null) {
            boolean h24 = android.text.format.DateFormat.is24HourFormat(this);
            card.addView(text(PrayerTimes.format(t[index], h24, cfg.prayerArabic), 40, 0xE6FFFFFF, false), margins(6 * d));
        }

        TextView stop = text("■  Hentikan Azan", 24, 0xFF101418, true);
        stop.setPadding((int) (36 * d), (int) (14 * d), (int) (36 * d), (int) (14 * d));
        GradientDrawable btn = new GradientDrawable();
        btn.setColor(0xFFFFD54F);
        btn.setCornerRadius(40 * d);
        stop.setBackground(btn);
        stop.setFocusable(true);
        stop.setClickable(true);
        stop.setOnClickListener(v -> finish());
        card.addView(stop, margins(32 * d));

        LinearLayout root = new LinearLayout(this);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(0xB3000000);
        root.addView(card);
        setContentView(root);
        stop.requestFocus();

        play(dir, index);
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        v.setTextColor(color);
        v.setGravity(Gravity.CENTER);
        v.setIncludeFontPadding(true);
        if (bold) v.setTypeface(Typeface.DEFAULT_BOLD);
        return v;
    }

    private static LinearLayout.LayoutParams margins(float top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = (int) top;
        return lp;
    }

    private void play(File dir, int index) {
        File f = AzanAlarm.audioFor(dir, index);
        if (f == null) {
            Log.w(TAG, "no azan audio yet");
            handler.postDelayed(this::finish, 60_000);
            return;
        }
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
        audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (audio != null) {
            focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(attrs)
                    .setOnAudioFocusChangeListener(change -> { })
                    .build();
            audio.requestAudioFocus(focus);
        }
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(attrs);
            player.setDataSource(f.getPath());
            player.setOnCompletionListener(mp -> handler.postDelayed(this::finish, 4000));
            player.setOnErrorListener((mp, what, extra) -> {
                Log.w(TAG, "player error " + what + "/" + extra);
                finish();
                return true;
            });
            player.prepare();
            player.start();
        } catch (Exception e) {
            Log.w(TAG, "play: " + e);
            finish();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_MEDIA_STOP
                || keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
            finish();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Home, another app, or the TV going to sleep: the azan stops, as dismissing it on a phone does.
        if (!isFinishing()) finish();
        release();
    }

    @Override
    protected void onDestroy() {
        release();
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void release() {
        if (player != null) {
            try {
                player.stop();
            } catch (Exception ignored) {
            }
            player.release();
            player = null;
        }
        if (audio != null && focus != null) {
            audio.abandonAudioFocusRequest(focus);
            focus = null;
        }
    }
}
