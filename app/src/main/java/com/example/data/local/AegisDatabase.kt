package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        CommanderStateEntity::class,
        FacilityEntity::class,
        OperatorEntity::class,
        GearEntity::class,
        DeploymentEntity::class,
        CitationEntity::class,
        PendingDeedEntity::class,
        SquadEntity::class
    ],
    version = 17,
    exportSchema = false
)
abstract class AegisDatabase : RoomDatabase() {
    abstract fun aegisDao(): AegisDao

    companion object {
        @Volatile
        private var INSTANCE: AegisDatabase? = null

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE commander_state ADD COLUMN veteranLegacyBps INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE commander_state ADD COLUMN retiredVeteransCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE commander_state ADD COLUMN lastRetirementAtMillis INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE operators ADD COLUMN retirementRequested INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE operators ADD COLUMN authorizedTierName TEXT NOT NULL DEFAULT 'SOLDIER'")
                db.execSQL(
                    """
                    UPDATE operators SET authorizedTierName = CASE
                        WHEN xp >= 7100 THEN 'FLAG_OFFICER'
                        WHEN xp >= 2000 THEN 'OFFICER'
                        WHEN xp >= 380 THEN 'NCO'
                        ELSE 'SOLDIER'
                    END
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE operators ADD COLUMN petitionCooldownUntilOps INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE operators ADD COLUMN tailorOverridesCsv TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    UPDATE operators SET authorizedTierName = 'SNCO'
                    WHERE authorizedTierName = 'NCO' AND xp > 800
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS squads (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        memberIdsCsv TEXT NOT NULL,
                        leaderId TEXT,
                        cohesionXp INTEGER NOT NULL,
                        missionsTogether INTEGER NOT NULL,
                        createdAtMillis INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("ALTER TABLE deployments ADD COLUMN squadId TEXT")
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE commander_state ADD COLUMN lastInspectionAtMillis INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE commander_state ADD COLUMN inspectionBuffUntilMillis INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE squads ADD COLUMN standardPatchName TEXT NOT NULL DEFAULT 'AUTO_MOS'")
                db.execSQL("ALTER TABLE squads ADD COLUMN standardCamoName TEXT")
                db.execSQL("ALTER TABLE squads ADD COLUMN standardHeadgearName TEXT")
                db.execSQL("ALTER TABLE squads ADD COLUMN standardDressStyleName TEXT")
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE commander_state ADD COLUMN smartWardrobeEnabled INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE operators ADD COLUMN activeWardrobeKitName TEXT NOT NULL DEFAULT 'ALPHAS'")
                db.execSQL(
                    """
                    UPDATE operators SET activeWardrobeKitName = CASE
                        WHEN uniformModeName = 'FIELD_TACTICAL' AND statusName != 'HQ_STAFF' THEN 'FIELD'
                        ELSE 'ALPHAS'
                    END
                    """.trimIndent()
                )
                db.execSQL("ALTER TABLE operators ADD COLUMN wardrobeCsv TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE squads ADD COLUMN standardBravosHeadgearName TEXT")
                db.execSQL("ALTER TABLE squads ADD COLUMN standardBravosDressStyleName TEXT")
                db.execSQL("ALTER TABLE squads ADD COLUMN standardMessDressStyleName TEXT")
                db.execSQL("ALTER TABLE squads ADD COLUMN standardMessHeadgearName TEXT")
                db.execSQL("ALTER TABLE squads ADD COLUMN standardWhiteGloves INTEGER")
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE operators ADD COLUMN combatMissionsCompleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    """
                    UPDATE operators SET combatMissionsCompleted = CASE
                        WHEN highestRiskName IS NOT NULL THEN max(1, missionsCompleted)
                        ELSE 0
                    END
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE commander_state ADD COLUMN completedCampaignStageIdsCsv TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE commander_state ADD COLUMN completedCampaignIdsCsv TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE commander_state ADD COLUMN rotatingMissionIdsCsv TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE commander_state ADD COLUMN rotationUpdatedAtMillis INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): AegisDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AegisDatabase::class.java,
                    "aegis_ops.db"
                )
                    .addMigrations(
                        MIGRATION_7_8,
                        MIGRATION_8_9,
                        MIGRATION_9_10,
                        MIGRATION_10_11,
                        MIGRATION_11_12,
                        MIGRATION_12_13,
                        MIGRATION_13_14,
                        MIGRATION_14_15,
                        MIGRATION_15_16,
                        MIGRATION_16_17
                    )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
