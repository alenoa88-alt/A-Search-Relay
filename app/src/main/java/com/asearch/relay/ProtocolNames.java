package com.asearch.relay;

import java.util.Locale;

final class ProtocolNames {
    private ProtocolNames() {}

    static String normalize(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return "unknown";
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.contains("whatsapp") || value.equals("wa")) {
            return "WhatsApp";
        }
        if (value.contains("instagram") || value.contains("insta")) {
            return "Instagram";
        }
        if (value.contains("facebook") || value.contains("messenger") || value.equals("fb")) {
            return "Facebook/Messenger";
        }
        return raw.trim();
    }
}

