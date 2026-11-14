package com.tallybook.core.common.di

import com.tallybook.core.common.clock.Clock
import com.tallybook.core.common.clock.DateProvider
import com.tallybook.core.common.clock.SystemClock
import com.tallybook.core.common.time.JavaTimeDateProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.ZoneId
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CommonModule {

    @Binds
    abstract fun bindClock(impl: SystemClock): Clock

    // Zone is provided explicitly rather than defaulted in the constructor: a default argument would
    // give Kotlin two constructors and break Dagger's single-inject-constructor rule.
    @Binds
    abstract fun bindDateProvider(impl: JavaTimeDateProvider): DateProvider

    companion object {
        @Provides
        @Singleton
        fun provideZoneId(): ZoneId = ZoneId.systemDefault()

        /**
         * Not annotated @Inject: the ZoneId argument is a DI concern, and keeping the constructor
         * plain means the provider can be unit-tested against a fixed zone with no Hilt involved.
         */
        @Provides
        @Singleton
        fun provideJavaTimeDateProvider(zoneId: ZoneId): JavaTimeDateProvider =
            JavaTimeDateProvider(zoneId)
    }
}
