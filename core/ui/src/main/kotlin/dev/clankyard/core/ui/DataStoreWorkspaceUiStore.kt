package dev.clankyard.core.ui

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import com.google.protobuf.InvalidProtocolBufferException
import dev.clankyard.core.ui.proto.WorkspaceUiStateProto
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.io.InputStream
import java.io.OutputStream

private val Context.workspaceUiDataStore: DataStore<WorkspaceUiStateProto> by dataStore(
    fileName = "settings.pb",
    serializer = WorkspaceUiStateSerializer,
)

class DataStoreWorkspaceUiStore(
    context: Context,
) : WorkspaceUiStore {
    private val dataStore = context.workspaceUiDataStore
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, _ -> },
    )

    override val state: StateFlow<WorkspaceUiState> =
        dataStore.data
            .map { it.toModel() }
            .catch { emit(WorkspaceUiState()) }
            .stateIn(scope, SharingStarted.Eagerly, WorkspaceUiState())

    override suspend fun update(transform: (WorkspaceUiState) -> WorkspaceUiState) {
        dataStore.updateData { current -> transform(current.toModel()).toProto() }
    }

    override suspend fun snapshot(): WorkspaceUiState = dataStore.data.first().toModel()
}

internal object WorkspaceUiStateSerializer : Serializer<WorkspaceUiStateProto> {
    override val defaultValue: WorkspaceUiStateProto = WorkspaceUiStateProto.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): WorkspaceUiStateProto =
        try {
            WorkspaceUiStateProto.parseFrom(input)
        } catch (e: InvalidProtocolBufferException) {
            throw CorruptionException("cannot read settings.pb", e)
        }

    override suspend fun writeTo(t: WorkspaceUiStateProto, output: OutputStream) {
        t.writeTo(output)
    }
}
