package com.asearch.relay;

import java.util.ArrayList;
import java.util.List;

final class Models {
    private Models() {}

    static final class Message {
        String roomId;
        String messageId;
        String senderId;
        String senderDisplayName;
        long timestamp;
        boolean sentByMe;
        boolean deleted;
        String type;
        String text;
    }

    static final class Chat {
        String roomId;
        String title;
        String protocolRaw;
        String network;
        String lastSenderId;
        long timestamp;
        boolean oneToOne;
        int unreadCount;
        final List<Message> sampledMessages = new ArrayList<>();
        String messageReadError;
    }

    static final class Snapshot {
        long createdAt;
        int chatPageSize;
        int sampledMessagesPerChat;
        final List<Chat> chats = new ArrayList<>();
        final List<String> warnings = new ArrayList<>();
    }
}

