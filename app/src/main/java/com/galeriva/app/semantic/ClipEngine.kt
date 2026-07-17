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
import kotlin.math.sqrt

/**
 * On-device SigLIP (base-patch16-224, int8 ONNX) — turns photos and text
 * queries into 768-dim vectors in a shared semantic space. Fully offline.
 * (Class name kept from the original CLIP engine to avoid churn.)
 */
class ClipEngine(private val context: Context) {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val initLock = Mutex()

    @Volatile private var visionSession: OrtSession? = null
    @Volatile private var textSession: OrtSession? = null
    @Volatile private var tokenizer: SiglipTokenizer? = null

    suspend fun encodeImage(bitmap: Bitmap): FloatArray = withContext(Dispatchers.Default) {
        val session = ensureVision()
        val pixels = preprocess(bitmap)
        OnnxTensor.createTensor(env, pixels, longArrayOf(1, 3, IMAGE_SIZE.toLong(), IMAGE_SIZE.toLong()))
            .use { tensor ->
                val inputName = session.inputNames.first()
                session.run(mapOf(inputName to tensor)).use { result ->
                    extractEmbedding(result, listOf("image_embeds", "pooler_output"))
                }
            }
    }

    suspend fun encodeText(text: String): FloatArray = withContext(Dispatchers.Default) {
        val session = ensureText()
        val tok = checkNotNull(tokenizer)
        val ids = tok.encode(text)
        val inputs = mutableMapOf<String, OnnxTensor>()
        try {
            for (name in session.inputNames) {
                when (name) {
                    "input_ids" -> inputs[name] = OnnxTensor.createTensor(
                        env, LongBuffer.wrap(ids), longArrayOf(1, ids.size.toLong())
                    )
                    "attention_mask" -> inputs[name] = OnnxTensor.createTensor(
                        env,
                        LongBuffer.wrap(LongArray(ids.size) { 1L }),
                        longArrayOf(1, ids.size.toLong())
                    )
                }
            }
            session.run(inputs).use { result ->
                extractEmbedding(result, listOf("text_embeds", "pooler_output"))
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
            tokenizer = SiglipTokenizer(readAssetText("models/siglip_tokenizer.json"))
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

    /**
     * Picks the pooled embedding from the session outputs: preferred names
     * first, then the first 2-D tensor — export output names vary.
     */
    private fun extractEmbedding(
        result: OrtSession.Result,
        preferredNames: List<String>
    ): FloatArray {
        for (name in preferredNames) {
            val optional = result.get(name)
            if (optional.isPresent) {
                val tensor = optional.get() as? OnnxTensor ?: continue
                if (tensor.info.shape.size == 2) return l2Normalize(firstRow(tensor))
            }
        }
        for (entry in result) {
            val value = entry.value
            if (value is OnnxTensor && value.info.shape.size == 2) {
                return l2Normalize(firstRow(value))
            }
        }
        error("No 2-D embedding output found")
    }

    private fun firstRow(tensor: OnnxTensor): FloatArray {
        @Suppress("UNCHECKED_CAST")
        return (tensor.value as Array<FloatArray>)[0]
    }

    private fun l2Normalize(vector: FloatArray): FloatArray {
        var sum = 0f
        for (v in vector) sum += v * v
        val norm = sqrt(sum)
        if (norm == 0f) return vector
        return FloatArray(vector.size) { vector[it] / norm }
    }

    /** SigLIP preprocessing: direct resize to 224x224, normalize (x/255-0.5)/0.5. */
    private fun preprocess(src: Bitmap): FloatBuffer {
        val scaled = Bitmap.createScaledBitmap(src, IMAGE_SIZE, IMAGE_SIZE, true)
        val pixels = IntArray(IMAGE_SIZE * IMAGE_SIZE)
        scaled.getPixels(pixels, 0, IMAGE_SIZE, 0, 0, IMAGE_SIZE, IMAGE_SIZE)
        if (scaled !== src) scaled.recycle()

        val buffer = FloatBuffer.allocate(3 * IMAGE_SIZE * IMAGE_SIZE)
        for (channel in 0..2) {
            for (pixel in pixels) {
                val raw = when (channel) {
                    0 -> (pixel shr 16) and 0xFF
                    1 -> (pixel shr 8) and 0xFF
                    else -> pixel and 0xFF
                }
                buffer.put(raw / 127.5f - 1f)
            }
        }
        buffer.rewind()
        return buffer
    }

    companion object {
        const val EMBEDDING_DIM = 768
        private const val IMAGE_SIZE = 224
        private const val VISION_MODEL_ASSET = "models/siglip_vision_q8.onnx"
        private const val TEXT_MODEL_ASSET = "models/siglip_text_q8.onnx"
    }
}
