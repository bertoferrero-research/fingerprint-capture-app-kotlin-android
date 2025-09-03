package com.bertoferrero.fingerprintcaptureapp.views.testscreens

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.screen.Screen
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bertoferrero.fingerprintcaptureapp.lib.BleScanner
import com.bertoferrero.fingerprintcaptureapp.views.components.resolvePermission
import com.bertoferrero.fingerprintcaptureapp.views.components.resolvePermissions
import dev.icerock.moko.permissions.Permission
import dev.icerock.moko.permissions.PermissionState
import dev.icerock.moko.permissions.PermissionsController
import dev.icerock.moko.permissions.bluetooth.BLUETOOTH_SCAN
import dev.icerock.moko.permissions.compose.BindEffect
import dev.icerock.moko.permissions.compose.PermissionsControllerFactory
import dev.icerock.moko.permissions.compose.rememberPermissionsControllerFactory
import dev.icerock.moko.permissions.location.LOCATION
import kotlin.collections.mutableListOf
import androidx.compose.foundation.lazy.items

class TestRssiMonitorScreen : Screen {

    private var bleScanner: BleScanner? = null

    data class BeaconInfo(
        val macAddress: String,
        val count: Int,
        val lastRssi: Int,
        val avgRssi: Double,
        val minRssi: Int,
        val maxRssi: Int,
        val isDiscarded: Boolean = false // Para la fase de descarte
    )

    enum class ScanPhase {
        DISCOVERY,    // Fase 1: Descubrir todas las balizas
        ELIMINATION   // Fase 2: Eliminar las que siguen activas
    }

    @SuppressLint("MutableCollectionMutableState")
    @Composable
    override fun Content() {
        val context = LocalContext.current
        var macHistoryPrinter by remember { mutableStateOf(listOf<String>()) }
        var beaconStats by remember { mutableStateOf(listOf<BeaconInfo>()) }
        var runningContent by remember { mutableStateOf(false) }
        var selectedTabIndex by remember { mutableStateOf(0) }
        var currentPhase by remember { mutableStateOf(ScanPhase.DISCOVERY) }
        var discoveredBeacons by remember { mutableStateOf(setOf<String>()) }
        var macPrefixFilter by remember { mutableStateOf("") }

        if(BleScanner.checkPermissions()) {
            Scaffold(
                modifier = Modifier.fillMaxSize()
            ) { innerPadding ->

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {

                    Button(
                        onClick = {
                            if (!runningContent) {
                                // Solo limpiar datos si estamos en fase de descubrimiento
                                if (currentPhase == ScanPhase.DISCOVERY) {
                                    macHistoryPrinter = emptyList()
                                    beaconStats = emptyList()
                                }
                                val scanStarted = initBleScan(context, currentPhase, discoveredBeacons, macPrefixFilter) { newHistory, newStats ->
                                    macHistoryPrinter = newHistory
                                    beaconStats = newStats
                                }
                                runningContent = scanStarted
                            } else {
                                stopBleScan()
                                runningContent = false

                            }
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(if (!runningContent) "Start Scan" else "Stop Scan")
                    }

                    // Campo de filtro por prefijo MAC
                    OutlinedTextField(
                        value = macPrefixFilter,
                        onValueChange = { newValue ->
                            // Convertir a mayúsculas y validar formato básico
                            val filtered = newValue.uppercase().filter { it.isLetterOrDigit() || it == ':' }
                            macPrefixFilter = filtered
                        },
                        label = { Text("Filtro por prefijo MAC (opcional)") },
                        placeholder = { Text("Ej: AA:BB:CC o 58:D5") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        enabled = !runningContent,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters
                        ),
                        singleLine = true
                    )

                    // Botón para cambiar de fase
                    if (!runningContent && beaconStats.isNotEmpty()) {
                        Button(
                            onClick = {
                                when (currentPhase) {
                                    ScanPhase.DISCOVERY -> {
                                        // Cambiar a fase de eliminación
                                        discoveredBeacons = beaconStats.map { it.macAddress }.toSet()
                                        currentPhase = ScanPhase.ELIMINATION
                                        macHistoryPrinter = emptyList()
                                        // Reiniciar beaconStats pero manteniendo las MACs descubiertas
                                        beaconStats = beaconStats.map { it.copy(count = 0, isDiscarded = false) }
                                    }
                                    ScanPhase.ELIMINATION -> {
                                        // Volver a fase de descubrimiento
                                        currentPhase = ScanPhase.DISCOVERY
                                        discoveredBeacons = emptySet()
                                        macHistoryPrinter = emptyList()
                                        beaconStats = emptyList()
                                        macPrefixFilter = "" // Limpiar filtro también
                                    }
                                }
                            },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            when (currentPhase) {
                                ScanPhase.DISCOVERY -> Text("🚫 Iniciar Fase de Descarte")
                                ScanPhase.ELIMINATION -> Text("🔍 Volver a Descubrimiento")
                            }
                        }
                    }

                    // Indicador de fase actual
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = when (currentPhase) {
                                ScanPhase.DISCOVERY -> MaterialTheme.colorScheme.primaryContainer
                                ScanPhase.ELIMINATION -> MaterialTheme.colorScheme.errorContainer
                            }
                        )
                    ) {
                        Text(
                            text = when (currentPhase) {
                                ScanPhase.DISCOVERY -> "🔍 FASE 1: Descubrimiento de balizas"
                                ScanPhase.ELIMINATION -> "🚫 FASE 2: Descarte de balizas activas"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                        
                        // Mostrar filtro activo si está definido
                        if (macPrefixFilter.isNotEmpty()) {
                            Text(
                                text = "🔍 Filtro activo: $macPrefixFilter*",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    TabRow(selectedTabIndex = selectedTabIndex) {
                        Tab(
                            selected = selectedTabIndex == 0,
                            onClick = { selectedTabIndex = 0 },
                            text = { 
                                val activeCount = beaconStats.count { !it.isDiscarded }
                                val totalCount = beaconStats.size
                                Text("Balizas ($activeCount/$totalCount)")
                            }
                        )
                        Tab(
                            selected = selectedTabIndex == 1,
                            onClick = { selectedTabIndex = 1 },
                            text = { Text("Historial (${macHistoryPrinter.size})") }
                        )
                    }

                    when (selectedTabIndex) {
                        0 -> BeaconStatsTab(beaconStats)
                        1 -> HistoryTab(macHistoryPrinter)
                    }
                }
            }
        }
    }

    @Composable
    private fun BeaconStatsTab(beaconStats: List<BeaconInfo>) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (beaconStats.isEmpty()) {
                item {
                    Text(
                        "No hay balizas detectadas",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                items(beaconStats.sortedByDescending { it.lastRssi }) { beacon ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = when {
                                beacon.isDiscarded -> MaterialTheme.colorScheme.errorContainer
                                beacon.count > 0 -> MaterialTheme.colorScheme.surfaceVariant
                                else -> MaterialTheme.colorScheme.surface
                            }
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${if (beacon.isDiscarded) "❌ " else ""}${beacon.macAddress}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (beacon.isDiscarded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${beacon.count} paquetes",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = when {
                                        beacon.isDiscarded -> MaterialTheme.colorScheme.error
                                        beacon.count > 0 -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                            
                            if (beacon.count > 0) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Último: ${beacon.lastRssi} dBm",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = "Promedio: ${String.format("%.1f", beacon.avgRssi)} dBm",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Min: ${beacon.minRssi} dBm",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        text = "Max: ${beacon.maxRssi} dBm",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            } else if (beacon.isDiscarded) {
                                Text(
                                    text = "⚠️ Baliza descartada (aún activa)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            } else {
                                Text(
                                    text = "🔍 Esperando detección...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun HistoryTab(macHistory: List<String>) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            if (macHistory.isEmpty()) {
                item {
                    Text(
                        "No hay historial disponible",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                item {
                    Text(
                        "Historial de detecciones:",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                items(macHistory.reversed()) { mac -> // Más recientes primero
                    Text(
                        text = mac,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    @RequiresApi(Build.VERSION_CODES.O)
    fun initBleScan(
        context: Context, 
        phase: ScanPhase,
        discoveredBeacons: Set<String>,
        macPrefixFilter: String,
        onUpdate: (List<String>, List<BeaconInfo>) -> Unit
    ): Boolean {
        val macHistory = mutableListOf<String>()
        val beaconData = mutableMapOf<String, MutableList<Int>>()
        val discardedBeacons = mutableSetOf<String>()
        
        // En fase de eliminación, inicializar con las balizas descubiertas
        if (phase == ScanPhase.ELIMINATION) {
            discoveredBeacons.forEach { mac ->
                beaconData[mac] = mutableListOf()
            }
        }
        
        bleScanner = BleScanner(
            filterMacs = emptyList(), // Sin filtro de MAC exactas
            filterMacPrefixes = if (macPrefixFilter.isNotEmpty()) listOf(macPrefixFilter) else emptyList(), // Usar filtrado por hardware para prefijos
            onDeviceFound = {
                val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                    .format(java.util.Date())
                
                val macAddress = it.device.address
                
                when (phase) {
                    ScanPhase.DISCOVERY -> {
                        // Fase 1: Registrar todas las balizas normalmente
                        Log.d(
                            "TestRssiMonitorScreen",
                            "DISCOVERY - Device found: $macAddress, RSSI: ${it.rssi}, TxPower: ${it.txPower}"
                        )
                        
                        macHistory.add("[$timestamp] $macAddress - RSSI: ${it.rssi}, TxPower: ${it.txPower}")
                        
                        if (!beaconData.containsKey(macAddress)) {
                            beaconData[macAddress] = mutableListOf()
                        }
                        beaconData[macAddress]!!.add(it.rssi)
                    }
                    
                    ScanPhase.ELIMINATION -> {
                        // Fase 2: Solo procesar balizas que estaban en el descubrimiento
                        if (discoveredBeacons.contains(macAddress)) {
                            Log.d(
                                "TestRssiMonitorScreen",
                                "ELIMINATION - Known device still active: $macAddress, RSSI: ${it.rssi}, TxPower: ${it.txPower} - DISCARDING"
                            )
                            
                            macHistory.add("[$timestamp] ❌ $macAddress - RSSI: ${it.rssi}, TxPower: ${it.txPower} (DESCARTADA)")
                            discardedBeacons.add(macAddress)
                            
                            // Registrar para estadísticas pero marcar como descartada
                            if (!beaconData.containsKey(macAddress)) {
                                beaconData[macAddress] = mutableListOf()
                            }
                            beaconData[macAddress]!!.add(it.rssi)
                        }
                    }
                }
                
                // Calcular estadísticas
                val beaconStats = when (phase) {
                    ScanPhase.DISCOVERY -> {
                        beaconData.map { (mac, rssiList) ->
                            BeaconInfo(
                                macAddress = mac,
                                count = rssiList.size,
                                lastRssi = rssiList.last(),
                                avgRssi = rssiList.average(),
                                minRssi = rssiList.minOrNull() ?: 0,
                                maxRssi = rssiList.maxOrNull() ?: 0,
                                isDiscarded = false
                            )
                        }
                    }
                    ScanPhase.ELIMINATION -> {
                        discoveredBeacons.map { mac ->
                            val rssiList = beaconData[mac] ?: emptyList()
                            val isDiscarded = discardedBeacons.contains(mac)
                            
                            BeaconInfo(
                                macAddress = mac,
                                count = rssiList.size,
                                lastRssi = rssiList.lastOrNull() ?: 0,
                                avgRssi = if (rssiList.isNotEmpty()) rssiList.average() else 0.0,
                                minRssi = rssiList.minOrNull() ?: 0,
                                maxRssi = rssiList.maxOrNull() ?: 0,
                                isDiscarded = isDiscarded
                            )
                        }
                    }
                }
                
                // Notificar cambios
                onUpdate(macHistory.toList(), beaconStats)
            }
        )
        
        //Start the scan
        val running = bleScanner?.startScan(context) ?: false
        if (!running) {
            Toast.makeText(context, "Please, enable the bluetooth", Toast.LENGTH_SHORT).show()
            bleScanner = null // Limpiar si falló
        }
        return running
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun stopBleScan() {
        bleScanner?.stopScan()
        bleScanner = null
    }

}