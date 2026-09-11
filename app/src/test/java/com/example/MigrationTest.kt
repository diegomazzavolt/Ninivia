package com.example

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MigrationTest {
    @Test fun v1DatabaseMigratesWithoutChangingEncryptedNotesOrAttachments() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-test"
        context.deleteDatabase(name)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE categories (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, color INTEGER NOT NULL)")
                    db.execSQL("CREATE TABLE notes (id TEXT NOT NULL PRIMARY KEY, categoryId TEXT, encryptedTitle TEXT NOT NULL, encryptedContent TEXT NOT NULL, tags TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, isSynced INTEGER NOT NULL)")
                    db.execSQL("CREATE TABLE attachments (id TEXT NOT NULL PRIMARY KEY, noteId TEXT NOT NULL, uri TEXT NOT NULL, type TEXT NOT NULL, name TEXT NOT NULL)")
                    db.execSQL("INSERT INTO categories VALUES ('project','Original',123)")
                    db.execSQL("INSERT INTO notes VALUES ('note','project','cipher-title','cipher-content','tag',12,34,0)")
                    db.execSQL("INSERT INTO attachments VALUES ('attachment','note','content://original','PDF','original.pdf')")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build())
        helper.writableDatabase
        helper.close()
        val db = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(AppDatabase.MIGRATION_1_2).build()
        try {
            // Opening invokes Room's real generated schema validation after migration.
            db.openHelper.readableDatabase.query("SELECT encryptedContent, createdAt, status, repeat FROM notes WHERE id='note'").use {
                assertTrue(it.moveToFirst())
                assertEquals("cipher-content", it.getString(0)); assertEquals(12, it.getLong(1))
                assertEquals("INBOX", it.getString(2)); assertEquals("NONE", it.getString(3))
            }
            db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM attachments").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
