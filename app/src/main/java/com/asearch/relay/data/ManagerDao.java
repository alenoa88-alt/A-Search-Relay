package com.asearch.relay.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface ManagerDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertContact(Entities.ContactEntity entity);

    @Query("SELECT * FROM contacts WHERE contactId = :contactId LIMIT 1")
    Entities.ContactEntity getContact(String contactId);

    @Query("SELECT * FROM contacts ORDER BY careerRelevant DESC, lastInteractionAt DESC")
    List<Entities.ContactEntity> getContacts();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertConversation(Entities.ConversationEntity entity);

    @Query("SELECT * FROM conversations WHERE roomId = :roomId LIMIT 1")
    Entities.ConversationEntity getConversation(String roomId);

    @Query("SELECT * FROM conversations ORDER BY lastActivityAt DESC")
    List<Entities.ConversationEntity> getConversations();

    @Query("SELECT * FROM conversations ORDER BY lastActivityAt DESC LIMIT :limit")
    List<Entities.ConversationEntity> getRecentConversations(int limit);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insertMessage(Entities.MessageEntity entity);

    @Query("SELECT COUNT(*) FROM messages WHERE roomId = :roomId AND messageId = :messageId")
    int messageCount(String roomId, String messageId);

    @Query("SELECT * FROM messages WHERE roomId = :roomId ORDER BY timestamp DESC LIMIT :limit")
    List<Entities.MessageEntity> getRecentMessages(String roomId, int limit);

    @Query("SELECT * FROM messages WHERE roomId = :roomId AND sentByMe = 1 ORDER BY timestamp DESC LIMIT :limit")
    List<Entities.MessageEntity> getAleStyleMessages(String roomId, int limit);

    @Query("SELECT COUNT(*) FROM messages")
    int totalMessageCount();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertStyleProfile(Entities.CommunicationStyleProfileEntity entity);

    @Query("SELECT * FROM communication_style_profiles WHERE contactId = :contactId LIMIT 1")
    Entities.CommunicationStyleProfileEntity getStyleProfile(String contactId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertRelationshipProfile(Entities.RelationshipProfileEntity entity);

    @Query("SELECT * FROM relationship_profiles WHERE contactId = :contactId LIMIT 1")
    Entities.RelationshipProfileEntity getRelationshipProfile(String contactId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertContactIntelligence(Entities.ContactIntelligenceEntity entity);

    @Query("SELECT * FROM contact_intelligence WHERE contactId = :contactId LIMIT 1")
    Entities.ContactIntelligenceEntity getContactIntelligence(String contactId);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insertAction(Entities.ActionEntity entity);

    @Update
    void updateAction(Entities.ActionEntity entity);

    @Query("SELECT * FROM actions WHERE status NOT IN ('DONE','IGNORE') ORDER BY CASE priority WHEN 'URGENT' THEN 0 WHEN 'HIGH' THEN 1 WHEN 'NORMAL' THEN 2 ELSE 3 END, happenedAt DESC")
    List<Entities.ActionEntity> getOpenActions();

    @Query("SELECT COUNT(*) FROM actions WHERE status NOT IN ('DONE','IGNORE')")
    int countOpenActions();

    @Query("SELECT COUNT(*) FROM actions WHERE status = :status")
    int countActionsByStatus(String status);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insertOpportunity(Entities.OpportunityEntity entity);

    @Query("SELECT * FROM opportunities WHERE status != 'ARCHIVED' ORDER BY lastActivityAt DESC")
    List<Entities.OpportunityEntity> getOpportunities();

    @Query("SELECT COUNT(*) FROM opportunities WHERE status = :status")
    int countOpportunitiesByStatus(String status);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertEvent(Entities.EventEntity entity);

    @Query("SELECT * FROM events WHERE status != 'ARCHIVED' ORDER BY startAt ASC")
    List<Entities.EventEntity> getEvents();

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insertFollowUp(Entities.FollowUpEntity entity);

    @Query("SELECT * FROM follow_ups WHERE status NOT IN ('DONE','CANCELLED') ORDER BY followUpAt ASC")
    List<Entities.FollowUpEntity> getOpenFollowUps();

    @Query("SELECT COUNT(*) FROM follow_ups WHERE status NOT IN ('DONE','CANCELLED')")
    int countOpenFollowUps();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertActivity(Entities.ActivityEntity entity);

    @Query("SELECT * FROM activity ORDER BY timestamp DESC LIMIT :limit")
    List<Entities.ActivityEntity> getActivity(int limit);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertSyncState(Entities.SyncStateEntity entity);

    @Query("SELECT * FROM sync_state WHERE stateKey = :key LIMIT 1")
    Entities.SyncStateEntity getSyncState(String key);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insertDecision(Entities.ManagerDecisionEntity entity);

    @Query("SELECT COUNT(*) FROM conversations")
    int totalConversationCount();

    @Query("SELECT MAX(timestamp) FROM activity WHERE type = :type")
    Long lastActivityOfType(String type);

    @Query("DELETE FROM messages")
    void deleteAllMessagesForTest();
}
