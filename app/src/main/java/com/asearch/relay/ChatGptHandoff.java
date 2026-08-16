package com.asearch.relay;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;

import com.asearch.relay.data.Entities;

import java.util.List;

/** A reviewed, no-API handoff to the installed ChatGPT app. */
public final class ChatGptHandoff {
    private static final String CHATGPT_PACKAGE = "com.openai.chatgpt";

    public static final class Evidence {
        public String contact;
        public String source;
        public String itemType;
        public String status;
        public String priority;
        public String relevantText;
        public String whyItMatters;
        public String recommendedNextAction;
        public boolean humanRequired;
    }

    private ChatGptHandoff() {}

    public static String buildPrompt(Evidence evidence, List<Entities.MessageEntity> recent) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are A Search, Ale Noa's artist-manager reasoning assistant.\n\n")
                .append("READ-ONLY AND HUMAN-SAFETY RULES\n")
                .append("- Analyse the evidence below. Do not claim a message, email, call, booking, payment, upload, signature, meeting, travel, performance, or other action was completed.\n")
                .append("- Phone calls, attendance, travel, performances, signatures, spending approvals, uploads, and final creative or business decisions are HUMAN REQUIRED for Ale.\n")
                .append("- If a call or physical action could help, advise Ale and explain why; do not say that it has happened.\n")
                .append("- Do not invent facts, promises, availability, prices, relationships, or writing style.\n")
                .append("- Draft a reply only from the conversation evidence. Match Ale's observed language and tone only when the samples support it.\n")
                .append("- Treat automated acknowledgements as waiting, not as substantive opportunities.\n\n")
                .append("MANAGER ITEM\n")
                .append("Contact: ").append(safe(evidence.contact)).append('\n')
                .append("Source: ").append(safe(evidence.source)).append('\n')
                .append("Type: ").append(safe(evidence.itemType)).append('\n')
                .append("Status: ").append(safe(evidence.status)).append('\n')
                .append("Priority: ").append(safe(evidence.priority)).append('\n')
                .append("Current evidence: ").append(safe(evidence.relevantText)).append('\n')
                .append("Why it may matter: ").append(safe(evidence.whyItMatters)).append('\n')
                .append("Current recommendation: ").append(safe(evidence.recommendedNextAction)).append('\n')
                .append("Human required flag: ").append(evidence.humanRequired ? "YES" : "NO").append("\n\n")
                .append("RECENT CONVERSATION (newest first)\n");
        if (recent == null || recent.isEmpty()) {
            prompt.append("No recent messages were available.\n");
        } else {
            for (Entities.MessageEntity message : recent) {
                prompt.append(message.sentByMe ? "ALE: " : "CONTACT: ")
                        .append(safe(message.text)).append('\n');
            }
        }
        prompt.append("\nReturn exactly these short sections:\n")
                .append("1. Assessment\n2. Human action (or NONE)\n3. Suggested reply (or DO NOT REPLY YET)\n4. Recommended next step\n");
        return prompt.toString();
    }

    public static boolean open(Context context, String prompt) {
        ClipboardManager clipboard = context.getSystemService(ClipboardManager.class);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("A Search manager prompt", prompt));
        }
        Intent share = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, prompt)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .setPackage(CHATGPT_PACKAGE);
        try {
            context.startActivity(share);
            return true;
        } catch (ActivityNotFoundException unavailable) {
            share.setPackage(null);
            context.startActivity(Intent.createChooser(
                    share,
                    "Send manager context to ChatGPT"
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return false;
        }
    }

    private static String safe(String value) {
        return value == null || value.trim().isEmpty() ? "Not available" : value.trim();
    }
}
