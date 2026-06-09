package org.adaway.ui.welcome

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.adaway.R

private enum class WelcomeStep {
    METHOD,
    SYNC,
    SUPPORT
}

@Composable
internal fun WelcomeRoute(onFinish: () -> Unit) {
    val steps = remember { WelcomeStep.entries }
    val pagerState = rememberPagerState(initialPage = 0) { steps.size }
    val coroutineScope = rememberCoroutineScope()
    val canProceed = remember { mutableStateListOf(false, false, true) }

    val currentPage = pagerState.currentPage
    val showBackButton = currentPage > 0
    val showNextButton = canProceed.getOrElse(currentPage) { false }
    val nextButtonTextRes = if (currentPage == steps.lastIndex) {
        R.string.welcome_finish_button
    } else {
        R.string.welcome_next_button
    }

    BackHandler(enabled = showBackButton) {
        coroutineScope.launch {
            pagerState.animateScrollToPage(currentPage - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            userScrollEnabled = false
        ) { page ->
            when (steps[page]) {
                WelcomeStep.METHOD -> WelcomeMethodStep(
                    onCanProceedChange = { canProceed[WelcomeStep.METHOD.ordinal] = it }
                )

                WelcomeStep.SYNC -> WelcomeSyncStep(
                    onCanProceedChange = { canProceed[WelcomeStep.SYNC.ordinal] = it }
                )

                WelcomeStep.SUPPORT -> WelcomeSupportStep(
                    onCanProceedChange = { canProceed[WelcomeStep.SUPPORT.ordinal] = it }
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(modifier = Modifier.width(100.dp)) {
                if (showBackButton) {
                    TextButton(
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(currentPage - 1)
                            }
                        }
                    ) {
                        Text(
                            text = stringResource(R.string.welcome_back_button),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(steps.size) { index ->
                    val selected = index == currentPage
                    val dotColor by animateColorAsState(
                        targetValue = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        },
                        label = "dotColor"
                    )
                    val dotWidth by animateDpAsState(
                        targetValue = if (selected) 24.dp else 8.dp,
                        label = "dotWidth"
                    )

                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(width = dotWidth, height = 8.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                            .semantics {
                                contentDescription = "Step ${index + 1}"
                            }
                    )
                }
            }

            Box(modifier = Modifier.width(100.dp), contentAlignment = Alignment.CenterEnd) {
                if (showNextButton) {
                    TextButton(
                        onClick = {
                            if (currentPage == steps.lastIndex) {
                                onFinish()
                            } else {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(currentPage + 1)
                                }
                            }
                        }
                    ) {
                        Text(
                            text = stringResource(nextButtonTextRes),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}
