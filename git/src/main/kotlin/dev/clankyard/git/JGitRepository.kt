package dev.clankyard.git

import dev.clankyard.workspace.FileBackedWorkspace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import java.io.File

class JGitRepository : GitRepository {
    override suspend fun open(workspace: FileBackedWorkspace): GitHandle? =
        withContext(Dispatchers.IO) {
            refusePublicFuse(workspace.root)
            val gitDir = File(workspace.root, Constants.DOT_GIT)
            if (!gitDir.exists()) return@withContext null
            try {
                val repo = FileRepositoryBuilder()
                    .setGitDir(gitDir)
                    .setWorkTree(workspace.root)
                    .setMustExist(true)
                    .build()
                forceCoreConfig(repo)
                JGitHandle(Git(repo), workspace)
            } catch (e: Exception) {
                wrapGitFailure(e)
            }
        }

    override suspend fun init(workspace: FileBackedWorkspace, identity: GitIdentity): GitHandle =
        withContext(Dispatchers.IO) {
            refusePublicFuse(workspace.root)
            identity.requireComplete()
            try {
                val git = Git.init()
                    .setDirectory(workspace.root)
                    .setGitDir(File(workspace.root, Constants.DOT_GIT))
                    .setInitialBranch("main")
                    .call()
                forceCoreConfig(git.repository)
                val config = git.repository.config
                config.setString("user", null, "name", identity.name)
                config.setString("user", null, "email", identity.email)
                config.save()
                JGitHandle(git, workspace)
            } catch (e: Exception) {
                wrapGitFailure(e)
            }
        }
}
