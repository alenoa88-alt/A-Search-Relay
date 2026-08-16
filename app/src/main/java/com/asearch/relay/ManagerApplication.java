package com.asearch.relay;

import android.app.Application;

public final class ManagerApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        MonitoringController.schedulePeriodic(this);
    }
}
