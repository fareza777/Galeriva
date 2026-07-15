package com.galeriva.app.semantic

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Serialization + math helpers for L2-normalized embedding vectors. */
object Embeddings {

    fun toBytes(vector: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(vector.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (v in vector) buffer.putFloat(v)
        return buffer.array()
    }

    fun fromBytes(bytes: ByteArray): FloatArray {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(bytes.size / 4) { buffer.getFloat(it * 4) }
    }

    /** Both vectors are L2-normalized, so the dot product IS the cosine similarity. */
    fun cosine(a: FloatArray, b: FloatArray): Float {
        var sum = 0f
        val n = minOf(a.size, b.size)
        for (i in 0 until n) sum += a[i] * b[i]
        return sum
    }

    /** Average of several normalized vectors, re-normalized (prompt ensembling). */
    fun meanNormalized(vectors: List<FloatArray>): FloatArray? {
        if (vectors.isEmpty()) return null
        val dim = vectors.first().size
        val mean = FloatArray(dim)
        for (vector in vectors) {
            for (i in 0 until dim) mean[i] += vector[i]
        }
        var norm = 0f
        for (v in mean) norm += v * v
        val scale = kotlin.math.sqrt(norm)
        if (scale == 0f) return null
        for (i in mean.indices) mean[i] /= scale
        return mean
    }
}
