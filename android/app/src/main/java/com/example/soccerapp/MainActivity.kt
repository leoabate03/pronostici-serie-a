package com.example.soccerapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.soccerapp.model.TflitePredictor
import com.example.soccerapp.ui.FixturesScreen
import com.example.soccerapp.ui.MainViewModel
import com.example.soccerapp.ui.MatchDetailScreen
import com.example.soccerapp.ui.ValueBetsScreen

class MainActivity : ComponentActivity() {

    private lateinit var predictor: TflitePredictor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Il .tflite viene creato una volta e tenuto in memoria (economico).
        predictor = TflitePredictor(applicationContext)

        setContent {
            MaterialTheme {
                val navController = rememberNavController()
                val viewModel = remember { MainViewModel().apply { attachPredictor(predictor) } }

                Scaffold { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "fixtures",
                        modifier = Modifier.padding(innerPadding),
                    ) {
                        composable("fixtures") {
                            FixturesScreen(viewModel, onFixtureClick = { id ->
                                navController.navigate("match/$id")
                            })
                        }
                        composable("valuebets") {
                            ValueBetsScreen(viewModel)
                        }
                        composable("match/{fixtureId}") { backStackEntry ->
                            val fixtureId = backStackEntry.arguments?.getString("fixtureId")
                            MatchDetailScreen(viewModel, fixtureId)
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        predictor.close()
        super.onDestroy()
    }
}