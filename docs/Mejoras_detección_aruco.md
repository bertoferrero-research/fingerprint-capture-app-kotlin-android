Que, a partir de ~5 m, las **componentes x/z** “se vuelvan locas” (signos negativos, saltos) es un patrón clásico de pose con **plano casi frontal + marcador pequeño en imagen**. Ahí aparecen tres culpas típicas:

1. **Ambigüedad planar (cheirality/flip)**
2. **Modelo PnP no óptimo para cuadrados**
3. **Pequeños errores de esquinas/calibración** que, a larga distancia, se amplifican en orientación y ejes (aunque el módulo de t sea razonable).

Te dejo un plan de corrección, en orden de impacto, con snippets:

---

# 1) Usa el solver adecuado: **IPPE para cuadrados**

Para marcadores planos y cuadrados, cambia a `SOLVEPNP_IPPE_SQUARE`. Este método está pensado para exactamente tu caso y reduce muchísimo el “flip” de soluciones.

```java
// puntos3D: 4 esquinas del aruco en su marco local (en metros)
// puntos2D: 4 esquinas detectadas en píxeles
boolean ok = Calib3d.solvePnP(
    objectPoints, imagePoints, cameraMatrix, distCoeffs,
    rvec, tvec, false, Calib3d.SOLVEPNP_IPPE_SQUARE
);

// (Opcional) refinado local
Calib3d.solvePnPRefineLM(objectPoints, imagePoints, cameraMatrix, distCoeffs, rvec, tvec);
```

> Si ya usas `estimatePoseSingleMarkers`, revisa que no fuerce `ITERATIVE`. En Camera2/Java, llama explícitamente a `solvePnP` con `IPPE_SQUARE`.

---

# 2) **Chequeo de cheirality** (descartar soluciones imposibles)

En OpenCV, `tvec` (marcador respecto a cámara) debe tener **z > 0** (el marcador está delante). Si obtienes z ≤ 0, **descarta** o intenta la **solución alternativa** (ver punto 3).

```java
double zc = tvec.get(2,0)[0];
if (zc <= 0) {
    // descarta esta pose (o intenta alternativa si la tienes)
    return null;
}
```

---

# 3) Considera **las dos soluciones** y elige la mejor

Con planos puede haber dos poses “válidas” geométricamente. Con IPPE puedes recuperar **ambas** usando `solvePnPGeneric` (si lo tienes disponible en tu binding) o repetir con una ligera inversión del normal y **elegir por menor error de reproyección**.

Idea (pseudo):

```java
// Pose A
double errA = reprojectionError(objectPoints, imagePoints, rvecA, tvecA);

// Genera Pose B (si tu API lo permite; con solvePnPGeneric te da dos)
double errB = reprojectionError(objectPoints, imagePoints, rvecB, tvecB);

// Quédate con la de menor error y z>0
```

Función de error:

```java
private static double reprojectionError(MatOfPoint3f obj, MatOfPoint2f img,
                                        Mat rvec, Mat tvec,
                                        Mat K, Mat dist) {
    MatOfPoint2f proj = new MatOfPoint2f();
    Calib3d.projectPoints(obj, rvec, tvec, K, dist, proj);
    Point[] p = proj.toArray();
    Point[] q = img.toArray();
    double s = 0;
    for (int i=0;i<p.length;i++){
        double dx = p[i].x - q[i].x;
        double dy = p[i].y - q[i].y;
        s += dx*dx + dy*dy;
    }
    return Math.sqrt(s / p.length);
}
```

---

# 4) **Refina esquinas** y endurece parámetros de detección

A 5–10 m el marcador ocupa decenas de píxeles; **una esquina mal 1–2 px** arruina la orientación.

* Activa **corner refinement subpíxel**:

  * `detectorParams.setCornerRefinementMethod(ARUCO_CORNER_REFINE_SUBPIX);`
* Sube `minMarkerPerimeterRate` y `minCornerDistanceRate` para evitar falsos positivos pequeños.

---

# 5) **Composición mundo**: valida la cadena de transformaciones

Cuando traes la pose a tu mundo, cualquier convención mal aplicada explota más allá de 5 m:

* Inversión correcta:

  * `R_cam_marker = R_marker_cam.t();`
  * `t_cam_marker = -R_cam_marker * t_marker_cam;` (ojo a tipos `CV_64F`)
* Transformación a mundo:

  * `t_cam_world = R_marker_world * t_cam_marker + t_marker_world;`
  * `R_cam_world = R_marker_world * R_cam_marker;`
* **RPY → R**: confirma orden (tú usas **Rz(yaw)·Ry(pitch)·Rx(roll)**). Si cambió, el signo “baila”.
* **Grados→radianes** siempre con `toRadians`.

> Consejo: imprime un caso controlado (marcador de frente a 6–8 m) y verifica que `tvec.z>0`, que al invertir e ir a mundo la posición cae donde esperas. Hazlo en `CV_64F` (doble precisión).

---

# 6) **Guardarraíles a distancia** (reglas simples que salvan)

* Rechaza poses con **ángulo oblicuo extremo**: calcula el ángulo entre el eje z del marcador y la vista; si |ángulo|> 70–75°, descarta (la incertidumbre explota).
* Rechaza **marcadores “demasiado pequeños”**: ancho en imagen < ~60–80 px ⇒ descarta.
* Pondera por **1/distancia²** y **error de reproyección** al fusionar.
* Usa `solvePnPRefineLM` o `RefineVVS` tras IPPE para afinar.

---

# 7) **Kalman/Fusión** consciente de distancia

Más allá de ~5 m, sube `measurementNoiseCov` y/o baja el peso de esa medición. Tu **mediana ponderada** ya te va muy bien: añade **peso ∝ 1/(d² * err²)**.

---

## Checklist rápido

* [ ] `SOLVEPNP_IPPE_SQUARE` + `solvePnPRefineLM`
* [ ] `tvec.z > 0` (si no, descartar/alternativa)
* [ ] Elegir solución por **menor reprojection error**
* [ ] **Subpixel corners** + parámetros más estrictos
* [ ] Transforms en **CV_64F** y convención RPY correcta
* [ ] Reglas de descarte por **ángulo** y **tamaño en px**
* [ ] Ponderar y filtrar más fuerte > 5 m

