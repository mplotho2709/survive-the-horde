package com.triathlonplanner.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.work.WorkManager
import com.triathlonplanner.core.designsystem.TriathlonPlannerTheme
import com.triathlonplanner.data.healthconnect.HealthConnectSyncWorker
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TriathlonPlannerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavHost()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Foreground sync on app resume - the periodic worker is only the safety net.
        HealthConnectSyncWorker.triggerImmediateSync(WorkManager.getInstance(this))
    }
}
