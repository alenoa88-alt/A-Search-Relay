package com.asearch.relay;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONException;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int READ_REQUEST_CODE = 1001;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private TextView permissionStatus;
    private TextView scanStatus;
    private Button verificationButton;
    private Button fullScanButton;
    private Button shareButton;
    private String latestJson;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildScreen());
        refreshPermissionStatus();
    }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == READ_REQUEST_CODE) {
            refreshPermissionStatus();
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                toast("Beeper read access granted.");
            } else {
                toast("Read access was not granted. The app remains safely inactive.");
            }
        }
    }

    private View buildScreen() {
        int spacing = dp(18);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(spacing, spacing, spacing, spacing);
        content.setBackgroundColor(Color.rgb(248, 250, 252));

        TextView title = text("Â Search Artist Manager", 28, Color.rgb(17, 24, 39));
        title.setTypeface(null, 1);
        content.addView(title);

        TextView badge = text("STRICT READ-ONLY BUILD", 16, Color.WHITE);
        badge.setTypeface(null, 1);
        badge.setBackgroundColor(Color.rgb(22, 101, 52));
        badge.setPadding(dp(12), dp(9), dp(12), dp(9));
        addWithTopMargin(content, badge, 12);

        TextView explanation = text(
                "This v0.3 verification build only queries Beeper data. Sending is disabled. " +
                "It scans every accessible chat and exports sampled history so Â Search can " +
                "semantically retain only music-career-relevant contacts and information.",
                16,
                Color.rgb(55, 65, 81)
        );
        explanation.setLineSpacing(0, 1.15f);
        addWithTopMargin(content, explanation, 14);

        permissionStatus = text("", 15, Color.rgb(75, 85, 99));
        addWithTopMargin(content, permissionStatus, 18);

        Button grantButton = button("Grant Beeper Read Access", Color.rgb(37, 99, 235));
        grantButton.setOnClickListener(view -> requestReadAccess());
        addWithTopMargin(content, grantButton, 10);

        verificationButton = button("Run Verification", Color.rgb(75, 85, 99));
        verificationButton.setOnClickListener(view -> runScan("Verification"));
        addWithTopMargin(content, verificationButton, 10);

        fullScanButton = button("⚡ CHECK EVERYTHING NOW", Color.rgb(109, 40, 217));
        fullScanButton.setOnClickListener(view -> runScan("Full read-only scan"));
        addWithTopMargin(content, fullScanButton, 10);

        shareButton = button("Export / Share JSON", Color.rgb(8, 145, 178));
        shareButton.setEnabled(false);
        shareButton.setOnClickListener(view -> shareSnapshot());
        addWithTopMargin(content, shareButton, 10);

        scanStatus = text(
                "No snapshot yet. Connect your networks in Beeper, grant read access, then run verification.",
                14,
                Color.rgb(75, 85, 99)
        );
        scanStatus.setTextIsSelectable(true);
        addWithTopMargin(content, scanStatus, 18);

        TextView instruction = text(SnapshotJson.HANDOFF, 14, Color.rgb(17, 24, 39));
        instruction.setTypeface(null, 1);
        addWithTopMargin(content, instruction, 18);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        return scroll;
    }

    private void requestReadAccess() {
        if (hasReadAccess()) {
            toast("Beeper read access is already granted.");
            return;
        }
        requestPermissions(new String[]{BeeperRepository.READ_PERMISSION}, READ_REQUEST_CODE);
    }

    private void runScan(String label) {
        if (!hasReadAccess()) {
            requestReadAccess();
            scanStatus.setText("Grant Beeper read access, then tap the scan button again.");
            return;
        }
        setBusy(true);
        latestJson = null;
        shareButton.setEnabled(false);
        scanStatus.setText(label + " started. Querying all accessible chat pages…");

        worker.execute(() -> {
            try {
                BeeperRepository repository = new BeeperRepository(getContentResolver());
                Models.Snapshot snapshot = repository.scanAll(
                        message -> runOnUiThread(() -> scanStatus.setText(message + "…"))
                );
                String json = SnapshotJson.encode(snapshot);
                latestJson = json;
                runOnUiThread(() -> {
                    scanStatus.setText(
                            label + " complete. " + snapshot.chats.size() +
                            " accessible chats scanned; sampled message history is ready to share."
                    );
                    shareButton.setEnabled(true);
                    setBusy(false);
                });
            } catch (SecurityException error) {
                showFailure("Beeper denied the read query. Re-grant read access and try again.");
            } catch (JSONException error) {
                showFailure("The validation JSON could not be generated: " + safeMessage(error));
            } catch (Exception error) {
                showFailure(
                        "Verification failed. Confirm Beeper is installed, signed in, and has connected chats. " +
                        safeMessage(error)
                );
            }
        });
    }

    private void shareSnapshot() {
        if (latestJson == null) {
            toast("Run verification before exporting.");
            return;
        }
        try {
            File directory = new File(getCacheDir(), "snapshots");
            if (!directory.exists() && !directory.mkdirs()) {
                throw new IllegalStateException("Could not create the snapshot folder.");
            }
            File snapshot = new File(directory, "A-Search-Artist-Manager-v0.3-validation.json");
            try (FileOutputStream output = new FileOutputStream(snapshot, false)) {
                output.write(latestJson.getBytes(StandardCharsets.UTF_8));
            }
            Uri uri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".files",
                    snapshot
            );
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("application/json");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.putExtra(Intent.EXTRA_SUBJECT, "Â Search Artist Manager validation snapshot");
            share.putExtra(Intent.EXTRA_TEXT, SnapshotJson.HANDOFF);
            share.setClipData(ClipData.newRawUri("validation snapshot", uri));
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, "Share validation JSON"));
        } catch (Exception error) {
            toast("Export failed: " + safeMessage(error));
        }
    }

    private boolean hasReadAccess() {
        return checkSelfPermission(BeeperRepository.READ_PERMISSION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void refreshPermissionStatus() {
        permissionStatus.setText(
                hasReadAccess()
                        ? "Beeper read access: GRANTED"
                        : "Beeper read access: NOT GRANTED"
        );
    }

    private void setBusy(boolean busy) {
        verificationButton.setEnabled(!busy);
        fullScanButton.setEnabled(!busy);
    }

    private void showFailure(String message) {
        runOnUiThread(() -> {
            scanStatus.setText(message);
            setBusy(false);
        });
    }

    private Button button(String label, int color) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setTextSize(15);
        button.setAllCaps(false);
        button.setBackgroundColor(color);
        button.setMinHeight(dp(52));
        return button;
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private void addWithTopMargin(LinearLayout parent, View view, int marginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = dp(marginDp);
        parent.addView(view, params);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private static String safeMessage(Exception error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty()
                ? error.getClass().getSimpleName()
                : message;
    }
}
