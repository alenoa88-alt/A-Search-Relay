package com.asearch.relay;

import com.asearch.relay.data.Entities;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class LocalCandidateBrain implements ManagerBrain {
    private static final String[] CAREER = {
            "artist", "dj", "producer", "songwriter", "promoter", "festival", "venue",
            "booking", "manager", "agency", "radio", "media", "videographer", "photographer",
            "sponsor", "council", "entertainment", "music", "studio", "label"
    };
    private static final String[] OPPORTUNITY = {
            "gig", "perform", "performance", "support slot", "festival", "booking", "booked",
            "collab", "collaboration", "feature", "casting", "sponsorship", "radio", "interview",
            "event", "venue", "lineup", "show"
    };
    private static final String[] ACTION = {
            "call me", "give me a call", "meeting", "meet", "send me", "deadline",
            "submit", "confirm", "let me know", "reply", "follow up", "follow-up"
    };
    private static final String[] MONEY = {
            "fee", "payment", "budget", "price", "rate", "contract", "agreement", "deposit"
    };
    private static final String[] HUMAN = {
            "call", "attend", "travel", "rehearse", "perform", "film", "meet", "sign",
            "approve spending", "upload", "creative decision"
    };

    @Override
    public ManagerCandidate analyze(
            Entities.MessageEntity current,
            List<Entities.MessageEntity> recentContext,
            Entities.RelationshipProfileEntity relationship
    ) {
        if (isAutomatedAcknowledgement(current == null ? null : current.text)) {
            ManagerCandidate acknowledgement = new ManagerCandidate();
            acknowledgement.careerRelevant = true;
            acknowledgement.opportunity = false;
            acknowledgement.followUp = false;
            acknowledgement.humanRequired = false;
            acknowledgement.category = "ACKNOWLEDGEMENT";
            acknowledgement.priority = "LOW";
            acknowledgement.status = "WAITING";
            acknowledgement.whyItMatters = "This appears to be an automated acknowledgement, not a substantive opportunity reply.";
            acknowledgement.recommendedNextAction = "Wait for a substantive response; no immediate action is required.";
            acknowledgement.confidence = 0.94;
            return acknowledgement;
        }
        String context = buildContext(current, recentContext);
        int careerSignals = matches(context, CAREER).size();
        int opportunitySignals = matches(context, OPPORTUNITY).size();
        int actionSignals = matches(context, ACTION).size();
        int moneySignals = matches(context, MONEY).size();
        boolean knownCareerRelationship = relationship != null
                && relationship.category != null
                && !"UNKNOWN".equals(relationship.category);

        ManagerCandidate candidate = new ManagerCandidate();
        candidate.careerRelevant = knownCareerRelationship
                || careerSignals >= 2
                || (careerSignals >= 1 && opportunitySignals >= 1)
                || opportunitySignals >= 2;
        if (!candidate.careerRelevant) {
            candidate.category = "IRRELEVANT_PERSONAL";
            candidate.status = "IGNORE";
            candidate.confidence = 0.75;
            return candidate;
        }

        candidate.opportunity = opportunitySignals > 0;
        candidate.followUp = actionSignals > 0 && !explicitWaitingOrRejection(context);
        candidate.humanRequired = matches(context, HUMAN).size() > 0;
        candidate.category = category(context, opportunitySignals, moneySignals);
        candidate.priority = priority(context);
        candidate.status = status(context, actionSignals);
        candidate.whyItMatters = why(candidate.category);
        candidate.recommendedNextAction = recommendation(candidate);
        candidate.confidence = Math.min(0.92,
                0.38 + careerSignals * 0.09 + opportunitySignals * 0.09 + actionSignals * 0.07);
        return candidate;
    }

    static boolean isAutomatedAcknowledgement(String text) {
        if (text == null) return false;
        String value = text.toLowerCase(Locale.ROOT);
        int signals = 0;
        if (value.contains("thanks for messaging") || value.contains("thank you for your message")) signals++;
        if (value.contains("we'll get back") || value.contains("we will get back")) signals++;
        if (value.contains("as responsive as possible") || value.contains("automated reply")
                || value.contains("automatic reply")) signals++;
        return signals >= 2;
    }

    static boolean explicitWaitingOrRejection(String context) {
        return context.matches(".*(i('|’)ll|i will) (contact|message|let you know).*")
                || context.matches(".*(not interested|cannot offer|no longer|rejected).*");
    }

    private static String buildContext(
            Entities.MessageEntity current,
            List<Entities.MessageEntity> recent
    ) {
        StringBuilder value = new StringBuilder();
        for (Entities.MessageEntity message : recent) {
            if (message.text != null) value.append(' ').append(message.text);
        }
        if (current.text != null) value.append(' ').append(current.text);
        return value.toString().toLowerCase(Locale.ROOT);
    }

    private static Set<String> matches(String text, String[] terms) {
        Set<String> matches = new HashSet<>();
        for (String term : terms) {
            if (text.contains(term)) matches.add(term);
        }
        return matches;
    }

    private static String category(String context, int opportunitySignals, int moneySignals) {
        if (context.matches(".*(booking|booked|gig|perform|lineup).*")) return "BOOKING";
        if (context.matches(".*(radio|interview|press|podcast).*")) return "MEDIA";
        if (context.matches(".*(sponsor|sponsorship).*")) return "SPONSORSHIP";
        if (context.matches(".*(collab|collaboration|feature).*")) return "COLLABORATION";
        if (context.matches(".*(call me|give me a call).*")) return "CALL REQUEST";
        if (context.matches(".*(deadline|submit by|submission).*")) return "DEADLINE";
        if (context.matches(".*(meeting|meet).*")) return "MEETING";
        if (context.matches(".*(confirmed|locked in).*")) return "CONFIRMATION";
        if (moneySignals > 0) return "PAYMENT/FEE";
        return opportunitySignals > 0 ? "CREATIVE OPPORTUNITY" : "FOLLOW-UP";
    }

    private static String priority(String context) {
        if (context.matches(".*(urgent|today|asap|immediately|deadline).*")) return "URGENT";
        if (context.matches(".*(tomorrow|this weekend|confirm|call me).*")) return "HIGH";
        return "NORMAL";
    }

    private static String status(String context, int actionSignals) {
        if (context.matches(".*(confirmed|booked|locked in).*")) return "CONFIRMED";
        if (context.matches(".*(i('|’)ll|i will) (contact|message|let you know).*")) return "WAITING";
        if (actionSignals > 0) return "ACTION REQUIRED";
        return "NEW";
    }

    private static String why(String category) {
        switch (category) {
            case "BOOKING": return "A possible performance or booking discussion needs manager attention.";
            case "CALL REQUEST": return "A professional contact requested a personal phone call.";
            case "DEADLINE": return "A time-sensitive career item may expire without action.";
            case "PAYMENT/FEE": return "Money or terms require careful human review.";
            case "COLLABORATION": return "A potential creative relationship may advance Ale's artist career.";
            default: return "Multiple career-context signals indicate this may be manager-relevant.";
        }
    }

    private static String recommendation(ManagerCandidate candidate) {
        if (candidate.humanRequired) {
            return "Review the source conversation and complete the personal action at an appropriate Malta time.";
        }
        if ("WAITING".equals(candidate.status)) {
            return "Monitor for their reply; avoid chasing without a new reason.";
        }
        return "Review the evidence and decide the next manager action.";
    }
}


