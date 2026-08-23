package io.github.jiangjinyue2333.actiongrid.update;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Icon;

import io.github.jiangjinyue2333.actiongrid.R;

public final class UpdateNotification {
    private static final String CHANNEL_ID = "updates";
    private static final int NOTIFICATION_ID = 1002;

    private UpdateNotification() {
    }

    public static boolean canNotify(Context context) {
        NotificationManager manager = manager(context);
        ensureChannel(context, manager);
        NotificationChannel channel = manager.getNotificationChannel(CHANNEL_ID);
        return context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED
                && manager.areNotificationsEnabled()
                && channel != null
                && channel.getImportance() != NotificationManager.IMPORTANCE_NONE;
    }

    public static boolean showReady(Context context) {
        if (!canNotify(context)) {
            return false;
        }
        PendingIntent contentIntent = PendingIntent.getActivity(
                context,
                0,
                UpdateInstallActivity.intent(context),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(context.getString(R.string.update_ready_title))
                .setContentText(context.getString(R.string.update_ready_text))
                .setContentIntent(contentIntent)
                .addAction(new Notification.Action.Builder(
                        Icon.createWithResource(context, R.drawable.ic_launcher_foreground),
                        context.getString(R.string.install_update), contentIntent).build())
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setCategory(Notification.CATEGORY_STATUS)
                .build();
        try {
            manager(context).notify(NOTIFICATION_ID, notification);
            return true;
        } catch (SecurityException exception) {
            return false;
        }
    }

    static void cancel(Context context) {
        manager(context).cancel(NOTIFICATION_ID);
    }

    private static NotificationManager manager(Context context) {
        return context.getSystemService(NotificationManager.class);
    }

    private static void ensureChannel(Context context, NotificationManager manager) {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.update_channel),
                NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription(context.getString(R.string.update_channel_description));
        manager.createNotificationChannel(channel);
    }
}
