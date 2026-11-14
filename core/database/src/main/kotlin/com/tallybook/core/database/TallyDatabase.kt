package com.tallybook.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.tallybook.core.database.dao.AccountDao
import com.tallybook.core.database.dao.BudgetDao
import com.tallybook.core.database.dao.CategoryDao
import com.tallybook.core.database.dao.TransactionDao
import com.tallybook.core.database.entity.AccountEntity
import com.tallybook.core.database.entity.BudgetEntity
import com.tallybook.core.database.entity.CategoryEntity
import com.tallybook.core.database.entity.Converters
import com.tallybook.core.database.entity.TransactionEntity
import com.tallybook.core.database.entity.TransactionTagEntity

/**
 * Schema versioning policy:
 * - Never edit a released entity without bumping [VERSION] and writing an explicit Migration.
 * - `exportSchema = true` + committed JSON snapshots make drift reviewable in code review.
 * - Destructive migration is deliberately NOT enabled: losing a user's ledger is unacceptable.
 *   It is enabled only for debug builds via a separate factory, never in release.
 */
@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        TransactionTagEntity::class,
        BudgetEntity::class,
    ],
    version = TallyDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class TallyDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao

    abstract fun accountDao(): AccountDao

    abstract fun categoryDao(): CategoryDao

    abstract fun budgetDao(): BudgetDao

    companion object {
        const val NAME = "tallybook.db"
        const val VERSION = 2
    }
}
