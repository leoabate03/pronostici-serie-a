package com.example.soccerapp.model

import android.content.Context
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.SelectTensorFlowDelegate
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Wrapper per il modello TensorFlow Lite addestrato in Colab
 * (notebooks/02_train_model.py). Il .tflite deve stare in
 * app/src/main/assets/seriea_model.tflite.
 *
 * L'ordine delle feature e' quello della colonna FEATURE_COLS (8, senza quote):
 *   [is_home, gameweek_norm, home_att_avg, home_def_avg, away_att_avg,
 *    away_def_avg, home_form5, away_form5]
 *
 * Gli output non sono ordinati come passati a keras: i tensori TFLite
 * vanno mappati per SHAPE ([1,3] = 1X2, [1,1] = over/under), non per indice.
 */
class TflitePredictor(context: Context) {

    private var interpreter: Interpreter? = null

    /**
     * true se il .tflite e' stato caricato con successo. Se false l'app usa
     * il fallback sulle frequenze storiche della Serie A.
     */
    val isModelLoaded: Boolean get() = interpreter != null

    /** Messaggio dell'ultimo errore di caricamento (null se ok o mai tentato). */
    var loadError: String? = null
        private set

    // Deve combaciare con FEATURE_COLS nel notebook di addestramento (8, no quotes).
    val featureCount = 8

    init {
        try {
            val options = Interpreter.Options().apply {
                addDelegate(SelectTensorFlowDelegate())
            }
            interpreter = Interpreter(loadModelFile(context), options)
        } catch (e: Exception) {
            // In assenza del .tflite (sviluppo) il modello cade nel fallback
            // probabilistico in ValueBetCalculator.
            loadError = e.javaClass.simpleName + ": " + (e.message ?: "no message")
            interpreter = null
        }
    }

    private fun loadModelFile(context: Context): ByteBuffer {
        // Lettura diretta via InputStream: funziona anche se l'asset .tflite
        // venisse compresso da AAPT (openFd avrebbe fallito).
        val bytes = context.assets.open("seriea_model.tflite").use { it.readBytes() }
        return ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).apply {
            put(bytes)
            rewind()
        }
    }

    /**
     * Predice probabilita' per una partita.
     * @param features vettore lungo 8 (stessa scala del training: is_home=1,
     * gameweek_norm in [0,1], gol/partita ~1.3 come media, forma in punti 0..3)
     */
    fun predict(features: FloatArray): Prediction {
        val it = interpreter
        if (it == null) {
            // Fallback deterministico quando il modello manca (dev).
            return Prediction(0.34, 0.30, 0.36, 0.50)
        }

        val input = arrayOf(features)

        // Trova gli indici degli output guardando il numero di elementi,
        // non l'ordine (TFLite inverte spesso l'ordine rispetto a keras).
        var idx1x2 = -1
        var idxOver = -1
        for (i in 0..1) {
            try {
                when (it.getOutputTensor(i).numElements()) {
                    3 -> idx1x2 = i
                    1 -> idxOver = i
                }
            } catch (_: Exception) {}
        }
        if (idx1x2 < 0 || idxOver < 0) {
            // output inattesi (modello non familiare): tratta come non caricato
            return Prediction(0.34, 0.30, 0.36, 0.50)
        }

        val out1x2 = Array(1) { FloatArray(3) }
        val outOver = Array(1) { FloatArray(1) }
        it.runForMultipleInputsOutputs(
            input,
            mapOf(idx1x2 to out1x2, idxOver to outOver),
        )

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