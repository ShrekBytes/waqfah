package com.shrekbytes.waqfah.data.local.appstate

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.UUID

// Monitored-app state is user data, so the membership identity and claim
// revision are added through a preserving migration rather than a destructive
// fallback. Each existing row becomes a distinct monitored-app membership.
object AppStateMigrations {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE monitored_apps_new (
                    package_name TEXT NOT NULL,
                    membership_id TEXT NOT NULL,
                    added_at INTEGER NOT NULL,
                    last_shown_at INTEGER,
                    trigger_revision INTEGER NOT NULL,
                    PRIMARY KEY(package_name)
                )
                """.trimIndent(),
            )

            db.query("SELECT package_name, added_at, last_shown_at FROM monitored_apps").use { cursor ->
                val packageIndex = cursor.getColumnIndexOrThrow("package_name")
                val addedAtIndex = cursor.getColumnIndexOrThrow("added_at")
                val lastShownAtIndex = cursor.getColumnIndexOrThrow("last_shown_at")
                while (cursor.moveToNext()) {
                    db.execSQL(
                        "INSERT INTO monitored_apps_new(package_name, membership_id, added_at, last_shown_at, trigger_revision) VALUES (?, ?, ?, ?, 0)",
                        arrayOf<Any?>(
                            cursor.getString(packageIndex),
                            UUID.randomUUID().toString(),
                            cursor.getLong(addedAtIndex),
                            if (cursor.isNull(lastShownAtIndex)) null else cursor.getLong(lastShownAtIndex),
                        ),
                    )
                }
            }

            db.execSQL("DROP TABLE monitored_apps")
            db.execSQL("ALTER TABLE monitored_apps_new RENAME TO monitored_apps")
        }
    }
}
