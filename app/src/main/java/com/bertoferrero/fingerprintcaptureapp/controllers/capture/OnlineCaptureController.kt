package com.bertoferrero.fingerprintcaptureapp.controllers.capture

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.bertoferrero.fingerprintcaptureapp.controllers.cameracontroller.ICameraController
import com.bertoferrero.fingerprintcaptureapp.lib.opencv.MatToFile
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Semaphore
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

    // SISTEMA DE COLA CON SEMÁFORO

    /** Scope para corrutinas de guardado */
    private val saveScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** Canal para cola de imágenes a guardar */
    private val saveQueue = Channel<ImageSaveTask>(capacity = Channel.UNLIMITED)

    /** Semáforo para limitar trabajos concurrentes de guardado */
    private val saveSemaphore = Semaphore(2) // Máximo 2 guardados concurrentes

    /** Job del procesador de cola */
    private var queueProcessorJob: Job? = null

    /**
     * Clase que representa una tarea de guardado de imagen.
     */
    private data class ImageSaveTask(
        val mat: Mat,
        val matGray: Mat,
        val timestamp: Long,
        val imageNumber: Int
    )



    // INTERFAZ ICameraController

    override fun initProcess() {
        if (!running) {
            running = true
            capturedImagesCounter = 0
            lastCaptureTimestamp = 0
            
            // Iniciar el procesador de cola de guardado
            startSaveQueueProcessor()
            
            Log.i("OnlineCaptureController", "Image capture process initialized with queue system")
        }
    }

    override fun finishProcess() {
        if (running) {
            running = false
            
            // Detener el procesador de cola y cerrar canal
            stopSaveQueueProcessor()
            
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
            val frameGrayCopy = Mat()
            inputFrame.gray().copyTo(frameGrayCopy)

            // Incrementar contador y actualizar timestamp
            capturedImagesCounter++
            lastCaptureTimestamp = timestamp

            // Notificar a ViewModel inmediatamente
            onImageCaptured(capturedImagesCounter)

            Log.d("OnlineCaptureController", "Image captured: $capturedImagesCounter")

            // Enviar a cola de guardado (no bloquea el hilo de captura)
            val saveTask = ImageSaveTask(frameCopy, frameGrayCopy, timestamp, capturedImagesCounter)
            val offered = saveQueue.trySend(saveTask)
            if (!offered.isSuccess) {
                Log.w("OnlineCaptureController", "Failed to queue image $capturedImagesCounter for saving")
                // Liberar memoria si no se pudo encolar
                frameCopy.release()
                frameGrayCopy.release()
            }

            // Nota: NO detener automáticamente por límite de imágenes.
            // El proceso se controla únicamente desde ViewModel/UI por:
            // 1. Límite de tiempo del RssiCaptureService
            // 2. Detención manual por el usuario

        } catch (e: Exception) {
            Log.e("OnlineCaptureController", "Error capturing frame", e)
        }
    }

    // SISTEMA DE COLA CON SEMÁFORO

    /**
     * Inicia el procesador de cola que consume tareas de guardado.
     */
    private fun startSaveQueueProcessor() {
        queueProcessorJob = saveScope.launch {
            try {
                while (isActive) {
                    try {
                        // Esperar por la siguiente tarea de guardado
                        val task = saveQueue.receive()
                        
                        // Lanzar guardado con semáforo para limitar concurrencia
                        launch {
                            saveSemaphore.acquire()
                            try {
                                saveImageAsync(task.mat, task.matGray, task.timestamp, task.imageNumber)
                            } finally {
                                // Liberar recursos
                                task.mat.release()
                                task.matGray.release()
                                saveSemaphore.release()
                            }
                        }
                    } catch (e: Exception) {
                        if (e !is CancellationException) {
                            Log.e("OnlineCaptureController", "Error in save queue processor", e)
                        }
                    }
                }
            } catch (e: CancellationException) {
                Log.i("OnlineCaptureController", "Save queue processor cancelled")
            }
        }
    }

    /**
     * Detiene el procesador de cola y limpia recursos.
     */
    private fun stopSaveQueueProcessor() {
        queueProcessorJob?.cancel()
        queueProcessorJob = null
        saveQueue.close()
        
        // Limpiar tareas pendientes
        saveScope.launch {
            @OptIn(ExperimentalCoroutinesApi::class)
            while (!saveQueue.isEmpty) {
                try {
                    val task = saveQueue.tryReceive().getOrNull()
                    task?.let {
                        // Guardar imagen antes de liberar
                        saveSemaphore.acquire()
                        try {
                            saveImageAsync(it.mat, it.matGray, it.timestamp, it.imageNumber)
                        } finally {
                            it.mat.release()
                            it.matGray.release()
                            saveSemaphore.release()
                        }
                    }
                } catch (e: Exception) {
                    break
                }
            }
        }
    }

    /**
     * Guarda una imagen de forma asíncrona.
     * Llamado desde el procesador de cola con semáforo controlando concurrencia.
     */
    private suspend fun saveImageAsync(mat: Mat, matGray: Mat, timestamp: Long, imageNumber: Int) {
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
                Imgcodecs.imencode(".jpg", matGray, jpgMat)
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