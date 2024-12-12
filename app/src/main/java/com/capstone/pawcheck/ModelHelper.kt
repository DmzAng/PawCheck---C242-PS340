package com.capstone.pawcheck

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ModelHelper(
    private val context: Context,
    private val listener: ClassifierListener?,
    private val modelName: String = "dog_disease_detection_model.tflite",
    private val inputImageWidth: Int = 224,
    private val inputImageHeight: Int = 224
) {

    private var tfliteInterpreter: org.tensorflow.lite.Interpreter? = null

    init {
        loadModel()
    }

    private fun loadModel() {
        try {
            val assetFileDescriptor = context.assets.openFd(modelName)
            val fileInputStream = assetFileDescriptor.createInputStream()
            val fileChannel = fileInputStream.channel
            val startOffset = assetFileDescriptor.startOffset
            val declaredLength = assetFileDescriptor.declaredLength
            val modelByteBuffer = fileChannel.map(
                java.nio.channels.FileChannel.MapMode.READ_ONLY,
                startOffset,
                declaredLength
            )
            tfliteInterpreter = org.tensorflow.lite.Interpreter(modelByteBuffer)
        } catch (e: Exception) {
            listener?.onError("Failed to load model: ${e.localizedMessage}")
            Log.e(TAG, "Error loading model: ${e.localizedMessage}")
        }
    }

    fun classifyImage(bitmap: Bitmap) {
        if (tfliteInterpreter == null) {
            listener?.onError("Model not loaded.")
            return
        }

        val tensorImage = preprocessImage(bitmap)

        val outputShape = tfliteInterpreter!!.getOutputTensor(0).shape()
        val outputBuffer = TensorBuffer.createFixedSize(outputShape, DataType.FLOAT32)

        val startTime = SystemClock.uptimeMillis()
        tfliteInterpreter!!.run(tensorImage.buffer, outputBuffer.buffer)
        val inferenceTime = SystemClock.uptimeMillis() - startTime

        val results = outputBuffer.floatArray

        val maxIndex = results.indices.maxByOrNull { results[it] } ?: -1
        if (maxIndex >= 0) {
            val predictedScore = results[maxIndex] * 100
            val disease: String
            val diagnosis: String
            val treatment: List<String>

            if (predictedScore >= 75) {
                disease = LABELS[maxIndex]
                diagnosis = DIAGNOSES[maxIndex]
                treatment = getTreatmentOptions(disease)
            } else {
                disease = "Undetected Disease"
                diagnosis = DIAGNOSES[6]
                treatment = listOf("No specific treatment. Please consult a veterinarian for further examination.")
            }

            listener?.onResults(results, inferenceTime, disease, diagnosis, treatment)
        } else {
            listener?.onError("Failed to classify the image.")
        }
    }

    private fun preprocessImage(bitmap: Bitmap): TensorImage {
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, inputImageWidth, inputImageHeight, true)

        val byteBuffer = ByteBuffer.allocateDirect(4 * inputImageWidth * inputImageHeight * 3)
        byteBuffer.order(ByteOrder.nativeOrder())

        val intValues = IntArray(inputImageWidth * inputImageHeight)
        resizedBitmap.getPixels(intValues, 0, inputImageWidth, 0, 0, inputImageWidth, inputImageHeight)

        for (pixel in intValues) {
            byteBuffer.putFloat(((pixel shr 16) and 0xFF) / 255.0f)
            byteBuffer.putFloat(((pixel shr 8) and 0xFF) / 255.0f)
            byteBuffer.putFloat((pixel and 0xFF) / 255.0f)
        }

        val tensorBuffer = TensorBuffer.createFixedSize(
            intArrayOf(1, inputImageWidth, inputImageHeight, 3), DataType.FLOAT32
        )

        tensorBuffer.loadBuffer(byteBuffer)

        val tensorImage = TensorImage(DataType.FLOAT32)
        tensorImage.load(tensorBuffer)

        return tensorImage
    }

    private fun getTreatmentOptions(predictedClass: String): List<String> {
        val treatment = mutableListOf<String>()
        when (predictedClass) {
            "Blepharitis" -> {
                treatment.add("Kompres hangat untuk mengurangi risiko kekambuhan.")
                treatment.add("Pangkas rambut di sekitar mata untuk mengurangi penumpukan cairan.")
                treatment.add("Gunakan sampo bayi untuk menghilangkan kotoran yang dapat menyumbat lubang kelenjar meibom.")
            }
            "Conjunctivitis" -> {
                treatment.add("Gunakan larutan pembersih mata untuk membersihkan area di sekitar mata dari kotoran dan kotoran.")
                treatment.add("Hindari alergen yang diketahui dan gunakan antihistamin jika perlu.")
                treatment.add("Jika konjungtivitis disebabkan oleh kondisi medis yang mendasari, pengobatan untuk kondisi tersebut juga diperlukan.")
            }
            "Entropion" -> {
                treatment.add("Diagnosis yang akurat sangat penting untuk menentukan tingkat keparahan entropion.")
                treatment.add("Sebelum operasi, obat tetes mata dapat diresepkan untuk mengurangi iritasi.")
                treatment.add("Pembedahan biasanya merupakan pengobatan yang paling efektif untuk entropion.")
            }
            "Eyelid Lump" -> {
                treatment.add("Pemeriksaan menyeluruh untuk menentukan apakah benjolan tersebut jinak atau ganas sangat penting.")
                treatment.add("Jika disebabkan oleh infeksi, antibiotik atau obat antiinflamasi dapat diresepkan.")
                treatment.add("Pembedahan mungkin diperlukan untuk mengangkat benjolan, terutama jika benjolan tersebut bersifat kanker atau memengaruhi penglihatan.")
            }
            "Nuclear Sclerosis" -> {
                treatment.add("Pantau kondisi mata anjing secara teratur untuk mengetahui adanya perubahan pada penglihatannya.")
                treatment.add("Jika katarak berkembang, pembedahan mungkin diperlukan untuk mengangkatnya.")
            }
            "Pigmented Keratitis" -> {
                treatment.add("Gunakan air mata buatan untuk menjaga kelembapan kornea.")
                treatment.add("Steroid atau NSAID dapat membantu mengurangi peradangan.")
                treatment.add("Atasi kondisi yang mendasari seperti mata kering untuk mencegah kerusakan lebih lanjut.")
            }
        }
        return treatment
    }

    interface ClassifierListener {
        fun onError(error: String)
        fun onResults(results: FloatArray, inferenceTime: Long, disease: String, diagnosis: String, treatment: List<String>)
    }

    companion object {
        private const val TAG = "ModelHelper"

        val LABELS = listOf(
            "Blepharitis",
            "Conjunctivitis",
            "Entropion",
            "Eyelid Lump",
            "Nuclear Sclerosis",
            "Pigmented Keratitis",
        )

        val DIAGNOSES = listOf(
            "Blepharitis adalah peradangan pada kelopak mata yang dapat mempengaruhi salah satu atau kedua kelopak mata anjing. Gejala yang umum terjadi adalah kemerahan, bengkak, gatal, dan keluarnya cairan dari area mata. Blepharitis dapat menyebabkan ketidaknyamanan dan dapat memengaruhi kualitas hidup anjing jika tidak ditangani.",
            "Konjungtivitis adalah peradangan pada konjungtiva, yaitu jaringan tipis yang melapisi bagian depan bola mata dan bagian dalam kelopak mata. Pada anjing, konjungtivitis dapat menyebabkan kemerahan, bengkak, keluarnya cairan dari mata, dan rasa tidak nyaman.",
            "Entropion adalah suatu kondisi di mana kelopak mata terlipat ke dalam, menyebabkan iritasi pada mata. Kondisi ini dapat menyebabkan peradangan, ketidaknyamanan, dan berpotensi merusak kornea jika tidak ditangani.",
            "Benjolan kelopak mata adalah pertumbuhan abnormal pada area kelopak mata anjing. Benjolan ini bisa jinak atau ganas, dan tergantung dari jenisnya, pengobatannya bisa berbeda-beda.",
            "Nuclear Sclerosis adalah proses penuaan yang normal pada anjing di mana lensa mata menjadi keruh. Kondisi ini tidak mengganggu penglihatan seperti halnya katarak, namun harus tetap dipantau.",
            "Keratitis berpigmen adalah suatu kondisi di mana pigmen terakumulasi pada kornea, yang berpotensi menyebabkan masalah penglihatan. Kondisi ini sering terjadi bersamaan dengan kondisi lain seperti mata kering.",
            "Keakuratan prediksi di bawah 75%, sehingga kami tidak dapat menampilkan penyakit yang terdeteksi. Untuk saat ini, kami hanya dapat mendeteksi 6 penyakit. Jika anjing Anda menunjukkan gejala yang tidak biasa, silakan berkonsultasi dengan dokter hewan secara langsung untuk mendapatkan diagnosis yang tepat."
        )


    }

}
