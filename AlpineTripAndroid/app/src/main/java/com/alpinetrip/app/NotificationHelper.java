package com.alpinetrip.app;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

public final class NotificationHelper {
    public static final String CHANNEL = "route_watch";
    private NotificationHelper() {}

    public static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = context.getSystemService(NotificationManager.class);
            NotificationChannel c = new NotificationChannel(CHANNEL, "Alpine Route Watch", NotificationManager.IMPORTANCE_HIGH);
            c.setDescription("Weather and road/pass checks for Alpine Trip 2026");
            nm.createNotificationChannel(c);
        }
    }

    public static void notify(Context context, MonitorEngine.Result r) {
        ensureChannel(context);
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;

        Intent open = new Intent(context, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(context, 2001, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(context, CHANNEL)
                : new Notification.Builder(context);
        b.setSmallIcon(android.R.drawable.ic_dialog_map)
         .setContentTitle(r.title)
         .setContentText(r.summary)
         .setStyle(new Notification.BigTextStyle().bigText(r.details))
         .setAutoCancel(true)
         .setContentIntent(pi);
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(3000 + r.dayIndex, b.build());
    }
}
