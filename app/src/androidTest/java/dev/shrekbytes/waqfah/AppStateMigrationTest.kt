package dev.shrekbytes.waqfah

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.shrekbytes.waqfah.data.local.appstate.AppStateMigrations
import dev.shrekbytes.waqfah.data.local.appstate.WaqfahAppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// The app-state database contains real user progress, so the migration test
// verifies preservation from the exported version-1 schema rather than only
// testing a fresh version-2 database.
@RunWith(AndroidJUnit4::class)
class AppStateMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        WaqfahAppDatabase::class.java,
    )

    @Test
    fun migrate1To2_preservesStateAndCreatesMembershipIdentity() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL("INSERT INTO monitored_apps(package_name, added_at, last_shown_at) VALUES ('com.example.one', 1000, 2000)")
            execSQL("INSERT INTO monitored_apps(package_name, added_at, last_shown_at) VALUES ('com.example.two', 3000, NULL)")
            execSQL("INSERT INTO read_verses(verse_id, read_at) VALUES (7, 4000)")
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DB,
            2,
            true,
            AppStateMigrations.MIGRATION_1_2,
        )

        migrated.query("SELECT package_name, membership_id, added_at, last_shown_at, trigger_revision FROM monitored_apps ORDER BY package_name").use { cursor ->
            assertEquals(2, cursor.count)

            assertEquals(true, cursor.moveToNext())
            assertEquals("com.example.one", cursor.getString(0))
            val firstId = cursor.getString(1)
            assertNotEquals("", firstId)
            assertEquals(1000L, cursor.getLong(2))
            assertEquals(2000L, cursor.getLong(3))
            assertEquals(0L, cursor.getLong(4))

            assertEquals(true, cursor.moveToNext())
            assertEquals("com.example.two", cursor.getString(0))
            val secondId = cursor.getString(1)
            assertNotEquals(firstId, secondId)
            assertEquals(3000L, cursor.getLong(2))
            assertEquals(true, cursor.isNull(3))
            assertEquals(0L, cursor.getLong(4))
        }

        migrated.query("SELECT read_at FROM read_verses WHERE verse_id = 7").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(4000L, cursor.getLong(0))
        }
        migrated.close()
    }

    // The bookmark collection arrives as a brand-new table in v3. This migration
    // reads no existing row and drops none, so the version-2 user data on both
    // sides of it — read progress and monitored-app state — must come through
    // byte-identical, with the new table simply empty.
    @Test
    fun migrate2To3_preservesProgressAndMonitoredState_andCreatesAnEmptyCollection() {
        helper.createDatabase(TEST_DB, 2).apply {
            execSQL(
                "INSERT INTO monitored_apps(package_name, membership_id, added_at, last_shown_at, trigger_revision) " +
                    "VALUES ('com.example.one', 'membership-1', 1000, 2000, 3)",
            )
            execSQL("INSERT INTO read_verses(verse_id, read_at) VALUES (7, 4000)")
            execSQL("INSERT INTO read_verses(verse_id, read_at) VALUES (9, 5000)")
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DB,
            3,
            true,
            AppStateMigrations.MIGRATION_2_3,
        )

        migrated.query("SELECT COUNT(*) FROM read_verses").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(2, cursor.getInt(0))
        }
        migrated.query("SELECT read_at FROM read_verses WHERE verse_id = 9").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(5000L, cursor.getLong(0))
        }
        migrated.query(
            "SELECT COUNT(*) FROM monitored_apps WHERE package_name = 'com.example.one' " +
                "AND membership_id = 'membership-1' AND added_at = 1000 " +
                "AND last_shown_at = 2000 AND trigger_revision = 3",
        ).use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
        // The new table exists and the migration backfilled nothing into it.
        migrated.query("SELECT COUNT(*) FROM bookmark_verses").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        migrated.close()
    }

    // Upgrading an install that is still on v1 must walk both migrations,
    // landing on v3 with the whole v1 dataset intact.
    @Test
    fun migrate1To3_preservesEverythingAcrossBothSteps() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL("INSERT INTO monitored_apps(package_name, added_at, last_shown_at) VALUES ('com.example.one', 1000, 2000)")
            execSQL("INSERT INTO read_verses(verse_id, read_at) VALUES (7, 4000)")
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DB,
            3,
            true,
            AppStateMigrations.MIGRATION_1_2,
            AppStateMigrations.MIGRATION_2_3,
        )

        migrated.query("SELECT COUNT(*) FROM monitored_apps WHERE package_name = 'com.example.one' AND added_at = 1000").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
        migrated.query("SELECT read_at FROM read_verses WHERE verse_id = 7").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(4000L, cursor.getLong(0))
        }
        migrated.query("SELECT COUNT(*) FROM bookmark_verses").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        migrated.close()
    }

    private companion object {
        const val TEST_DB = "app-state-migration-test"
    }
}
