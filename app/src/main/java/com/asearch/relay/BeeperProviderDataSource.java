package com.asearch.relay;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class BeeperProviderDataSource implements ManagerDataSource {
    public static final String READ_PERMISSION = "com.beeper.android.permission.READ_PERMISSION";
    public static final Uri CHATS_URI = Uri.parse("content://com.beeper.api/chats");
    public static final Uri MESSAGES_URI = Uri.parse("content://com.beeper.api/messages");
    static final int PAGE_SIZE = 100;
    private static final int MAX_CHATS = 20000;

    private final ContentResolver resolver;

    public BeeperProviderDataSource(ContentResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public List<RemoteChat> loadAllChats() {
        List<RemoteChat> result = new ArrayList<>();
        Set<String> rooms = new HashSet<>();
        for (int offset = 0; offset < MAX_CHATS; offset += PAGE_SIZE) {
            Uri uri = CHATS_URI.buildUpon()
                    .appendQueryParameter("limit", String.valueOf(PAGE_SIZE))
                    .appendQueryParameter("offset", String.valueOf(offset))
                    .build();
            int rows = 0;
            try (Cursor cursor = resolver.query(uri, null, null, null, null)) {
                if (cursor == null) break;
                while (cursor.moveToNext()) {
                    rows++;
                    RemoteChat chat = new RemoteChat();
                    chat.roomId = string(cursor, "roomId", "room_id", "id");
                    if (chat.roomId == null || !rooms.add(chat.roomId)) continue;
                    chat.title = string(cursor, "title", "displayName", "name");
                    chat.protocolRaw = string(cursor, "protocol", "network", "accountNetwork");
                    chat.network = ProtocolNames.normalize(chat.protocolRaw);
                    chat.lastSenderId = string(
                            cursor, "senderEntityId", "senderContactId", "senderId"
                    );
                    chat.timestamp = timestamp(cursor, "timestamp", "lastMessageTimestamp");
                    chat.oneToOne = bool(cursor, "oneToOne", "isOneToOne", "direct");
                    chat.unreadCount = integer(cursor, "unreadCount", "unread_count");
                    result.add(chat);
                }
            }
            if (rows < PAGE_SIZE) break;
        }
        return result;
    }

    @Override
    public List<RemoteMessage> loadMessagePage(String roomId, int limit, int offset) {
        Uri uri = MESSAGES_URI.buildUpon()
                .appendQueryParameter("roomIds", roomId)
                .appendQueryParameter("limit", String.valueOf(limit))
                .appendQueryParameter("offset", String.valueOf(offset))
                .build();
        List<RemoteMessage> result = new ArrayList<>();
        try (Cursor cursor = resolver.query(uri, null, null, null, null)) {
            if (cursor == null) return result;
            while (cursor.moveToNext()) {
                RemoteMessage message = new RemoteMessage();
                message.roomId = string(cursor, "roomId", "room_id");
                if (message.roomId == null) message.roomId = roomId;
                message.messageId = string(cursor, "originalId", "messageId", "id", "eventId");
                message.senderId = string(
                        cursor, "senderContactId", "senderEntityId", "senderId", "sender"
                );
                message.senderDisplayName = string(
                        cursor, "displayName", "senderDisplayName", "senderName"
                );
                message.timestamp = timestamp(cursor, "timestamp", "originServerTs", "date");
                message.sentByMe = bool(cursor, "isSentByMe", "sentByMe", "itsMe");
                message.deleted = bool(cursor, "isDeleted", "deleted");
                message.messageType = string(cursor, "type", "messageType");
                message.text = string(cursor, "text_content", "text", "body", "message");
                if (message.messageId == null || message.messageId.isEmpty()) {
                    message.messageId = stableFallbackId(message);
                }
                result.add(message);
            }
        }
        return result;
    }

    public static final class RemoteChat {
        public String roomId;
        public String title;
        public String protocolRaw;
        public String network;
        public String lastSenderId;
        public long timestamp;
        public boolean oneToOne;
        public int unreadCount;
    }

    public static final class RemoteMessage {
        public String roomId;
        public String messageId;
        public String senderId;
        public String senderDisplayName;
        public long timestamp;
        public boolean sentByMe;
        public boolean deleted;
        public String messageType;
        public String text;
    }

    static String stableFallbackId(RemoteMessage message) {
        String raw = nullSafe(message.roomId) + "|" + message.timestamp + "|"
                + nullSafe(message.senderId) + "|" + nullSafe(message.text);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder value = new StringBuilder("fallback:");
            for (byte item : digest) value.append(String.format("%02x", item));
            return value.toString();
        } catch (Exception ignored) {
            return "fallback:" + raw.hashCode();
        }
    }

    private static String string(Cursor cursor, String... names) {
        for (String name : names) {
            int index = cursor.getColumnIndex(name);
            if (index >= 0 && !cursor.isNull(index)) return cursor.getString(index);
        }
        return null;
    }

    private static long timestamp(Cursor cursor, String... names) {
        String value = string(cursor, names);
        if (value == null) return 0;
        try {
            long parsed = Long.parseLong(value);
            return parsed > 0 && parsed < 100000000000L ? parsed * 1000L : parsed;
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static int integer(Cursor cursor, String... names) {
        String value = string(cursor, names);
        if (value == null) return 0;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static boolean bool(Cursor cursor, String... names) {
        String value = string(cursor, names);
        return "1".equals(value) || "true".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value);
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
