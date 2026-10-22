package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Alarms do not survive a restart: set the next azan again once the TV has booted. */
public class AzanBootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String a = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(a) && !"android.intent.action.QUICKBOOT_POWERON".equals(a)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)) return;
        final PendingResult done = goAsync();
        final Context app = context.getApplicationContext();
        new Thread(() -> {
            try {
                AzanAlarm.scheduleBlocking(app);
            } catch (Exception ignored) {
            } finally {
                done.finish();
            }
        }, "azan-boot").start();
    }
}
