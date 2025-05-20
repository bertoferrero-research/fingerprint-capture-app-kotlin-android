package com.bertoferrero.fingerprintcaptureapp.controllers.settings

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.bertoferrero.fingerprintcaptureapp.lib.MatSerialization
import com.bertoferrero.fingerprintcaptureapp.lib.MatToFile
import org.opencv.android.CameraBridgeViewBase
import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.imgcodecs.Imgcodecs

class CameraSamplerController(
)  {

    fun processAndStoreSampleFrame(
        inputFrame: CameraBridgeViewBase.CvCameraViewFrame?,
        context: Context,
        outputFolderUri: Uri
    ): Mat {

        // Get the mat
        val frame = inputFrame?.rgba() ?: Mat()
        val gray = inputFrame?.gray()

        // Store the sample
        //val matToStore = MatSerialization.Companion.SerializeFromMat(rgb)
        val folder = DocumentFile.fromTreeUri(context, outputFolderUri!!)
        val timestamp = System.currentTimeMillis()
        val fileName = "$timestamp.matphoto"

        val newFile = folder?.createFile("application/octet-stream", fileName)
        newFile?.uri?.let { fileUri ->
            context.contentResolver.openOutputStream(fileUri)?.use { out ->
                //out.write(matToStore.toByteArray())
                MatToFile(frame, out)
            }
        }

        //Store a jpg version
        val jpgMat = MatOfByte()
        Imgcodecs.imencode(".jpg", gray, jpgMat)
        val jpgFile = folder?.createFile("image/jpeg", "${timestamp}_preview.jpg")
        jpgFile?.uri?.let { fileUri ->
            context.contentResolver.openOutputStream(fileUri)?.use { out ->
                out.write(jpgMat.toArray())
            }
        }


        return frame
    }


}