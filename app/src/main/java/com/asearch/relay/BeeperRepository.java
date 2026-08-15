package com.asearch.relay;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;

import java.util.HashSet;
import java.util.Set;

final class BeeperRepository {
    static final String READ_PERMISSION = "com.beeper.android.permission.READ_PERMISSION";
    private static final Uri CHATS_URI = Uri.parse("content://com.beeper.api/chats");
    private static final Uri MESSAGES_URI = Uri.parse("content://com.beeper.api/messages");
    private static final int CHAT_PAGE_SIZE = 100;
    private static final int MESSAGE_SAMPLE_SIZE = 40;
    private static final int MAX_ACCESSIBLE_CHATS = 20000;

    interface Progress {
        void update(String message);
    }

    private final ContentResolver resolver;

    BeeperRepository(ContentResolver resolver) {
        this.resolver = resolver;
    }

    Models.Snapshot scanAll(Progress progress) {
        Models.Snapshot snapshot = new Models.Snapshot();
        snapshot.createdAt = System.currentTimeMillis();
        snapshot.chatPageSize = CHAT_PAGE_SIZE;
        snapshot.sampledMessagesPerChat = MESSAGE_SAMPLE_SIZE;

        Set<String> seenRooms = new HashSet<>();
        int offset = 0;
        while (offset < MAX_ACCESSIBLE_CHATS) {
            Uri page = CHATS_URI.buildUpon()
                    .appendQueryParameter("limit", String.valueOf(CHAT_PAGE_SIZE))
                    .appendQueryParameter("offset", String.valueOf(offset))
                    .build();
            int rowCount = readChatPage(page, snapshot, seenRooms, progress);
            if (rowCount < CHAT_PAGE_SIZE) {
                break;
            }
            offset += rowCount;
        }
        if (offset >= MAX_ACCESSIBLE_CHATS) {
            snapshot.warnings.add("Safety ceiling reached at " + MAX_ACCESSIBLE_CHATS + " chats.");
        }
        return snapshot;
    }

    private int readChatPage(
            Uri uri,
            Models.Snapshot snapshot,
            Set<String> seenRooms,
            Progress progress
    ) {
        int rows = 0;
        try (Cursor cursor = resolver.query(uri, null, null, null, null)) {
            if (cursor == null) {
                snapshot.warnings.add("Beeper returned no chat cursor.");
                return 0;
            }
            while (cursor.moveToNext()) {
                rows++;
                Models.Chat chat = new Models.Chat();
                chat.roomId = string(cursor, "roomId", "room_id", "id");
                chat.title = string(cursor, "title", "displayName", "name");
                chat.protocolRaw = string(cursor, "protocol", "network", "accountNetwork");
                chat.network = ProtocolNames.normalize(chat.protocolRaw);
                chat.lastSenderId = string(cursor, "senderEntityId", "senderContactId", "senderId");
                chat.timestamp = longValue(cursor, "timestamp", "lastMessageTimestamp");
                chat.oneToOne = booleanValue(cursor, "oneToOne", "isOneToOne", "direct");
                chat.unreadCount = intValue(cursor, "unreadCount", "unread_count");

                if (chat.roomId == null || chat.roomId.isEmpty()) {
                    snapshot.warnings.add("Skipped a chat row without a room ID.");
                    continue;
                }
                if (!seenRooms.add(chat.roomId)) {
                    snapshot.warnings.add("Stopped after Beeper repeated room IDs at offset pagination.");
                    return rows;
                }

                progress.update("Reading " + (chat.title == null ? chat.roomId : chat.title));
                readMessageSample(chat);
                snapshot.chats.add(chat);
            }
        } catch (SecurityException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalStateException("Unable to query Beeper chats: " + safeMessage(error), error);
        }
        return rows;
    }

    private void readMessageSample(Models.Chat chat) {
        Uri uri = MESSAGES_URI.buildUpon()
                .appendQueryParameter("roomIds", chat.roomId)
                .appendQueryParameter("limit", String.valueOf(MESSAGE_SAMPLE_SIZE))
                .appendQueryParameter("offset", "0")
                .build();
        try (Cursor cursor = resolver.query(uri, null, null, null, null)) {
            if (cursor == null) {
                chat.messageReadError = "Beeper returned no message cursor.";
                return;
            }
            while (cursor.moveToNext()) {
                Models.Message message = new Models.Message();
                message.roomId = string(cursor, "roomId", "room_id");
                if (message.roomId == null) {
                    message.roomId = chat.roomId;
                }
                message.messageId = string(cursor, "originalId", "messageId", "id", "eventId");
                message.senderId = string(
                        cursor, "senderContactId", "senderEntityId", "senderId", "sender"
                );
                message.senderDisplayName = string(
                        cursor, "displayName", "senderDisplayName", "senderName"
                );
                message.timestamp = longValue(cursor, "timestamp", "originServerTs", "date");
                message.sentByMe = booleanValue(cursor, "isSentByMe", "sentByMe", "itsMe");
                message.deleted = booleanValue(cursor, "isDeleted", "deleted");
                message.type = string(cursor, "type", "messageType");
                message.text = string(cursor, "text_content", "text", "body", "message");
                chat.sampledMessages.add(message);
            }
        } catch (Exception error) {
            chat.messageReadError = safeMessage(error);
        }
    }

    private static String string(Cursor cursor, String... names) {
        for (String name : names) {
            int index = cursor.getColumnIndex(name);
            if (index >= 0 && !cursor.isNull(index)) {
                return cursor.getString(index);
            }
        }
        return null;
    }

    private static long longValue(Cursor cursor, String... names) {
        String value = string(cursor, names);
        if (value == null) return 0L;
        try {
            long parsed = Long.parseLong(value);
            return parsed > 0 && parsed < 100000000000L ? parsed * 1000L : parsed;
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private static int intValue(Cursor cursor, String... names) {
        String value = string(cursor, names);
        if (value == null) return 0;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static boolean booleanValue(Cursor cursor, String... names) {
        String value = string(cursor, names);
        return "1".equals(value) || "true".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value);
    }

    private static String safeMessage(Exception error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty()
                ? error.getClass().getSimpleName()
                : message;
    }
}
