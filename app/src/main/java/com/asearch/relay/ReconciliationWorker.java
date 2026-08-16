package com.asearch.relay;

import android.content.Context;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.asearch.relay.data.ManagerDatabase;

public final class ReconciliationWorker extends Worker {
    public ReconciliationWorker(
            @NonNull Context appContext,
            @NonNull WorkerParameters workerParams
    ) {
        super(appContext, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        if (context.checkSelfPermission(BeeperProviderDataSource.READ_PERMISSION)
                != PackageManager.PERMISSION_GRANTED) {
            return Result.success();
        }
        try {
            ManagerEngine engine = new ManagerEngine(
                    context,
                    ManagerDatabase.get(context).managerDao(),
                    new BeeperProviderDataSource(context.getContentResolver())
            );
            if (!engine.isInitialImportComplete()) return Result.success();
            engine.reconcile(null);
            return Result.success();
        } catch (SecurityException error) {
            return Result.success();
        } catch (Exception error) {
            return Result.retry();
        }
    }
}

