package dev.clankyard.diff

import kotlin.math.max
import kotlin.math.min

private const val CONTEXT = 3
private const val DP_CELL_LIMIT = 1_000_000
private const val NO_NEWLINE = "\\ No newline at end of file"

internal enum class Op { Keep, Insert, Delete }

internal data class Step(val op: Op, val a: Int, val b: Int)

internal data class Change(val a0: Int, val a1: Int, val b0: Int, val b1: Int)

internal data class SplitLines(val lines: List<String>, val endsWithNewline: Boolean)

class MyersDiffEngine : DiffEngine {
    override fun unified(beforeUtf8: String, afterUtf8: String, pathLabel: String): String {
        val before = splitLines(beforeUtf8)
        val after = splitLines(afterUtf8)
        val steps = diffSteps(before.lines, after.lines)
        return renderUnified(pathLabel, before, after, steps)
    }
}

internal fun splitLines(text: String): SplitLines {
    if (text.isEmpty()) return SplitLines(emptyList(), true)
    val ends = text.endsWith('\n')
    val raw = text.split('\n')
    val lines = if (ends) raw.subList(0, raw.lastIndex) else raw
    return SplitLines(lines, ends)
}

internal fun diffSteps(a: List<String>, b: List<String>): List<Step> {
    if (a.isEmpty() && b.isEmpty()) return emptyList()
    val cells = a.size.toLong() * b.size.toLong()
    return if (cells <= DP_CELL_LIMIT) lcsSteps(a, b) else coarseSteps(a, b)
}

internal fun lcsSteps(a: List<String>, b: List<String>): List<Step> {
    val n = a.size
    val m = b.size
    val dp = Array(n + 1) { IntArray(m + 1) }
    for (i in n - 1 downTo 0) {
        val row = dp[i]
        val next = dp[i + 1]
        for (j in m - 1 downTo 0) {
            row[j] = if (a[i] == b[j]) next[j + 1] + 1 else max(next[j], row[j + 1])
        }
    }
    val steps = ArrayList<Step>(n + m)
    var i = 0
    var j = 0
    while (i < n && j < m) {
        when {
            a[i] == b[j] -> {
                steps.add(Step(Op.Keep, i, j))
                i++
                j++
            }
            dp[i + 1][j] >= dp[i][j + 1] -> {
                steps.add(Step(Op.Delete, i, j))
                i++
            }
            else -> {
                steps.add(Step(Op.Insert, i, j))
                j++
            }
        }
    }
    while (i < n) {
        steps.add(Step(Op.Delete, i, j))
        i++
    }
    while (j < m) {
        steps.add(Step(Op.Insert, i, j))
        j++
    }
    return steps
}

internal fun coarseSteps(a: List<String>, b: List<String>): List<Step> {
    var start = 0
    val shared = min(a.size, b.size)
    while (start < shared && a[start] == b[start]) start++
    var aEnd = a.size
    var bEnd = b.size
    while (aEnd > start && bEnd > start && a[aEnd - 1] == b[bEnd - 1]) {
        aEnd--
        bEnd--
    }
    val steps = ArrayList<Step>(a.size + b.size)
    for (i in 0 until start) steps.add(Step(Op.Keep, i, i))
    for (i in start until aEnd) steps.add(Step(Op.Delete, i, start))
    for (j in start until bEnd) steps.add(Step(Op.Insert, aEnd, j))
    var k = 0
    while (aEnd + k < a.size) {
        steps.add(Step(Op.Keep, aEnd + k, bEnd + k))
        k++
    }
    return steps
}

internal fun collectChanges(steps: List<Step>): List<Change> {
    val out = ArrayList<Change>()
    var open = false
    var a0 = 0
    var b0 = 0
    var a1 = 0
    var b1 = 0
    for (step in steps) {
        when (step.op) {
            Op.Keep -> {
                if (open) {
                    out.add(Change(a0, a1, b0, b1))
                    open = false
                }
            }
            Op.Delete, Op.Insert -> {
                if (!open) {
                    a0 = step.a
                    b0 = step.b
                    open = true
                }
                a1 = if (step.op == Op.Delete) step.a + 1 else step.a
                b1 = if (step.op == Op.Insert) step.b + 1 else step.b
            }
        }
    }
    if (open) out.add(Change(a0, a1, b0, b1))
    return out
}

internal fun mergeChanges(changes: List<Change>): List<Change> {
    if (changes.isEmpty()) return emptyList()
    val merged = ArrayList<Change>(changes.size)
    var cur = changes.first()
    for (i in 1 until changes.size) {
        val next = changes[i]
        val closeA = next.a0 - cur.a1 <= CONTEXT * 2
        val closeB = next.b0 - cur.b1 <= CONTEXT * 2
        if (closeA && closeB) {
            cur = Change(cur.a0, next.a1, cur.b0, next.b1)
        } else {
            merged.add(cur)
            cur = next
        }
    }
    merged.add(cur)
    return merged
}

internal fun renderUnified(
    pathLabel: String,
    before: SplitLines,
    after: SplitLines,
    steps: List<Step>,
): String {
    val a = before.lines
    val b = after.lines
    val hunks = mergeChanges(collectChanges(steps))
    val out = StringBuilder()
    out.append("--- a/").append(pathLabel).append('\n')
    out.append("+++ b/").append(pathLabel).append('\n')
    for (change in hunks) {
        appendHunk(out, a, b, before.endsWithNewline, after.endsWithNewline, change)
    }
    return out.toString()
}

private fun appendHunk(
    out: StringBuilder,
    a: List<String>,
    b: List<String>,
    aEndsNl: Boolean,
    bEndsNl: Boolean,
    change: Change,
) {
    val aStart = max(0, change.a0 - CONTEXT)
    val bStart = max(0, change.b0 - CONTEXT)
    val aEnd = min(a.size, change.a1 + CONTEXT)
    val bEnd = min(b.size, change.b1 + CONTEXT)
    val oldCount = aEnd - aStart
    val newCount = bEnd - bStart
    val oldStart = if (oldCount == 0) aStart else aStart + 1
    val newStart = if (newCount == 0) bStart else bStart + 1
    out.append("@@ -").append(oldStart).append(',').append(oldCount)
    out.append(" +").append(newStart).append(',').append(newCount).append(" @@\n")
    for (i in aStart until change.a0) {
        out.append(' ').append(a[i]).append('\n')
    }
    for (i in change.a0 until change.a1) {
        out.append('-').append(a[i]).append('\n')
        if (i == a.lastIndex && !aEndsNl) out.append(NO_NEWLINE).append('\n')
    }
    for (j in change.b0 until change.b1) {
        out.append('+').append(b[j]).append('\n')
        if (j == b.lastIndex && !bEndsNl) out.append(NO_NEWLINE).append('\n')
    }
    for (i in change.a1 until aEnd) {
        out.append(' ').append(a[i]).append('\n')
        if (i == a.lastIndex && !aEndsNl) out.append(NO_NEWLINE).append('\n')
    }
}
