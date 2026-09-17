package dev.clankyard.ai.secret

import kotlin.math.ln

internal data class Redaction(
    val text: String,
    val redacted: Boolean,
    val omissions: List<String>,
)

internal fun redactSecrets(text: String, propertiesStyle: Boolean): Redaction {
    var out = text
    val omissions = ArrayList<String>()
    out = replaceAll(out, PEM_BLOCK, "[REDACTED_PEM]", omissions, "PEM")
    out = replaceAll(out, AKIA, "[REDACTED]", omissions, "AKIA")
    out = replaceAll(out, SK_ANT, "[REDACTED]", omissions, "sk-ant")
    out = replaceAll(out, SK_PROJ, "[REDACTED]", omissions, "sk-proj")
    out = replaceAll(out, XAI, "[REDACTED]", omissions, "xai")
    out = replaceAll(out, SK, "[REDACTED]", omissions, "sk")
    out = redactHighEntropy(out, propertiesStyle, omissions)
    return Redaction(out, omissions.isNotEmpty(), omissions)
}

internal fun containsNulInProbe(text: String): Boolean {
    val probe = if (text.length <= SecretLimits.BINARY_PROBE_BYTES) text
    else text.substring(0, SecretLimits.BINARY_PROBE_BYTES)
    return probe.indexOf('\u0000') >= 0
}

private fun replaceAll(
    text: String,
    pattern: Regex,
    replacement: String,
    omissions: MutableList<String>,
    label: String,
): String {
    if (!pattern.containsMatchIn(text)) return text
    if (label !in omissions) omissions += label
    return pattern.replace(text, replacement)
}

private fun redactHighEntropy(
    text: String,
    propertiesStyle: Boolean,
    omissions: MutableList<String>,
): String {
    val pattern = if (propertiesStyle) PROP_ASSIGN else ASSIGN
    return pattern.replace(text) { match ->
        replaceAssignment(match, propertiesStyle, omissions)
    }
}

private fun replaceAssignment(
    match: MatchResult,
    propertiesStyle: Boolean,
    omissions: MutableList<String>,
): String {
    val value = match.groupValues.last()
    if (!isHighEntropySecret(value, propertiesStyle)) return match.value
    if ("high-entropy" !in omissions) omissions += "high-entropy"
    val prefix = match.value.dropLast(value.length)
    return prefix + "[REDACTED]"
}

internal fun isHighEntropySecret(value: String, propertiesStyle: Boolean): Boolean {
    val minLen = if (propertiesStyle) 16 else 24
    if (value.length < minLen) return false
    if (looksLikePath(value)) return false
    val alnum = value.count { it.isLetterOrDigit() || it in "+/=_-." }
    if (alnum < value.length * 9 / 10) return false
    val minEntropy = if (propertiesStyle) 4.5 else 4.8
    return shannon(value) >= minEntropy
}

private fun looksLikePath(value: String): Boolean {
    if (value.startsWith("/") || value.startsWith("./") || value.startsWith("../")) return true
    if (value.startsWith("~")) return true
    return value.length >= 2 && value[1] == ':' && (value[0].isLetter())
}

private fun shannon(value: String): Double {
    val counts = HashMap<Char, Int>(value.length.coerceAtMost(128))
    for (c in value) counts[c] = (counts[c] ?: 0) + 1
    val n = value.length.toDouble()
    var h = 0.0
    for (count in counts.values) {
        val p = count / n
        h -= p * ln(p) / LN_2
    }
    return h
}

private val PEM_BLOCK = Regex(
    """-----BEGIN [A-Z0-9 ]+-----.*?-----END [A-Z0-9 ]+-----""",
    setOf(RegexOption.DOT_MATCHES_ALL),
)
private val AKIA = Regex("""AKIA[0-9A-Z]{16}""")
private val SK_ANT = Regex("""sk-ant-[^\s"'\\]+""")
private val SK_PROJ = Regex("""sk-proj-[^\s"'\\]+""")
private val XAI = Regex("""xai-[^\s"'\\]+""")
private val SK = Regex("""sk-[^\s"'\\]{8,}""")
private val ASSIGN = Regex(
    """(^|[\s;])([A-Za-z_][\w.-]*)\s*[=:]\s*([^\s#'"]{24,})""",
    setOf(RegexOption.MULTILINE),
)
private val PROP_ASSIGN = Regex(
    """^([A-Za-z_][\w.-]*)\s*[=:]\s*(\S+)$""",
    setOf(RegexOption.MULTILINE),
)
private val LN_2 = ln(2.0)
