package com.asearch.relay.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;

@Database(
        entities = {
                Entities.ContactEntity.class,
                Entities.ConversationEntity.class,
                Entities.MessageEntity.class,
                Entities.CommunicationStyleProfileEntity.class,
                Entities.RelationshipProfileEntity.class,
                Entities.ContactIntelligenceEntity.class,
                Entities.ActionEntity.class,
                Entities.OpportunityEntity.class,
                Entities.EventEntity.class,
                Entities.FollowUpEntity.class,
                Entities.ActivityEntity.class,
                Entities.SyncStateEntity.class,
                Entities.ManagerDecisionEntity.class
        },
        version = ManagerDatabase.VERSION,
        exportSchema = true
)
public abstract class ManagerDatabase extends RoomDatabase {
    public static final int VERSION = 1;
    public static final Migration[] MIGRATIONS = new Migration[0];
    private static volatile ManagerDatabase instance;

    public abstract ManagerDao managerDao();

    public static ManagerDatabase get(Context context) {
        if (instance == null) {
            synchronized (ManagerDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    ManagerDatabase.class,
                                    "a-search-manager.db"
                            )
                            .addMigrations(MIGRATIONS)
                            .build();
                }
            }
        }
        return instance;
    }
}
