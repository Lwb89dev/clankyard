package dev.clankyard.app.di

import android.content.Context
import android.util.Log
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.clankyard.core.common.DefaultRedactingLogger
import dev.clankyard.core.common.RedactingLogger
import dev.clankyard.core.ui.DataStoreWorkspaceUiStore
import dev.clankyard.core.ui.WorkspaceUiStore
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideRedactingLogger(): RedactingLogger =
        DefaultRedactingLogger { level, tag, message, t ->
            when (level) {
                "DEBUG" -> Log.d(tag, message, t)
                "INFO" -> Log.i(tag, message, t)
                "WARN" -> Log.w(tag, message, t)
                else -> Log.e(tag, message, t)
            }
        }

    @Provides
    @Singleton
    fun provideWorkspaceUiStore(@ApplicationContext context: Context): WorkspaceUiStore =
        DataStoreWorkspaceUiStore(context)
}
