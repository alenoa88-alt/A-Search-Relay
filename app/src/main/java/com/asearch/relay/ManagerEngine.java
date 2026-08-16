package com.asearch.relay;

import android.content.Context;

import com.asearch.relay.data.Entities;
import com.asearch.relay.data.ManagerDao;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ManagerEngine {
    public static final String INITIAL_IMPORT_COMPLETE = "initial_import_complete";
    public static final String LAST_RECONCILIATION = "last_reconciliation";
    private static final int MESSAGE_PAGE_SIZE = 100;
    private static final int MAX_INITIAL_MESSAGE_PAGES = 500;
    private static final int MAX_INCREMENTAL_MESSAGE_PAGES = 20;

    public interface Progress {
        void update(String message, int completed, int total);
    }

    public static final class Result {
        public int conversationsChecked;
        public int conversationsUpdated;
        public int messagesImported;
        public int opportunitiesCreated;
        public int actionsCreated;
        public int followUpsCreated;
        public int unrelatedIgnored;
        public boolean initialImport;

        public String summary() {
            return conversationsUpdated + " conversations updated · "
                    + opportunitiesCreated + " possible opportunities · "
                    + actionsCreated + " actions required · "
                    + unrelatedIgnored + " unrelated conversations ignored";
        }
    }

    private final Context context;
    private final ManagerDao dao;
    private final ManagerDataSource dataSource;
    private final ManagerBrain brain;
    private final CommunicationStyleAnalyzer styleAnalyzer;
    private final RelationshipAnalyzer relationshipAnalyzer;
    private final LanguageDetector languageDetector = new LanguageDetector();

    public ManagerEngine(
            Context context,
            ManagerDao dao,
            ManagerDataSource dataSource
    ) {
        this.context = context.getApplicationContext();
        this.dao = dao;
        this.dataSource = dataSource;
        this.brain = new LocalCandidateBrain();
        this.styleAnalyzer = new LocalCommunicationStyleAnalyzer();
        this.relationshipAnalyzer = new LocalRelationshipAnalyzer();
    }

    public boolean isInitialImportComplete() {
        Entities.SyncStateEntity state = dao.getSyncState(INITIAL_IMPORT_COMPLETE);
        return state != null && "true".equals(state.value);
    }

    public Result runInitialImport(Progress progress) {
        return run(true, progress);
    }

    public Result reconcile(Progress progress) {
        if (!isInitialImportComplete()) {
            Result result = new Result();
            result.initialImport = false;
            return result;
        }
        return run(false, progress);
    }

    private Result run(boolean initial, Progress progress) {
        Result result = new Result();
        result.initialImport = initial;
        List<BeeperProviderDataSource.RemoteChat> chats = dataSource.loadAllChats();
        int total = chats.size();
        int completed = 0;
        for (BeeperProviderDataSource.RemoteChat remote : chats) {
            completed++;
            result.conversationsChecked++;
            if (progress != null) {
                progress.update(
                        remote.title == null ? remote.roomId : remote.title,
                        completed,
                        total
                );
            }
            Entities.ConversationEntity existing = dao.getConversation(remote.roomId);
            boolean changed = initial
                    || existing == null
                    || remote.timestamp > existing.lastActivityAt
                    || remote.unreadCount != existing.unreadCount;
            if (!changed) continue;
            processConversation(remote, existing, initial, result);
        }

        long now = System.currentTimeMillis();
        upsertState(LAST_RECONCILIATION, String.valueOf(now), now);
        if (initial) upsertState(INITIAL_IMPORT_COMPLETE, "true", now);
        Entities.ActivityEntity activity = new Entities.ActivityEntity();
        activity.type = initial ? "INITIAL_IMPORT" : "RECONCILIATION";
        activity.summary = initial
                ? "Built Â Search relationship index from " + result.conversationsChecked + " conversations."
                : result.summary();
        activity.timestamp = now;
        dao.insertActivity(activity);
        return result;
    }

    private void processConversation(
            BeeperProviderDataSource.RemoteChat remote,
            Entities.ConversationEntity existing,
            boolean initial,
            Result result
    ) {
        long checkpoint = existing == null ? 0 : existing.lastProcessedTimestamp;
        String checkpointMessageId = existing == null ? null : existing.lastProcessedMessageId;
        List<Entities.MessageEntity> imported = new ArrayList<>();
        long newestTimestamp = checkpoint;
        String newestMessageId = checkpointMessageId;
        boolean reachedCheckpoint = false;
        int maxPages = initial ? MAX_INITIAL_MESSAGE_PAGES : MAX_INCREMENTAL_MESSAGE_PAGES;

        for (int page = 0; page < maxPages; page++) {
            List<BeeperProviderDataSource.RemoteMessage> messages = dataSource.loadMessagePage(
                    remote.roomId,
                    MESSAGE_PAGE_SIZE,
                    page * MESSAGE_PAGE_SIZE
            );
            if (messages.isEmpty()) break;
            for (BeeperProviderDataSource.RemoteMessage source : messages) {
                boolean atOrBeforeCheckpoint = !initial && checkpoint > 0
                        && source.timestamp <= checkpoint;
                if (atOrBeforeCheckpoint) reachedCheckpoint = true;
                Entities.MessageEntity entity = toMessage(source);
                long inserted = dao.insertMessage(entity);
                if (inserted != -1) {
                    entity.id = inserted;
                    imported.add(entity);
                    result.messagesImported++;
                }
                if (source.timestamp >= newestTimestamp) {
                    newestTimestamp = source.timestamp;
                    newestMessageId = source.messageId;
                }
            }
            if (messages.size() < MESSAGE_PAGE_SIZE || (!initial && reachedCheckpoint)) break;
        }

        Entities.ContactEntity contact = dao.getContact(remote.roomId);
        if (contact == null) {
            contact = new Entities.ContactEntity();
            contact.contactId = remote.roomId;
            contact.firstInteractionAt = imported.stream()
                    .mapToLong(item -> item.timestamp)
                    .min()
                    .orElse(remote.timestamp);
        }
        contact.displayName = remote.title == null ? "Unknown contact" : remote.title;
        contact.primaryNetwork = remote.network;
        contact.lastInteractionAt = Math.max(contact.lastInteractionAt, remote.timestamp);

        Entities.ConversationEntity conversation =
                existing == null ? new Entities.ConversationEntity() : existing;
        conversation.roomId = remote.roomId;
        conversation.contactId = contact.contactId;
        conversation.network = remote.network;
        conversation.protocolRaw = remote.protocolRaw;
        conversation.title = remote.title;
        conversation.lastSenderId = remote.lastSenderId;
        conversation.lastActivityAt = remote.timestamp;
        conversation.lastMessageId = newestMessageId;
        conversation.lastProcessedTimestamp = Math.max(checkpoint, newestTimestamp);
        conversation.lastProcessedMessageId = newestMessageId;
        conversation.oneToOne = remote.oneToOne;
        conversation.unreadCount = remote.unreadCount;
        conversation.initialImportComplete = initial || conversation.initialImportComplete;

        List<Entities.MessageEntity> contextMessages = dao.getRecentMessages(remote.roomId, 40);
        Entities.RelationshipProfileEntity relationship = relationshipAnalyzer.update(
                contact.contactId,
                dao.getRelationshipProfile(contact.contactId),
                contextMessages
        );
        dao.upsertRelationshipProfile(relationship);
        contact.category = relationship.category;
        contact.relationshipStatus = relationship.status;

        Entities.CommunicationStyleProfileEntity style = styleAnalyzer.analyze(
                contact.contactId,
                dao.getAleStyleMessages(remote.roomId, 80)
        );
        dao.upsertStyleProfile(style);
        contact.styleConfidence = style.confidenceScore;

        int careerSignals = conversation.careerSignalScore;
        imported.sort(Comparator.comparingLong(item -> item.timestamp));
        List<Entities.MessageEntity> candidateMessages = selectCandidateMessages(imported, initial);
        for (Entities.MessageEntity message : candidateMessages) {
            ManagerCandidate candidate = brain.analyze(message, contextMessages, relationship);
            saveDecision(message, candidate);
            if (!candidate.careerRelevant) {
                result.unrelatedIgnored++;
                continue;
            }
            careerSignals++;
            contact.careerRelevant = true;
            if (candidate.opportunity && createOpportunity(contact, conversation, message, candidate)) {
                result.opportunitiesCreated++;
            }
            if (!"WAITING".equals(candidate.status)
                    && createAction(contact, conversation, message, candidate)) {
                result.actionsCreated++;
            }
            if (candidate.followUp && createFollowUp(contact, conversation, message, candidate)) {
                result.followUpsCreated++;
            }
        }
        conversation.careerSignalScore = careerSignals;
        contact.openActionCount = countActionsForContact(contact.contactId);
        contact.openOpportunityCount = countOpportunitiesForContact(contact.contactId);
        dao.upsertContact(contact);
        dao.upsertConversation(conversation);

        if (!imported.isEmpty()) {
            result.conversationsUpdated++;
            Entities.ActivityEntity activity = new Entities.ActivityEntity();
            activity.type = "BEEPER_IMPORT";
            activity.summary = imported.size() + " new messages imported from " + contact.displayName + ".";
            activity.roomId = remote.roomId;
            activity.timestamp = System.currentTimeMillis();
            dao.insertActivity(activity);
        }
    }

    private Entities.MessageEntity toMessage(BeeperProviderDataSource.RemoteMessage source) {
        Entities.MessageEntity entity = new Entities.MessageEntity();
        entity.roomId = source.roomId;
        entity.messageId = source.messageId;
        entity.senderId = source.senderId;
        entity.senderDisplayName = source.senderDisplayName;
        entity.sentByMe = source.sentByMe;
        entity.text = source.text;
        entity.messageType = source.messageType;
        entity.timestamp = source.timestamp;
        entity.localTime = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
                Instant.ofEpochMilli(source.timestamp).atZone(TimeContext.MALTA)
        );
        entity.language = languageDetector.detect(source.text).primaryLanguage;
        entity.deleted = source.deleted;
        entity.importedAt = System.currentTimeMillis();
        return entity;
    }

    static List<Entities.MessageEntity> selectCandidateMessages(
            List<Entities.MessageEntity> imported,
            boolean initial
    ) {
        List<Entities.MessageEntity> selected = new ArrayList<>();
        if (!initial) {
            selected.addAll(imported);
            return selected;
        }
        for (int index = imported.size() - 1; index >= 0; index--) {
            Entities.MessageEntity message = imported.get(index);
            if (!message.sentByMe && !message.deleted && message.text != null
                    && !message.text.trim().isEmpty()) {
                selected.add(message);
                break;
            }
        }
        return selected;
    }
    public static boolean shouldImport(long messageTimestamp, long checkpointTimestamp) {
        return checkpointTimestamp <= 0 || messageTimestamp > checkpointTimestamp;
    }

    private void saveDecision(Entities.MessageEntity message, ManagerCandidate candidate) {
        Entities.ManagerDecisionEntity decision = new Entities.ManagerDecisionEntity();
        decision.sourceMessageId = message.messageId;
        decision.decisionType = candidate.careerRelevant ? "CANDIDATE" : "IGNORED";
        decision.category = candidate.category;
        decision.reasoning = candidate.whyItMatters;
        decision.intentConfidence = candidate.confidence;
        decision.relationshipConfidence = candidate.careerRelevant ? 0.6 : 0.3;
        decision.styleConfidence = 0;
        decision.actionConfidence = candidate.confidence;
        decision.requiresHumanReview = candidate.humanRequired || candidate.confidence < 0.75;
        decision.createdAt = System.currentTimeMillis();
        dao.insertDecision(decision);
    }

    private boolean createAction(
            Entities.ContactEntity contact,
            Entities.ConversationEntity conversation,
            Entities.MessageEntity message,
            ManagerCandidate candidate
    ) {
        Entities.ActionEntity action = new Entities.ActionEntity();
        action.contactId = contact.contactId;
        action.contactName = contact.displayName;
        action.source = conversation.network;
        action.priority = candidate.priority;
        action.status = candidate.status;
        action.category = candidate.category;
        action.whatHappened = message.text;
        action.whyItMatters = candidate.whyItMatters;
        action.recommendedNextAction = candidate.recommendedNextAction;
        action.happenedAt = message.timestamp;
        action.assignedTo = candidate.humanRequired ? "ÂDMIN" : "Â SEARCH";
        action.humanRequired = candidate.humanRequired;
        action.roomId = conversation.roomId;
        action.relevantMessageId = message.messageId;
        action.updatedAt = System.currentTimeMillis();
        long id = dao.insertAction(action);
        if (id != -1 && candidate.confidence >= 0.7 && isInitialImportComplete()) {
            NotificationHelper.notifyCandidate(context, id, contact.displayName, candidate.category);
        }
        return id != -1;
    }

    private boolean createOpportunity(
            Entities.ContactEntity contact,
            Entities.ConversationEntity conversation,
            Entities.MessageEntity message,
            ManagerCandidate candidate
    ) {
        Entities.OpportunityEntity opportunity = new Entities.OpportunityEntity();
        opportunity.contactId = contact.contactId;
        opportunity.contactName = contact.displayName;
        opportunity.type = candidate.category;
        opportunity.source = conversation.network;
        opportunity.status = "CONFIRMED".equals(candidate.status) ? "CONFIRMED" : "NEW";
        opportunity.summary = message.text;
        opportunity.nextAction = candidate.recommendedNextAction;
        opportunity.lastActivityAt = message.timestamp;
        opportunity.roomId = conversation.roomId;
        opportunity.relevantMessageId = message.messageId;
        return dao.insertOpportunity(opportunity) != -1;
    }

    private boolean createFollowUp(
            Entities.ContactEntity contact,
            Entities.ConversationEntity conversation,
            Entities.MessageEntity message,
            ManagerCandidate candidate
    ) {
        Entities.FollowUpEntity followUp = new Entities.FollowUpEntity();
        followUp.contactId = contact.contactId;
        followUp.contactName = contact.displayName;
        followUp.waitingOn = message.sentByMe ? "CONTACT" : "ALE";
        followUp.lastOutgoingAt = message.sentByMe ? message.timestamp : 0;
        followUp.lastIncomingAt = message.sentByMe ? 0 : message.timestamp;
        followUp.followUpAt = message.timestamp + 2L * 24 * 60 * 60 * 1000;
        followUp.reason = candidate.category;
        followUp.status = "DUE";
        followUp.roomId = conversation.roomId;
        followUp.relevantMessageId = message.messageId;
        return dao.insertFollowUp(followUp) != -1;
    }

    private int countActionsForContact(String contactId) {
        int count = 0;
        for (Entities.ActionEntity item : dao.getOpenActions()) {
            if (contactId.equals(item.contactId)) count++;
        }
        return count;
    }

    private int countOpportunitiesForContact(String contactId) {
        int count = 0;
        for (Entities.OpportunityEntity item : dao.getOpportunities()) {
            if (contactId.equals(item.contactId)) count++;
        }
        return count;
    }

    private void upsertState(String key, String value, long now) {
        Entities.SyncStateEntity state = new Entities.SyncStateEntity();
        state.stateKey = key;
        state.value = value;
        state.updatedAt = now;
        dao.upsertSyncState(state);
    }
}
