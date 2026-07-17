package com.galeriva.app.semantic

import org.json.JSONObject

/**
 * SentencePiece **unigram** tokenizer for SigLIP, parsed from HuggingFace's
 * tokenizer.json. Segmentation uses the standard Viterbi algorithm: the
 * token sequence with the highest total log-probability wins.
 *
 * Normalization follows SiglipTokenizer.canonicalize_text: lowercase,
 * strip punctuation, collapse whitespace.
 */
class SiglipTokenizer(tokenizerJson: String) {

    private class Piece(val id: Int, val score: Double)

    private val vocab = HashMap<String, Piece>()
    private val maxPieceLength: Int
    private val unkId: Int
    private val unkScore: Double
    private val eosId: Long
    private val padId: Long

    init {
        val model = JSONObject(tokenizerJson).getJSONObject("model")
        require(model.getString("type") == "Unigram") { "Expected Unigram tokenizer" }
        val entries = model.getJSONArray("vocab")
        var maxLen = 1
        var minScore = 0.0
        var eos = -1
        var unk = model.optInt("unk_id", 0)
        for (i in 0 until entries.length()) {
            val entry = entries.getJSONArray(i)
            val piece = entry.getString(0)
            val score = entry.getDouble(1)
            vocab[piece] = Piece(i, score)
            if (piece.length > maxLen) maxLen = piece.length
            if (score < minScore) minScore = score
            if (piece == "</s>") eos = i
        }
        maxPieceLength = maxLen
        unkId = unk
        unkScore = minScore - 10.0
        eosId = (if (eos >= 0) eos else 1).toLong()
        padId = eosId // SigLIP pads with </s>
    }

    /** Returns input_ids of exactly [CONTEXT_LENGTH], eos-terminated, padded. */
    fun encode(text: String): LongArray {
        val normalized = normalize(text)
        val ids = if (normalized.isEmpty()) emptyList() else viterbi("▁" + normalized.replace(' ', '▁'))
        val body = ids.take(CONTEXT_LENGTH - 1)
        val output = LongArray(CONTEXT_LENGTH) { padId }
        body.forEachIndexed { i, id -> output[i] = id.toLong() }
        output[body.size] = eosId
        return output
    }

    private fun normalize(text: String): String =
        text.lowercase()
            .replace(PUNCTUATION, "")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun viterbi(s: String): List<Int> {
        val n = s.length
        val bestScore = DoubleArray(n + 1) { Double.NEGATIVE_INFINITY }
        val bestTokenId = IntArray(n + 1) { -1 }
        val bestStart = IntArray(n + 1) { -1 }
        bestScore[0] = 0.0

        for (i in 0 until n) {
            if (bestScore[i] == Double.NEGATIVE_INFINITY) continue
            val limit = minOf(maxPieceLength, n - i)
            for (len in 1..limit) {
                val piece = vocab[s.substring(i, i + len)] ?: continue
                val candidate = bestScore[i] + piece.score
                if (candidate > bestScore[i + len]) {
                    bestScore[i + len] = candidate
                    bestTokenId[i + len] = piece.id
                    bestStart[i + len] = i
                }
            }
            // Unknown single-character fallback keeps the lattice connected.
            val unkCandidate = bestScore[i] + unkScore
            if (unkCandidate > bestScore[i + 1]) {
                bestScore[i + 1] = unkCandidate
                bestTokenId[i + 1] = unkId
                bestStart[i + 1] = i
            }
        }

        val reversed = mutableListOf<Int>()
        var position = n
        while (position > 0) {
            reversed += bestTokenId[position]
            position = bestStart[position]
        }
        return reversed.reversed()
    }

    companion object {
        const val CONTEXT_LENGTH = 64
        private val PUNCTUATION = Regex("[!-/:-@\\[-`{-~]")
    }
}
