package com.paryavarankavalu.paryavarankavalu.ai

import android.graphics.Bitmap
import org.tensorflow.lite.DataType
import java.nio.ByteBuffer
import java.nio.ByteOrder

object ImagePreprocessor {
    const val INPUT_SIZE = 224
    private const val CHANNELS = 3

    fun preprocess(bitmap: Bitmap, inputType: DataType): ByteBuffer {
        val resized = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        val bytesPerChannel = if (inputType == DataType.UINT8 || inputType == DataType.INT8) 1 else 4
        val buffer = ByteBuffer.allocateDirect(INPUT_SIZE * INPUT_SIZE * CHANNELS * bytesPerChannel)
        buffer.order(ByteOrder.nativeOrder())

        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        resized.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        pixels.forEach { pixel ->
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            when (inputType) {
                DataType.UINT8 -> {
                    buffer.put(r.toByte())
                    buffer.put(g.toByte())
                    buffer.put(b.toByte())
                }
                DataType.INT8 -> {
                    buffer.put((r - 128).toByte())
                    buffer.put((g - 128).toByte())
                    buffer.put((b - 128).toByte())
                }
                else -> {
                buffer.putFloat((r / 127.5f) - 1f)
                buffer.putFloat((g / 127.5f) - 1f)
                buffer.putFloat((b / 127.5f) - 1f)
                }
            }
        }

        buffer.rewind()
        return buffer
    }
}
