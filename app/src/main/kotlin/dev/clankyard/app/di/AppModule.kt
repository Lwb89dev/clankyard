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
import dev.clankyard.ai.patch.CachingPatchEngineFactory
import dev.clankyard.ai.patch.PatchEngineFactory
import dev.clankyard.ai.provider.http.ProviderHttp
import dev.clankyard.ai.secret.DefaultSecretFilter
import dev.clankyard.ai.secret.SecretFilter
import dev.clankyard.ai.tools.DefaultToolRegistry
import dev.clankyard.ai.tools.ToolRegistry
import dev.clankyard.core.security.SecureCredentialStore
import dev.clankyard.feature.settings.WorkshopSettingsStore
import dev.clankyard.core.model.Credential
import dev.clankyard.feature.settings.ExecutionKind
import dev.clankyard.terminal.api.ExecutionBackend
import dev.clankyard.terminal.api.SwitchingExecutionBackend
import dev.clankyard.terminal.local.LocalProcessBackend
import dev.clankyard.terminal.ssh.SshConnectRequest
import dev.clankyard.terminal.ssh.SshExecutionBackend
import okhttp3.OkHttpClient
import dev.clankyard.search.TextSearch
import dev.clankyard.search.WorkspaceTextSearch
import dev.clankyard.diff.DiffEngine
import dev.clankyard.diff.MyersDiffEngine
import dev.clankyard.feature.workspacepicker.AndroidWorkspaceIo
import dev.clankyard.feature.workspacepicker.DefaultAndroidWorkspaceIo
import dev.clankyard.git.GitRepository
import dev.clankyard.git.JGitRepository
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
    fun provideGitRepository(): GitRepository = JGitRepository()

    @Provides
    @Singleton
    fun provideDiffEngine(): DiffEngine = MyersDiffEngine()

    @Provides
    @Singleton
    fun providePatchEngineFactory(diffEngine: DiffEngine): PatchEngineFactory =
        CachingPatchEngineFactory(diffEngine)

    @Provides
    @Singleton
    fun provideTextSearch(): TextSearch = WorkspaceTextSearch()

    @Provides
    @Singleton
    fun provideToolRegistry(search: TextSearch): ToolRegistry = DefaultToolRegistry.mvp(search)

    @Provides
    @Singleton
    fun provideProjectSearch(): ProjectSearch = InProcessProjectSearch()

    @Provides
    @Singleton
    fun provideSecretFilter(): SecretFilter = DefaultSecretFilter()

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = ProviderHttp.client()

    @Provides
    @Singleton
    fun provideExecutionBackend(
        store: WorkshopSettingsStore,
        credentials: SecureCredentialStore,
    ): ExecutionBackend {
        val local = LocalProcessBackend()
        val ssh = SshExecutionBackend(
            load = {
                val snap = store.read()
                if (snap.sshHost.isBlank() || snap.sshUser.isBlank()) {
                    null
                } else {
                    val secret = credentials.get(WorkshopSettingsStore.sshSlot(snap.sshHost))
                    val password = (secret as? Credential.ApiKey)?.secret
                    SshConnectRequest(
                        host = snap.sshHost.trim(),
                        port = snap.sshPort,
                        username = snap.sshUser.trim(),
                        password = password,
                        remoteCwd = snap.sshRemoteCwd.trim().ifBlank { null },
                        fingerprint = snap.sshHostFingerprint.trim().ifBlank { null },
                    )
                }
            },
            onUnknownHost = { fingerprint ->
                store.write(store.read().copy(sshPendingFingerprint = fingerprint))
            },
        )
        return SwitchingExecutionBackend(local, ssh) {
            store.read().execution == ExecutionKind.Ssh
        }
    }

    @Provides
    @Singleton
    fun provideWorkshopSettingsStore(
        @ApplicationContext context: Context,
        credentials: SecureCredentialStore,
    ): WorkshopSettingsStore = WorkshopSettingsStore(context, credentials)
}
