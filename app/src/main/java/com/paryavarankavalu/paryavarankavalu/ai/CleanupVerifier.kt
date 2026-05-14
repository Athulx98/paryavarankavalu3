package com.paryavarankavalu.paryavarankavalu.ai

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp

object CleanupVerifier : Closeable {
    private const val TAG = "CleanupVerifier"
    private val mutex = Mutex()

    private val objectDetector by lazy {
        val options = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
            .enableMultipleObjects()
            .enableClassification()
            .build()
        ObjectDetection.getClient(options)
    }

    @Volatile private var cleanupInterpreter: Interpreter? = null

    suspend fun verify(context: Context, beforeBitmap: Bitmap?, afterBitmap: Bitmap): CleanupResult {
        verifyWithTflite(context, beforeBitmap, afterBitmap)?.let { return it }
        return verifyWithMlKit(beforeBitmap, afterBitmap)
    }

    suspend fun verifyWithTflite(context: Context, beforeBitmap: Bitmap?, afterBitmap: Bitmap): CleanupResult? = withContext(Dispatchers.Default) {
        if (!ModelLoader.hasAsset(context, ModelLoader.CLEANUP_MODEL_FILE)) return@withContext null

        mutex.withLock {
            try {
                val beforeLevel = beforeBitmap?.let { estimateWasteLevelWithModel(context, it) }
                val afterLevel = estimateWasteLevelWithModel(context, afterBitmap)
                if (beforeLevel != null) {
                    CleanupResult.fromLevels(beforeLevel, afterLevel, source = "TensorFlow Lite")
                } else {
                    CleanupResult.afterOnly(afterLevel, source = "TensorFlow Lite")
                }
            } catch (e: Exception) {
                Log.w(TAG, "TFLite cleanup verifier failed: ${e.message}")
                null
            }
        }
    }

    suspend fun verifyWithMlKit(beforeBitmap: Bitmap?, afterBitmap: Bitmap): CleanupResult {
        val afterLevel = estimateWasteLevelWithMlKit(afterBitmap)
        val beforeLevel = beforeBitmap?.let { estimateWasteLevelWithMlKit(it) }
        return if (beforeLevel != null) {
            CleanupResult.fromLevels(beforeLevel, afterLevel, source = "ML Kit")
        } else {
            CleanupResult.afterOnly(afterLevel, source = "ML Kit")
        }
    }

    private fun estimateWasteLevelWithModel(context: Context, bitmap: Bitmap): Float {
        val interpreter = cleanupInterpreter ?: ModelLoader.createInterpreter(
            context,
            ModelLoader.CLEANUP_MODEL_FILE
        ).also { cleanupInterpreter = it }

        val inputTensor = interpreter.getInputTensor(0)
        val outputTensor = interpreter.getOutputTensor(0)
        val input = ImagePreprocessor.preprocess(bitmap, inputTensor.dataType())
        val output = ByteBuffer.allocateDirect(outputTensor.numBytes()).order(ByteOrder.nativeOrder())
        interpreter.run(input, output)
        val scores = readTensor(output, outputTensor.dataType(), outputTensor.numElements())
        return interpretWasteLevel(scores, outputTensor.shape())
    }

    private suspend fun estimateWasteLevelWithMlKit(bitmap: Bitmap): Float = withContext(Dispatchers.Default) {
        try {
            val objects = objectDetector.process(InputImage.fromBitmap(bitmap, 0)).await()
            if (objects.isEmpty()) return@withContext 0.08f

            val imageArea = (bitmap.width * bitmap.height).toFloat().coerceAtLeast(1f)
            var wasteArea = 0f
            var wasteConfidence = 0f
            var wasteObjects = 0

            objects.forEach { obj ->
                val labels = obj.labels.map { it.text to it.confidence }
                val labelWaste = LabelMappingUtils.containsWaste(labels, minConfidence = 0.08f)
                val genericWaste = obj.labels.isEmpty() && obj.boundingBox.width() * obj.boundingBox.height() > imageArea * 0.04f
                if (labelWaste || genericWaste) {
                    val boxArea = (obj.boundingBox.width() * obj.boundingBox.height()).toFloat() / imageArea
                    val confidence = obj.labels.maxOfOrNull { it.confidence } ?: 0.35f
                    wasteArea += boxArea.coerceIn(0f, 0.45f) * confidence
                    wasteConfidence += confidence
                    wasteObjects += 1
                }
            }

            val areaScore = (wasteArea * 2.5f).coerceIn(0f, 1f)
            val countScore = (wasteObjects / 6f).coerceIn(0f, 1f)
            val confidenceScore = (wasteConfidence / 4f).coerceIn(0f, 1f)
            maxOf(areaScore, (countScore * 0.45f) + (confidenceScore * 0.55f))
        } catch (e: Exception) {
            Log.w(TAG, "ML Kit cleanup estimate failed: ${e.message}")
            0.65f
        }
    }

    private fun readTensor(buffer: ByteBuffer, dataType: DataType, elementCount: Int): FloatArray {
        buffer.rewind()
        return when (dataType) {
            DataType.UINT8 -> FloatArray(elementCount) { (buffer.get().toInt() and 0xFF) / 255f }
            DataType.INT8 -> FloatArray(elementCount) { (buffer.get().toInt() + 128) / 255f }
            else -> FloatArray(elementCount) { buffer.float }
        }
    }

    private fun interpretWasteLevel(scores: FloatArray, shape: IntArray): Float {
        if (scores.isEmpty()) return 0.65f
        if (scores.size == 1) return scores[0].coerceIn(0f, 1f)

        val normalized = if (scores.any { it < 0f || it > 1f }) softmax(scores) else scores
        if (shape.lastOrNull() == 2 && normalized.size >= 2) {
            var wasteSum = 0f
            var count = 0
            var index = 1
            while (index < normalized.size) {
                wasteSum += normalized[index]
                count += 1
                index += 2
            }
            return (wasteSum / count.coerceAtLeast(1)).coerceIn(0f, 1f)
        }
        return normalized.maxOrNull()?.coerceIn(0f, 1f) ?: 0.65f
    }

    private fun softmax(values: FloatArray): FloatArray {
        val max = values.maxOrNull() ?: return values
        val exps = values.map { exp((it - max).toDouble()).toFloat() }
        val sum = exps.sum().takeIf { it > 0f } ?: return values
        return FloatArray(exps.size) { exps[it] / sum }
    }

    override fun close() {
        cleanupInterpreter?.close()
        cleanupInterpreter = null
    }
}
