package com.paryavarankavalu.paryavarankavalu.ai

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import kotlin.math.exp

class TFLiteWasteClassifier(context: Context) : Closeable {
    private val appContext = context.applicationContext
    private val mutex = Mutex()

    @Volatile private var interpreter: Interpreter? = null
    @Volatile private var labels: List<String>? = null

    suspend fun classify(bitmap: Bitmap): PredictionResult = withContext(Dispatchers.Default) {
        if (bitmap.width <= 1 || bitmap.height <= 1 || bitmap.isRecycled) {
            return@withContext PredictionResult.notDetected()
        }

        mutex.withLock {
            try {
                val model = getInterpreter()
                val labelList = getLabels()
                val inputTensor = model.getInputTensor(0)
                val outputTensor = model.getOutputTensor(0)
                val input = ImagePreprocessor.preprocess(bitmap, inputTensor.dataType())
                val output = createOutputBuffer(outputTensor.shape().last(), outputTensor.dataType())
                val started = SystemClock.elapsedRealtime()

                model.run(input, output)

                val scores = readScores(output, outputTensor.dataType())
                val probabilities = if (scores.any { it < 0f || it > 1f }) softmax(scores) else scores
                val bestIndex = probabilities.indices.maxByOrNull { probabilities[it] } ?: -1
                if (bestIndex !in labelList.indices) {
                    return@withLock PredictionResult.notDetected()
                }

                val category = LabelMappingUtils.normalizeWasteCategory(labelList[bestIndex])
                val confidence = probabilities[bestIndex].coerceIn(0f, 1f)
                if (category == PredictionResult.NOT_DETECTED || confidence < MIN_CONFIDENCE) {
                    PredictionResult.notDetected()
                } else {
                    PredictionResult(
                        category = category,
                        confidence = confidence,
                        recommendedBin = LabelMappingUtils.recommendedBinFor(category),
                        inferenceTimeMs = SystemClock.elapsedRealtime() - started
                    )
                }
            } catch (e: OutOfMemoryError) {
                Log.e(TAG, "Not enough memory for waste classification", e)
                PredictionResult.notDetected()
            } catch (e: Exception) {
                Log.e(TAG, "TFLite classification failed: ${e.message}", e)
                PredictionResult.notDetected()
            }
        }
    }

    private fun getInterpreter(): Interpreter {
        return interpreter ?: ModelLoader.createInterpreter(appContext).also { interpreter = it }
    }

    private fun getLabels(): List<String> {
        return labels ?: ModelLoader.loadLabels(appContext).also { labels = it }
    }

    private fun createOutputBuffer(labelCount: Int, dataType: DataType): Any {
        return when (dataType) {
            DataType.UINT8, DataType.INT8 -> Array(1) { ByteArray(labelCount) }
            else -> Array(1) { FloatArray(labelCount) }
        }
    }

    private fun readScores(output: Any, dataType: DataType): FloatArray {
        return when (dataType) {
            DataType.UINT8 -> {
                val bytes = (output as Array<ByteArray>)[0]
                FloatArray(bytes.size) { (bytes[it].toInt() and 0xFF) / 255f }
            }
            DataType.INT8 -> {
                val bytes = (output as Array<ByteArray>)[0]
                FloatArray(bytes.size) { (bytes[it].toInt() + 128) / 255f }
            }
            else -> (output as Array<FloatArray>)[0]
        }
    }

    private fun softmax(values: FloatArray): FloatArray {
        val max = values.maxOrNull() ?: return values
        val exps = values.map { exp((it - max).toDouble()).toFloat() }
        val sum = exps.sum().takeIf { it > 0f } ?: return values
        return FloatArray(exps.size) { exps[it] / sum }
    }

    override fun close() {
        interpreter?.close()
        interpreter = null
    }

    companion object {
        private const val TAG = "TFLiteWasteClassifier"
        private const val MIN_CONFIDENCE = 0.20f
    }
}
