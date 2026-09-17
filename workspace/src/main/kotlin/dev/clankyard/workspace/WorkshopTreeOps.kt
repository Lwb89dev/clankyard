package dev.clankyard.workspace

import dev.clankyard.core.model.WorkspaceId
import java.io.File

const val FREE_SPACE_MARGIN_BYTES: Long = 64L * 1024L * 1024L

data class TreeExportPlan(
    val created: Int, // N files that will be created at dest
    val overwritten: Int, // M dest files that will be replaced after confirm
    val extraDest: Int, // D dest files with no workshop counterpart — left untouched
)

class InsufficientSpaceException(
    val usable: Long,
    val required: Long, // uncompressedSize + 64 MiB
) : Exception("need $required bytes, have $usable")

/**
 * JVM-pure export/copy planning. No `android.net.Uri`.
 * Android SAF (`Uri`) lives in `:feature:workspace-picker` as `AndroidWorkspaceIo`.
 */
interface WorkshopTreeOps {
    /** Refuse unless `usableSpace >= uncompressedSize + 64 MiB`. */
    fun requireFreeSpace(destVolume: File, uncompressedSize: Long)

    suspend fun planExportToTree(id: WorkspaceId, destRoot: File): TreeExportPlan
}

fun requiredBytesForCopy(uncompressedSize: Long): Long {
    require(uncompressedSize >= 0) { "uncompressedSize must be >= 0" }
    val required = uncompressedSize + FREE_SPACE_MARGIN_BYTES
    if (required < uncompressedSize) return Long.MAX_VALUE
    return required
}
