package com.asearch.relay;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.asearch.relay.data.Entities;
import com.asearch.relay.data.ManagerDao;
import com.asearch.relay.data.ManagerDatabase;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = android.app.Application.class)
public class ManagerEngineRoomTest {
    private Context context;
    private ManagerDatabase database;
    private ManagerDao dao;
    private FakeDataSource source;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, ManagerDatabase.class)
                .allowMainThreadQueries()
                .build();
        dao = database.managerDao();
        source = new FakeDataSource();
    }

    @After
    public void tearDown() {
        database.close();
    }

    @Test
    public void initialAndDeltaImportsDeduplicateAndCheckpoint() {
        source.chat.timestamp = 1000;
        source.messages.add(remote("m1", false, 1000, "music promoter festival booking"));
        ManagerEngine engine = new ManagerEngine(context, dao, source);

        ManagerEngine.Result initial = engine.runInitialImport(null);
        assertTrue(initial.initialImport);
        assertEquals(1, dao.totalMessageCount());
        assertTrue(engine.isInitialImportComplete());
        assertEquals(1000, dao.getConversation("room").lastProcessedTimestamp);

        source.chat.timestamp = 2000;
        source.messages.add(0, remote("m2", true, 2000, "thanks bro"));
        ManagerEngine.Result delta = engine.reconcile(null);
        assertEquals(1, delta.messagesImported);
        assertEquals(2, dao.totalMessageCount());
        assertEquals(2000, dao.getConversation("room").lastProcessedTimestamp);
        assertTrue(dao.getRecentMessages("room", 1).get(0).sentByMe);

        ManagerEngine.Result repeated = engine.reconcile(null);
        assertEquals(0, repeated.messagesImported);
        assertEquals(2, dao.totalMessageCount());
    }

    @Test
    public void roomUniqueIndexRejectsDuplicateMessage() {
        Entities.MessageEntity first = entity("same");
        Entities.MessageEntity duplicate = entity("same");
        assertNotEquals(-1, dao.insertMessage(first));
        assertEquals(-1, dao.insertMessage(duplicate));
        assertEquals(1, dao.messageCount("room", "same"));
    }

    @Test
    public void relationshipAndStyleProfilesPersistAcrossQueries() {
        Entities.RelationshipProfileEntity relationship =
                new Entities.RelationshipProfileEntity();
        relationship.contactId = "room";
        relationship.status = "WAITING";
        dao.upsertRelationshipProfile(relationship);

        Entities.CommunicationStyleProfileEntity style =
                new Entities.CommunicationStyleProfileEntity();
        style.contactId = "room";
        style.sampleCount = 4;
        style.confidenceScore = 0.2;
        dao.upsertStyleProfile(style);

        assertEquals("WAITING", dao.getRelationshipProfile("room").status);
        assertEquals(4, dao.getStyleProfile("room").sampleCount);
    }

    @Test
    public void versionOneSchemaOpensAllRequiredRoomTables() {
        assertEquals(1, ManagerDatabase.VERSION);
        assertEquals(0, ManagerDatabase.MIGRATIONS.length);
        android.database.Cursor cursor = database.getOpenHelper().getReadableDatabase().query(
                "SELECT name FROM sqlite_master WHERE type='table'"
        );
        List<String> tables = new ArrayList<>();
        while (cursor.moveToNext()) tables.add(cursor.getString(0));
        cursor.close();
        assertTrue(tables.contains("contacts"));
        assertTrue(tables.contains("conversations"));
        assertTrue(tables.contains("messages"));
        assertTrue(tables.contains("communication_style_profiles"));
        assertTrue(tables.contains("relationship_profiles"));
        assertTrue(tables.contains("contact_intelligence"));
        assertTrue(tables.contains("actions"));
        assertTrue(tables.contains("opportunities"));
        assertTrue(tables.contains("events"));
        assertTrue(tables.contains("follow_ups"));
        assertTrue(tables.contains("activity"));
        assertTrue(tables.contains("sync_state"));
        assertTrue(tables.contains("manager_decisions"));
    }

    private static Entities.MessageEntity entity(String id) {
        Entities.MessageEntity entity = new Entities.MessageEntity();
        entity.roomId = "room";
        entity.messageId = id;
        entity.timestamp = 100;
        return entity;
    }

    private static BeeperProviderDataSource.RemoteMessage remote(
            String id,
            boolean sentByMe,
            long timestamp,
            String text
    ) {
        BeeperProviderDataSource.RemoteMessage message =
                new BeeperProviderDataSource.RemoteMessage();
        message.roomId = "room";
        message.messageId = id;
        message.sentByMe = sentByMe;
        message.timestamp = timestamp;
        message.text = text;
        message.senderId = sentByMe ? "ale" : "contact";
        message.messageType = "text";
        return message;
    }

    private static final class FakeDataSource implements ManagerDataSource {
        final BeeperProviderDataSource.RemoteChat chat =
                new BeeperProviderDataSource.RemoteChat();
        final List<BeeperProviderDataSource.RemoteMessage> messages = new ArrayList<>();

        FakeDataSource() {
            chat.roomId = "room";
            chat.title = "Test Promoter";
            chat.network = "WhatsApp";
            chat.protocolRaw = "whatsapp";
            chat.oneToOne = true;
        }

        @Override
        public List<BeeperProviderDataSource.RemoteChat> loadAllChats() {
            return List.of(chat);
        }

        @Override
        public List<BeeperProviderDataSource.RemoteMessage> loadMessagePage(
                String roomId,
                int limit,
                int offset
        ) {
            if (offset >= messages.size()) return List.of();
            return new ArrayList<>(messages.subList(offset, Math.min(messages.size(), offset + limit)));
        }
    }
}

