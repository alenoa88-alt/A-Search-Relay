package com.asearch.relay;

import com.asearch.relay.data.Entities;

import java.util.List;

interface ManagerBrain {
    ManagerCandidate analyze(
            Entities.MessageEntity current,
            List<Entities.MessageEntity> recentContext,
            Entities.RelationshipProfileEntity relationship
    );
}

interface ManagerDataSource {
    List<BeeperProviderDataSource.RemoteChat> loadAllChats();
    List<BeeperProviderDataSource.RemoteMessage> loadMessagePage(String roomId, int limit, int offset);
}

interface ManagerDecisionRepository {
    void saveDecision(Entities.ManagerDecisionEntity decision);
}

interface ContactResearchProvider {
    ResearchResult researchPublicProfessionalContext(String contactId, String professionalContext);
}

interface CommunicationStyleAnalyzer {
    Entities.CommunicationStyleProfileEntity analyze(
            String contactId,
            List<Entities.MessageEntity> messages
    );
}

interface RelationshipAnalyzer {
    Entities.RelationshipProfileEntity update(
            String contactId,
            Entities.RelationshipProfileEntity existing,
            List<Entities.MessageEntity> recentContext
    );
}

interface ReplyComposer {
    FutureMessaging.MessageDraft compose(ReplyContext context);
}

final class ResearchResult {
    final String summary;
    final String sourcesJson;
    final double confidence;

    ResearchResult(String summary, String sourcesJson, double confidence) {
        this.summary = summary;
        this.sourcesJson = sourcesJson;
        this.confidence = confidence;
    }
}

final class ReplyContext {
    final String contactId;
    final String objective;
    final TimeContext timeContext;
    final LanguageContext languageContext;

    ReplyContext(
            String contactId,
            String objective,
            TimeContext timeContext,
            LanguageContext languageContext
    ) {
        this.contactId = contactId;
        this.objective = objective;
        this.timeContext = timeContext;
        this.languageContext = languageContext;
    }
}

final class ManagerCandidate {
    String category;
    String priority = "NORMAL";
    String status = "NEW";
    String whyItMatters;
    String recommendedNextAction;
    boolean careerRelevant;
    boolean opportunity;
    boolean followUp;
    boolean humanRequired;
    double confidence;
}

