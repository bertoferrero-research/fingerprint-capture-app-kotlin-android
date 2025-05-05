package com.bertoferrero.fingerprintcaptureapp.models

import android.content.SharedPreferences
import com.bertoferrero.fingerprintcaptureapp.lib.MatSerialization
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Scalar
import androidx.core.content.edit

/**
 * Class that manages the camera calibration parameters.
 * It loads and saves the camera matrix and distortion coefficients.
 * The parameters are saved in the shared preferences.
 */
class CameraCalibrationParameters(var cameraMatrix: Mat, var distCoeffs: Mat) {

    public fun saveParameters() {
        saveMatrix(cameraMatrixParamKey, cameraMatrix)
        saveMatrix(distCoeffsParamKey, distCoeffs)
    }

    private fun saveMatrix(key: String, matrix: Mat) {
        val sharedPreferences: SharedPreferences = SharedPreferencesManager.getCameraCalibrationSharedPreferences()
        sharedPreferences.edit() {
            putString("${key}_data", MatSerialization.SerializeFromMat(matrix))
        }
    }

    companion object{
        const val cameraMatrixParamKey = "cameraMatrix"
        const val distCoeffsParamKey = "distCoeffs"

        /**
         * Loads the camera calibration parameters from the shared preferences.
         * If the parameters are not found, it throws an exception or returns an empty object.
         * @param throwExceptionIfEmpty If true, it throws an exception if the parameters are not found.
         * @return The camera calibration parameters.
         */
        fun loadParameters(throwExceptionIfEmpty: Boolean = true): CameraCalibrationParameters {
            var cameraMatrix = loadMatrix(cameraMatrixParamKey)
            var distCoeffs = loadMatrix(distCoeffsParamKey)

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

        private fun loadMatrix(key: String): Mat? {
            val sharedPreferences: SharedPreferences = SharedPreferencesManager.getCameraCalibrationSharedPreferences()

            val dataString = sharedPreferences.getString("${key}_data", null)
            if (dataString == null) {
                return null
            }
            return MatSerialization.DeserializeToMat(dataString)
        }

        public fun export():  Map<String, String?>{
            val sharedPreferences: SharedPreferences = SharedPreferencesManager.getCameraCalibrationSharedPreferences()
            return mapOf(
                cameraMatrixParamKey to sharedPreferences.getString("${cameraMatrixParamKey}_data", null),
                distCoeffsParamKey to sharedPreferences.getString("${distCoeffsParamKey}_data", null)
            )
        }
    }
}