package com.bertoferrero.fingerprintcaptureapp.views.tools

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.bertoferrero.fingerprintcaptureapp.lib.opencv.MatFromFile
import com.bertoferrero.fingerprintcaptureapp.lib.opencv.MatToFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MatPhotoCompressionToolScreen : Screen {
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        
        var selectedFolderUri by remember { mutableStateOf<Uri?>(null) }
        var isProcessing by remember { mutableStateOf(false) }
        var processedFiles by remember { mutableStateOf(0) }
        var totalFiles by remember { mutableStateOf(0) }
        var currentFileName by remember { mutableStateOf("") }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        var successMessage by remember { mutableStateOf<String?>(null) }
        var spaceSavedBytes by remember { mutableLongStateOf(0L) }

        // Launcher para seleccionar carpeta
        val folderPickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree()
        ) { uri ->
            selectedFolderUri = uri
            errorMessage = null
            successMessage = null
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("MatPhoto Compression Tool") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "MatPhoto Compression Tool",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = "Esta herramienta convierte archivos MatPhoto sin comprimir a formato comprimido para ahorrar espacio de almacenamiento.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "• Busca archivos .matphoto en la carpeta seleccionada\n• Los convierte a formato comprimido\n• Mantiene compatibilidad con archivos existentes",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                // Seleccionar carpeta
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Seleccionar Carpeta",
                            style = MaterialTheme.typography.titleMedium
                        )
                        
                        Button(
                            onClick = { folderPickerLauncher.launch(null) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isProcessing
                        ) {
                            Text("Seleccionar Carpeta con Archivos MatPhoto")
                        }
                        
                        selectedFolderUri?.let { uri ->
                            Text(
                                text = "Carpeta seleccionada: ${DocumentFile.fromTreeUri(context, uri)?.name ?: "Desconocida"}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                // Botón de procesamiento
                selectedFolderUri?.let { uri ->
                    Button(
                        onClick = {
                            scope.launch {
                                try {
                                    isProcessing = true
                                    errorMessage = null
                                    successMessage = null
                                    processedFiles = 0
                                    totalFiles = 0
                                    spaceSavedBytes = 0L
                                    
                                    val result = processMatPhotoFiles(
                                        context = context,
                                        folderUri = uri,
                                        onProgress = { processed, total, filename, savedBytes ->
                                            processedFiles = processed
                                            totalFiles = total
                                            currentFileName = filename
                                            spaceSavedBytes += savedBytes
                                        }
                                    )
                                    
                                    if (result.success) {
                                        successMessage = "Procesamiento completado: ${result.processedCount} archivos convertidos. Espacio ahorrado: ${formatBytes(spaceSavedBytes)}"
                                    } else {
                                        errorMessage = result.errorMessage
                                    }
                                } catch (e: Exception) {
                                    errorMessage = "Error: ${e.message}"
                                } finally {
                                    isProcessing = false
                                    currentFileName = ""
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isProcessing
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(if (isProcessing) "Procesando..." else "Iniciar Compresión")
                    }
                }

                // Progreso
                if (isProcessing && totalFiles > 0) {
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Progreso: $processedFiles / $totalFiles",
                                style = MaterialTheme.typography.titleMedium
                            )
                            
                            LinearProgressIndicator(
                                progress = if (totalFiles > 0) processedFiles.toFloat() / totalFiles.toFloat() else 0f,
                                modifier = Modifier.fillMaxWidth()
                            )
                            
                            if (currentFileName.isNotEmpty()) {
                                Text(
                                    text = "Procesando: $currentFileName",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            
                            if (spaceSavedBytes > 0) {
                                Text(
                                    text = "Espacio ahorrado: ${formatBytes(spaceSavedBytes)}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }

                // Mensajes de estado
                errorMessage?.let { message ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = message,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                successMessage?.let { message ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = message,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }
}

data class ProcessResult(
    val success: Boolean,
    val processedCount: Int,
    val errorMessage: String? = null
)

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
suspend fun processMatPhotoFiles(
    context: android.content.Context,
    folderUri: Uri,
    onProgress: (processed: Int, total: Int, filename: String, savedBytes: Long) -> Unit
): ProcessResult = withContext(Dispatchers.IO) {
    try {
        val documentFile = DocumentFile.fromTreeUri(context, folderUri)
            ?: return@withContext ProcessResult(false, 0, "No se pudo acceder a la carpeta")
        
        // Buscar archivos .matphoto
        val matPhotoFiles = findMatPhotoFiles(documentFile)
        
        if (matPhotoFiles.isEmpty()) {
            return@withContext ProcessResult(false, 0, "No se encontraron archivos .matphoto en la carpeta")
        }
        
        var processedCount = 0
        val totalCount = matPhotoFiles.size
        
        for (file in matPhotoFiles) {
            try {
                val originalSize = file.length()
                
                // Leer el archivo original
                context.contentResolver.openInputStream(file.uri)?.use { inputStream ->
                    val mat = MatFromFile(inputStream)
                    
                    // Escribir el archivo comprimido (sobrescribir el original)
                    context.contentResolver.openOutputStream(file.uri, "wt")?.use { outputStream ->
                        MatToFile(mat, outputStream)
                    }
                }
                
                val newSize = file.length()
                val savedBytes = originalSize - newSize
                
                processedCount++
                onProgress(processedCount, totalCount, file.name ?: "archivo", savedBytes)
                
            } catch (e: Exception) {
                // Continuar con el siguiente archivo si hay error
                val message = e.message
                continue
            }
        }
        
        ProcessResult(true, processedCount)
        
    } catch (e: Exception) {
        ProcessResult(false, 0, e.message)
    }
}

fun findMatPhotoFiles(documentFile: DocumentFile): List<DocumentFile> {
    val matPhotoFiles = mutableListOf<DocumentFile>()
    
    fun searchRecursively(folder: DocumentFile) {
        folder.listFiles().forEach { file ->
            if (file.isDirectory) {
                searchRecursively(file)
            } else if (file.name?.endsWith(".matphoto", ignoreCase = true) == true) {
                matPhotoFiles.add(file)
            }
        }
    }
    
    searchRecursively(documentFile)
    return matPhotoFiles
}

fun formatBytes(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    
    return when {
        gb >= 1.0 -> "%.2f GB".format(gb)
        mb >= 1.0 -> "%.2f MB".format(mb)
        kb >= 1.0 -> "%.2f KB".format(kb)
        else -> "$bytes bytes"
    }
}
