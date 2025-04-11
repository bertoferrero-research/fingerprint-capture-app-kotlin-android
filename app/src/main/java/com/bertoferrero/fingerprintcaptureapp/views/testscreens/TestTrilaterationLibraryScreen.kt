package com.bertoferrero.fingerprintcaptureapp.views.testscreens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.core.screen.Screen
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.unit.dp
import com.bertoferrero.fingerprintcaptureapp.views.components.NumberField
import com.bertoferrero.fingerprintcaptureapp.views.components.SimpleDropdownMenu
import com.lemmingapex.trilateration.NonLinearLeastSquaresSolver
import com.lemmingapex.trilateration.TrilaterationFunction
import org.apache.commons.math3.fitting.leastsquares.LeastSquaresOptimizer
import org.apache.commons.math3.fitting.leastsquares.LevenbergMarquardtOptimizer
import org.apache.commons.math3.linear.RealVector

class TestTrilaterationLibraryScreen : Screen {

    @Composable
    override fun Content() {

        var markers = remember {
            mutableListOf(
                mutableListOf<Double>(3.1, 0.0, 0.552, 1.5), // Distance, x, y, z
                mutableListOf<Double>(3.03, 0.0, 1.103, 1.5),
                mutableListOf<Double>(2.84, 0.02, 1.97, 1.94),
            )
        }

        val (dimensions, setDimensions) = remember { mutableIntStateOf(2) }

        var markersPropertiesLabels = remember {
            listOf<String>("Distance", "X", "Y", "Z")
        }

        Scaffold(
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    item {
                        SimpleDropdownMenu(
                            label = "Dimensions",
                            options = arrayOf("x, y", "x, y, z"),
                            values = arrayOf(2, 3),
                            onOptionSelected = {
                                setDimensions(it)
                            },
                            selectedValue = dimensions,
                            modifier = Modifier
                                .fillParentMaxWidth()
                                .padding(2.dp)
                        )
                        for (i in 0..(markers.size - 1)) {
                            Row(
                                modifier = Modifier.fillParentMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                for (j in 0..(markersPropertiesLabels.size - 1)) {
                                    NumberField<Double>(
                                        value = markers[i][j],
                                        onValueChange = {
                                            markers[i][j] = it
                                        },
                                        label = { Text("Marker ${i + 1} - ${markersPropertiesLabels[j]}") },
                                        modifier = Modifier
                                            .fillParentMaxWidth(0.25f)
                                            .padding(2.dp)
                                    )
                                }
                            }
                        }
                        Spacer(
                            modifier = Modifier.padding(vertical = 20.dp)
                        )
                        CalculateTrilateration(dimensions, markers)
                    }
                }
            }
        }
    }

    @Composable
    private fun CalculateTrilateration(dimensions: Int, markers: List<List<Double>>) {
        var optimum: LeastSquaresOptimizer.Optimum? = null
        try {
            //Compile distances and positions in the format required by the trilateration function
            val distances: MutableList<Double> = mutableListOf()
            val positions: MutableList<DoubleArray> = mutableListOf()
            for (marker in markers) {
                //Distance
                distances.add(marker[0])
                //positions
                var positionRow = mutableListOf<Double>(
                    marker[1],
                    marker[2]
                )
                if (dimensions == 3) {
                    positionRow.add(marker[3])
                }
                positions.add(positionRow.toDoubleArray())
            }

            //Initialize library and calculate results
            //https://github.com/lemmingapex/trilateration
            val solver = NonLinearLeastSquaresSolver(
                TrilaterationFunction(positions.toTypedArray(), distances.toDoubleArray()),
                LevenbergMarquardtOptimizer()
            )
            optimum = solver.solve()
        } catch (e: Exception) {
            e.printStackTrace()
            Text("Exception on calculating trilateration: ${e.message}")
        }

        if(optimum !== null){
            val centroid = optimum.point.toArray()
            Text("Result")
            Text("X: ${centroid[0]}")
            Text("Y: ${centroid[1]}")
            if(dimensions == 3) {
                Text("Z: ${centroid[2]}")
            }
            var sigma: RealVector? = null
            try {
                sigma = optimum.getSigma(0.0)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            if(sigma != null) {
                Text("Error (Sigma)")
                Text("X: ${sigma.getEntry(0)}")
                Text("Y: ${sigma.getEntry(1)}")
                if (dimensions == 3) {
                    Text("Z: ${sigma.getEntry(2)}")
                }
            }
        }
    }

}