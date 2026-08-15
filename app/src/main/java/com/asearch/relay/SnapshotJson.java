package com.asearch.relay;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

final class SnapshotJson {
    static final String HANDOFF =
            "SCAN EVERYTHING ACCESSIBLE, BUT KEEP/RETAIN ONLY MUSIC-CAREER-RELEVANT " +
            "CONTACTS AND INFORMATION FOR Â SEARCH ARTIST MANAGER.";

    private SnapshotJson() {}

    static String encode(Models.Snapshot snapshot) throws JSONException {
        JSONObject root = new JSONObject();
        root.put("schemaVersion", 1);
        root.put("product", "Â Search Artist Manager");
        root.put("coreIntelligence", "Â Search");
        root.put("build", "v0.3-readonly");
        root.put("strictReadOnly", true);
        root.put("createdAtEpochMs", snapshot.createdAt);
        root.put("createdAtUtc", iso8601(snapshot.createdAt));
        root.put("handoffInstruction", HANDOFF);
        root.put("classificationPolicy",
                "All accessible chats are exported for semantic review. No chat is permanently classified by keywords.");

        JSONObject provider = new JSONObject();
        provider.put("authority", "com.beeper.api");
        provider.put("chatsUri", "content://com.beeper.api/chats");
        provider.put("messagesUri", "content://com.beeper.api/messages");
        provider.put("accessMode", "query-only");
        root.put("beeperProvider", provider);

        JSONObject scan = new JSONObject();
        scan.put("chatPagination", "offset");
        scan.put("chatPageSize", snapshot.chatPageSize);
        scan.put("sampledMessagesPerConversation", snapshot.sampledMessagesPerChat);
        scan.put("totalChats", snapshot.chats.size());
        root.put("scan", scan);

        JSONArray chats = new JSONArray();
        Set<String> networks = new LinkedHashSet<>();
        for (Models.Chat chat : snapshot.chats) {
            JSONObject item = new JSONObject();
            item.put("roomId", nullable(chat.roomId));
            item.put("title", nullable(chat.title));
            item.put("protocolRaw", nullable(chat.protocolRaw));
            item.put("network", nullable(chat.network));
            item.put("lastSenderId", nullable(chat.lastSenderId));
            item.put("timestamp", chat.timestamp);
            item.put("timestampUtc", chat.timestamp > 0 ? iso8601(chat.timestamp) : JSONObject.NULL);
            item.put("oneToOne", chat.oneToOne);
            item.put("unreadCount", chat.unreadCount);
            if (chat.messageReadError != null) {
                item.put("messageReadError", chat.messageReadError);
            }

            if (chat.network != null && !"unknown".equals(chat.network)) {
                networks.add(chat.network);
            }

            JSONArray messages = new JSONArray();
            for (Models.Message message : chat.sampledMessages) {
                JSONObject msg = new JSONObject();
                msg.put("roomId", nullable(message.roomId));
                msg.put("messageId", nullable(message.messageId));
                msg.put("senderId", nullable(message.senderId));
                msg.put("senderDisplayName", nullable(message.senderDisplayName));
                msg.put("timestamp", message.timestamp);
                msg.put("timestampUtc",
                        message.timestamp > 0 ? iso8601(message.timestamp) : JSONObject.NULL);
                msg.put("isSentByMe", message.sentByMe);
                msg.put("isDeleted", message.deleted);
                msg.put("type", nullable(message.type));
                msg.put("text", nullable(message.text));
                messages.put(msg);
            }
            item.put("sampledMessages", messages);
            chats.put(item);
        }
        root.put("networksSeen", new JSONArray(networks));
        root.put("chats", chats);
        root.put("warnings", new JSONArray(snapshot.warnings));
        return root.toString(2);
    }

    private static Object nullable(String value) {
        return value == null ? JSONObject.NULL : value;
    }

    private static String iso8601(long epochMs) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date(epochMs));
    }
}

