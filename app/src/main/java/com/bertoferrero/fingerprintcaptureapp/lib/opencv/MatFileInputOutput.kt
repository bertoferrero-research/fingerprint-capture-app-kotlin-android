package com.bertoferrero.fingerprintcaptureapp.lib.opencv

import android.os.Build
import androidx.annotation.RequiresApi
import org.opencv.core.Mat
import java.io.InputStream
import java.io.OutputStream

fun MatToFile(mat: Mat, out: OutputStream){
    // Escribir filas, columnas y tipo como enteros (4 bytes cada uno)
    out.write(intToBytes(mat.rows()))
    out.write(intToBytes(mat.cols()))
    out.write(intToBytes(mat.type()))

    // Escribir los datos del Mat
    val data = ByteArray(mat.total().toInt() * mat.elemSize().toInt())
    mat.get(0, 0, data)
    out.write(data)
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
fun MatFromFile(ins: InputStream): Mat{
    // Leer filas, columnas y tipo como enteros (4 bytes cada uno)
    val rows = bytesToInt(ins.readNBytes(4))
    val cols = bytesToInt(ins.readNBytes(4))
    val type = bytesToInt(ins.readNBytes(4))


    // Crear el Mat y leer los datos
    val mat = Mat(rows, cols, type)
    val data = ins.readBytes()
    mat.put(0, 0, data)
    return mat
}

fun intToBytes(value: Int): ByteArray {
    return byteArrayOf(
        (value shr 24).toByte(),
        (value shr 16).toByte(),
        (value shr 8).toByte(),
        value.toByte()
    )
}

fun bytesToInt(bytes: ByteArray): Int {
    return (bytes[0].toInt() and 0xFF shl 24) or
            (bytes[1].toInt() and 0xFF shl 16) or
            (bytes[2].toInt() and 0xFF shl 8) or
            (bytes[3].toInt() and 0xFF)
}