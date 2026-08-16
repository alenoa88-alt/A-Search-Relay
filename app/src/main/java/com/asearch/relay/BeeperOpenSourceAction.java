package com.asearch.relay;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;

public final class BeeperOpenSourceAction implements OpenSourceAction {
    @Override
    public Result open(Context context, Evidence evidence) {
        String title = evidence.title == null ? "Unknown conversation" : evidence.title;
        ClipboardManager clipboard = context.getSystemService(ClipboardManager.class);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("Beeper conversation", title));
        }
        Intent launch = context.getPackageManager().getLaunchIntentForPackage("com.beeper.android");
        if (launch == null) {
            return new Result(
                    false,
                    "Beeper is not installed. Conversation title copied: " + title
            );
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(launch);
        return new Result(
                false,
                "Beeper currently documents no exact-room Android intent. Opened Beeper and copied: " + title
        );
    }
}

