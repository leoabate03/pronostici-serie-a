package com.example.soccerapp.model

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Wrapper per il modello TensorFlow Lite addestrato in Colab
 * (notebooks/02_train_model.py). Il .tflite deve stare in
 * app/src/main/assets/seriea_model.tflite.
 *
 * L'ordine delle feature e' quello della colonna FEATURE_COLS del notebook:
 *   [is_home, gameweek_norm, home_att_avg, home_def_avg, away_att_avg,
 *    away_def_avg, home_form5, away_form5, impl_home, impl_draw, impl_away]
 *
 * Se nel notebook ometti le quote (feature impl_*), aggiorna FEATURE_COUNT.
 */
class TflitePredictor(context: Context) {

    private var interpreter: Interpreter? = null

    /**
     * true se il .tflite e' stato caricato con successo. Se false l'app usa
     * il fallback sulle probabilita' implicite dei bookmaker.
     */
    val isModelLoaded: Boolean get() = interpreter != null

    // Deve combaciare con FEATURE_COLS nel notebook di addestramento.
    val featureCount = 11

    init {
        try {
            interpreter = Interpreter(loadModelFile(context))
        } catch (e: Exception) {
            // In assenza del .tflite (sviluppo) il modello cade nel fallback
            // probabilistico in ValueBetCalculator.
            interpreter = null
        }
    }

    private fun loadModelFile(context: Context): MappedByteBuffer {
        val descriptor = context.assets.openFd("seriea_model.tflite")
        val input = descriptor.createInputStream()
        val buffer = input.channel.map(
            FileChannel.MapMode.READ_ONLY,
            descriptor.startOffset,
            descriptor.declaredLength,
        )
        input.close()
        return buffer
    }

    /**
     * Predice probabilita' per una partita.
     * @param features vettore lungo `featureCount` (0.0..1.0 normalizzati)
     */
    fun predict(features: FloatArray): Prediction {
        val it = interpreter
        if (it == null) {
            // Fallback deterministico quando il modello manca (dev): ritorna
            // valori neutri, la UI mostrera' comunque le quote e i consigli.
            return Prediction(0.34, 0.30, 0.36, 0.50)
        }

        val input = arrayOf(features)
        // Output "outcome_1x2" (3 valori) + "over_under_25" (1 valore)
        val out1x2 = Array(1) { FloatArray(3) }
        val outOver = Array(1) { FloatArray(1) }
        it.run(input, mapOf(0 to out1x2, 1 to outOver))

        val p = out1x2[0]
        return Prediction(
            homeProb = p[0].toDouble(),
            drawProb = p[1].toDouble(),
            awayProb = p[2].toDouble(),
            over25Prob = outOver[0][0].toDouble(),
        )
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }
}