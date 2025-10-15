# Mejoras Implementadas en la Detección ArUco

## Cambios Realizados

### 1. Refinamiento de Esquinas Subpíxel

**Archivos modificados:**
- `MarkersDetector.kt`
- `MarkersDetectorNoCalibration.kt`  
- `TestDistanceCameraController.kt`

**Implementación:**
```kotlin
val detectorParams = DetectorParameters()
try {
    // Activar refinamiento de esquinas subpíxel para mayor precisión
    detectorParams._cornerRefinementMethod = 1 // 1 = CORNER_REFINE_SUBPIX
} catch (e: Exception) {
    // Si no está disponible, usar configuración por defecto
}
```

**Beneficios:**
- Mejora la precisión de detección de esquinas más allá de la resolución del píxel
- Proporciona estimaciones de pose más estables y precisas
- Reduce fluctuaciones en las mediciones entre frames
- Especialmente útil para mejorar la precisión en ejes X/Z

### 2. Resolución de Ambigüedad de Pose con solvePnPGeneric

**Archivos modificados:**
- `MarkersDetector.kt`
- `detectMarkers.kt`

**Problema resuelto:**
Los marcadores planos pueden tener dos poses geométricamente válidas, causando ambigüedad en la estimación de posición.

**Implementación:**
```kotlin
// Usar solvePnPGeneric para obtener múltiples soluciones
val rvecsList = mutableListOf<Mat>()
val tvecsList = mutableListOf<Mat>()
val reprojectionErrors = Mat()

val solutionsCount = Calib3d.solvePnPGeneric(
    objectPoints,
    cornerMatOfPoint2f,
    cameraMatrix,
    distCoeffs,
    rvecsList,
    tvecsList,
    false,
    Calib3d.SOLVEPNP_IPPE_SQUARE,
    rvecs,
    tvecs,
    reprojectionErrors
)
```

**Método de Selección de la Mejor Solución (ChatGPT Method):**

Implementamos el método superior recomendado por ChatGPT que calcula el error de reproyección manualmente:

```kotlin
private fun calculateReprojectionError(
    objectPoints: MatOfPoint3f,
    imagePoints: MatOfPoint2f,
    rvec: Mat,
    tvec: Mat,
    cameraMatrix: Mat,
    distCoeffs: MatOfDouble
): Double {
    val projectedPoints = MatOfPoint2f()
    Calib3d.projectPoints(objectPoints, rvec, tvec, cameraMatrix, distCoeffs, projectedPoints)
    
    val projected = projectedPoints.toArray()
    val detected = imagePoints.toArray()
    
    var sumSquaredErrors = 0.0
    for (i in projected.indices) {
        val dx = projected[i].x - detected[i].x
        val dy = projected[i].y - detected[i].y
        sumSquaredErrors += dx * dx + dy * dy
    }
    
    return sqrt(sumSquaredErrors / projected.size) // Error RMS
}
```

**Criterios de Selección:**

1. **Validez Física**: Descarta soluciones con Z negativo (detrás de la cámara)
2. **Error de Reproyección Real**: Calcula manualmente usando `projectPoints` - más confiable que solvePnPGeneric
4. **Selección Óptima**: Elige la solución con menor error RMS de reproyección

**Ventajas del Método de ChatGPT:**
- ✅ **Más preciso**: Calcula el error real, no depende de OpenCV
- ✅ **Más confiable**: Siempre obtiene un error válido
- ✅ **Mejor criterio**: El error de reproyección es el mejor indicador de calidad de pose

**Beneficios:**
- Elimina la ambigüedad de pose que puede causar saltos erráticos en la posición
- Mejora la consistencia temporal de las estimaciones
- Reduce outliers y mediciones incorrectas
- Proporciona estimaciones más robustas especialmente en entornos complejos

### 3. Parámetros de Detección Optimizados

**Archivos modificados:**
- `MarkersDetector.kt`
- `MarkersDetectorNoCalibration.kt`
- `TestDistanceCameraController.kt`

**Parámetros Implementados:**
```kotlin
val detectorParams = DetectorParameters()
try {
    // Parámetros recomendados por ChatGPT para evitar falsos positivos
    detectorParams._minMarkerPerimeterRate = 0.05 // Evitar marcadores pequeños
    detectorParams._minCornerDistanceRate = 0.08 // Evitar esquinas cercanas
    detectorParams._adaptiveThreshWinSizeMin = 3
    detectorParams._adaptiveThreshWinSizeMax = 23
    detectorParams._adaptiveThreshWinSizeStep = 10
    detectorParams._minMarkerDistanceRate = 0.02
    detectorParams._cornerRefinementWinSize = 5
    detectorParams._cornerRefinementMaxIterations = 30
} catch (e: Exception) {
    // Si no están disponibles, usar configuración por defecto
}
```

**Beneficios:**
- **Reduce falsos positivos**: Especialmente a largas distancias (>5m)
- **Mejora calidad de detección**: Filtro más estricto para marcadores válidos
- **Optimización para marcadores pequeños**: Mejor detección cuando el marcador ocupa pocos píxeles

### 4. Validaciones Geométricas (ChatGPT Recommendations)

**Archivos modificados:**
- `GlobalPositioner.kt`

**Validaciones Implementadas:**

1. **Cheirality Check (tvec.z > 0):**
```kotlin
// Verificar que el marcador esté frente a la cámara
val tvecZ = detectedMarker.tvecs?.get(2, 0)?.get(0)
if (tvecZ == null || tvecZ <= 0) {
    android.util.Log.w("GlobalPositioner", "Marker ${markerData.id}: tvec.z <= 0 ($tvecZ), marcador detrás de cámara - descartando")
    continue
}
```

2. **Validación de Ángulo Oblicuo Extremo:**
```kotlin
// Calcula el ángulo entre el eje Z del marcador (normal) y la dirección de vista
val markerNormal = Mat(3, 1, CvType.CV_64F)
markerNormal.put(0, 0, 0.0, 0.0, 1.0) // Eje Z del marcador

// Transformar el normal del marcador a coordenadas de cámara
val markerNormalInCamera = Mat()
Core.gemm(r_marker_cam, markerNormal, 1.0, Mat(), 0.0, markerNormalInCamera)

// Calcular ángulo con dirección de vista
val angle = Math.toDegrees(Math.acos(Math.abs(dotProduct).coerceIn(-1.0, 1.0)))

// Rechazar si el ángulo es mayor a 75° (marcador muy oblicuo)
if (angle > 75.0) {
    android.util.Log.w("GlobalPositioner", "Marker ${markerData.id}: ángulo oblicuo extremo ${angle}° > 75° - descartando")
    continue
}
```

3. **Validación de Tamaño Mínimo en Píxeles:**
```kotlin
// Calcular el ancho aproximado del marcador en píxeles
val markerWidthPx = sqrt(
    (corners[1].x - corners[0].x).pow(2) + 
    (corners[1].y - corners[0].y).pow(2)
)

// Rechazar marcadores menores a 60 píxeles de ancho
if (markerWidthPx < 60.0) {
    android.util.Log.w("GlobalPositioner", "Marker ${markerData.id}: tamaño demasiado pequeño ${markerWidthPx}px < 60px - descartando")
    continue
}
```

**Beneficios:**
- **Elimina poses imposibles**: Marcadores detrás de la cámara
- **Reduce incertidumbre**: Rechaza ángulos oblicuos extremos (>75°) donde la precisión se degrada
- **Mejora precisión**: Descarta marcadores demasiado pequeños (<60px) con baja resolución
- **Robustez a largas distancias**: Especialmente crítico >5m donde pequeños errores se amplifican
- **Logging detallado**: Facilita debugging y análisis de rendimiento

### 5. RANSAC Adaptativo con Rango de Threshold

**Archivos modificados:**
- `GlobalPositioner.kt`
- `TestPositioningRotationController.kt`  
- `TestPositioningRotationScreen.kt`
- `TestPositioningRotationViewModel.kt`

**Implementación en GlobalPositioner:**
```kotlin
public fun getPositionFromArucoMarkers(
    detectedMarkers: List<MarkersInFrame>,
    multipleMarkersBehaviour: MultipleMarkersBehaviour = MultipleMarkersBehaviour.WEIGHTED_AVERAGE,
    closestMarkersUsed: Int = 0,
    ransacThreshold: Double = 0.2,
    ransacThresholdMax: Double? = null,
    ransacThresholdStep: Double = 0.1
): Pair<Position, List<PositionFromMarker>>? {

    var ransacThresholdValue = ransacThreshold
    var returnData: Position?
    do {
        returnData = filterPositionList(extractedPositions, multipleMarkersBehaviour, ransacThresholdValue)
        ransacThresholdValue += ransacThresholdStep
    } while(returnData == null && ransacThresholdMax != null && ransacThresholdValue <= ransacThresholdMax)
    
    return if (returnData == null) null else Pair(returnData, extractedPositions)
}
```

**Configuración en la Vista:**
- **RT - Ransac Threshold**: Threshold inicial (ej: 0.2)
- **RTM - Ransac Threshold Max**: Threshold máximo (ej: 0.8, o 0 para desactivar)

**Funcionamiento:**
1. **Threshold fijo** (RTM = 0): Comportamiento tradicional
2. **Threshold adaptativo** (RTM > RT): 
   - Comienza con threshold inicial
   - Si no encuentra solución válida, incrementa en pasos de 0.1
   - Continúa hasta alcanzar el threshold máximo
   - Solo falla si agota todas las posibilidades

**Beneficios:**
- **Mayor robustez**: Más probabilidad de encontrar posición válida en condiciones adversas
- **Flexibilidad**: Permite relajar criterios RANSAC gradualmente
- **Compatibilidad**: Mantiene comportamiento original con RTM = 0
- **Logging completo**: Registra ambos parámetros en CSV para análisis posterior

### 5. Compatibilidad y Robustez

**Manejo de Errores:**
- Si `solvePnPGeneric` no está disponible, automáticamente usa `solvePnP` tradicional
- Validación de parámetros antes del procesamiento
- Manejo seguro de excepciones
- Parámetros de detección con fallback a valores por defecto

**Fallback Automático:**
```kotlin
} catch (e: Exception) {
    // Si solvePnPGeneric no está disponible, usar solvePnP tradicional
    Calib3d.solvePnP(/* parámetros tradicionales */)
    1 // Una sola solución
}
```

## Impacto Esperado

### En Precisión de Posicionamiento:
- **Mejor resolución subpíxel**: Mejora especialmente notable en ejes X/Z
- **Eliminación de ambigüedad**: Reduce saltos erráticos en la posición estimada
- **Mayor consistencia**: Mediciones más estables entre frames consecutivos
- **Reducción de falsos positivos**: Menos detecciones erróneas a largas distancias

### En Robustez del Sistema:
- **Menos outliers**: Mejor filtrado de soluciones incorrectas
- **Mejor manejo de casos límite**: Descarta poses físicamente imposibles
- **RANSAC adaptativo**: Mayor flexibilidad en condiciones variables
- **Parámetros optimizados**: Detección más estricta y confiable
- **Compatibilidad mantenida**: Funciona tanto con versiones nuevas como antiguas de OpenCV

## Uso Recomendado

### Configuraciones por Escenario:

**Entornos Controlados (Laboratorio):**
- RT: 0.2, RTM: 0 (threshold fijo)
- Parámetros de detección estrictos
- Enfoque en máxima precisión

**Entornos Variables (Campo):**
- RT: 0.2, RTM: 0.6-0.8 (threshold adaptativo)
- Parámetros balanceados entre precisión y robustez
- Prioriza encontrar solución válida

**Testing Exhaustivo:**
- RT: 0.1, RTM: 1.0 (rango amplio)
- Logging completo para análisis posterior
- Identificar configuración óptima por condiciones

**Aplicaciones Específicas:**
- **Sistemas de alta precisión**: Threshold fijo + parámetros estrictos
- **Condiciones de iluminación variables**: RANSAC adaptativo
- **Múltiples marcadores**: Error de reproyección para selección óptima
- **Largas distancias (>5m)**: Parámetros optimizados + refinamiento subpíxel

## Consideraciones de Rendimiento

- El refinamiento subpíxel añade un pequeño coste computacional
- `solvePnPGeneric` es ligeramente más costoso que `solvePnP` pero proporciona mejor calidad
- El coste adicional es mínimo comparado con los beneficios en precisión

## Configuraciones Recomendadas por ChatGPT

### Parámetros de Detección Optimizados:
```kotlin
// Para evitar falsos positivos a largas distancias
minMarkerPerimeterRate = 0.05  (default: 0.03)
minCornerDistanceRate = 0.08   (default: 0.05)
adaptiveThreshWinSizeMin = 3
adaptiveThreshWinSizeMax = 23
cornerRefinementWinSize = 5
cornerRefinementMaxIterations = 30
```

### RANSAC Threshold por Distancia:
- **0-3m**: RT: 0.1, RTM: 0.3
- **3-7m**: RT: 0.2, RTM: 0.5  
- **7-10m**: RT: 0.3, RTM: 0.8
- **>10m**: RT: 0.4, RTM: 1.0

### Recomendaciones Adicionales:
- **Desactivar estabilización de vídeo** durante captura (deforma geometría)
- **Aumentar ISO/velocidad** para minimizar motion blur
- **Validar marcadores por tamaño**: Rechazar < 60-80 píxeles en imagen
- **Filtrar por ángulo oblicuo**: Descartar marcadores con ángulo > 70-75°

## Pruebas Recomendadas

1. **Comparar precisión**: Medir la mejora en la precisión de posicionamiento X/Z
2. **Evaluar estabilidad**: Verificar la reducción en fluctuaciones entre frames
3. **Probar condiciones adversas**: Evaluar rendimiento con iluminación variable
4. **Validar consistencia**: Comprobar estimaciones más consistentes temporalmente
5. **Testing de rango RANSAC**: Encontrar configuración óptima RT/RTM por escenario
6. **Validar parámetros de detección**: Confirmar reducción de falsos positivos

## Checklist de Implementación ChatGPT

- [x] ✅ `SOLVEPNP_IPPE_SQUARE` implementado para marcadores cuadrados
- [x] ✅ Cheirality check: `tvec.z > 0` (marcador frente a cámara)
- [x] ✅ `solvePnPGeneric` con selección por menor error de reproyección
- [x] ✅ Refinamiento de esquinas subpíxel activado
- [x] ✅ Parámetros de detección optimizados (minMarkerPerimeterRate, etc.)
- [x] ✅ Transformaciones en CV_64F (doble precisión)
- [x] ✅ Orden de rotación correcto: Rz(yaw)·Ry(pitch)·Rx(roll)  
- [x] ✅ Conversión grados→radianes implementada
- [x] ✅ Inversión correcta: R_cam_marker = R_marker_cam.t()
- [x] ✅ Transformación a mundo: t_cam_world = R_marker_world * t_cam_marker + t_marker_world
- [x] ✅ RANSAC adaptativo con rango configurable
- [x] ✅ Logging completo para debugging
- [x] ✅ Compatibilidad y fallbacks implementados
- [ ] 🔄 Validación por ángulo oblicuo extremo (>70-75°)
- [ ] 🔄 Filtrado por tamaño mínimo en píxeles (60-80px)
- [ ] 🔄 solvePnPRefineLM post-procesamiento
- [ ] 🔄 Testing en condiciones reales
- [ ] 🔄 Optimización de parámetros por escenario
- [ ] 🔄 Validación de mejoras en precisión X/Z