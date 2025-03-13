package com.bertoferrero.fingerprintcaptureapp.models

import android.content.Context
import android.content.SharedPreferences
import com.bertoferrero.fingerprintcaptureapp.lib.MatSerialization
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Scalar

class CameraCalibrationParameters(var cameraMatrix: Mat, var distCoeffs: Mat) {

    public fun saveParameters(context: Context){
        saveMatrix(context, cameraMatrixParamKey, cameraMatrix)
        saveMatrix(context, distCoeffsParamKey, distCoeffs)
    }

    private fun saveMatrix(context: Context, key: String, matrix: Mat) {
        val sharedPreferences: SharedPreferences = context.getSharedPreferences("CalibrationData", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        /*val rows = matrix.rows()
        val cols = matrix.cols()
        val data = FloatArray(rows * cols)
        matrix.get(0, 0, data)
        editor.putInt("${key}_rows", rows)
        editor.putInt("${key}_cols", cols)
        editor.putString("${key}_data", data.joinToString(","))*/
        editor.putString("${key}_data", MatSerialization.SerializeFromMat(matrix))
        editor.apply()
    }

    companion object{
        const val cameraMatrixParamKey = "cameraMatrix"
        const val distCoeffsParamKey = "distCoeffs"

        fun loadParameters(context: Context, throwExceptionIfEmpty: Boolean = true): CameraCalibrationParameters {
            var cameraMatrix = loadMatrix(context, cameraMatrixParamKey)
            var distCoeffs = loadMatrix(context, distCoeffsParamKey)

            if(cameraMatrix != null && distCoeffs != null){
                return CameraCalibrationParameters(cameraMatrix, distCoeffs)
            }

            // If the parameters are not found, throw an exception or return an empty object
            if(throwExceptionIfEmpty) {
                throw Exception("No calibration parameters found")
            }

            //Initialize the parameters with empty matrices based on the information from
            // https://docs.opencv.org/4.x/dc/dbb/tutorial_py_calibration.html
            cameraMatrix = Mat(3, 3, CvType.CV_32F, Scalar(0.0))
            distCoeffs = Mat(1, 5, CvType.CV_64FC1, Scalar(0.0))

            return CameraCalibrationParameters(cameraMatrix, distCoeffs)
        }

        private fun loadMatrix(context: Context, key: String): Mat? {
            val sharedPreferences: SharedPreferences = context.getSharedPreferences("CalibrationData", Context.MODE_PRIVATE)
            //val rows = sharedPreferences.getInt("${key}_rows", -1)
            //val cols = sharedPreferences.getInt("${key}_cols", -1)
            val dataString = sharedPreferences.getString("${key}_data", null)
            if (/*rows == -1 || cols == -1 || */dataString == null) {
                return null
            }
            return MatSerialization.DeserializeToMat(dataString)
            /*val data = dataString.split(",").map { it.toFloat() }.toFloatArray()
            val matrix = Mat(rows, cols, CvType.CV_32F)
            matrix.put(0, 0, data)
            return matrix*/
        }
    }
}