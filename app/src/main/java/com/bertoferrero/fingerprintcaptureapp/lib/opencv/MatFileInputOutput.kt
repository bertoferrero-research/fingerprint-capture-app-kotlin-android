package com.bertoferrero.fingerprintcaptureapp.lib.opencv

import android.os.Build
import androidx.annotation.RequiresApi
import org.opencv.core.Mat
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

fun MatToFile(mat: Mat, out: OutputStream){
    // Ahora siempre comprime usando GZIP
    val gzipOut = GZIPOutputStream(out)
    try {
        // Escribir un marcador para identificar archivos comprimidos
        gzipOut.write("GZIP".toByteArray())
        
        // Escribir filas, columnas y tipo como enteros (4 bytes cada uno)
        gzipOut.write(intToBytes(mat.rows()))
        gzipOut.write(intToBytes(mat.cols()))
        gzipOut.write(intToBytes(mat.type()))

        // Escribir los datos del Mat
        val data = ByteArray(mat.total().toInt() * mat.elemSize().toInt())
        mat.get(0, 0, data)
        gzipOut.write(data)
    } finally {
        gzipOut.close()
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
fun MatFromFile(ins: InputStream): Mat{
    // Usar BufferedInputStream para soportar mark/reset
    val bufferedInputStream = if (ins is BufferedInputStream) ins else BufferedInputStream(ins)
    
    // Marcar la posición inicial y leer los primeros 2 bytes para detectar formato
    bufferedInputStream.mark(10) // Marcar con suficientes bytes de buffer
    val header = bufferedInputStream.readNBytes(2)
    
    return if (header.size == 2 && header[0] == 0x1f.toByte() && header[1] == 0x8b.toByte()) {
        // Archivo comprimido GZIP - volver al inicio
        bufferedInputStream.reset()
        val gzipIn = GZIPInputStream(bufferedInputStream)
        try {
            // Leer y verificar el marcador GZIP
            val marker = gzipIn.readNBytes(4)
            if (String(marker) != "GZIP") {
                throw IllegalArgumentException("Archivo no es un Mat comprimido válido")
            }
            
            // Leer filas, columnas y tipo como enteros (4 bytes cada uno)
            val rows = bytesToInt(gzipIn.readNBytes(4))
            val cols = bytesToInt(gzipIn.readNBytes(4))
            val type = bytesToInt(gzipIn.readNBytes(4))

            // Crear el Mat y leer los datos
            val mat = Mat(rows, cols, type)
            val data = gzipIn.readBytes()
            mat.put(0, 0, data)
            mat
        } finally {
            gzipIn.close()
        }
    } else {
        // Archivo sin comprimir - volver al inicio y leer normalmente
        bufferedInputStream.reset()
        
        // Leer filas, columnas y tipo como enteros (4 bytes cada uno)
        val rows = bytesToInt(bufferedInputStream.readNBytes(4))
        val cols = bytesToInt(bufferedInputStream.readNBytes(4))
        val type = bytesToInt(bufferedInputStream.readNBytes(4))

        // Crear el Mat y leer los datos
        val mat = Mat(rows, cols, type)
        val data = bufferedInputStream.readBytes()
        mat.put(0, 0, data)
        mat
    }
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