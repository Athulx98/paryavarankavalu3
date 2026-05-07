package com.paryavarankavalu.paryavarankavalu.ai

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.MappedByteBuffer

object ModelLoader {
    private const val TAG = "ModelLoader"
    const val WASTE_MODEL_FILE = "waste_model.tflite"
    const val CLEANUP_MODEL_FILE = "cleanup_model.tflite"
    const val LABELS_FILE = "labels.txt"

    private val modelBuffers = mutableMapOf<String, MappedByteBuffer>()
    private val labelCache = mutableMapOf<String, List<String>>()

    fun loadModel(context: Context, modelFile: String = WASTE_MODEL_FILE): MappedByteBuffer {
        return modelBuffers[modelFile] ?: synchronized(this) {
            modelBuffers[modelFile] ?: FileUtil.loadMappedFile(context.applicationContext, modelFile).also {
                modelBuffers[modelFile] = it
                Log.d(TAG, "TFLite model mapped from assets/$modelFile")
            }
        }
    }

    fun loadLabels(context: Context, labelsFile: String = LABELS_FILE): List<String> {
        return labelCache[labelsFile] ?: synchronized(this) {
            labelCache[labelsFile] ?: context.applicationContext.assets.open(labelsFile).bufferedReader().useLines { lines ->
                lines.map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .toList()
            }.also {
                labelCache[labelsFile] = it
                Log.d(TAG, "Loaded ${it.size} waste labels")
            }
        }
    }

    fun createInterpreter(context: Context, modelFile: String = WASTE_MODEL_FILE): Interpreter {
        val options = Interpreter.Options().apply {
            setNumThreads(Runtime.getRuntime().availableProcessors().coerceIn(2, 4))
            setUseXNNPACK(true)
        }
        return Interpreter(loadModel(context, modelFile), options)
    }

    fun hasAsset(context: Context, assetName: String): Boolean {
        return try {
            context.applicationContext.assets.open(assetName).close()
            true
        } catch (_: Exception) {
            false
        }
    }
}
