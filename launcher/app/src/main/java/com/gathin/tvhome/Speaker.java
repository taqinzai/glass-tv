package com.gathin.tvhome;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.Locale;

/**
 * Says the name of whatever the remote moves to (speak_names), for someone who cannot see the
 * screen well. Lighter than TalkBack: only the app or button name, in the language of
 * speak_language (English by default; "ms" for Malay), else the TV's own. Waits a moment after each move, so scrolling past
 * five tiles says only the one it stops on.
 */
final class Speaker {
    private static final String TAG = "TvHome.Speak";
    private static final long SETTLE_MS = 180;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextToSpeech tts;
    private boolean ready;
    private String pending;
    private String last;
    private final Runnable say = () -> {
        if (!ready || pending == null) return;
        tts.speak(pending, TextToSpeech.QUEUE_FLUSH, null, "tile");
        last = pending;
        pending = null;
    };

    Speaker(Context ctx, String language) {
        final Locale wanted = language == null || language.isEmpty() ? Locale.ENGLISH : Locale.forLanguageTag(language);
        tts = new TextToSpeech(ctx.getApplicationContext(), status -> {
            if (status != TextToSpeech.SUCCESS) {
                Log.w(TAG, "no text-to-speech engine");
                return;
            }
            for (Locale l : new Locale[]{wanted, new Locale(wanted.getLanguage()), Locale.getDefault()}) {
                int r = tts.setLanguage(l);
                if (r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.i(TAG, "voice " + l);
                    break;
                }
            }
            tts.setSpeechRate(0.9f);
            ready = true;
            handler.post(say);
        });
    }

    /** Says the text after the selection has settled; the same text twice in a row is said once. */
    void say(CharSequence text) {
        if (tts == null || text == null) return;
        String s = text.toString().trim();
        if (s.isEmpty()) return;
        pending = s;
        handler.removeCallbacks(say);
        handler.postDelayed(say, SETTLE_MS);
    }

    /** Says it even when it was the last thing said (Home coming back to the same app). */
    void sayAgain(CharSequence text) {
        last = null;
        say(text);
    }

    /** The view's description, else the first text inside it. */
    void sayView(View v) {
        CharSequence d = v.getContentDescription();
        if (d != null && d.length() > 0) {
            say(d);
            return;
        }
        CharSequence t = firstText(v);
        if (t != null) say(t);
    }

    private static CharSequence firstText(View v) {
        if (v instanceof TextView) {
            CharSequence t = ((TextView) v).getText();
            if (t != null && t.toString().trim().length() > 0) return t;
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                CharSequence t = firstText(g.getChildAt(i));
                if (t != null) return t;
            }
        }
        return null;
    }

    void stop() {
        handler.removeCallbacks(say);
        pending = null;
        if (tts != null && ready) tts.stop();
    }

    void shutdown() {
        stop();
        if (tts != null) tts.shutdown();
        tts = null;
        ready = false;
    }
}
