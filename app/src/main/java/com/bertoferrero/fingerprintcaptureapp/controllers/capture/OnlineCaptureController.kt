package com.bertoferrero.fingerprintcaptureapp.controllers.capture

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.bertoferrero.fingerprintcaptureapp.controllers.cameracontroller.ICameraController
import com.bertoferrero.fingerprintcaptureapp.lib.opencv.MatToFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.opencv.core.MatOfByte
import org.opencv.imgcodecs.Imgcodecs

import org.opencv.android.CameraBridgeViewBase
import org.opencv.core.Mat


/**
 * Controlador para captura online de imágenes OpenCV.
 * Gestiona la captura de imágenes Mat con frecuencia configurable,
 * guardado asíncrono comprimido y límites de captura.
 * Coordina con OnlineCaptureViewModel para sincronización.
 */
class OnlineCaptureController(
    private val context: Context,
    private val onImageCaptured: (Int) -> Unit // Callback para notificar imagen capturada
) : ICameraController {

    // CONFIGURACIÓN

    /** Frecuencia de muestreo para imágenes (milisegundos) */
    var samplingFrequency: Int = 1000

    /** Límite de imágenes a capturar (0 = ilimitado) */
    var imageLimit: Int = 0

    /** URI de la carpeta de salida */
    var outputFolderUri: Uri? = null

    // ESTADO INTERNO

    /** Indica si el controlador está procesando frames */
    private var running = false

    /** Timestamp de la última captura de imagen */
    private var lastCaptureTimestamp: Long = 0

    /** Contador de imágenes capturadas */
    private var capturedImagesCounter = 0

    /** Scope para corrutinas de guardado asíncrono */
    private val saveScope = CoroutineScope(Dispatchers.IO)



    // INTERFAZ ICameraController

    override fun initProcess() {
        if (!running) {
            running = true
            capturedImagesCounter = 0
            lastCaptureTimestamp = 0
            
            Log.i("OnlineCaptureController", "Image capture process initialized")
        }
    }

    override fun finishProcess() {
        if (running) {
            running = false
            
            Log.i("OnlineCaptureController", "Image capture process finished. Total images: $capturedImagesCounter")
        }
    }

    override fun processFrame(inputFrame: CameraBridgeViewBase.CvCameraViewFrame?): Mat {
        if (!running || inputFrame == null) {
            return inputFrame?.rgba() ?: Mat()
        }

        val currentTime = System.currentTimeMillis()
        
        // Verificar si es momento de capturar según la frecuencia
        if (shouldCaptureFrame(currentTime)) {
            captureFrame(inputFrame, currentTime)
        }

        // Devolver el frame para visualización
        return inputFrame.rgba()
    }

    // LÓGICA DE CAPTURA

    /**
     * Determina si debe capturar el frame actual según la frecuencia configurada.
     */
    private fun shouldCaptureFrame(currentTime: Long): Boolean {
        // Primera captura o tiempo suficiente transcurrido
        val timeElapsed = currentTime - lastCaptureTimestamp
        val shouldCapture = lastCaptureTimestamp == 0L || timeElapsed >= samplingFrequency
        
        // Verificar límite de imágenes
        val withinLimit = imageLimit <= 0 || capturedImagesCounter < imageLimit
        
        return shouldCapture && withinLimit
    }

    /**
     * Captura un frame y lo guarda de forma asíncrona (.NET async style).
     */
    private fun captureFrame(inputFrame: CameraBridgeViewBase.CvCameraViewFrame, timestamp: Long) {
        try {
            // Obtener el frame RGBA
            val frame = inputFrame.rgba()
            if (frame.empty()) {
                Log.w("OnlineCaptureController", "Empty frame received, skipping capture")
                return
            }

            // Crear copia del Mat para guardado asíncrono
            val frameCopy = Mat()
            frame.copyTo(frameCopy)

            // Incrementar contador y actualizar timestamp
            capturedImagesCounter++
            lastCaptureTimestamp = timestamp

            // Notificar a ViewModel inmediatamente
            onImageCaptured(capturedImagesCounter)

            Log.d("OnlineCaptureController", "Image captured: $capturedImagesCounter")

            // Guardar de forma asíncrona (estilo .NET async)
            saveScope.launch {
                try {
                    saveImageAsync(frameCopy, timestamp, capturedImagesCounter)
                } catch (e: Exception) {
                    Log.e("OnlineCaptureController", "Error saving image $capturedImagesCounter", e)
                } finally {
                    // Liberar memoria del Mat
                    frameCopy.release()
                }
            }

            // Verificar si se alcanzó el límite
            if (imageLimit > 0 && capturedImagesCounter >= imageLimit) {
                Log.i("OnlineCaptureController", "Image limit reached: $imageLimit")
                finishProcess()
            }

        } catch (e: Exception) {
            Log.e("OnlineCaptureController", "Error capturing frame", e)
        }
    }

    // GUARDADO ASÍNCRONO (.NET ASYNC STYLE)

    /**
     * Guarda una imagen de forma asíncrona (estilo .NET async/await).
     * Cada imagen se guarda independientemente en paralelo.
     */
    private suspend fun saveImageAsync(mat: Mat, timestamp: Long, imageNumber: Int) {
        try {
            val folder = DocumentFile.fromTreeUri(context, outputFolderUri!!)
                ?: throw IllegalStateException("Cannot access output folder")

            val fileName = "image_${String.format("%04d", imageNumber)}_${timestamp}"
            val newFile = folder.createFile("application/octet-stream", fileName+".matphoto")
                ?: throw IllegalStateException("Cannot create image file: $fileName")
            
            context.contentResolver.openOutputStream(newFile.uri)?.use { outputStream ->
                MatToFile(mat, outputStream)
            } ?: throw IllegalStateException("Cannot open output stream for: $fileName")

             // Guardar preview JPG (no crítico)
            try {
                val jpgMat = MatOfByte()
                Imgcodecs.imencode(".jpg", mat, jpgMat)
                val jpgFile = folder.createFile("image/jpeg", fileName+"_preview.jpg")
                jpgFile?.uri?.let { fileUri ->
                    context.contentResolver.openOutputStream(fileUri)?.use { out ->
                        out.write(jpgMat.toArray())
                    }
                }
                Log.d("OnlineCaptureController", "Preview JPG saved: ${fileName}_preview.jpg")
            } catch (e: Exception) {
                // Log pero no fallar - el preview no es crítico
                Log.w("OnlineCaptureController", "Failed to save JPG preview for $fileName", e)
            }
            
        } catch (e: Exception) {
            Log.e("OnlineCaptureController", "Failed to save image $imageNumber", e)
            throw e
        }

        
    }

    // GETTERS PÚBLICOS

    /**
     * Obtiene el número de imágenes capturadas.
     */
    fun getCapturedImagesCount(): Int = capturedImagesCounter

    /**
     * Verifica si el controlador está en ejecución.
     */
    fun isRunning(): Boolean = running

    /**
     * Verifica si se alcanzó el límite de imágenes.
     */
    fun isImageLimitReached(): Boolean = imageLimit > 0 && capturedImagesCounter >= imageLimit
}