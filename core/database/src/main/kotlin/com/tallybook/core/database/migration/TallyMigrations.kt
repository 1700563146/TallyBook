package com.tallybook.core.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schema migrations, kept in one file so the history is readable top-to-bottom.
 *
 * Rules for this file:
 * - Never edit a shipped migration. A migration that ran on someone's device is history; changing it
 *   silently produces two different schemas for two different users.
 * - Every migration needs a test that opens a v(n) database and upgrades it (Robolectric +
 *   MigrationTestHelper). See docs/ROADMAP.md Phase 0.5.
 * - Prefer additive changes. SQLite's `ALTER TABLE` cannot drop or retype a column, so a destructive
 *   change means create-new-table + copy + rename, which must be written carefully and tested.
 */
object TallyMigrations {

    /**
     * v1 -> v2: refunds.
     *
     * Adds `refund_of_id` as a nullable self-reference. Nullable + no default needed for existing
     * rows is exactly why refunds were added this way: existing data is untouched and the column is
     * meaningful only for the new INCOME-refund rows.
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE transactions ADD COLUMN refund_of_id TEXT DEFAULT NULL")
        }
    }

    /** Registered by the DI module; keep in version order. */
    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
