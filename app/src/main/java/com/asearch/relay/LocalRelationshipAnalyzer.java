package com.asearch.relay;

import com.asearch.relay.data.Entities;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class LocalRelationshipAnalyzer implements RelationshipAnalyzer {
    @Override
    public Entities.RelationshipProfileEntity update(
            String contactId,
            Entities.RelationshipProfileEntity existing,
            List<Entities.MessageEntity> recentContext
    ) {
        Entities.RelationshipProfileEntity profile =
                existing == null ? new Entities.RelationshipProfileEntity() : existing;
        profile.contactId = contactId;
        if (recentContext.isEmpty()) {
            profile.lastUpdated = System.currentTimeMillis();
            return profile;
        }
        long first = recentContext.stream().mapToLong(item -> item.timestamp).min().orElse(0);
        long latest = recentContext.stream().mapToLong(item -> item.timestamp).max().orElse(0);
        if (profile.firstInteractionAt == 0 || first < profile.firstInteractionAt) {
            profile.firstInteractionAt = first;
        }
        profile.latestInteractionAt = Math.max(profile.latestInteractionAt, latest);
        String context = recentContext.stream()
                .sorted(Comparator.comparingLong(item -> item.timestamp))
                .map(item -> item.text == null ? "" : item.text)
                .reduce("", (left, right) -> left + " " + right)
                .toLowerCase(Locale.ROOT);
        profile.category = inferCategory(context, profile.category);
        profile.status = inferStatus(context);
        profile.strengthScore = Math.min(1,
                Math.max(profile.strengthScore * 0.9, recentContext.size() / 30.0));
        profile.personSummary = "Locally inferred relationship context; semantic review pending Â Search.";
        if (context.matches(".*(i('|’)ll|i will) (contact|message|let you know).*")) {
            profile.contactPromises = "Contact indicated they will initiate when appropriate.";
            profile.openCommitments = "WAITING_ON_CONTACT";
        }
        if (context.matches(".*(we('|’)ll|we will|you are) (confirm|booked|confirmed).*")) {
            profile.pastConfirmations = "Recent confirmation language detected.";
        }
        profile.lastUpdated = System.currentTimeMillis();
        return profile;
    }

    static String inferStatus(String context) {
        if (context.matches(".*(not interested|no longer|cannot offer|won('|’)t be able|rejected).*")) {
            return "REJECTED";
        }
        if (context.matches(".*(i('|’)ll|i will) (contact|message|let you know).*")) {
            return "WAITING";
        }
        if (context.matches(".*(confirmed|booked|see you at|locked in).*")) {
            return "CONFIRMED";
        }
        return "ACTIVE";
    }

    static String inferCategory(String context, String fallback) {
        if (context.matches(".*(promoter|organiser|organizer|festival|venue|booking).*")) {
            return "PROMOTER_OR_ORGANISER";
        }
        if (context.matches(".*(radio|interview|press|media|podcast).*")) return "MEDIA";
        if (context.matches(".*(producer|studio|mix|master).*")) return "PRODUCER";
        if (context.matches(".*(dj|artist|collab|feature|songwriter).*")) return "ARTIST_PEER";
        return fallback == null ? "UNKNOWN" : fallback;
    }
}

