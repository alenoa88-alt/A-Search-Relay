package com.asearch.relay;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import com.asearch.relay.data.Entities;
import com.asearch.relay.data.ManagerDatabase;

public final class NotificationHelper {
    private static final String CHANNEL_ID = "manager_attention";

    private NotificationHelper() {}

    public static void notifyCandidate(Context context, long actionId, String contact, String category) {
        Entities.SyncStateEntity initial = ManagerDatabase.get(context)
                .managerDao()
                .getSyncState(ManagerEngine.INITIAL_IMPORT_COMPLETE);
        if (initial == null || !"true".equals(initial.value)) return;
        if (Build.VERSION.SDK_INT >= 33
                && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Manager attention",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("Meaningful career actions detected by Â Search.");
            manager.createNotificationChannel(channel);
        }
        Intent open = new Intent(context, MainActivity.class)
                .putExtra("actionId", actionId)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pending = PendingIntent.getActivity(
                context,
                (int) (actionId % Integer.MAX_VALUE),
                open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        android.app.Notification notification = new android.app.Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Â Search found something that may need attention")
                .setContentText(category + " · " + contact)
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build();
        manager.notify((int) (actionId % Integer.MAX_VALUE), notification);
    }
}
