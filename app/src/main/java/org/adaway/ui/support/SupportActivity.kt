package org.adaway.ui.support

import org.adaway.ui.compose.safeClickable

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.adaway.R
import org.adaway.ui.compose.AdAwayExpressiveTheme
import org.adaway.ui.compose.ExpressiveActionCard
import org.adaway.ui.compose.ExpressivePage
import org.adaway.ui.compose.ExpressiveScaffold
import org.adaway.ui.compose.ExpressiveTopBar
import org.adaway.ui.compose.ExpressiveAsymmetricShape1
import org.adaway.ui.compose.ExpressiveAsymmetricShape2
import org.adaway.ui.compose.ScallopedShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.clip

object SupportLinks {
    @JvmField
    val SUPPORT_LINK: Uri = Uri.parse("https://paypal.me/BruceBUJON")

    @JvmField
    val SPONSORSHIP_LINK: Uri = Uri.parse("https://github.com/sponsors/PerfectSlayer")
}

@Composable
internal fun SupportRoute(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    SupportScreen(
        onNavigateBack = onNavigateBack,
        onSupportClick = { context.startActivity(Intent(Intent.ACTION_VIEW, SupportLinks.SUPPORT_LINK)) },
        onSponsorshipClick = { context.startActivity(Intent(Intent.ACTION_VIEW, SupportLinks.SPONSORSHIP_LINK)) }
    )
}

@Composable
private fun SupportScreen(
    onNavigateBack: () -> Unit,
    onSupportClick: () -> Unit,
    onSponsorshipClick: () -> Unit
) {
    ExpressiveScaffold(
        topBar = {
            ExpressiveTopBar(
                title = stringResource(R.string.support_label),
                onNavigateBack = onNavigateBack
            )
        }
    ) { innerPadding ->
        SupportContent(
            contentPadding = innerPadding,
            onSupportClick = onSupportClick,
            onSponsorshipClick = onSponsorshipClick
        )
    }
}

@Composable
private fun SupportContent(
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onSupportClick: () -> Unit,
    onSponsorshipClick: () -> Unit
) {
    val heartTransition = rememberInfiniteTransition(label = "heart")
    val heartScale by heartTransition.animateFloat(
        initialValue = 1F,
        targetValue = 1.25F,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "heartScale"
    )

    ExpressivePage(
        modifier = Modifier.padding(contentPadding)
    ) {
        Box(
            modifier = Modifier
                .padding(top = 16.dp)
                .size(136.dp)
                .scale(heartScale)
                .clip(ScallopedShape(numPetals = 12, depth = 8.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .safeClickable(onClick = onSupportClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.baseline_favorite_24),
                contentDescription = stringResource(R.string.welcome_support_logo),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp)
            )
        }

        Text(
            text = stringResource(R.string.welcome_support_header),
            style = MaterialTheme.typography.displaySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 32.dp)
        )

        Text(
            text = stringResource(R.string.welcome_support_summary),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        ExpressiveActionCard(
            label = stringResource(R.string.welcome_support_button),
            icon = {
                Image(
                    painter = painterResource(R.drawable.paypal),
                    contentDescription = null,
                    modifier = Modifier.size(28.dp)
                )
            },
            shape = ExpressiveAsymmetricShape1,
            onClick = onSupportClick
        )

        ExpressiveActionCard(
            label = stringResource(R.string.support_sponsorship_button),
            icon = {
                Icon(
                    painter = painterResource(R.drawable.ic_github_32dp),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(28.dp)
                )
            },
            shape = ExpressiveAsymmetricShape2,
            onClick = onSponsorshipClick
        )

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Preview(showBackground = true)
@Composable
private fun SupportPreview() {
    AdAwayExpressiveTheme {
        SupportContent(onSupportClick = {}, onSponsorshipClick = {})
    }
}



