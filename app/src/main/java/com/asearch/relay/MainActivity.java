package com.asearch.relay;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.asearch.relay.data.Entities;
import com.asearch.relay.data.ManagerDao;
import com.asearch.relay.data.ManagerDatabase;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int READ_REQUEST = 1001;
    private static final int NOTIFICATION_REQUEST = 1002;
    private static final int NAVY = Color.rgb(12, 20, 38);
    private static final int INK = Color.rgb(25, 32, 48);
    private static final int MUTED = Color.rgb(102, 112, 133);
    private static final int PURPLE = Color.rgb(109, 40, 217);
    private static final int CYAN = Color.rgb(8, 145, 178);
    private static final int SURFACE = Color.rgb(246, 247, 251);

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable observerDebounce = () -> runReconciliation("Beeper change detected");
    private final Runnable clockTicker = new Runnable() {
        @Override public void run() {
            updateMaltaClock();
            mainHandler.postDelayed(this, 60_000);
        }
    };

    private ManagerDao dao;
    private LinearLayout body;
    private TextView monitoringBadge;
    private TextView beeperActivityView;
    private TextView reconciliationView;
    private TextView maltaTimeView;
    private TextView pendingCountView;
    private ContentObserver contentObserver;
    private boolean observerRegistered;
    private boolean initialImportRunning;
    private String currentScreen = "TODAY";
    private String latestDiagnosticsJson;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dao = ManagerDatabase.get(this).managerDao();
        setContentView(buildShell());
        mainHandler.post(clockTicker);
        refreshHeaderStats();
        showScreen("TODAY");
        ensureInitialImport();
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerBeeperObserver();
    }

    @Override
    protected void onStop() {
        unregisterBeeperObserver();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        mainHandler.removeCallbacksAndMessages(null);
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
        if (requestCode == READ_REQUEST) {
            if (hasBeeperAccess()) {
                registerBeeperObserver();
                ensureInitialImport();
                toast("Beeper read access granted.");
            } else {
                toast("Read access was not granted. No Beeper data was accessed.");
            }
            showScreen(currentScreen);
        }
    }

    private View buildShell() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(SURFACE);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(18), dp(16), dp(18), dp(14));
        header.setBackgroundColor(NAVY);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);
        TextView title = label("Â Search", 27, Color.WHITE, true);
        TextView subtitle = label("ARTIST MANAGER", 12, Color.rgb(159, 174, 206), true);
        brand.addView(title);
        brand.addView(subtitle);
        titleRow.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        Button settings = compactButton("SETTINGS", Color.rgb(42, 54, 82));
        settings.setOnClickListener(view -> showScreen("SETTINGS"));
        titleRow.addView(settings);
        header.addView(titleRow);

        monitoringBadge = label("", 12, Color.WHITE, true);
        monitoringBadge.setPadding(dp(10), dp(7), dp(10), dp(7));
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(-2, -2);
        badgeParams.topMargin = dp(12);
        header.addView(monitoringBadge, badgeParams);

        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        stats.setGravity(Gravity.CENTER_VERTICAL);
        beeperActivityView = stat("LAST BEEPER", "—");
        reconciliationView = stat("RECONCILED", "—");
        maltaTimeView = stat("MALTA TIME", "—");
        pendingCountView = stat("PENDING", "0");
        stats.addView(beeperActivityView, weight());
        stats.addView(reconciliationView, weight());
        stats.addView(maltaTimeView, weight());
        stats.addView(pendingCountView, weight());
        LinearLayout.LayoutParams statsParams = new LinearLayout.LayoutParams(-1, -2);
        statsParams.topMargin = dp(14);
        header.addView(stats, statsParams);
        root.addView(header);

        ScrollView scroll = new ScrollView(this);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(16), dp(16), dp(30));
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(buildNavigation());
        updateMonitoringBadge();
        return root;
    }

    private View buildNavigation() {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setBackgroundColor(Color.WHITE);
        LinearLayout nav = new LinearLayout(this);
        nav.setPadding(dp(8), dp(7), dp(8), dp(7));
        String[] tabs = {"TODAY", "OPPORTUNITIES", "CALENDAR", "FOLLOW-UPS", "CONTACTS", "ACTIVITY"};
        for (String tab : tabs) {
            Button button = compactButton(tab, Color.TRANSPARENT);
            button.setTextColor(INK);
            button.setOnClickListener(view -> showScreen(tab));
            nav.addView(button);
        }
        scroll.addView(nav);
        return scroll;
    }

    private void showScreen(String screen) {
        currentScreen = screen;
        body.removeAllViews();
        if ("SETTINGS".equals(screen)) {
            renderSettings();
            return;
        }
        addScreenTitle(screen);
        if (!hasBeeperAccess()) {
            addInfoCard(
                    "BEEPER ACCESS REQUIRED",
                    "Grant read access to build and maintain the manager dashboard.",
                    "Grant Beeper Read Access",
                    view -> requestBeeperAccess()
            );
            return;
        }
        switch (screen) {
            case "OPPORTUNITIES": loadOpportunities(); break;
            case "CALENDAR": loadCalendar(); break;
            case "FOLLOW-UPS": loadFollowUps(); break;
            case "CONTACTS": loadContacts(); break;
            case "ACTIVITY": loadActivity(); break;
            default: loadToday(); break;
        }
    }

    private void loadToday() {
        Button check = primaryButton("⚡ CHECK NOW", PURPLE);
        check.setOnClickListener(view -> runReconciliation("Checking recent Beeper changes"));
        body.addView(check);
        worker.execute(() -> {
            List<Entities.ActionEntity> actions = dao.getOpenActions();
            List<Entities.OpportunityEntity> opportunities = dao.getOpportunities();
            List<Entities.FollowUpEntity> followUps = dao.getOpenFollowUps();
            List<Entities.ActivityEntity> activity = dao.getActivity(8);
            runOnUiThread(() -> {
                if (!"TODAY".equals(currentScreen)) return;
                addActionSection("URGENT", filterActions(actions, "priority", "URGENT"));
                addActionSection("ACTION REQUIRED", filterActions(actions, "status", "ACTION REQUIRED"));
                addOpportunitySection("NEW OPPORTUNITIES", filterOpportunities(opportunities, "NEW"));
                addOpportunitySection("CONFIRMED BOOKINGS", filterOpportunities(opportunities, "CONFIRMED"));
                addOpportunitySection("PENDING BOOKINGS", filterOpportunities(opportunities, "ACTIVE"));
                addFollowUpSection("FOLLOW-UPS DUE", followUps);
                addActionSection("WAITING FOR REPLY", filterActions(actions, "status", "WAITING"));
                addActionSection("HUMAN REQUIRED", humanActions(actions));
                addActivitySection("RECENT IMPORTANT ACTIVITY", activity);
            });
        });
    }

    private void loadOpportunities() {
        worker.execute(() -> {
            List<Entities.OpportunityEntity> items = dao.getOpportunities();
            runOnUiThread(() -> {
                if (!"OPPORTUNITIES".equals(currentScreen)) return;
                for (String status : new String[]{"NEW", "ACTIVE", "WAITING", "CONFIRMED", "LOST", "ARCHIVED"}) {
                    addOpportunitySection(status, filterOpportunities(items, status));
                }
            });
        });
    }

    private void loadCalendar() {
        LinearLayout views = new LinearLayout(this);
        views.addView(chip("UPCOMING"));
        views.addView(chip("LIST"));
        views.addView(chip("MONTH"));
        body.addView(views);
        worker.execute(() -> {
            List<Entities.EventEntity> events = dao.getEvents();
            runOnUiThread(() -> {
                if (!"CALENDAR".equals(currentScreen)) return;
                addSectionHeading("MANAGER CALENDAR", events.size());
                if (events.isEmpty()) {
                    addEmpty("Local calendar is ready for confirmed performances, pending performances, meetings, calls, soundchecks, arrivals, deadlines, releases, filming and rehearsals.");
                }
                for (Entities.EventEntity event : events) {
                    LinearLayout card = card();
                    card.addView(label(event.title, 17, INK, true));
                    card.addView(label(event.type + " · " + event.status, 12, PURPLE, true));
                    card.addView(label(formatDate(event.startAt), 14, MUTED, false));
                    if (event.roomId != null) addOpenChat(card, event.roomId, event.title, event.relevantMessageId, event.startAt);
                    body.addView(card, cardMargin());
                }
            });
        });
    }

    private void loadFollowUps() {
        worker.execute(() -> {
            List<Entities.FollowUpEntity> items = dao.getOpenFollowUps();
            runOnUiThread(() -> {
                if (!"FOLLOW-UPS".equals(currentScreen)) return;
                addFollowUpSection("DUE AND UPCOMING", items);
            });
        });
    }

    private void loadContacts() {
        worker.execute(() -> {
            List<Entities.ContactEntity> contacts = dao.getContacts();
            runOnUiThread(() -> {
                if (!"CONTACTS".equals(currentScreen)) return;
                addSectionHeading("RELATIONSHIPS", contacts.size());
                for (Entities.ContactEntity contact : contacts) {
                    if (!contact.careerRelevant) continue;
                    LinearLayout card = card();
                    card.addView(label(contact.displayName, 17, INK, true));
                    card.addView(label(
                            safe(contact.category) + " · " + safe(contact.primaryNetwork),
                            12, PURPLE, true
                    ));
                    card.addView(label(
                            safe(contact.relationshipStatus) + " · last " + formatRelative(contact.lastInteractionAt),
                            14, MUTED, false
                    ));
                    card.addView(label(
                            contact.openActionCount + " actions · " + contact.openOpportunityCount
                                    + " opportunities · style " + percent(contact.styleConfidence)
                                    + " · research " + safe(contact.intelligenceStatus),
                            13, MUTED, false
                    ));
                    addOpenChat(card, contact.contactId, contact.displayName, null, contact.lastInteractionAt);
                    body.addView(card, cardMargin());
                }
            });
        });
    }

    private void loadActivity() {
        worker.execute(() -> {
            List<Entities.ActivityEntity> activity = dao.getActivity(100);
            runOnUiThread(() -> {
                if (!"ACTIVITY".equals(currentScreen)) return;
                addActivitySection("MANAGER ACTIVITY", activity);
            });
        });
    }

    private void renderSettings() {
        addScreenTitle("SETTINGS");
        addSectionHeading("BACKGROUND MONITORING", 0);
        LinearLayout monitor = card();
        Switch toggle = new Switch(this);
        toggle.setText(MonitoringController.isEnabled(this) ? "ON" : "OFF");
        toggle.setChecked(MonitoringController.isEnabled(this));
        toggle.setOnCheckedChangeListener((button, checked) -> {
            MonitoringController.setEnabled(this, checked);
            button.setText(checked ? "ON" : "OFF");
            updateMonitoringBadge();
            if (checked) {
                requestNotificationAccess();
                registerBeeperObserver();
            } else unregisterBeeperObserver();
        });
        monitor.addView(toggle);
        monitor.addView(label(
                "Hourly reconciliation is inexact and respects Android battery scheduling. Active-process Beeper changes are debounced.",
                13, MUTED, false
        ));
        body.addView(monitor, cardMargin());

        addSectionHeading("BEEPER ACCESS", 0);
        addInfoCard(
                hasBeeperAccess() ? "READ ACCESS GRANTED" : "READ ACCESS NOT GRANTED",
                "Beeper remains strictly query-only. Sending is disabled.",
                "Grant Beeper Read Access",
                view -> requestBeeperAccess()
        );

        addSectionHeading("DIAGNOSTICS", 0);
        LinearLayout diagnostics = card();
        Button verification = secondaryButton("Run Verification");
        verification.setOnClickListener(view -> runDiagnosticsVerification());
        diagnostics.addView(verification);
        Button export = secondaryButton("Export / Share JSON");
        export.setOnClickListener(view -> exportDiagnostics());
        diagnostics.addView(export);
        Button testOpen = secondaryButton("TEST OPEN CHAT");
        testOpen.setOnClickListener(view -> testOpenChat());
        diagnostics.addView(testOpen);
        diagnostics.addView(label(
                "Exact-room opening is not claimed: Beeper documents no supported room deep link. The fallback copies the conversation title and launches Beeper.",
                13, MUTED, false
        ));
        body.addView(diagnostics, cardMargin());

        addSectionHeading("DATA STATUS", 0);
        LinearLayout data = card();
        TextView status = label("Loading local manager state…", 14, MUTED, false);
        data.addView(status);
        body.addView(data, cardMargin());
        worker.execute(() -> {
            int conversations = dao.totalConversationCount();
            int messages = dao.totalMessageCount();
            Entities.SyncStateEntity initial = dao.getSyncState(ManagerEngine.INITIAL_IMPORT_COMPLETE);
            runOnUiThread(() -> status.setText(
                    "Room database v" + ManagerDatabase.VERSION + "\n"
                            + conversations + " conversations · " + messages + " messages\n"
                            + "Relationship index: " + (initial != null ? "READY" : "NOT BUILT") + "\n"
                            + "Contact intelligence: architecture ready; no local internet research is performed."
            ));
        });
    }

    private void ensureInitialImport() {
        if (!hasBeeperAccess() || initialImportRunning) return;
        worker.execute(() -> {
            ManagerEngine engine = engine();
            if (!engine.isInitialImportComplete()) {
                initialImportRunning = true;
                runOnUiThread(() -> {
                    body.removeAllViews();
                    addScreenTitle("BUILDING RELATIONSHIP INDEX");
                    addEmpty("Building Â Search relationship index…");
                });
                try {
                    ManagerEngine.Result result = engine.runInitialImport((name, completed, total) ->
                            runOnUiThread(() -> {
                                body.removeAllViews();
                                addScreenTitle("BUILDING RELATIONSHIP INDEX");
                                addEmpty("Building Â Search relationship index…\n"
                                        + completed + " / " + total + "\n" + name);
                            })
                    );
                    runOnUiThread(() -> toast("Initial import complete: " + result.summary()));
                } catch (Exception error) {
                    runOnUiThread(() -> toast("Initial import paused: " + safeMessage(error)));
                } finally {
                    initialImportRunning = false;
                    runOnUiThread(() -> {
                        refreshHeaderStats();
                        showScreen("TODAY");
                    });
                }
            }
        });
    }

    private void runReconciliation(String message) {
        if (!hasBeeperAccess()) {
            requestBeeperAccess();
            return;
        }
        addTransientStatus(message + "…");
        worker.execute(() -> {
            try {
                ManagerEngine engine = engine();
                if (!engine.isInitialImportComplete()) {
                    runOnUiThread(this::ensureInitialImport);
                    return;
                }
                ManagerEngine.Result result = engine.reconcile(null);
                runOnUiThread(() -> {
                    toast(result.summary());
                    refreshHeaderStats();
                    showScreen(currentScreen);
                });
            } catch (Exception error) {
                runOnUiThread(() -> toast("Check failed: " + safeMessage(error)));
            }
        });
    }

    private void runDiagnosticsVerification() {
        if (!hasBeeperAccess()) {
            requestBeeperAccess();
            return;
        }
        addTransientStatus("Running provider verification…");
        worker.execute(() -> {
            try {
                latestDiagnosticsJson = DiagnosticsExporter.providerSnapshot(
                        this,
                        message -> runOnUiThread(() -> addTransientStatus(message + "…"))
                );
                runOnUiThread(() -> toast("Verification JSON is ready."));
            } catch (Exception error) {
                runOnUiThread(() -> toast("Verification failed: " + safeMessage(error)));
            }
        });
    }

    private void exportDiagnostics() {
        worker.execute(() -> {
            try {
                if (latestDiagnosticsJson == null) {
                    latestDiagnosticsJson = DiagnosticsExporter.managerState(dao);
                }
                File directory = new File(getCacheDir(), "snapshots");
                if (!directory.exists() && !directory.mkdirs()) {
                    throw new IllegalStateException("Could not create diagnostics directory.");
                }
                File file = new File(directory, "A-Search-Artist-Manager-v0.4A-diagnostics.json");
                try (FileOutputStream output = new FileOutputStream(file, false)) {
                    output.write(latestDiagnosticsJson.getBytes(StandardCharsets.UTF_8));
                }
                Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".files", file);
                Intent share = new Intent(Intent.ACTION_SEND)
                        .setType("application/json")
                        .putExtra(Intent.EXTRA_STREAM, uri)
                        .putExtra(Intent.EXTRA_TEXT, SnapshotJson.HANDOFF)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                share.setClipData(ClipData.newRawUri("Â Search diagnostics", uri));
                runOnUiThread(() -> startActivity(Intent.createChooser(share, "Share diagnostics JSON")));
            } catch (Exception error) {
                runOnUiThread(() -> toast("Export failed: " + safeMessage(error)));
            }
        });
    }

    private void testOpenChat() {
        worker.execute(() -> {
            List<Entities.ConversationEntity> recent = dao.getRecentConversations(1);
            runOnUiThread(() -> {
                if (recent.isEmpty()) {
                    toast("No imported conversation is available yet.");
                    return;
                }
                Entities.ConversationEntity conversation = recent.get(0);
                openChat(conversation.roomId, conversation.title, conversation.lastMessageId, conversation.lastActivityAt);
            });
        });
    }

    private void registerBeeperObserver() {
        if (observerRegistered || !hasBeeperAccess() || !MonitoringController.isEnabled(this)) return;
        if (contentObserver == null) {
            contentObserver = new ContentObserver(mainHandler) {
                @Override public void onChange(boolean selfChange) {
                    mainHandler.removeCallbacks(observerDebounce);
                    mainHandler.postDelayed(observerDebounce, 1500);
                }
            };
        }
        try {
            getContentResolver().registerContentObserver(
                    BeeperProviderDataSource.CHATS_URI,
                    true,
                    contentObserver
            );
            observerRegistered = true;
        } catch (Exception error) {
            observerRegistered = false;
        }
        updateMonitoringBadge();
    }

    private void unregisterBeeperObserver() {
        if (!observerRegistered || contentObserver == null) return;
        try {
            getContentResolver().unregisterContentObserver(contentObserver);
        } catch (Exception ignored) {
        }
        observerRegistered = false;
        updateMonitoringBadge();
    }

    private void refreshHeaderStats() {
        updateMonitoringBadge();
        worker.execute(() -> {
            List<Entities.ConversationEntity> recent = dao.getRecentConversations(1);
            long beeper = recent.isEmpty() ? 0 : recent.get(0).lastActivityAt;
            Entities.SyncStateEntity sync = dao.getSyncState(ManagerEngine.LAST_RECONCILIATION);
            int pending = dao.countOpenActions() + dao.countOpenFollowUps();
            runOnUiThread(() -> {
                beeperActivityView.setText("LAST BEEPER\n" + formatShort(beeper));
                reconciliationView.setText("RECONCILED\n" + formatShort(sync == null ? 0 : sync.updatedAt));
                pendingCountView.setText("PENDING\n" + pending);
            });
        });
    }

    private void updateMonitoringBadge() {
        if (monitoringBadge == null) return;
        boolean enabled = MonitoringController.isEnabled(this);
        monitoringBadge.setText(enabled ? "● MONITORING" : "MONITORING PAUSED");
        monitoringBadge.setBackground(roundRect(enabled ? Color.rgb(22, 101, 52) : Color.rgb(153, 27, 27), 30));
    }

    private void updateMaltaClock() {
        if (maltaTimeView == null) return;
        SimpleDateFormat format = new SimpleDateFormat("EEE HH:mm", Locale.UK);
        format.setTimeZone(TimeZone.getTimeZone("Europe/Malta"));
        maltaTimeView.setText("MALTA TIME\n" + format.format(new Date()));
    }

    private void requestBeeperAccess() {
        requestPermissions(new String[]{BeeperProviderDataSource.READ_PERMISSION}, READ_REQUEST);
    }

    private void requestNotificationAccess() {
        if (android.os.Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_REQUEST);
        }
    }

    private boolean hasBeeperAccess() {
        return checkSelfPermission(BeeperProviderDataSource.READ_PERMISSION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private ManagerEngine engine() {
        return new ManagerEngine(
                this,
                dao,
                new BeeperProviderDataSource(getContentResolver())
        );
    }

    private void addActionSection(String title, List<Entities.ActionEntity> items) {
        addSectionHeading(title, items.size());
        if (items.isEmpty()) addEmpty("Nothing here right now.");
        for (Entities.ActionEntity item : items) {
            LinearLayout card = card();
            card.addView(label(safe(item.contactName), 17, INK, true));
            card.addView(label(item.status + " · " + item.priority + " · " + safe(item.source), 12, PURPLE, true));
            card.addView(label(safe(item.whatHappened), 15, INK, false));
            card.addView(label("Why it matters\n" + safe(item.whyItMatters), 13, MUTED, false));
            card.addView(label("Recommended\n" + safe(item.recommendedNextAction), 14, INK, true));
            card.addView(label(
                    formatDate(item.happenedAt)
                            + (item.deadlineAt > 0 ? " · deadline " + formatDate(item.deadlineAt) : "")
                            + "\nNext: " + safe(item.assignedTo)
                            + (item.humanRequired ? " · HUMAN REQUIRED" : ""),
                    12, MUTED, false
            ));
            addOpenChat(card, item.roomId, item.contactName, item.relevantMessageId, item.happenedAt);
            body.addView(card, cardMargin());
        }
    }

    private void addOpportunitySection(String title, List<Entities.OpportunityEntity> items) {
        addSectionHeading(title, items.size());
        if (items.isEmpty()) addEmpty("Nothing here right now.");
        for (Entities.OpportunityEntity item : items) {
            LinearLayout card = card();
            card.addView(label(safe(item.contactName), 17, INK, true));
            card.addView(label(safe(item.type) + " · " + safe(item.status) + " · " + safe(item.source), 12, PURPLE, true));
            card.addView(label(safe(item.summary), 15, INK, false));
            card.addView(label("Next\n" + safe(item.nextAction), 14, MUTED, false));
            card.addView(label("Last activity " + formatDate(item.lastActivityAt), 12, MUTED, false));
            addOpenChat(card, item.roomId, item.contactName, item.relevantMessageId, item.lastActivityAt);
            body.addView(card, cardMargin());
        }
    }

    private void addFollowUpSection(String title, List<Entities.FollowUpEntity> items) {
        addSectionHeading(title, items.size());
        if (items.isEmpty()) addEmpty("Nothing here right now.");
        for (Entities.FollowUpEntity item : items) {
            LinearLayout card = card();
            card.addView(label(safe(item.contactName), 17, INK, true));
            card.addView(label("WAITING ON " + safe(item.waitingOn), 12, PURPLE, true));
            card.addView(label(safe(item.reason), 14, INK, false));
            card.addView(label("Follow up " + formatDate(item.followUpAt), 12, MUTED, false));
            addOpenChat(card, item.roomId, item.contactName, item.relevantMessageId, item.followUpAt);
            body.addView(card, cardMargin());
        }
    }

    private void addActivitySection(String title, List<Entities.ActivityEntity> items) {
        addSectionHeading(title, items.size());
        if (items.isEmpty()) addEmpty("No manager activity yet.");
        for (Entities.ActivityEntity item : items) {
            LinearLayout card = card();
            card.addView(label(item.type.replace('_', ' '), 12, CYAN, true));
            card.addView(label(safe(item.summary), 14, INK, false));
            card.addView(label(formatDate(item.timestamp), 12, MUTED, false));
            body.addView(card, cardMargin());
        }
    }

    private void addOpenChat(
            LinearLayout card,
            String roomId,
            String title,
            String messageId,
            long timestamp
    ) {
        Button open = secondaryButton("OPEN CHAT");
        open.setOnClickListener(view -> openChat(roomId, title, messageId, timestamp));
        card.addView(open);
    }

    private void openChat(String roomId, String title, String messageId, long timestamp) {
        OpenSourceAction.Evidence evidence = new OpenSourceAction.Evidence();
        evidence.source = "Beeper";
        evidence.roomId = roomId;
        evidence.title = title;
        evidence.relevantMessageId = messageId;
        evidence.timestamp = timestamp;
        OpenSourceAction.Result result = new BeeperOpenSourceAction().open(this, evidence);
        toast(result.explanation);
    }

    private List<Entities.ActionEntity> filterActions(
            List<Entities.ActionEntity> input,
            String field,
            String value
    ) {
        List<Entities.ActionEntity> result = new ArrayList<>();
        for (Entities.ActionEntity item : input) {
            String actual = "priority".equals(field) ? item.priority : item.status;
            if (value.equals(actual)) result.add(item);
        }
        return result;
    }

    private List<Entities.ActionEntity> humanActions(List<Entities.ActionEntity> input) {
        List<Entities.ActionEntity> result = new ArrayList<>();
        for (Entities.ActionEntity item : input) if (item.humanRequired) result.add(item);
        return result;
    }

    private List<Entities.OpportunityEntity> filterOpportunities(
            List<Entities.OpportunityEntity> input,
            String status
    ) {
        List<Entities.OpportunityEntity> result = new ArrayList<>();
        for (Entities.OpportunityEntity item : input) {
            if (status.equals(item.status)) result.add(item);
        }
        return result;
    }

    private void addScreenTitle(String value) {
        body.addView(label(value, 25, INK, true));
    }

    private void addSectionHeading(String title, int count) {
        TextView value = label(title + (count > 0 ? "  " + count : ""), 13, MUTED, true);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(22);
        params.bottomMargin = dp(7);
        body.addView(value, params);
    }

    private void addEmpty(String value) {
        TextView text = label(value, 14, MUTED, false);
        text.setPadding(dp(2), dp(8), dp(2), dp(8));
        body.addView(text);
    }

    private void addTransientStatus(String value) {
        runOnUiThread(() -> {
            TextView status = label(value, 13, CYAN, true);
            status.setPadding(0, dp(8), 0, dp(8));
            body.addView(status, Math.min(1, body.getChildCount()));
        });
    }

    private void addInfoCard(String title, String text, String action, View.OnClickListener listener) {
        LinearLayout card = card();
        card.addView(label(title, 16, INK, true));
        card.addView(label(text, 14, MUTED, false));
        Button button = secondaryButton(action);
        button.setOnClickListener(listener);
        card.addView(button);
        body.addView(card, cardMargin());
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(roundRect(Color.WHITE, 18));
        card.setElevation(dp(2));
        return card;
    }

    private Button primaryButton(String text, int color) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(15);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setBackground(roundRect(color, 16));
        button.setMinHeight(dp(54));
        return button;
    }

    private Button secondaryButton(String text) {
        Button button = primaryButton(text, Color.rgb(232, 235, 245));
        button.setTextColor(INK);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(48));
        params.topMargin = dp(10);
        button.setLayoutParams(params);
        return button;
    }

    private Button compactButton(String text, int color) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(11);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setBackground(roundRect(color, 12));
        return button;
    }

    private TextView chip(String text) {
        TextView view = label(text, 11, PURPLE, true);
        view.setPadding(dp(12), dp(8), dp(12), dp(8));
        view.setBackground(roundRect(Color.rgb(237, 233, 254), 30));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
        params.setMargins(0, dp(12), dp(8), 0);
        view.setLayoutParams(params);
        return view;
    }

    private TextView stat(String title, String value) {
        TextView view = label(title + "\n" + value, 10, Color.rgb(203, 213, 225), true);
        view.setGravity(Gravity.CENTER);
        return view;
    }

    private TextView label(String text, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setLineSpacing(0, 1.12f);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private GradientDrawable roundRect(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private LinearLayout.LayoutParams cardMargin() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(10);
        return params;
    }

    private LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, -2, 1);
    }

    private String formatDate(long timestamp) {
        if (timestamp <= 0) return "Time unknown";
        SimpleDateFormat format = new SimpleDateFormat("EEE d MMM · HH:mm", Locale.UK);
        format.setTimeZone(TimeZone.getTimeZone("Europe/Malta"));
        return format.format(new Date(timestamp));
    }

    private String formatShort(long timestamp) {
        if (timestamp <= 0) return "—";
        SimpleDateFormat format = new SimpleDateFormat("d MMM HH:mm", Locale.UK);
        format.setTimeZone(TimeZone.getTimeZone("Europe/Malta"));
        return format.format(new Date(timestamp));
    }

    private String formatRelative(long timestamp) {
        if (timestamp <= 0) return "unknown";
        return TimeContext.now(timestamp).relativeTo(timestamp) + " ago";
    }

    private static String percent(double value) {
        return Math.round(value * 100) + "%";
    }

    private static String safe(String value) {
        return value == null || value.trim().isEmpty() ? "—" : value;
    }

    private static String safeMessage(Exception error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty()
                ? error.getClass().getSimpleName()
                : message;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
