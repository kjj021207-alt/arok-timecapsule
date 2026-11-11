package com.arok.timecapsule.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [CapsuleEntity::class], version = 2, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun capsuleDao(): CapsuleDao

    companion object {
        /** v2: 사용자가 고른 사진 경로(photoPath) 추가. 기존 캡슐은 그대로 두고 칸만 늘린다. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE capsules ADD COLUMN photoPath TEXT")
            }
        }

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "timecapsule.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
