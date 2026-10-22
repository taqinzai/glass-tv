package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.util.Log;

/** The azan alarm (and the after-midnight reschedule): opens AzanActivity, then sets the next alarm. */
public class AzanReceiver extends BroadcastReceiver {
    /** An alarm that arrives this late (the TV was asleep) is skipped instead of playing out of time. */
    private static final long LATE_MS = 3 * 60_000L;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (AzanAlarm.ACTION_AZAN.equals(intent.getAction())) {
            int index = intent.getIntExtra(AzanAlarm.EXTRA_INDEX, -1);
            long at = intent.getLongExtra(AzanAlarm.EXTRA_AT, 0);
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            boolean on = pm == null || pm.isInteractive();
            boolean onTime = Math.abs(System.currentTimeMillis() - at) < LATE_MS;
            if (index >= 0 && on && onTime) {
                context.startActivity(new Intent(context, AzanActivity.class)
                        .putExtra(AzanAlarm.EXTRA_INDEX, index)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
            } else {
                Log.i("TvHome.Azan", "skipped: screen " + (on ? "on" : "off") + ", " + (onTime ? "on time" : "late"));
            }
        }
        final PendingResult done = goAsync();
        final Context app = context.getApplicationContext();
        new Thread(() -> {
            try {
                AzanAlarm.scheduleBlocking(app);
            } catch (Exception e) {
                Log.w("TvHome.Azan", "reschedule: " + e);
            } finally {
                done.finish();
            }
        }, "azan-next").start();
    }
}
