package com.asearch.relay;

import com.asearch.relay.data.Entities;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = android.app.Application.class)
public class LocalIntelligenceTest {
    @Test
    public void styleSamplesUseOnlyAleSentMessages() {
        Entities.MessageEntity incoming = message("incoming", false, "Hey bro 🔥", 100);
        Entities.MessageEntity outgoing = message("outgoing", true, "thanks bro 🙏", 200);
        List<Entities.MessageEntity> selected =
                LocalCommunicationStyleAnalyzer.selectAleSamples(List.of(incoming, outgoing), 20);
        assertEquals(1, selected.size());
        assertEquals("outgoing", selected.get(0).messageId);

        Entities.CommunicationStyleProfileEntity profile =
                new LocalCommunicationStyleAnalyzer().analyze("contact", List.of(incoming, outgoing));
        assertEquals(1, profile.sampleCount);
        assertTrue(profile.commonTerms.contains("bro"));
    }

    @Test
    public void candidateGenerationRequiresCareerContext() {
        LocalCandidateBrain brain = new LocalCandidateBrain();
        Entities.MessageEntity personal = message("p", false, "what are we eating later", 100);
        ManagerCandidate ignored = brain.analyze(personal, List.of(personal), null);
        assertFalse(ignored.careerRelevant);
        assertEquals("IGNORE", ignored.status);

        Entities.MessageEntity booking = message(
                "b",
                false,
                "Festival promoter wants to book your performance, call me tomorrow",
                200
        );
        ManagerCandidate candidate = brain.analyze(booking, List.of(booking), null);
        assertTrue(candidate.careerRelevant);
        assertTrue(candidate.opportunity);
        assertTrue(candidate.humanRequired);
        assertEquals("BOOKING", candidate.category);
        assertEquals("ACTION REQUIRED", candidate.status);
    }

    @Test
    public void waitingPromiseDoesNotCreateFollowUp() {
        Entities.MessageEntity waiting = message(
                "w",
                false,
                "I will contact you when another festival booking comes up",
                100
        );
        ManagerCandidate candidate =
                new LocalCandidateBrain().analyze(waiting, List.of(waiting), null);
        assertTrue(candidate.careerRelevant);
        assertEquals("WAITING", candidate.status);
        assertFalse(candidate.followUp);
        assertTrue(LocalCandidateBrain.explicitWaitingOrRejection(waiting.text.toLowerCase()));
    }

    @Test
    public void automatedAcknowledgementIsWaitingNotOpportunity() {
        Entities.MessageEntity acknowledgement = message(
                "auto",
                false,
                "Thanks for messaging us. We try to be as responsive as possible. We'll get back to you soon.",
                100
        );
        ManagerCandidate candidate = new LocalCandidateBrain().analyze(
                acknowledgement,
                List.of(acknowledgement),
                null
        );
        assertTrue(LocalCandidateBrain.isAutomatedAcknowledgement(acknowledgement.text));
        assertEquals("ACKNOWLEDGEMENT", candidate.category);
        assertEquals("WAITING", candidate.status);
        assertFalse(candidate.opportunity);
        assertFalse(candidate.followUp);
        assertFalse(candidate.humanRequired);
    }

    @Test
    public void chatGptHandoffPreservesHumanBoundaryAndEvidence() {
        ChatGptHandoff.Evidence evidence = new ChatGptHandoff.Evidence();
        evidence.contact = "Venue Contact";
        evidence.source = "Beeper";
        evidence.itemType = "CALL REQUEST";
        evidence.status = "ACTION REQUIRED";
        evidence.priority = "HIGH";
        evidence.relevantText = "Give me a call tomorrow";
        evidence.whyItMatters = "A professional contact requested a personal phone call.";
        evidence.recommendedNextAction = "Ale should review the source conversation.";
        evidence.humanRequired = true;

        String prompt = ChatGptHandoff.buildPrompt(
                evidence,
                List.of(message("call", false, "Give me a call tomorrow", 100))
        );
        assertTrue(prompt.contains("Venue Contact"));
        assertTrue(prompt.contains("CONTACT: Give me a call tomorrow"));
        assertTrue(prompt.contains("HUMAN REQUIRED"));
        assertTrue(prompt.contains("do not say that it has happened"));
        assertTrue(prompt.contains("Do not claim"));
    }

    @Test
    public void checkpointRulesImportOnlyNewerTimestamps() {
        assertTrue(ManagerEngine.shouldImport(200, 100));
        assertFalse(ManagerEngine.shouldImport(100, 100));
        assertFalse(ManagerEngine.shouldImport(99, 100));
        assertTrue(ManagerEngine.shouldImport(1, 0));
    }

    @Test
    public void timeContextUsesEuropeMaltaAndMessageReferenceDate() {
        long reference = Instant.parse("2026-08-15T00:30:00Z").toEpochMilli();
        TimeContext context = TimeContext.at(reference, 0);
        assertEquals("LATE_NIGHT", context.dayPart);
        assertEquals("SUNDAY", context.resolveRelativeDay("call me tomorrow").getDayOfWeek().name());
        assertFalse(context.isReasonableProfessionalCallTime());
    }

    @Test
    public void relationshipStatePersistsWaitingMeaning() {
        Entities.MessageEntity message = message(
                "r",
                false,
                "I will contact you when a venue booking comes up",
                100
        );
        Entities.RelationshipProfileEntity profile = new LocalRelationshipAnalyzer().update(
                "contact",
                null,
                List.of(message)
        );
        assertEquals("WAITING", profile.status);
        assertEquals("WAITING_ON_CONTACT", profile.openCommitments);
        assertEquals("PROMOTER_OR_ORGANISER", profile.category);
    }

    @Test
    public void fallbackMessageIdentityIsStable() {
        BeeperProviderDataSource.RemoteMessage message = new BeeperProviderDataSource.RemoteMessage();
        message.roomId = "room";
        message.timestamp = 1234;
        message.senderId = "sender";
        message.text = "hello";
        assertEquals(
                BeeperProviderDataSource.stableFallbackId(message),
                BeeperProviderDataSource.stableFallbackId(message)
        );
    }

    @Test
    public void initialImportTriagesOnlyTheLatestIncomingMessage() {
        Entities.MessageEntity firstIncoming = message("first", false, "festival booking", 100);
        Entities.MessageEntity latestIncoming = message("latest", false, "call me tomorrow", 200);
        Entities.MessageEntity outgoing = message("outgoing", true, "thanks bro", 300);

        List<Entities.MessageEntity> selected = ManagerEngine.selectCandidateMessages(
                List.of(firstIncoming, latestIncoming, outgoing),
                true
        );

        assertEquals(1, selected.size());
        assertEquals("latest", selected.get(0).messageId);
    }

    @Test
    public void incrementalImportTriagesEveryUnseenMessage() {
        List<Entities.MessageEntity> messages = List.of(
                message("one", false, "festival", 100),
                message("two", true, "thanks", 200)
        );
        assertEquals(2, ManagerEngine.selectCandidateMessages(messages, false).size());
    }
    private static Entities.MessageEntity message(
            String id,
            boolean sentByMe,
            String text,
            long timestamp
    ) {
        Entities.MessageEntity entity = new Entities.MessageEntity();
        entity.roomId = "room";
        entity.messageId = id;
        entity.sentByMe = sentByMe;
        entity.text = text;
        entity.timestamp = timestamp;
        return entity;
    }
}
