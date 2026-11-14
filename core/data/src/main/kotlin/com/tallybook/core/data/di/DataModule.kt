package com.tallybook.core.data.di

import com.tallybook.core.data.repository.AccountRepository
import com.tallybook.core.data.repository.AccountRepositoryImpl
import com.tallybook.core.data.repository.BudgetRepository
import com.tallybook.core.data.repository.BudgetRepositoryImpl
import com.tallybook.core.data.repository.CategoryRepository
import com.tallybook.core.data.repository.CategoryRepositoryImpl
import com.tallybook.core.data.repository.StatsRepository
import com.tallybook.core.data.repository.StatsRepositoryImpl
import com.tallybook.core.data.repository.TransactionRepository
import com.tallybook.core.data.repository.TransactionRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Interface -> implementation bindings.
 *
 * @Binds rather than @Provides: it generates a direct factory call (no reflection, no extra code)
 * and fails at compile time when an implementation's constructor changes shape.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    abstract fun bindTransactionRepository(impl: TransactionRepositoryImpl): TransactionRepository

    @Binds
    abstract fun bindStatsRepository(impl: StatsRepositoryImpl): StatsRepository

    @Binds
    abstract fun bindAccountRepository(impl: AccountRepositoryImpl): AccountRepository

    @Binds
    abstract fun bindCategoryRepository(impl: CategoryRepositoryImpl): CategoryRepository

    @Binds
    abstract fun bindBudgetRepository(impl: BudgetRepositoryImpl): BudgetRepository
}
