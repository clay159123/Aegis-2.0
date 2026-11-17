package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
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
    version = 21,
    exportSchema = false
)
abstract class AegisDatabase : RoomDatabase() {
    abstract fun aegisDao(): AegisDao

    companion object {
        @Volatile
        private var INSTANCE: AegisDatabase? = null

        /**
         * Set when Room wipes the database because the saved schema didn't match
         * (a version bump without a migration, or a downgrade). The repository
         * reads it once at startup so the player can be told a new save started.
         */
        @Volatile
        private var destructiveResetPending = false

        fun consumeDestructiveResetFlag(): Boolean {
            val pending = destructiveResetPending
            destructiveResetPending = false
            return pending
        }

        fun getInstance(context: Context): AegisDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AegisDatabase::class.java,
                    "aegis_ops.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
                            destructiveResetPending = true
                        }
                    })
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
