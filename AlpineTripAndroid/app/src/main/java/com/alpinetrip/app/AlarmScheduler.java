package com.alpinetrip.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.time.ZonedDateTime;

public final class AlarmScheduler {
    private static final int[] HOURS = {7, 11, 15};
    private AlarmScheduler() {}

    public static void scheduleTripChecks(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        long now = System.currentTimeMillis();
        for (int day = 0; day < TripData.DAYS.size(); day++) {
            for (int slot = 0; slot < HOURS.length; slot++) {
                ZonedDateTime zdt = TripData.DAYS.get(day).date.atTime(HOURS[slot],0).atZone(TripData.ZURICH);
                long trigger = zdt.toInstant().toEpochMilli();
                if (trigger <= now) continue;
                Intent i = new Intent(context, AlarmReceiver.class);
                i.putExtra("dayIndex", day);
                i.putExtra("slotHour", HOURS[slot]);
                int requestCode = 1000 + day * 10 + slot;
                PendingIntent pi = PendingIntent.getBroadcast(context, requestCode, i,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi);
                    } else {
                        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi);
                    }
                } catch (SecurityException ex) {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi);
                }
            }
        }
    }
}
