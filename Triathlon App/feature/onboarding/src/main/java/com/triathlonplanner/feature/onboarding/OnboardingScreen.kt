package com.triathlonplanner.feature.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.triathlonplanner.core.designsystem.AppRadius
import com.triathlonplanner.core.designsystem.AppSpacing
import com.triathlonplanner.core.designsystem.DisciplineBadge
import com.triathlonplanner.core.designsystem.SectionHeader
import com.triathlonplanner.core.designsystem.accents
import com.triathlonplanner.core.designsystem.visualFor
import com.triathlonplanner.core.model.Discipline
import com.triathlonplanner.core.model.Distance
import com.triathlonplanner.data.healthconnect.HealthConnectPermissions
import com.triathlonplanner.data.healthconnect.healthConnectPermissionRequestContract
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

private val RUN_PB_OPTIONS = listOf(
    "5K" to 5_000.0,
    "10K" to 10_000.0,
    "Half" to Distance.HALF_IRON.runMeters.toDouble(),
    "Marathon" to Distance.FULL_IRON.runMeters.toDouble(),
)

/** Steps the athlete walks through. PLAN_READY is the outcome, not a step, so it's excluded. */
private val PROGRESS_STEPS = listOf(
    OnboardingStep.DISTANCE,
    OnboardingStep.RACE_DATE,
    OnboardingStep.MAX_HR,
    OnboardingStep.FTP_CSS,
    OnboardingStep.GOAL_TYPE,
    OnboardingStep.CURRENT_FITNESS,
    OnboardingStep.TRAINING_AVAILABILITY,
    OnboardingStep.TRAINING_DAYS,
    OnboardingStep.HEALTH_CONNECT,
)

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.isComplete) {
        if (state.isComplete) onFinished()
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = AppSpacing.gutter),
        ) {
            if (state.step != OnboardingStep.PLAN_READY) {
                Spacer(Modifier.height(AppSpacing.lg))
                StepProgressRail(state.step)
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(top = AppSpacing.xl),
            ) {
                when (state.step) {
                    OnboardingStep.DISTANCE -> DistanceStep(state.selectedDistance, viewModel::selectDistance)
                    OnboardingStep.RACE_DATE -> RaceDateStep(state.raceDate, viewModel::selectRaceDate)
                    OnboardingStep.MAX_HR -> MaxHrStep(state, viewModel)
                    OnboardingStep.FTP_CSS -> FtpCssStep(state, viewModel)
                    OnboardingStep.GOAL_TYPE -> GoalTypeStep(state, viewModel)
                    OnboardingStep.CURRENT_FITNESS -> CurrentFitnessStep(state, viewModel)
                    OnboardingStep.TRAINING_AVAILABILITY -> TrainingAvailabilityStep(state, viewModel)
                    OnboardingStep.TRAINING_DAYS -> TrainingDaysStep(state, viewModel)
                    OnboardingStep.HEALTH_CONNECT -> HealthConnectStep(
                        granted = state.healthConnectPermissionsGranted,
                        onRefresh = viewModel::refreshHealthConnectStatus,
                        onPermissionsResult = viewModel::onHealthConnectPermissionsResult,
                    )
                    OnboardingStep.PLAN_READY -> PlanReadyStep(state.planWarnings)
                }
                Spacer(Modifier.height(AppSpacing.xl))
            }

            state.errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(AppSpacing.sm))
            }

            BottomActions(state, viewModel)
        }
    }
}

/** Thin segmented rail: filled where you've been and where you are, empty ahead. */
@Composable
private fun StepProgressRail(current: OnboardingStep) {
    val currentIndex = PROGRESS_STEPS.indexOf(current)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        PROGRESS_STEPS.forEachIndexed { index, _ ->
            val reached = currentIndex < 0 || index <= currentIndex
            val target = if (reached) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            val color by animateColorAsState(targetValue = target, label = "railSegment")
            Box(
                Modifier
                    .weight(1f)
                    .height(3.dp)
                    .clip(RoundedCornerShape(AppRadius.pill))
                    .background(color),
            )
        }
    }
}

@Composable
private fun BottomActions(state: OnboardingUiState, viewModel: OnboardingViewModel) {
    if (state.step == OnboardingStep.PLAN_READY) {
        Button(
            onClick = viewModel::confirmPlanReady,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(AppRadius.cell),
        ) { Text("Start training", style = MaterialTheme.typography.labelLarge) }
        Spacer(Modifier.height(AppSpacing.lg))
        return
    }

    val isFinalStep = state.step == OnboardingStep.HEALTH_CONNECT ||
        (state.step == OnboardingStep.TRAINING_DAYS && state.hasExistingProfile)

    val canProceed = when (state.step) {
        OnboardingStep.DISTANCE -> state.canProceedFromDistance
        OnboardingStep.RACE_DATE -> state.canProceedFromDate
        OnboardingStep.MAX_HR -> state.canProceedFromMaxHr
        OnboardingStep.FTP_CSS -> true
        OnboardingStep.GOAL_TYPE -> state.canProceedFromGoalType
        OnboardingStep.CURRENT_FITNESS -> state.canProceedFromCurrentFitness
        OnboardingStep.TRAINING_AVAILABILITY -> state.canProceedFromAvailability
        OnboardingStep.TRAINING_DAYS -> state.canProceedFromTrainingDays && !state.isSaving
        OnboardingStep.HEALTH_CONNECT -> !state.isSaving
        OnboardingStep.PLAN_READY -> true
    }

    Column(Modifier.fillMaxWidth()) {
        Button(
            onClick = viewModel::advance,
            enabled = canProceed,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(AppRadius.cell),
        ) {
            if (state.isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
            } else {
                Text(
                    if (isFinalStep) "Create my plan" else "Continue",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            if (state.step != OnboardingStep.DISTANCE) {
                TextButton(onClick = viewModel::back) { Text("Back") }
            } else {
                Spacer(Modifier.width(1.dp))
            }
            if (state.step == OnboardingStep.CURRENT_FITNESS) {
                TextButton(onClick = viewModel::skipCurrentFitness) { Text("Skip this") }
            }
        }
    }
}

// --- Shared step scaffolding -----------------------------------------------------------------

@Composable
private fun StepHeading(title: String, subtitle: String? = null) {
    Text(title, style = MaterialTheme.typography.headlineMedium)
    subtitle?.let {
        Spacer(Modifier.height(AppSpacing.sm))
        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(Modifier.height(AppSpacing.xl))
}

/** Tappable option card. Selection is a primary border plus a check, never colour alone. */
@Composable
private fun SelectableCard(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick),
        shape = RoundedCornerShape(AppRadius.card),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.07f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(AppSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            content()
            Spacer(Modifier.width(AppSpacing.sm))
            if (selected) {
                Box(
                    Modifier.size(22.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun NumField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        shape = RoundedCornerShape(AppRadius.cell),
        modifier = modifier,
    )
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

// --- Steps ------------------------------------------------------------------------------------

@Composable
private fun DistanceStep(selected: Distance?, onSelect: (Distance) -> Unit) {
    StepHeading("What are you racing?", "This sets the whole shape of your plan.")
    Distance.entries.forEach { distance ->
        SelectableCard(
            selected = selected == distance,
            onClick = { onSelect(distance) },
            modifier = Modifier.padding(bottom = AppSpacing.sm),
        ) {
            Column(Modifier.weight(1f)) {
                Text(distance.displayName, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(AppSpacing.xs))
                Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
                    LegChip(Discipline.SWIM, "${distance.swimMeters} m")
                    LegChip(Discipline.BIKE, "${distance.bikeMeters / 1000} km")
                    LegChip(Discipline.RUN, "${distance.runMeters / 1000.0} km")
                }
            }
        }
    }
}

/** Sport glyph plus distance. The icon names the leg, so no colour is needed to tell them apart. */
@Composable
private fun LegChip(discipline: Discipline, text: String) {
    val visual = visualFor(discipline)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            visual.icon,
            contentDescription = visual.label,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(13.dp),
        )
        Spacer(Modifier.width(AppSpacing.xs))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RaceDateStep(selectedDate: LocalDate?, onSelect: (LocalDate) -> Unit) {
    StepHeading(
        "When's race day?",
        selectedDate?.let { "Selected: $it" } ?: "Pick your race date so we can size the training block.",
    )
    val datePickerState = rememberDatePickerState()
    DatePicker(state = datePickerState, title = null, headline = null, showModeToggle = false)
    LaunchedEffect(datePickerState.selectedDateMillis) {
        datePickerState.selectedDateMillis?.let { millis ->
            onSelect(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
        }
    }
}

@Composable
private fun MaxHrStep(state: OnboardingUiState, viewModel: OnboardingViewModel) {
    StepHeading(
        "What's your max heart rate?",
        "From a recent test or race. If you're unsure, 220 minus your age is a rough starting point.",
    )
    NumField(state.maxHrInput, viewModel::updateMaxHr, "Max HR (bpm)", Modifier.fillMaxWidth())
    Spacer(Modifier.height(AppSpacing.md))
    NumField(state.restingHrInput, viewModel::updateRestingHr, "Resting HR (bpm) - optional", Modifier.fillMaxWidth())
    Spacer(Modifier.height(AppSpacing.sm))
    Hint("Resting HR lets us use heart-rate reserve, which is more accurate than %max alone.")
}

@Composable
private fun FtpCssStep(state: OnboardingUiState, viewModel: OnboardingViewModel) {
    StepHeading(
        "Bike power and swim pace",
        "Optional. Leave them blank and we'll prescribe by heart rate until you add them.",
    )
    NumField(state.ftpInput, viewModel::updateFtp, "FTP (watts)", Modifier.fillMaxWidth())
    Spacer(Modifier.height(AppSpacing.lg))
    SectionHeader("Critical swim speed per 100m")
    Spacer(Modifier.height(AppSpacing.sm))
    Row {
        NumField(state.cssMinutesInput, viewModel::updateCssMinutes, "min", Modifier.weight(1f))
        Spacer(Modifier.width(AppSpacing.sm))
        NumField(state.cssSecondsInput, viewModel::updateCssSeconds, "sec", Modifier.weight(1f))
    }
    Spacer(Modifier.height(AppSpacing.sm))
    Hint("Both can be measured later with a guided test from your Profile tab.")
}

@Composable
private fun GoalTypeStep(state: OnboardingUiState, viewModel: OnboardingViewModel) {
    StepHeading("What's the goal?", "A target time changes how much volume we recommend.")

    SelectableCard(
        selected = state.goalType == GoalType.JUST_FINISH,
        onClick = { viewModel.selectGoalType(GoalType.JUST_FINISH) },
        modifier = Modifier.padding(bottom = AppSpacing.sm),
    ) {
        Column(Modifier.weight(1f)) {
            Text("Just finish", style = MaterialTheme.typography.titleMedium)
            Text(
                "Get to the line healthy and complete the distance.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    SelectableCard(
        selected = state.goalType == GoalType.TARGET_TIME,
        onClick = { viewModel.selectGoalType(GoalType.TARGET_TIME) },
    ) {
        DisciplineBadge(Icons.Filled.EmojiEvents, MaterialTheme.colorScheme.primary, null, size = 34.dp)
        Spacer(Modifier.width(AppSpacing.md))
        Column(Modifier.weight(1f)) {
            Text("Hit a target time", style = MaterialTheme.typography.titleMedium)
            Text(
                "We'll sanity-check it against your current fitness.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (state.goalType == GoalType.TARGET_TIME) {
        Spacer(Modifier.height(AppSpacing.lg))
        SectionHeader("Target finish time")
        Spacer(Modifier.height(AppSpacing.sm))
        Row {
            NumField(state.targetTimeHoursInput, viewModel::updateTargetTimeHours, "hr", Modifier.weight(1f))
            Spacer(Modifier.width(AppSpacing.sm))
            NumField(state.targetTimeMinutesInput, viewModel::updateTargetTimeMinutes, "min", Modifier.weight(1f))
            Spacer(Modifier.width(AppSpacing.sm))
            NumField(state.targetTimeSecondsInput, viewModel::updateTargetTimeSeconds, "sec", Modifier.weight(1f))
        }
    }
}

@Composable
private fun CurrentFitnessStep(state: OnboardingUiState, viewModel: OnboardingViewModel) {
    StepHeading(
        "Where are you now?",
        "This tells us how big a jump your target is, so the volume we recommend is honest.",
    )

    SelectableCard(
        selected = state.fitnessEstimateMode == FitnessEstimateMode.DIRECT_ENTRY,
        onClick = { viewModel.selectFitnessEstimateMode(FitnessEstimateMode.DIRECT_ENTRY) },
        modifier = Modifier.padding(bottom = AppSpacing.sm),
    ) {
        Column(Modifier.weight(1f)) {
            Text("I know my current finish time", style = MaterialTheme.typography.titleMedium)
        }
    }

    SelectableCard(
        selected = state.fitnessEstimateMode == FitnessEstimateMode.CALCULATED,
        onClick = { viewModel.selectFitnessEstimateMode(FitnessEstimateMode.CALCULATED) },
    ) {
        Column(Modifier.weight(1f)) {
            Text("Work it out from a run PB", style = MaterialTheme.typography.titleMedium)
        }
    }

    when (state.fitnessEstimateMode) {
        FitnessEstimateMode.DIRECT_ENTRY -> {
            Spacer(Modifier.height(AppSpacing.lg))
            SectionHeader("Estimated finish time")
            Spacer(Modifier.height(AppSpacing.sm))
            Row {
                NumField(state.currentEstimateHoursInput, viewModel::updateCurrentEstimateHours, "hr", Modifier.weight(1f))
                Spacer(Modifier.width(AppSpacing.sm))
                NumField(state.currentEstimateMinutesInput, viewModel::updateCurrentEstimateMinutes, "min", Modifier.weight(1f))
                Spacer(Modifier.width(AppSpacing.sm))
                NumField(state.currentEstimateSecondsInput, viewModel::updateCurrentEstimateSeconds, "sec", Modifier.weight(1f))
            }
        }
        FitnessEstimateMode.CALCULATED -> {
            Spacer(Modifier.height(AppSpacing.lg))
            SectionHeader("Which distance?")
            Spacer(Modifier.height(AppSpacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                RUN_PB_OPTIONS.forEach { (label, meters) ->
                    val isSelected = state.runPbDistanceM == meters
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(AppRadius.cell))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant,
                            )
                            .selectable(selected = isSelected, onClick = { viewModel.updateRunPbDistance(meters) })
                            .padding(vertical = AppSpacing.md),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(AppSpacing.lg))
            SectionHeader("Your time for it")
            Spacer(Modifier.height(AppSpacing.sm))
            Row {
                NumField(state.runPbMinutesInput, viewModel::updateRunPbMinutes, "min", Modifier.weight(1f))
                Spacer(Modifier.width(AppSpacing.sm))
                NumField(state.runPbSecondsInput, viewModel::updateRunPbSeconds, "sec", Modifier.weight(1f))
            }
            Spacer(Modifier.height(AppSpacing.md))
            if (state.effectiveCssPaceSecPer100m == null) {
                Text(
                    "We also need a swim CSS pace for this - add one on the previous step, or skip and we'll use the plain recommendation.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                Hint("We combine this with your swim pace. The bike leg isn't estimated - it depends too much on the course.")
            }
        }
        null -> Unit
    }
}

@Composable
private fun TrainingAvailabilityStep(state: OnboardingUiState, viewModel: OnboardingViewModel) {
    StepHeading(
        "How much can you train?",
        "We've pre-filled a recommendation" +
            (if (state.goalType == GoalType.TARGET_TIME) " based on your race and target time." else " for your race distance.") +
            " Adjust it to what your week really allows.",
    )
    NumField(state.weeklyHoursInput, viewModel::updateWeeklyHours, "Hours per week", Modifier.fillMaxWidth())
    Spacer(Modifier.height(AppSpacing.md))
    NumField(state.daysPerWeekInput, viewModel::updateDaysPerWeek, "Days per week", Modifier.fillMaxWidth())
    if (state.hasExistingProfile) {
        Spacer(Modifier.height(AppSpacing.lg))
        Hint("Using the max HR, FTP and swim pace already in your profile - change them any time from the Profile tab.")
    }
}

/**
 * Per-discipline day availability plus long-session days. Laid out as one row of day toggles per
 * discipline: an athlete thinks "I swim Tuesdays and Thursdays", not "Tuesday is a swim-and-run
 * day", so the row-per-sport shape matches how the answer is actually held in their head.
 */
@Composable
private fun TrainingDaysStep(state: OnboardingUiState, viewModel: OnboardingViewModel) {
    StepHeading(
        "Which days suit which sport?",
        "Tap to turn days on or off. We'll rotate sessions through the days you pick, so no two " +
            "weeks look quite the same.",
    )

    listOf(Discipline.SWIM, Discipline.BIKE, Discipline.RUN).forEach { discipline ->
        val visual = visualFor(discipline)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = AppSpacing.xs)) {
            DisciplineBadge(visual.icon, MaterialTheme.colorScheme.onSurfaceVariant, visual.label, size = 28.dp)
            Spacer(Modifier.width(AppSpacing.sm))
            Text(visual.label, style = MaterialTheme.typography.titleSmall)
        }
        DayToggleRow(
            selected = state.daysFor(discipline),
            onToggle = { day -> viewModel.toggleTrainingDay(discipline, day) },
        )
        if (state.daysFor(discipline).isEmpty()) {
            Text(
                "Pick at least one day for ${visual.label.lowercase()}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(Modifier.height(AppSpacing.lg))
    }

    SectionHeader("Long session days")
    Spacer(Modifier.height(AppSpacing.xs))
    Hint("Days with room for your long ride or long run - usually a weekend, but yours may differ.")
    Spacer(Modifier.height(AppSpacing.sm))
    DayToggleRow(
        selected = state.longSessionDays,
        onToggle = viewModel::toggleLongSessionDay,
    )
    if (state.longSessionDays.isEmpty()) {
        Text(
            "Pick at least one day that can take a long session.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

/** Seven compact toggles, Monday first. Selection uses the brand accent, not a per-sport hue -
 * these are all the same kind of control, so tinting them by sport implied a distinction that
 * doesn't exist. The row's own heading says which sport it belongs to. */
@Composable
private fun DayToggleRow(
    selected: Set<DayOfWeek>,
    onToggle: (DayOfWeek) -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
    ) {
        DayOfWeek.entries.forEach { day ->
            val isOn = day in selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .clip(RoundedCornerShape(AppRadius.cell))
                    .background(if (isOn) accent else MaterialTheme.colorScheme.surfaceVariant)
                    .selectable(selected = isOn, onClick = { onToggle(day) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    day.getDisplayName(JavaTextStyle.NARROW, Locale.getDefault()),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isOn) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun HealthConnectStep(granted: Boolean, onRefresh: () -> Unit, onPermissionsResult: (Boolean) -> Unit) {
    val launcher = rememberLauncherForActivityResult(
        contract = healthConnectPermissionRequestContract(),
    ) { result -> onPermissionsResult(result.containsAll(HealthConnectPermissions.REQUIRED)) }

    LaunchedEffect(Unit) { onRefresh() }

    StepHeading(
        "Connect your watch",
        "If Garmin Connect syncs to Health Connect, we'll read your completed workouts and adapt the plan automatically.",
    )

    if (granted) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(MaterialTheme.accents.success, CircleShape))
            Spacer(Modifier.width(AppSpacing.sm))
            Text("Connected - your workouts will sync.", style = MaterialTheme.typography.bodyMedium)
        }
    } else {
        Button(
            onClick = { launcher.launch(HealthConnectPermissions.REQUIRED) },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(AppRadius.cell),
        ) { Text("Grant Health Connect access", style = MaterialTheme.typography.labelLarge) }
    }
    Spacer(Modifier.height(AppSpacing.md))
    Hint("Everything stays on your device. You can skip this and connect later from Profile.")
}

@Composable
private fun PlanReadyStep(planWarnings: List<String>) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(AppSpacing.xl))
        Box(
            Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.EmojiEvents,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
        }
        Spacer(Modifier.height(AppSpacing.lg))
        Text("Your plan is ready", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(AppSpacing.sm))
        Text(
            if (planWarnings.isEmpty()) {
                "Everything checks out. Good luck with the block."
            } else {
                "A few things worth knowing before you start."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (planWarnings.isNotEmpty()) {
        Spacer(Modifier.height(AppSpacing.xl))
        planWarnings.forEach { warning ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = AppSpacing.sm)
                    .clip(RoundedCornerShape(AppRadius.card))
                    .background(MaterialTheme.accents.warning.copy(alpha = 0.10f))
                    .padding(AppSpacing.lg),
            ) {
                Icon(
                    Icons.Filled.Info,
                    contentDescription = null,
                    tint = MaterialTheme.accents.warning,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(AppSpacing.md))
                Text(warning, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
