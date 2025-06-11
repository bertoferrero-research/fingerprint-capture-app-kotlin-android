// CalibrationManager.kt
package com.bertoferrero.fingerprintcaptureapp.controllers.cameracontroller

import android.content.Context
import com.bertoferrero.fingerprintcaptureapp.lib.markers.MarkersDetectorNoCalibration
import com.bertoferrero.fingerprintcaptureapp.models.MarkerDefinition
import com.lemmingapex.trilateration.NonLinearLeastSquaresSolver
import com.lemmingapex.trilateration.TrilaterationFunction
import org.apache.commons.math3.fitting.leastsquares.LevenbergMarquardtOptimizer
import org.opencv.android.CameraBridgeViewBase
import org.opencv.core.Mat
import kotlin.math.round


class TestPositioningTrilaterationController(
    private val context: Context,
    public var markerSize: Float = 0.1765f,
    public var arucoDictionaryType: Int = org.opencv.objdetect.Objdetect.DICT_6X6_250,
    public var marker1: MarkerDefinition = MarkerDefinition(27, 0f, 0.552f, 1.5f, 0.173f),
    public var marker2: MarkerDefinition = MarkerDefinition(3, 0f, 1.103f, 1.5f, 0.173f),
    public var marker3: MarkerDefinition = MarkerDefinition(0, 0.02f, 1.97f, 1.94f, 0.173f),
) : ICameraController {
    // Running variables
    private var running = false
    private var markersDetector: MarkersDetectorNoCalibration? = null
    private var markersId: List<Int>? = null

    override fun initProcess() {
        if (!running) {
            running = true
            markersDetector = MarkersDetectorNoCalibration(context, arucoDictionaryType)
            markersId = listOf<Int>(marker1.id, marker2.id, marker3.id)
        }
    }

    override fun finishProcess() {
        if (running) {
            running = false
            markersDetector = null
            markersId = null
        }
    }

    override fun processFrame(inputFrame: CameraBridgeViewBase.CvCameraViewFrame?): Mat {

        if (!running || inputFrame == null) {
            return inputFrame?.rgba() ?: Mat()
        }

        // Detect markers
        val corners: MutableList<Mat> = mutableListOf()
        val ids: Mat = Mat()
        val detectedMarkers =
            markersDetector?.detectMarkers(inputFrame, markerSize, markersId!!, corners, ids)

        // Prepare the output frame
        val frame = inputFrame.rgba() ?: Mat()
        val rgb = Mat()
        org.opencv.imgproc.Imgproc.cvtColor(frame, rgb, org.opencv.imgproc.Imgproc.COLOR_RGBA2RGB)

        if (detectedMarkers != null && detectedMarkers.size >= 3){
            //https://github.com/lemmingapex/trilateration
            //Compile distances and positions in the format required by the trilateration function
            val distances : MutableList<Double> = mutableListOf()
            val positions : MutableList<DoubleArray> = mutableListOf()
            for (i in 0 until detectedMarkers.size) {
                //Get the marker information
                val marker = detectedMarkers[i]
                val id = marker.markerId
                distances.add(marker.distance)
                //Locate and extract the position of the marker
                var markerData : MarkerDefinition? = null
                when (id) {
                    marker1.id -> markerData = marker1
                    marker2.id -> markerData = marker2
                    marker3.id -> markerData = marker3
                }
                if (markerData != null) {
                    positions.add(doubleArrayOf(markerData.x.toDouble(), markerData.y.toDouble(), markerData.z.toDouble()))
                }

                org.opencv.imgproc.Imgproc.putText(
                    rgb,
                    "Distance: ${round(marker.distance * 10000) / 10000}m",
                    org.opencv.core.Point(
                        detectedMarkers[i].corners[1, 0][0],
                        detectedMarkers[i].corners[1, 0][1]
                    ),
                    org.opencv.imgproc.Imgproc.FONT_HERSHEY_SIMPLEX,
                    1.0,
                    org.opencv.core.Scalar(255.0, 0.0, 0.0),
                    2,
                    org.opencv.imgproc.Imgproc.LINE_AA
                )
            }
            val solver = NonLinearLeastSquaresSolver(
                TrilaterationFunction(positions.toTypedArray(), distances.toDoubleArray()),
                LevenbergMarquardtOptimizer()
            )
            val optimum = solver.solve()
            val centroid = optimum.point.toArray()

            //Print the position
            println("Position: (${centroid[0]}, ${centroid[1]}, ${centroid[2]})")
            //Also in the image
            org.opencv.imgproc.Imgproc.putText(
                rgb,
                "Position: (${centroid[0]}, ${centroid[1]}, ${centroid[2]})",
                org.opencv.core.Point(
                    25.0,25.0
                ),
                org.opencv.imgproc.Imgproc.FONT_HERSHEY_SIMPLEX,
                1.0,
                org.opencv.core.Scalar(255.0, 0.0, 0.0),
                2,
                org.opencv.imgproc.Imgproc.LINE_AA
            )
        }

        return rgb
    }


}