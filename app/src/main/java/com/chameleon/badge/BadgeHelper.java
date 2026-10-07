package com.chameleon.badge;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;

import androidx.core.app.NotificationCompat;

public class BadgeHelper {

    public static final String SHORTCUT_ID = "identity";

    private static final int NOTIFICATION_ID = 1;
    private static final String CHANNEL_ID = "mail_unread2";
    private static final String CHANNEL_NAME = "Unread messages";
    private static final String DEFAULT_TITLE = "Chameleon";

    /** Show the badge, borrowing an optional target app identity for vendor extras. */
    public static void show(Context ctx, int n, String title, Bitmap largeIcon,
                            String targetPkg, String targetCls, boolean shortcutId) {
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT);
        channel.setShowBadge(true);
        channel.setSound(null, null);
        nm.createNotificationChannel(channel);

        Intent intent = new Intent(ctx, MainActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(
                ctx, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String contentTitle = (title == null || title.isEmpty()) ? DEFAULT_TITLE : title;
        String text = (n == 1) ? "1 unread message" : n + " unread messages";

        NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setOngoing(true)
                .setSilent(true)
                .setSmallIcon(R.drawable.ic_stat_email)
                .setContentTitle(contentTitle)
                .setContentText(text)
                .setNumber(n)
                .setAutoCancel(false)
                .setContentIntent(contentIntent);
        if (shortcutId) {
            builder.setShortcutId(SHORTCUT_ID);
        }
        if (largeIcon != null) {
            builder.setLargeIcon(largeIcon);
        }

        // Vendor extras.
        Bundle extras = new Bundle();
        extras.putInt("badge", n);
        extras.putString("badge_icon",
                new ComponentName(ctx, MainActivity.class).flattenToShortString());
        extras.putString("miui.intent.extra.BADGE_COUNT", String.valueOf(n));
        builder.addExtras(extras);

        nm.notify(NOTIFICATION_ID, builder.build());

        sendHuaweiBroadcast(ctx, n, targetPkg, targetCls);
    }

    public static void clear(Context ctx, String targetPkg, String targetCls) {
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.cancel(NOTIFICATION_ID);
        }
        sendHuaweiBroadcast(ctx, 0, targetPkg, targetCls);
    }

    private static void sendHuaweiBroadcast(Context ctx, int n, String targetPkg, String targetCls) {
        try {
            String pkg = (targetPkg == null || targetPkg.isEmpty())
                    ? ctx.getPackageName() : targetPkg;
            String cls = (targetCls == null || targetCls.isEmpty())
                    ? MainActivity.class.getName() : targetCls;
            Intent intent = new Intent("com.huawei.android.launcher.permission.CHANGE_BADGE");
            intent.putExtra("package", pkg);
            intent.putExtra("class", cls);
            intent.putExtra("badgenumber", n);
            ctx.sendBroadcast(intent);
        } catch (Exception ignored) {
        }
    }
}
