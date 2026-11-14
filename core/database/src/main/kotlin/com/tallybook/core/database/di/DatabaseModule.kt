package com.tallybook.core.database.di

import android.content.Context
import androidx.room.Room
import com.tallybook.core.database.TallyDatabase
import com.tallybook.core.database.migration.TallyMigrations
import com.tallybook.core.database.dao.AccountDao
import com.tallybook.core.database.dao.BudgetDao
import com.tallybook.core.database.dao.CategoryDao
import com.tallybook.core.database.dao.TransactionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * No `fallbackToDestructiveMigration()`: a lost ledger is a lost user. Migrations are added to
     * [addMigrations] as the schema evolves.
     */
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): TallyDatabase =
        Room.databaseBuilder(context, TallyDatabase::class.java, TallyDatabase.NAME)
            .addMigrations(*TallyMigrations.ALL)
            .build()

    /*
     * DAOs are stateless handles; Room caches them per database instance, so unscoped @Provides is
     * correct here. The database itself is @Singleton, which is what actually matters for
     * connection pooling and WAL behaviour.
     */

    @Provides
    fun provideTransactionDao(db: TallyDatabase): TransactionDao = db.transactionDao()

    @Provides
    fun provideAccountDao(db: TallyDatabase): AccountDao = db.accountDao()

    @Provides
    fun provideCategoryDao(db: TallyDatabase): CategoryDao = db.categoryDao()

    @Provides
    fun provideBudgetDao(db: TallyDatabase): BudgetDao = db.budgetDao()
}
