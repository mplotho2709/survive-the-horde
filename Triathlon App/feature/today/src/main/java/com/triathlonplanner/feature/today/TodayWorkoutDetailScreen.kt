package com.triathlonplanner.feature.today

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.triathlonplanner.core.designsystem.AppCard
import com.triathlonplanner.core.designsystem.AppSpacing
import com.triathlonplanner.core.designsystem.EmptyState
import com.triathlonplanner.core.designsystem.WorkoutLegDetail

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayWorkoutDetailScreen(onBack: () -> Unit, viewModel: TodayWorkoutDetailViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val detail = state.legDetail

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Session", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> CircularProgressIndicator(Modifier.padding(AppSpacing.xl))
                detail == null -> EmptyState(
                    title = "Session not found",
                    subtitle = "It may have been removed when your plan was last adjusted.",
                )
                else -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = AppSpacing.gutter),
                ) {
                    AppCard(Modifier.fillMaxWidth()) {
                        Text(detail.title, style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(AppSpacing.md))
                        WorkoutLegDetail(detail, showHeading = false)
                    }
                    Spacer(Modifier.height(AppSpacing.xl))
                }
            }
        }
    }
}
