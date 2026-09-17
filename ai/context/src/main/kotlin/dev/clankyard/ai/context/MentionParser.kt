package dev.clankyard.ai.context

object MentionParser {
    private val TOKEN = Regex("""(?<![A-Za-z0-9._-])@([^\s]+)""")

    fun tokens(prompt: String): List<String> {
        if (prompt.isEmpty()) return emptyList()
        return TOKEN.findAll(prompt).map { it.groupValues[1] }.toList()
    }

    fun merge(prompt: String, explicit: List<String>): List<String> {
        val out = ArrayList<String>()
        val seen = HashSet<String>()
        addAll(out, seen, explicit)
        addAll(out, seen, tokens(prompt))
        return out
    }

    fun normalize(raw: String): String = raw.trim().removePrefix("@").trim()

    private fun addAll(out: MutableList<String>, seen: MutableSet<String>, items: List<String>) {
        for (item in items) {
            val normalized = normalize(item)
            if (normalized.isEmpty() || !seen.add(normalized)) continue
            out += normalized
        }
    }
}
