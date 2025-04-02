package com.bertoferrero.fingerprintcaptureapp.lib

import org.opencv.core.CvType
import org.opencv.core.Mat
import kotlin.math.cos
import kotlin.math.sin

fun eulerAnglesToRotationMatrix(roll: Double, pitch: Double, yaw: Double): Mat {
    // Cálculo de cosenos y senos
    val c1 = cos(yaw) // Z
    val s1 = sin(yaw)
    val c2 = cos(pitch) // Y
    val s2 = sin(pitch)
    val c3 = cos(roll) // X
    val s3 = sin(roll)

    // Matriz de rotación: R = Rz(yaw) * Ry(pitch) * Rx(roll)
    val r = Array<DoubleArray?>(3) { DoubleArray(3) }

    r[0]!![0] = c1 * c2
    r[0]!![1] = c1 * s2 * s3 - s1 * c3
    r[0]!![2] = c1 * s2 * c3 + s1 * s3

    r[1]!![0] = s1 * c2
    r[1]!![1] = s1 * s2 * s3 + c1 * c3
    r[1]!![2] = s1 * s2 * c3 - c1 * s3

    r[2]!![0] = -s2
    r[2]!![1] = c2 * s3
    r[2]!![2] = c2 * c3

    // Convertimos a Mat de OpenCV
    val rotationMatrix = Mat(3, 3, CvType.CV_64F)
    for (i in 0..2) {
        rotationMatrix.put(i, 0, r[i]!![0])
        rotationMatrix.put(i, 1, r[i]!![1])
        rotationMatrix.put(i, 2, r[i]!![2])
    }

    return rotationMatrix
}
