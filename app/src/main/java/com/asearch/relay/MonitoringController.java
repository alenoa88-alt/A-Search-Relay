package com.asearch.relay;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

public final class MonitoringController {
    private static final String PREFS = "manager_settings";
    private static final String MONITORING = "background_monitoring";
    private static final String PERIODIC_NAME = "a-search-hourly-reconciliation";
    private static final String CHANGE_NAME = "a-search-beeper-change";

    private MonitoringController() {}

    public static boolean isEnabled(Context context) {
        return preferences(context).getBoolean(MONITORING, true);
    }

    public static void setEnabled(Context context, boolean enabled) {
        preferences(context).edit().putBoolean(MONITORING, enabled).apply();
        if (enabled) schedulePeriodic(context);
        else WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_NAME);
    }

    public static void schedulePeriodic(Context context) {
        if (!isEnabled(context)) return;
        Constraints constraints = new Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build();
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                ReconciliationWorker.class,
                1,
                TimeUnit.HOURS
        ).setConstraints(constraints).build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
        );
    }

    public static void enqueueBeeperChange(Context context) {
        if (!isEnabled(context)) return;
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(ReconciliationWorker.class)
                .addTag(CHANGE_NAME)
                .build();
        WorkManager.getInstance(context).enqueueUniqueWork(
                CHANGE_NAME,
                ExistingWorkPolicy.REPLACE,
                request
        );
    }

    public static void enqueueCheckNow(Context context) {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(ReconciliationWorker.class)
                .addTag("manual-check")
                .build();
        WorkManager.getInstance(context).enqueueUniqueWork(
                "a-search-check-now",
                ExistingWorkPolicy.REPLACE,
                request
        );
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}

