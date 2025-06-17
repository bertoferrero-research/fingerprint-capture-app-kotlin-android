package com.bertoferrero.fingerprintcaptureapp.controllers.settings

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.bertoferrero.fingerprintcaptureapp.lib.opencv.MatToFile
import org.opencv.android.CameraBridgeViewBase
import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.videoio.VideoWriter
import java.io.File

// CameraSamplerController handles the logic for capturing and saving samples from the camera.
// It supports both photo and video capture using OpenCV. Video is encoded using OpenCV's VideoWriter.
class CameraSamplerController(
)  {
    // VideoWriter instance for encoding video frames
    private var videoWriter: VideoWriter? = null
    // Indicates if video recording is currently active
    private var isRecording: Boolean = false
    // Temporary file where video is written before moving to final destination
    private var videoFile: File? = null
    // Frames per second for video recording
    private var fps: Double = 30.0
    // Frame size for the video (width x height)
    private var frameSize: org.opencv.core.Size? = null

    /**
     * Starts video recording. Initializes the VideoWriter and prepares a temporary file for output.
     * The video will be saved in the directory selected by the user after recording stops.
     * @param context Android context
     * @param outputFolderUri URI of the output directory
     * @param width Frame width
     * @param height Frame height
     * @param fps Frames per second (default 30)
     */
    fun startVideoRecording(context: Context, outputFolderUri: Uri, width: Int, height: Int, fps: Double = 30.0) {
        if (isRecording) return // Prevent double initialization
        this.fps = fps
        this.frameSize = org.opencv.core.Size(width.toDouble(), height.toDouble())

        // Ensure cache directory exists
        val cacheDir = context.cacheDir
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }

        val folder = DocumentFile.fromTreeUri(context, outputFolderUri)
        val timestamp = System.currentTimeMillis()
        val fileName = "video_$timestamp.mp4"
        val newFile = folder?.createFile("video/mp4", fileName)
        val fileUri = newFile?.uri ?: return

        // Create a temporary file in cache directory for writing video
        val tempFile = File(cacheDir, fileName)
        try {
            if (!tempFile.exists()) {
                tempFile.createNewFile()
            }
            // Verify if the file is writable
            if (!tempFile.canWrite()) {
                throw RuntimeException("Temporary file is not writable: ${tempFile.absolutePath}")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            println("Error creating or accessing temporary file: ${e.message}")
            return
        }

        videoFile = tempFile

        // Usar solo el códec H264, ya que es el único compatible en este dispositivo
        videoWriter = VideoWriter(
            tempFile.absolutePath,
            VideoWriter.fourcc('H','2','6','4'), // H264 codec
            fps,
            frameSize,
            true
        )

        if (!videoWriter!!.isOpened) {
            println("VideoWriter failed to open with H264 codec.")
            videoWriter = null
            tempFile.delete()
            throw RuntimeException("Failed to open VideoWriter with H264 codec.")
        }

        isRecording = true
    }

    /**
     * Writes a frame to the video file if recording is active.
     * @param mat Frame to write (should match the initialized frame size)
     */
    fun writeVideoFrame(mat: Mat) {
        if (isRecording && videoWriter != null && mat.width() > 0 && mat.height() > 0) {
            videoWriter?.write(mat)
        }
    }

    /**
     * Stops video recording, releases the VideoWriter, and moves the temporary file to the user-selected directory.
     * @param context Android context
     * @param outputFolderUri URI of the output directory
     */
    fun stopVideoRecording(context: Context, outputFolderUri: Uri) {
        if (!isRecording) return
        isRecording = false
        videoWriter?.release()
        videoWriter = null
        // Move the temporary file to the final destination in the selected folder
        videoFile?.let { tempFile ->
            val folder = DocumentFile.fromTreeUri(context, outputFolderUri)
            val timestamp = System.currentTimeMillis()
            val newFile = folder?.createFile("video/mp4", tempFile.name ?: "video.mp4")
            newFile?.uri?.let { fileUri ->
                context.contentResolver.openOutputStream(fileUri)?.use { out ->
                    tempFile.inputStream().use { input ->
                        input.copyTo(out)
                    }
                }
            }
            tempFile.delete()
        }
        videoFile = null
    }

    /**
     * Processes a camera frame. If in video mode, writes the frame to the video file.
     * If in photo mode, saves the frame as a .mat file and a JPEG preview.
     * @param inputFrame The camera frame
     * @param context Android context
     * @param outputFolderUri Output directory
     * @param isVideo True if in video mode, false for photo
     * @return The processed frame (for display)
     */
    fun processAndStoreSampleFrame(
        inputFrame: CameraBridgeViewBase.CvCameraViewFrame?,
        context: Context,
        outputFolderUri: Uri,
        isVideo: Boolean = false
    ): Mat {
        // Get the color frame (RGBA)
        val frame = inputFrame?.rgba() ?: Mat()
        val gray = inputFrame?.gray()

        if (isVideo) {
            // Write frame to video if recording
            writeVideoFrame(frame)
            return frame
        }

        // --- Photo mode: save .mat and JPEG preview ---
        val folder = DocumentFile.fromTreeUri(context, outputFolderUri!!)
        val timestamp = System.currentTimeMillis()
        val fileName = "photo_$timestamp.matphoto"

        // Save the frame as a .mat file
        val newFile = folder?.createFile("application/octet-stream", fileName)
        newFile?.uri?.let { fileUri ->
            context.contentResolver.openOutputStream(fileUri)?.use { out ->
                MatToFile(frame, out)
            }
        }

        // Save a JPEG preview of the grayscale image
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