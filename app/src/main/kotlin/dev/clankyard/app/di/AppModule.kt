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
import dev.clankyard.feature.workspacepicker.AndroidWorkspaceIo
import dev.clankyard.feature.workspacepicker.DefaultAndroidWorkspaceIo
import dev.clankyard.search.InProcessProjectSearch
import dev.clankyard.search.ProjectSearch
import dev.clankyard.workspace.FileWorkspaceRegistry
import dev.clankyard.workspace.WorkshopTreeOps
import java.io.File
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

    @Provides
    @Singleton
    fun provideWorkspaceRegistry(@ApplicationContext context: Context): FileWorkspaceRegistry =
        FileWorkspaceRegistry(
            workspacesDir = File(context.filesDir, "workspaces"),
            journalRoot = File(context.filesDir, "journal"),
        )

    @Provides
    @Singleton
    fun provideWorkshopTreeOps(registry: FileWorkspaceRegistry): WorkshopTreeOps = registry.treeOps

    @Provides
    @Singleton
    fun provideAndroidWorkspaceIo(
        @ApplicationContext context: Context,
        registry: FileWorkspaceRegistry,
    ): AndroidWorkspaceIo = DefaultAndroidWorkspaceIo(context, registry)

    @Provides
    @Singleton
    fun provideProjectSearch(): ProjectSearch = InProcessProjectSearch()
}
