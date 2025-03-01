package com.bertoferrero.fingerprintcaptureapp.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import com.bertoferrero.fingerprintcaptureapp.components.OpenCvCamera
import org.opencv.android.CameraBridgeViewBase
import org.opencv.core.Mat
import org.opencv.core.Size

class CalibratingScreen : Screen {

    //Calibrating variables
    private val minSamplesAmount = 50
    private var imageSize: Size? = null
    private var arucoDictionaryType = org.opencv.objdetect.Objdetect.DICT_6X6_250
    private var charucoVerticalSquares = 7 //TODO ¿están al reves?
    private var charucoHorizontalSquares = 5
    private var charucoSquareLength = 0.035f
    private var charucoMarkerLength = 0.018f
    private var calibrating = false
    private var arucoDictionary: org.opencv.objdetect.Dictionary? = null
    private var charucoBoard: org.opencv.objdetect.CharucoBoard? = null
    private var arucoDetectorParams: org.opencv.objdetect.DetectorParameters? = null
    private var arucoDetector: org.opencv.objdetect.ArucoDetector? = null
    private var charucoDetector: org.opencv.objdetect.CharucoDetector? = null

    //Calibrating pools
    private val objPointsPool: MutableList<Mat> = mutableListOf()
    private val imgPointsPool: MutableList<Mat> = mutableListOf()


    protected fun processFrameCalibration(inputFrame: CameraBridgeViewBase.CvCameraViewFrame?): Mat {
        //Basado en https://stackoverflow.com/questions/79084728/how-do-to-camera-calibration-using-charuco-board-for-opencv-4-10-0
        // Implementa el procesamiento del frame aquí
        if(!calibrating){
            return inputFrame?.rgba() ?: Mat()
        }

        //Copiamos la imagen
        val frame = inputFrame?.rgba() ?: Mat()

        if(imageSize == null){
            //Set image size
            imageSize?.width = frame.width().toDouble()
            imageSize?.height = frame.height().toDouble()
        }

        //Convert to grayscale (from https://github.com/RivoLink/Aruco-Android)
        val rgb = Mat()
        org.opencv.imgproc.Imgproc.cvtColor(frame, rgb, org.opencv.imgproc.Imgproc.COLOR_RGBA2RGB)
        val gray = inputFrame?.gray()


        //Estimate charuco board
        val charucoCorners = Mat()
        val charucoIds = Mat()
        charucoDetector?.detectBoard(gray, charucoCorners, charucoIds)

        if(charucoIds.total() > 0){
            //Draw markers
            org.opencv.objdetect.Objdetect.drawDetectedCornersCharuco(rgb, charucoCorners, charucoIds)

            val objPoints = Mat()
            val imgPoints = Mat()
            val detectedCorners: MutableList<Mat> = mutableListOf()
            detectedCorners.add(charucoCorners)
            try {
                charucoBoard?.matchImagePoints(charucoCorners, charucoIds, objPoints, imgPoints)
                objPointsPool.add(objPoints)
                imgPointsPool.add(imgPoints)
            }catch(e: Exception){
               var a = 1
            }


            /*CvException [org.opencv.core.CvException: cv::Exception: OpenCV(4.11.0) /home/ci/opencv/modules/objdetect/src/aruco/aruco_board.cpp:425: error: (-2:Unspecified error) in function 'virtual void cv::aruco::CharucoBoardImpl::matchImagePoints(InputArrayOfArrays, InputArray, OutputArray, OutputArray) const'
                                                                                                    > Number of corners and ids must be equal (expected: 'detectedIds.total() == detectedCharuco.total()'), where
                                                                                                    >     'detectedIds.total()' is 2
                                                                                                    > must be equal to
                                                                                                    >     'detectedCharuco.total()' is 1
                                                                                                    ]*/
        }

        return rgb

    }

    protected fun initCalibration() {
        // Initialize the calibration process
        arucoDictionary =
            org.opencv.objdetect.Objdetect.getPredefinedDictionary(arucoDictionaryType)
        charucoBoard = org.opencv.objdetect.CharucoBoard(
            Size(
                charucoVerticalSquares.toDouble(),
                charucoHorizontalSquares.toDouble()
            ), charucoSquareLength, charucoMarkerLength, arucoDictionary
        )
        charucoDetector = org.opencv.objdetect.CharucoDetector(charucoBoard)

        objPointsPool.clear()
        imgPointsPool.clear()

        calibrating = true
    }

    protected fun finishCalibration() {
        // Stop the calibration process
        calibrating = false

        //Calibration result process
        if(objPointsPool.size >= minSamplesAmount){
            val cameraMatrix = Mat()
            val distCoeffs = Mat()
            val rvecs = mutableListOf<Mat>()
            val tvecs = mutableListOf<Mat>()
            org.opencv.calib3d.Calib3d.calibrateCamera(
                objPointsPool,
                imgPointsPool,
                imageSize,
                cameraMatrix,
                distCoeffs,
                rvecs,
                tvecs
            )
        }
        else{
            //TODO toast
        }

        // Clean up
        arucoDictionary = null
        charucoBoard = null
        charucoDetector = null
        objPointsPool.clear()
        imgPointsPool.clear()
        imageSize = null

        //TODO Save calibration data

    }

    @Composable
    override fun Content() {
        val (calibratingContent, setCalibratingContent) = remember { mutableStateOf(false) }

        if (!calibratingContent) {
            RenderSettingsScreen(setCalibratingContent)
        } else {
            RenderCalibratingScreen(setCalibratingContent)
        }
    }

    @Composable
    fun RenderSettingsScreen(setCalibratingContent: (Boolean) -> Unit) {
        Scaffold(
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {

                Button(
                    onClick = {
                        initCalibration()
                        setCalibratingContent(true)
                    }) {
                    Text("Start calibration")
                }
            }
        }

    }

    @Composable
    fun RenderCalibratingScreen(setCalibratingContent: (Boolean) -> Unit) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                    finishCalibration()
                    setCalibratingContent(false)
                }) {
                    Text(
                        modifier = Modifier.padding(10.dp, 10.dp),
                        text = "Finish calibration"
                    )
                }
            }) { innerPadding ->

            OpenCvCamera(
                object :
                    CameraBridgeViewBase.CvCameraViewListener2 {
                    override fun onCameraFrame(inputFrame: CameraBridgeViewBase.CvCameraViewFrame?): Mat {
                        return processFrameCalibration(inputFrame)
                    }

                    override fun onCameraViewStarted(width: Int, height: Int) {
                    }

                    override fun onCameraViewStopped() {
                    }
                }
            ).Render()

        }
    }
}