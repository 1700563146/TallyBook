package com.tallybook.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.tallybook.core.database.migration.TallyMigrations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class TallyMigrationsTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        TallyDatabase::class.java,
    )

    @Test
    fun migration_1_2_preserves_existing_rows_and_adds_refund_column() {
        helper.createDatabase(TallyDatabase.NAME, 1).use { db ->
            db.execSQL(
                """
                INSERT INTO accounts
                    (id, name, type, currency, initial_balance_minor, is_archived, sort_order, created_at, updated_at)
                VALUES ('a1', '现金', 'CASH', 'CNY', 0, 0, 0, 1, 1)
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO categories
                    (id, name, type, parent_id, icon_key, color_argb, sort_order, is_archived)
                VALUES ('c1', '餐饮', 'EXPENSE', NULL, NULL, NULL, 0, 0)
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO transactions
                    (id, type, kind, amount_minor, currency, account_id, category_id, occurred_at,
                     note, counter_account_id, created_at, updated_at, deleted_at)
                VALUES ('t1', 'EXPENSE', 'NORMAL', 1200, 'CNY', 'a1', 'c1', 1000,
                        NULL, NULL, 1, 1, NULL)
                """.trimIndent(),
            )
        }

        val migrated = helper.runMigrationsAndValidate(
            TallyDatabase.NAME,
            2,
            true,
            TallyMigrations.MIGRATION_1_2,
        )

        migrated.query("SELECT id, refund_of_id FROM transactions").use { cursor ->
            cursor.moveToFirst()
            assertEquals("t1", cursor.getString(0))
            assertNull(cursor.getString(1))
        }
    }
}
