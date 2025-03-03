package com.bertoferrero.fingerprintcaptureapp.lib

import org.opencv.core.Mat
import com.google.gson.Gson

//Basado en https://stackoverflow.com/questions/53333710/how-to-serialize-deserialize-opencv-mat-in-java

class SerializedMat {
    var bytes: ByteArray = ByteArray(0)
    var shorts: ShortArray = ShortArray(0)
    var ints: IntArray = IntArray(0)
    var floats: FloatArray = FloatArray(0)
    var doubles: DoubleArray = DoubleArray(0)

    var type: Int = 0
    var rows: Int = 0
    var cols: Int = 0
}

class MatSerialization {
    companion object {
        fun SerializeFromMat(mat: Mat): String {
            val serializedMat = SerializedMat()
            serializedMat.type = mat.type()
            serializedMat.rows = mat.rows()
            serializedMat.cols = mat.cols()

            if (serializedMat.type == 0 || serializedMat.type == 8 || serializedMat.type == 16 || serializedMat.type == 24) {
                serializedMat.bytes = ByteArray((mat.total() * mat.elemSize()).toInt())
                mat[0, 0, serializedMat.bytes]
            } else if (serializedMat.type == 1 || serializedMat.type == 9 || serializedMat.type == 17 || serializedMat.type == 25) {
                serializedMat.bytes = ByteArray((mat.total() * mat.elemSize()).toInt())
                mat[0, 0, serializedMat.bytes]
            } else if (serializedMat.type == 2 || serializedMat.type == 10 || serializedMat.type == 18 || serializedMat.type == 26) {
                serializedMat.shorts = ShortArray((mat.total() * mat.elemSize()).toInt())
                mat[0, 0, serializedMat.shorts]
            } else if (serializedMat.type == 3 || serializedMat.type == 11 || serializedMat.type == 19 || serializedMat.type == 27) {
                serializedMat.shorts = ShortArray((mat.total() * mat.elemSize()).toInt())
                mat[0, 0, serializedMat.shorts]
            } else if (serializedMat.type == 4 || serializedMat.type == 12 || serializedMat.type == 20 || serializedMat.type == 28) {
                serializedMat.ints = IntArray((mat.total() * mat.elemSize()).toInt())
                mat[0, 0, serializedMat.ints]
            } else if (serializedMat.type == 5 || serializedMat.type == 13 || serializedMat.type == 21 || serializedMat.type == 29) {
                serializedMat.floats = FloatArray((mat.total() * mat.elemSize()).toInt())
                mat[0, 0, serializedMat.floats]
            } else if (serializedMat.type == 6 || serializedMat.type == 14 || serializedMat.type == 22 || serializedMat.type == 30) {
                serializedMat.doubles = DoubleArray((mat.total() * mat.elemSize()).toInt())
                mat[0, 0, serializedMat.doubles]
            }

            val gson: Gson = Gson()
            return gson.toJson(serializedMat)
        }

        fun DeserializeToMat(json: String?): Mat {
            val gson: Gson = Gson()
            val serializedMat: SerializedMat = gson.fromJson(json, SerializedMat::class.java)
            val mat = Mat(serializedMat.rows, serializedMat.cols, serializedMat.type)

            if (serializedMat.type == 0 || serializedMat.type == 8 || serializedMat.type == 16 || serializedMat.type == 24) {
                mat.put(0, 0, serializedMat.bytes)
            } else if (serializedMat.type == 1 || serializedMat.type == 9 || serializedMat.type == 17 || serializedMat.type == 25) {
                mat.put(0, 0, serializedMat.bytes)
            } else if (serializedMat.type == 2 || serializedMat.type == 10 || serializedMat.type == 18 || serializedMat.type == 26) {
                mat.put(0, 0, serializedMat.shorts)
            } else if (serializedMat.type == 3 || serializedMat.type == 11 || serializedMat.type == 19 || serializedMat.type == 27) {
                mat.put(0, 0, serializedMat.shorts)
            } else if (serializedMat.type == 4 || serializedMat.type == 12 || serializedMat.type == 20 || serializedMat.type == 28) {
                mat.put(0, 0, serializedMat.ints)
            } else if (serializedMat.type == 5 || serializedMat.type == 13 || serializedMat.type == 21 || serializedMat.type == 29) {
                mat.put(0, 0, serializedMat.floats)
            } else if (serializedMat.type == 6 || serializedMat.type == 14 || serializedMat.type == 22 || serializedMat.type == 30) {
                mat.put(0, 0, *serializedMat.doubles)
            }

            return mat
        }

    }
}