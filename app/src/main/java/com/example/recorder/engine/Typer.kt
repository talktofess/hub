package com.example.recorder.engine

import kotlin.random.Random

/**
 * Pure typing primitives shared by every sim. Port of src/recording/typer.ts.
 *
 * A sim turns its script into a flat list of [TypeStep]s; [TypeEngine] runs them.
 * Body text may contain the typo markup [[wrong|right]] — type the wrong spelling,
 * pause, backspace it, type the correction — expanded into keystrokes by [tokenize].
 */

sealed interface TypeStep {
    /** Type [text] character-by-character, pushing each partial to [onUpdate]. */
    data class Type(
        val text: String,
        val onUpdate: (String) -> Unit,
        val cue: Float? = null,
    ) : TypeStep

    /** Run [fn] (reveal a card, reset state), then optionally wait [delay] ms. */
    data class Reveal(val fn: () -> Unit, val delay: Int? = null) : TypeStep

    /** Idle for [ms] milliseconds. */
    data class Pause(val ms: Int) : TypeStep
}

sealed interface KeyAction {
    data class Char(val ch: String) : KeyAction
    data object Back : KeyAction
    data class Wait(val ms: Int) : KeyAction
}

private val TYPO_RE = Regex("""\[\[([^\]|]*)\|([^\]]*)]]""")

/** Expand a body string (with optional [[wrong|right]] typos) into keystrokes. */
fun tokenize(text: String): List<KeyAction> {
    val out = ArrayList<KeyAction>()
    fun pushChars(s: String) {
        // iterate by code points so emoji/surrogate pairs stay intact
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            val n = Character.charCount(cp)
            out.add(KeyAction.Char(s.substring(i, i + n)))
            i += n
        }
    }
    var last = 0
    for (m in TYPO_RE.findAll(text)) {
        pushChars(text.substring(last, m.range.first))
        val wrong = m.groupValues[1]
        val right = m.groupValues[2]
        pushChars(wrong)
        out.add(KeyAction.Wait((240 + Random.nextDouble() * 160).toInt()))
        val wrongLen = wrong.codePointCount(0, wrong.length)
        repeat(wrongLen) { out.add(KeyAction.Back) }
        out.add(KeyAction.Wait((110 + Random.nextDouble() * 90).toInt()))
        pushChars(right)
        last = m.range.last + 1
    }
    pushChars(text.substring(last))
    return out
}

/** Strip the typo markup to get the final, settled text (for instant fills/preview). */
fun settledText(text: String): String =
    TYPO_RE.replace(text) { it.groupValues[2] }

data class TimingOpts(val jitter: Float, val thinkPauses: Float)

/**
 * Per-character delay (ms). jitter scales variance; thinkPauses scales the
 * occasional hesitation. Spaces/punctuation breathe; letters are quick.
 */
fun charDelay(ch: String, o: TimingOpts): Double {
    val j = 0.35 + o.jitter // keep a little variance even at 0
    if (ch == "\n") return 170 + Random.nextDouble() * 150 * j
    if (ch == " ") return 52 + Random.nextDouble() * 55 * j
    if (ch.length == 1 && ",.;:!?—".contains(ch)) return 115 + Random.nextDouble() * 130 * j
    val base = 40 + Random.nextDouble() * 65 * j
    val hitchProb = 0.1 * o.thinkPauses
    return if (Random.nextDouble() < hitchProb)
        base + 150 + Random.nextDouble() * 260 * o.thinkPauses
    else base
}

/** A plausible wrong neighbor key for the auto-typo effect. */
private val NEIGHBORS: Map<Char, Char> = mapOf(
    'a' to 's', 's' to 'd', 'd' to 'f', 'f' to 'g', 'g' to 'h', 'h' to 'j', 'j' to 'k', 'k' to 'l', 'l' to 'k',
    'q' to 'w', 'w' to 'e', 'e' to 'r', 'r' to 't', 't' to 'y', 'y' to 'u', 'u' to 'i', 'i' to 'o', 'o' to 'p', 'p' to 'o',
    'z' to 'x', 'x' to 'c', 'c' to 'v', 'v' to 'b', 'b' to 'n', 'n' to 'm', 'm' to 'n',
)

fun fumbleFor(ch: String): String? {
    if (ch.length != 1) return null
    val c = ch[0]
    val lower = c.lowercaseChar()
    val n = NEIGHBORS[lower] ?: return null
    return if (c == lower) n.toString() else n.uppercaseChar().toString()
}

/** A plausible mistyped version of a whole word (one neighbor-key swap). */
fun fumbleWord(word: String): String {
    if (word.length < 2) return word
    val i = Random.nextInt(word.length)
    val rep = fumbleFor(word[i].toString()) ?: return word
    return word.substring(0, i) + rep + word.substring(i + 1)
}

/** Split text into ordered (segment, isWord) runs — words vs whitespace runs. */
fun splitWords(t: String): List<Pair<String, Boolean>> {
    if (t.isEmpty()) return emptyList()
    val res = ArrayList<Pair<String, Boolean>>()
    var i = 0
    while (i < t.length) {
        val ws = t[i].isWhitespace()
        val start = i
        while (i < t.length && t[i].isWhitespace() == ws) i++
        res.add(t.substring(start, i) to !ws)
    }
    return res
}
