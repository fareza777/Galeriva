package com.galeriva.app.semantic

import org.json.JSONObject

/**
 * CLIP BPE tokenizer (byte-level, GPT-2 style with `</w>` word suffix),
 * compatible with openai/clip-vit-base-patch32 vocab.json + merges.txt.
 * Padding uses the end-of-text token like HuggingFace's CLIPTokenizer.
 */
class ClipTokenizer(vocabJson: String, mergesText: String) {

    private val encoder: Map<String, Int>
    private val bpeRanks: Map<Pair<String, String>, Int>
    private val byteEncoder: Map<Int, Char> = bytesToUnicode()
    private val cache = HashMap<String, List<String>>()

    private val pattern = Regex(
        """'s|'t|'re|'ve|'m|'ll|'d|[\p{L}]+|[\p{N}]|[^\s\p{L}\p{N}]+""",
        RegexOption.IGNORE_CASE
    )

    init {
        val json = JSONObject(vocabJson)
        val vocab = HashMap<String, Int>(json.length())
        json.keys().forEach { key -> vocab[key] = json.getInt(key) }
        encoder = vocab

        bpeRanks = mergesText.lineSequence()
            .drop(1) // "#version" header
            .filter { it.isNotBlank() }
            .mapIndexed { rank, line ->
                val parts = line.split(" ")
                (parts[0] to parts[1]) to rank
            }
            .toMap()
    }

    /** Returns (inputIds, attentionMask), both of length [CONTEXT_LENGTH]. */
    fun encode(text: String): Pair<LongArray, LongArray> {
        val clean = text.trim().lowercase().replace(Regex("\\s+"), " ")
        val tokens = mutableListOf<Int>()
        for (match in pattern.findAll(clean)) {
            val word = match.value
                .toByteArray(Charsets.UTF_8)
                .map { byte -> byteEncoder.getValue(byte.toInt() and 0xFF) }
                .joinToString("")
            for (piece in bpe(word)) {
                encoder[piece]?.let { tokens += it }
            }
        }
        val body = tokens.take(CONTEXT_LENGTH - 2)
        val ids = LongArray(CONTEXT_LENGTH) { EOT_TOKEN }
        val mask = LongArray(CONTEXT_LENGTH)
        ids[0] = SOT_TOKEN
        mask[0] = 1
        body.forEachIndexed { i, token ->
            ids[i + 1] = token.toLong()
            mask[i + 1] = 1
        }
        mask[body.size + 1] = 1 // the first EOT closes the sequence
        return ids to mask
    }

    private fun bpe(token: String): List<String> {
        cache[token]?.let { return it }
        if (token.isEmpty()) return emptyList()

        var word = token.map { it.toString() }.toMutableList()
        word[word.size - 1] = word.last() + "</w>"
        var pairs = getPairs(word)
        if (pairs.isEmpty()) {
            return listOf(token + "</w>").also { cache[token] = it }
        }

        while (true) {
            val bigram = pairs.minByOrNull { bpeRanks[it] ?: Int.MAX_VALUE } ?: break
            if (bigram !in bpeRanks) break
            val (first, second) = bigram
            val merged = mutableListOf<String>()
            var i = 0
            while (i < word.size) {
                val j = word.indexOfFrom(first, i)
                if (j == -1) {
                    merged += word.subList(i, word.size)
                    break
                }
                merged += word.subList(i, j)
                i = j
                if (i < word.size - 1 && word[i] == first && word[i + 1] == second) {
                    merged += first + second
                    i += 2
                } else {
                    merged += word[i]
                    i += 1
                }
            }
            word = merged
            if (word.size == 1) break
            pairs = getPairs(word)
        }
        cache[token] = word
        return word
    }

    private fun getPairs(word: List<String>): Set<Pair<String, String>> {
        val pairs = mutableSetOf<Pair<String, String>>()
        for (i in 0 until word.size - 1) pairs += word[i] to word[i + 1]
        return pairs
    }

    private fun List<String>.indexOfFrom(element: String, fromIndex: Int): Int {
        for (i in fromIndex until size) if (this[i] == element) return i
        return -1
    }

    private fun bytesToUnicode(): Map<Int, Char> {
        val bs = mutableListOf<Int>()
        bs += ('!'.code..'~'.code)
        bs += ('¡'.code..'¬'.code)
        bs += ('®'.code..'ÿ'.code)
        val cs = bs.toMutableList()
        var n = 0
        for (b in 0..255) {
            if (b !in bs) {
                bs += b
                cs += 256 + n
                n++
            }
        }
        return bs.zip(cs.map { it.toChar() }).toMap()
    }

    companion object {
        const val CONTEXT_LENGTH = 77
        const val SOT_TOKEN = 49406L
        const val EOT_TOKEN = 49407L
    }
}
