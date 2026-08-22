package com.alpinetrip.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        final PendingResult pending = goAsync();
        final int dayIndex = intent.getIntExtra("dayIndex", 0);
        final PowerManager.WakeLock lock = ((PowerManager)context.getSystemService(Context.POWER_SERVICE))
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AlpineTrip:RouteCheck");
        lock.acquire(45_000L);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                MonitorEngine.Result r = MonitorEngine.check(context.getApplicationContext(), dayIndex);
                NotificationHelper.notify(context.getApplicationContext(), r);
            } finally {
                if (lock.isHeld()) lock.release();
                pending.finish();
                executor.shutdown();
            }
        });
    }
}
