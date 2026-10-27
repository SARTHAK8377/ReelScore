package com.reelscore.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ReelEvent::class, TrackingPing::class, SeenReel::class, CompletedJourney::class], version = 3)
abstract class AppDatabase : RoomDatabase() {
    abstract fun reelDao(): ReelDao

    companion object {
        /** v2 adds seen_reel. Reels counted before this have no entry, so each can count once more at most. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `seen_reel` (`key` TEXT NOT NULL, `firstSeen` INTEGER NOT NULL, PRIMARY KEY(`key`))",
                )
            }
        }

        /** v3 adds completed_journey. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `completed_journey` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`fromName` TEXT NOT NULL, `toName` TEXT NOT NULL, `meters` REAL NOT NULL, " +
                        "`startedAt` INTEGER NOT NULL, `finishedAt` INTEGER NOT NULL)",
                )
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "reelscore.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
