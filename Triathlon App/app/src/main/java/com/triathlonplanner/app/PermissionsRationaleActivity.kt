package com.triathlonplanner.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.triathlonplanner.core.designsystem.TriathlonPlannerTheme

/**
 * Health Connect's permission screen links here (the "privacy policy" link) - required manifest
 * declaration for the Health Connect permission flow. All Health Connect data stays on-device;
 * there's no external server this app talks to.
 */
class PermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TriathlonPlannerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Text(
                        "Triathlon Planner reads your completed workouts (heart rate, power, pace) " +
                            "from Health Connect to track progress against your training plan. This " +
                            "data is only ever stored on your device and is never uploaded to any " +
                            "server.",
                        modifier = Modifier.padding(24.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}
