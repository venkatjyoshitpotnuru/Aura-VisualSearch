package com.example.mysearchapp

import android.content.Context
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.OrtSession.SessionOptions
import java.io.File
import java.io.FileOutputStream
import java.nio.FloatBuffer
import java.nio.IntBuffer

class OnnxEngine(context: Context) {
    private val ortEnvironment = OrtEnvironment.getEnvironment()

    private var visionSession: OrtSession? = null
    private var textSession: OrtSession? = null

    init {
        // 1. Copy the main model files
        val visionFile = copyAssetToStorage(context, "vision_encoder.onnx")
        val textFile = copyAssetToStorage(context, "text_encoder.onnx")

        // 2. Copy the external weight (.data) files required by the models
        copyAssetToStorage(context, "vision_encoder.onnx.data")
        copyAssetToStorage(context, "text_encoder.onnx.data") // Safe to call even if it doesn't exist

        val sessionOptions = SessionOptions().apply {
            val cores = Runtime.getRuntime().availableProcessors()
            setIntraOpNumThreads(cores.coerceAtLeast(2))
            setOptimizationLevel(SessionOptions.OptLevel.ALL_OPT)
        }

        // 3. Create sessions (ONNX Runtime will automatically find the .data files next to the .onnx files)
        visionSession = ortEnvironment.createSession(visionFile.absolutePath, sessionOptions)
        textSession = ortEnvironment.createSession(textFile.absolutePath, sessionOptions)
    }

    private fun copyAssetToStorage(context: Context, filename: String): File {
        val file = File(context.filesDir, filename)
        try {
            // Force delete to prevent caching mismatched versions
            if (file.exists()) {
                file.delete()
            }
            context.assets.open(filename).use { inputStream ->
                FileOutputStream(file).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        } catch (e: Exception) {
            // Silent catch: It's okay if a specific .data file doesn't exist for the text encoder
        }
        return file
    }

    fun processImage(bitmap: android.graphics.Bitmap): FloatArray {
        val resized = if (bitmap.width == 224 && bitmap.height == 224) {
            bitmap
        } else {
            android.graphics.Bitmap.createScaledBitmap(bitmap, 224, 224, true)
        }

        val floatArray = FloatArray(3 * 224 * 224)
        val pixels = IntArray(224 * 224)
        resized.getPixels(pixels, 0, 224, 0, 0, 224, 224)

        var index = 0
        for (pixel in pixels) {
            val r = ((pixel shr 16) and 0xFF) / 255.0f
            val g = ((pixel shr 8) and 0xFF) / 255.0f
            val b = (pixel and 0xFF) / 255.0f

            floatArray[index] = (r - 0.48145466f) / 0.26862954f
            floatArray[index + 50176] = (g - 0.4578275f) / 0.26130258f
            floatArray[index + 100352] = (b - 0.40821073f) / 0.27577711f
            index++
        }
        return floatArray
    }

    fun extractImageVector(imagePixels: FloatArray): FloatArray? {
        return try {
            val shape = longArrayOf(1, 3, 224, 224)
            val inputTensor = ai.onnxruntime.OnnxTensor.createTensor(
                ortEnvironment,
                FloatBuffer.wrap(imagePixels),
                shape
            )
            val result = visionSession?.run(mapOf("image_input" to inputTensor))
            val rawOutput = result?.get(0)?.value
            val vector = when (rawOutput) {
                is Array<*> -> rawOutput[0] as FloatArray
                is FloatArray -> rawOutput
                else -> null
            }
            inputTensor.close()
            result?.close()
            vector
        } catch (e: Exception) {
            null
        }
    }

    fun extractTextVector(tokens: IntArray): FloatArray? {
        return try {
            val shape = longArrayOf(1, 77)
            val inputTensor = ai.onnxruntime.OnnxTensor.createTensor(
                ortEnvironment,
                IntBuffer.wrap(tokens),
                shape
            )
            val result = textSession?.run(mapOf("text_input" to inputTensor))
            val rawOutput = result?.get(0)?.value
            val vector = when (rawOutput) {
                is Array<*> -> {
                    when (val batch = rawOutput[0]) {
                        is Array<*> -> batch[0] as FloatArray
                        is FloatArray -> batch
                        else -> null
                    }
                }
                is FloatArray -> rawOutput
                else -> null
            }
            inputTensor.close()
            result?.close()
            vector
        } catch (e: Exception) {
            null
        }
    }

    fun cosineSimilarity(v1: FloatArray, v2: FloatArray): Float {
        if (v1.size != v2.size) return 0f
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in v1.indices) {
            dot += v1[i] * v2[i]
            normA += v1[i] * v1[i]
            normB += v2[i] * v2[i]
        }
        val denom = Math.sqrt((normA * normB).toDouble()).toFloat()
        return if (denom == 0f) 0f else dot / denom
    }
}