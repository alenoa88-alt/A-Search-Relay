package com.asearch.relay.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

public final class Entities {
    private Entities() {}

    @Entity(tableName = "contacts", indices = {@Index(value = {"displayName", "primaryNetwork"})})
    public static class ContactEntity {
        @PrimaryKey @NonNull public String contactId = "";
        public String displayName;
        public String category = "UNKNOWN";
        public String primaryNetwork;
        public String relationshipStatus = "UNASSESSED";
        public long firstInteractionAt;
        public long lastInteractionAt;
        public int openActionCount;
        public int openOpportunityCount;
        public double styleConfidence;
        public String intelligenceStatus = "NOT_RESEARCHED";
        public boolean careerRelevant;
    }

    @Entity(tableName = "conversations", indices = {
            @Index(value = {"lastActivityAt"}),
            @Index(value = {"contactId"})
    })
    public static class ConversationEntity {
        @PrimaryKey @NonNull public String roomId = "";
        public String contactId;
        public String network;
        public String protocolRaw;
        public String title;
        public String lastSenderId;
        public long lastActivityAt;
        public String lastMessageId;
        public long lastProcessedTimestamp;
        public String lastProcessedMessageId;
        public boolean oneToOne;
        public int unreadCount;
        public boolean initialImportComplete;
        public int careerSignalScore;
    }

    @Entity(tableName = "messages", indices = {
            @Index(value = {"roomId", "messageId"}, unique = true),
            @Index(value = {"roomId", "timestamp"}),
            @Index(value = {"senderId"})
    })
    public static class MessageEntity {
        @PrimaryKey(autoGenerate = true) public long id;
        @NonNull public String roomId = "";
        @NonNull public String messageId = "";
        public String senderId;
        public String senderDisplayName;
        public boolean sentByMe;
        public String text;
        public String messageType;
        public long timestamp;
        public String localTime;
        public String language;
        public boolean deleted;
        public long importedAt;
    }

    @Entity(tableName = "communication_style_profiles")
    public static class CommunicationStyleProfileEntity {
        @PrimaryKey @NonNull public String contactId = "";
        public int sampleCount;
        public String primaryLanguage;
        public String secondaryLanguage;
        public double formalityScore;
        public double averageMessageLength;
        public double emojiFrequency;
        public String commonEmojis;
        public String punctuationStyle;
        public String capitalizationStyle;
        public String commonTerms;
        public String greetingPatterns;
        public String closingPatterns;
        public String representativeMessageIds;
        public double confidenceScore;
        public long lastUpdated;
    }

    @Entity(tableName = "relationship_profiles")
    public static class RelationshipProfileEntity {
        @PrimaryKey @NonNull public String contactId = "";
        public String personSummary;
        public String howAleKnowsThem;
        public long firstInteractionAt;
        public String aleRequests;
        public String contactOffers;
        public String alePromises;
        public String contactPromises;
        public String pastOpportunities;
        public String pastRejections;
        public String pastConfirmations;
        public String openCommitments;
        public long latestInteractionAt;
        public String category = "UNKNOWN";
        public double strengthScore;
        public String status = "UNASSESSED";
        public long lastUpdated;
    }

    @Entity(tableName = "contact_intelligence")
    public static class ContactIntelligenceEntity {
        @PrimaryKey @NonNull public String contactId = "";
        public String publicIdentity;
        public String stageOrCompanyName;
        public String officialRole;
        public String professionalCategory;
        public String genre;
        public String biography;
        public String officialWebsite;
        public String currentProjects;
        public String relevanceToAle;
        public String contactApproach;
        public String sourcesJson;
        public String researchSummary;
        public String certainty = "UNCERTAIN";
        public double confidence;
        public long researchedAt;
        public long refreshAfter;
    }

    @Entity(tableName = "actions", indices = {
            @Index(value = {"status", "priority"}),
            @Index(value = {"relevantMessageId", "category"}, unique = true)
    })
    public static class ActionEntity {
        @PrimaryKey(autoGenerate = true) public long id;
        public String contactId;
        public String contactName;
        public String source;
        public String priority = "NORMAL";
        public String status = "NEW";
        public String category;
        public String whatHappened;
        public String whyItMatters;
        public String recommendedNextAction;
        public long happenedAt;
        public long deadlineAt;
        public String eventOrOpportunity;
        public String assignedTo = "Â SEARCH";
        public boolean humanRequired;
        public String roomId;
        public String relevantMessageId;
        public long updatedAt;
    }

    @Entity(tableName = "opportunities", indices = {
            @Index(value = {"status", "lastActivityAt"}),
            @Index(value = {"relevantMessageId", "type"}, unique = true)
    })
    public static class OpportunityEntity {
        @PrimaryKey(autoGenerate = true) public long id;
        public String contactId;
        public String contactName;
        public String type;
        public String source;
        public String status = "NEW";
        public String summary;
        public String nextAction;
        public long deadlineAt;
        public long lastActivityAt;
        public String roomId;
        public String relevantMessageId;
    }

    @Entity(tableName = "events", indices = {@Index(value = {"startAt", "status"})})
    public static class EventEntity {
        @PrimaryKey(autoGenerate = true) public long id;
        public String title;
        public String type;
        public String status = "PENDING";
        public long startAt;
        public long endAt;
        public String location;
        public String contactId;
        public String roomId;
        public String relevantMessageId;
        public String notes;
    }

    @Entity(tableName = "follow_ups", indices = {
            @Index(value = {"status", "followUpAt"}),
            @Index(value = {"relevantMessageId"}, unique = true)
    })
    public static class FollowUpEntity {
        @PrimaryKey(autoGenerate = true) public long id;
        public String contactId;
        public String contactName;
        public long lastIncomingAt;
        public long lastOutgoingAt;
        public String waitingOn = "UNKNOWN";
        public long followUpAt;
        public String reason;
        public String status = "DUE";
        public String roomId;
        public String relevantMessageId;
    }

    @Entity(tableName = "activity", indices = {@Index(value = {"timestamp"})})
    public static class ActivityEntity {
        @PrimaryKey(autoGenerate = true) public long id;
        public String type;
        public String summary;
        public String roomId;
        public String relatedEntityType;
        public long relatedEntityId;
        public long timestamp;
    }

    @Entity(tableName = "sync_state")
    public static class SyncStateEntity {
        @PrimaryKey @NonNull public String stateKey = "";
        public String value;
        public long updatedAt;
    }

    @Entity(tableName = "manager_decisions", indices = {
            @Index(value = {"sourceMessageId", "decisionType"}, unique = true)
    })
    public static class ManagerDecisionEntity {
        @PrimaryKey(autoGenerate = true) public long id;
        public String sourceMessageId;
        public String decisionType;
        public String category;
        public String reasoning;
        public double intentConfidence;
        public double relationshipConfidence;
        public double styleConfidence;
        public double actionConfidence;
        public boolean requiresHumanReview;
        public long createdAt;
    }
}
