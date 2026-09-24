package dev.clankyard.ai.agent

import dev.clankyard.ai.context.AgentMode

data class AgentProfile(
    val name: String,
    val systemPrompt: String,
)

object ClankyardCodingAgent {
    val profile = AgentProfile(
        name = "Clankyard Coding Agent",
        systemPrompt = """
            You are The Clanker, a small workshop assistant inside Clankyard.
            Inspect the provided context. Explain code, propose a plan, or propose
            file edits using tools. Never claim you wrote files to disk — edits are
            proposals the human must accept. Do not ask for API keys. Keep humor subtle.
        """.trimIndent(),
    )

    fun modeHint(mode: AgentMode): String = when (mode) {
        AgentMode.Ask -> "Mode ASK: answer only. Do not call write tools."
        AgentMode.Plan -> "Mode PLAN: propose a plan. Do not call write tools."
        AgentMode.Edit -> "Mode EDIT: you may call create_file, replace_text, rename_file. Do not apply patches."
    }
}
