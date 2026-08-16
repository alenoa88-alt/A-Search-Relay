package com.asearch.relay;

import android.content.Context;

import com.asearch.relay.data.Entities;
import com.asearch.relay.data.ManagerDao;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

final class DiagnosticsExporter {
    private DiagnosticsExporter() {}

    static String providerSnapshot(Context context, BeeperRepository.Progress progress) throws Exception {
        Models.Snapshot snapshot = new BeeperRepository(context.getContentResolver()).scanAll(progress);
        return SnapshotJson.encode(snapshot).replace("v0.3-readonly", "v0.4A");
    }

    static String managerState(ManagerDao dao) throws Exception {
        JSONObject root = new JSONObject();
        root.put("product", "Â Search Artist Manager");
        root.put("build", "v0.4A");
        root.put("strictReadOnly", true);
        root.put("initialImportComplete", syncValue(dao, ManagerEngine.INITIAL_IMPORT_COMPLETE));
        root.put("conversationCount", dao.totalConversationCount());
        root.put("messageCount", dao.totalMessageCount());
        root.put("pendingActionCount", dao.countOpenActions());
        root.put("pendingFollowUpCount", dao.countOpenFollowUps());

        JSONArray conversations = new JSONArray();
        List<Entities.ConversationEntity> recent = dao.getRecentConversations(100);
        for (Entities.ConversationEntity item : recent) {
            JSONObject value = new JSONObject();
            value.put("roomId", item.roomId);
            value.put("network", item.network);
            value.put("title", item.title);
            value.put("lastActivityAt", item.lastActivityAt);
            value.put("lastProcessedTimestamp", item.lastProcessedTimestamp);
            value.put("lastProcessedMessageId", item.lastProcessedMessageId);
            value.put("careerSignalScore", item.careerSignalScore);
            conversations.put(value);
        }
        root.put("recentConversations", conversations);
        root.put("openChatCapability",
                "No exact-room Android intent is documented by Beeper; the app launches Beeper and copies the conversation title.");
        root.put("handoffInstruction", SnapshotJson.HANDOFF);
        return root.toString(2);
    }

    private static String syncValue(ManagerDao dao, String key) {
        Entities.SyncStateEntity state = dao.getSyncState(key);
        return state == null ? null : state.value;
    }
}
