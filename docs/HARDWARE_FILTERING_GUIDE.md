# Guía de Filtrado por Hardware BLE

## Filtrado por Hardware Implementado

El sistema ahora soporta filtrado por hardware usando `ScanFilter` de Android, lo cual es mucho más eficiente que el filtrado por software.

### Tipos de Filtrado Disponibles

#### 1. Filtrado por MAC Exacta (Hardware - Más Eficiente)
```kotlin
val bleScanner = BleScanner(
    filterMacs = listOf("AA:BB:CC:DD:EE:FF", "11:22:33:44:55:66"),
    filterMacPrefixes = emptyList(),
    onDeviceFound = { result -> /* procesar resultado */ }
)
```

#### 2. Filtrado por Prefijo MAC (Software - Fabricantes)
```kotlin
val bleScanner = BleScanner(
    filterMacs = emptyList(),
    filterMacPrefixes = listOf("D0:39:72"), // Estimote beacons
    onDeviceFound = { result -> /* procesar resultado */ }
)
```

#### 3. Sin Filtrado (Todos los Dispositivos)
```kotlin
val bleScanner = BleScanner(
    filterMacs = emptyList(),
    filterMacPrefixes = emptyList(),
    onDeviceFound = { result -> /* procesar resultado */ }
)
```

### Prefijos MAC de Fabricantes Comunes

El scanner incluye constantes para fabricantes conocidos:

```kotlin
// Usar prefijos predefinidos
val bleScanner = BleScanner(
    filterMacs = emptyList(),
    filterMacPrefixes = listOf(BleScanner.MacPrefixes.ESTIMOTE),
    onDeviceFound = { result -> /* procesar resultado */ }
)

// Fabricantes soportados:
BleScanner.MacPrefixes.ESTIMOTE        // "D0:39:72"
BleScanner.MacPrefixes.KONTAKT         // "EC:58:8F"
BleScanner.MacPrefixes.RADIUS_NETWORKS // "D4:CA:6E"
BleScanner.MacPrefixes.BLUE_SENSE      // "C4:AC:59"
BleScanner.MacPrefixes.MINEW           // "AC:23:3F"
BleScanner.MacPrefixes.FEASYCOM        // "84:2E:14"
```

### Configuración de Escaneo Optimizada

El scanner usa configuración optimizada para investigación:

- **SCAN_MODE_LOW_LATENCY**: Escaneo de alta frecuencia
- **CALLBACK_TYPE_ALL_MATCHES**: Reporta todos los anuncios
- **MATCH_MODE_AGGRESSIVE**: Coincidencia agresiva
- **MATCH_NUM_MAX_ADVERTISEMENT**: Máximo número de coincidencias
- **Report Delay = 0**: Sin agrupación, reporte inmediato

### Ventajas del Filtrado por Hardware

1. **Eficiencia**: Reduce el uso de CPU y batería
2. **Menor Latencia**: Filtrado en el chip Bluetooth
3. **Mejor Rendimiento**: Menos interrupciones al sistema
4. **Escalabilidad**: Maneja mejor múltiples filtros

### Limitaciones

- **Número de Filtros**: Hardware limitado a 8-16 filtros simultáneos
- **Solo MAC Exactas**: Hardware no soporta filtrado por prefijo
- **Dependiente del Hardware**: Capacidades varían según el dispositivo

### Información de Depuración

El scanner proporciona información sobre el filtrado activo:

```kotlin
val filterInfo = bleScanner.getFilteringInfo()
Log.i("App", filterInfo)
// Output: "Hardware filtering: 3 exact MAC addresses"
// o "Software filtering: 2 MAC prefixes" 
// o "No filtering - scanning all devices"
```

### Uso en Captura Offline

El servicio de captura RSSI ya está configurado para usar filtrado por hardware:

```kotlin
// En RssiCaptureService se usa automáticamente
bleScanner = BleScanner(
    filterMacs = macFilterList, // Filtrado por hardware
    filterMacPrefixes = emptyList()
) { /* callback */ }
```

### Uso en TestRssiMonitorScreen

La pantalla de pruebas usa filtrado por prefijo para filtrar por fabricante:

```kotlin
// Automáticamente usa hardware/software según el tipo de filtro
bleScanner = BleScanner(
    filterMacs = emptyList(),
    filterMacPrefixes = if (macPrefixFilter.isNotEmpty()) listOf(macPrefixFilter) else emptyList()
) { /* callback */ }
```

### Estructura del CSV
```
timestamp,time,mac_address,rssi,pos_x,pos_y,pos_z
1693737600000,2023-09-03 12:00:00.000,AA:BB:CC:DD:EE:FF,-45,4,1.0,2.0,0.0
```

### **Beneficios para la investigación de fingerprinting:**
1. **ScanSettings optimizado**: Mejor consistencia en la captura de señales BLE
2. **TxPower**: Permite calcular distancias estimadas usando la fórmula RSSI
3. **Filtrado eficiente**: Reducción significativa del uso de CPU y batería
4. **Datos limpios**: Solo información confiable y verificable
5. **Compatibilidad**: Mantiene toda la funcionalidad existente
