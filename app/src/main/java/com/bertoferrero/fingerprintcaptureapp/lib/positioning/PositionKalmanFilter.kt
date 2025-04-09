package com.bertoferrero.fingerprintcaptureapp.lib.positioning

import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.video.KalmanFilter

class PositionKalmanFilter(
    private val CovQ: Double = 1e-4, // process noise covariance
    private val CovR: Double = 1e-2 // measurement noise covariance
) {
    private val kf: KalmanFilter
    private val measurement: Mat

    private var lastUpdateTimestamp: Long? = null

    init {
        kf = KalmanFilter(6, 3, 0, CvType.CV_32F)

        // Inicializamos con dt = 1.0, pero se actualiza dinámicamente en cada frame
        updateTransitionMatrix(1.0f)

        // Matriz de observación: mide posición
        kf._measurementMatrix = Mat.eye(3, 6, CvType.CV_32F) // 3x6

        // Covarianzas
        Core.setIdentity(kf._processNoiseCov, Scalar(CovQ)) // Confianza en el modelo
        Core.setIdentity(kf._measurementNoiseCov, Scalar(CovR)) // Confianza en la medición
        Core.setIdentity(kf._errorCovPost, Scalar(1.0)) // Error inicial

        // Estado inicial
        kf._statePost.setTo(Scalar(0.0))

        measurement = Mat(3, 1, CvType.CV_32F)
    }

    /**
     * Reset the timestamp updating system.
     */
    public fun resetLastUpdateTimestamp() {
        lastUpdateTimestamp = null
    }

    /**
     * Update the transition matrix based on the time delta.
     * @param dt Time delta since last update
     */
    private fun updateTransitionMatrix(dt: Float) {
        val A = Mat.zeros(6, 6, CvType.CV_32F)

        // Posición
        A.put(0, 0, 1.0)
        A.put(0, 3, dt.toDouble())
        A.put(1, 1, 1.0)
        A.put(1, 4, dt.toDouble())
        A.put(2, 2, 1.0)
        A.put(2, 5, dt.toDouble())

        // Velocidad
        A.put(3, 3, 1.0)
        A.put(4, 4, 1.0)
        A.put(5, 5, 1.0)

        kf._transitionMatrix = A
    }

    /**
     * Update the Kalman filter with new measurements.
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     * @param dt Time delta since last update
     * @return Filtered position as a FloatArray
     */
    fun updateWithDelta(x: Float, y: Float, z: Float, dt: Float): FloatArray {
        updateTransitionMatrix(dt)

        // Cargar nueva medición
        measurement.put(0, 0, x.toDouble())
        measurement.put(1, 0, y.toDouble())
        measurement.put(2, 0, z.toDouble())

        // Predicción
        kf.predict()

        // Corrección
        kf.correct(measurement)

        // Devolver posición filtrada
        val state: Mat = kf._statePost
        val result = FloatArray(3)
        result[0] = state.get(0, 0)[0].toFloat() // x
        result[1] = state.get(1, 0)[0].toFloat() // y
        result[2] = state.get(2, 0)[0].toFloat() // z

        return result
    }

    /**
     * Update the Kalman filter using internal timestamp control.
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     * @return Filtered position as a FloatArray
     */
    fun updateWithTimestampControl(x: Float, y: Float, z: Float): FloatArray {
        val now = System.nanoTime()
        var dt = 1.0f
        lastUpdateTimestamp?.let { dt = (now - it) / 1_000_000_000.0f }
        lastUpdateTimestamp = now

        return updateWithDelta(x, y, z, dt)
    }
}