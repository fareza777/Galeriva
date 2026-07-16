package com.galeriva.app.semantic

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * On-device CLIP (ViT-B/32, int8 ONNX) — turns photos and text queries into
 * 512-dim vectors in the same semantic space. Fully offline.
 */
class ClipEngine(private val context: Context) {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val initLock = Mutex()

    @Volatile private var visionSession: OrtSession? = null
    @Volatile private var textSession: OrtSession? = null
    @Volatile private var tokenizer: ClipTokenizer? = null

    suspend fun encodeImage(bitmap: Bitmap): FloatArray = withContext(Dispatchers.Default) {
        val session = ensureVision()
        val pixels = preprocess(bitmap)
        OnnxTensor.createTensor(env, pixels, longArrayOf(1, 3, IMAGE_SIZE.toLong(), IMAGE_SIZE.toLong()))
            .use { tensor ->
                val inputName = session.inputNames.first()
                session.run(mapOf(inputName to tensor)).use { result ->
                    extractEmbedding(result, "image_embeds")
                }
            }
    }

    suspend fun encodeText(text: String): FloatArray = withContext(Dispatchers.Default) {
        val session = ensureText()
        val tok = checkNotNull(tokenizer)
        val (ids, mask) = tok.encode(text)
        val seq = ids.size.toLong()
        val inputs = mutableMapOf<String, OnnxTensor>()
        try {
            for (name in session.inputNames) {
                when (name) {
                    "input_ids" ->
                        inputs[name] = OnnxTensor.createTensor(env, LongBuffer.wrap(ids), longArrayOf(1, seq))
                    "attention_mask" ->
                        inputs[name] = OnnxTensor.createTensor(env, LongBuffer.wrap(mask), longArrayOf(1, seq))
                }
            }
            session.run(inputs).use { result ->
                extractEmbedding(result, "text_embeds")
            }
        } finally {
            inputs.values.forEach { it.close() }
        }
    }

    private suspend fun ensureVision(): OrtSession = initLock.withLock {
        visionSession ?: createSession(VISION_MODEL_ASSET).also { visionSession = it }
    }

    private suspend fun ensureText(): OrtSession = initLock.withLock {
        if (tokenizer == null) {
            tokenizer = ClipTokenizer(
                vocabJson = readAssetText("models/clip_vocab.json"),
                mergesText = readAssetText("models/clip_merges.txt")
            )
        }
        textSession ?: createSession(TEXT_MODEL_ASSET).also { textSession = it }
    }

    private suspend fun createSession(assetName: String): OrtSession =
        withContext(Dispatchers.IO) {
            val file = assetToFile(assetName)
            val options = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(
                    (Runtime.getRuntime().availableProcessors() - 1).coerceIn(1, 4)
                )
            }
            env.createSession(file.absolutePath, options)
        }

    /** ONNX models are shipped in assets; copy once to filesDir so ORT can mmap them. */
    private fun assetToFile(assetName: String): File {
        val target = File(context.filesDir, assetName.substringAfterLast('/'))
        val assetSize = context.assets.openFd(assetName).use { it.length }
        if (!target.exists() || target.length() != assetSize) {
            context.assets.open(assetName).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }
        return target
    }

    private fun readAssetText(assetName: String): String =
        context.assets.open(assetName).bufferedReader().use { it.readText() }

    private fun extractEmbedding(result: OrtSession.Result, preferredOutput: String): FloatArray {
        val optional = result.get(preferredOutput)
        val value = if (optional.isPresent) optional.get() else result.get(0)
        val tensor = value as OnnxTensor
        @Suppress("UNCHECKED_CAST")
        val rows = tensor.value as Array<FloatArray>
        return l2Normalize(rows[0])
    }

    private fun l2Normalize(vector: FloatArray): FloatArray {
        var sum = 0f
        for (v in vector) sum += v * v
        val norm = sqrt(sum)
        if (norm == 0f) return vector
        return FloatArray(vector.size) { vector[it] / norm }
    }

    /** Resize shortest side to 224, center-crop, normalize with CLIP mean/std, NCHW. */
    private fun preprocess(src: Bitmap): FloatBuffer {
        val scale = IMAGE_SIZE.toFloat() / minOf(src.width, src.height)
        val w = (src.width * scale).roundToInt().coerceAtLeast(IMAGE_SIZE)
        val h = (src.height * scale).roundToInt().coerceAtLeast(IMAGE_SIZE)
        val scaled = Bitmap.createScaledBitmap(src, w, h, true)
        val cropped = Bitmap.createBitmap(
            scaled,
            (w - IMAGE_SIZE) / 2,
            (h - IMAGE_SIZE) / 2,
            IMAGE_SIZE,
            IMAGE_SIZE
        )
        val pixels = IntArray(IMAGE_SIZE * IMAGE_SIZE)
        cropped.getPixels(pixels, 0, IMAGE_SIZE, 0, 0, IMAGE_SIZE, IMAGE_SIZE)
        if (cropped !== scaled) cropped.recycle()
        if (scaled !== src) scaled.recycle()

        val buffer = FloatBuffer.allocate(3 * IMAGE_SIZE * IMAGE_SIZE)
        for (channel in 0..2) {
            for (pixel in pixels) {
                val raw = when (channel) {
                    0 -> (pixel shr 16) and 0xFF
                    1 -> (pixel shr 8) and 0xFF
                    else -> pixel and 0xFF
                }
                buffer.put((raw / 255f - MEAN[channel]) / STD[channel])
            }
        }
        buffer.rewind()
        return buffer
    }

    companion object {
        const val EMBEDDING_DIM = 512
        private const val IMAGE_SIZE = 224
        private const val VISION_MODEL_ASSET = "models/clip_vision_b16_q8.onnx"
        private const val TEXT_MODEL_ASSET = "models/clip_text_b16_q8.onnx"
        private val MEAN = floatArrayOf(0.48145466f, 0.4578275f, 0.40821073f)
        private val STD = floatArrayOf(0.26862954f, 0.26130258f, 0.27577711f)
    }
}
