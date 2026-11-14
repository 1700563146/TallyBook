package com.tallybook.di

import com.tallybook.app.notify.BudgetAlertNotifier
import com.tallybook.core.data.notify.BudgetAlertChecker
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Composition-root bindings: the Android implementation lives in `:app`, the port in `:core:data`.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    abstract fun bindBudgetAlertChecker(impl: BudgetAlertNotifier): BudgetAlertChecker
}
