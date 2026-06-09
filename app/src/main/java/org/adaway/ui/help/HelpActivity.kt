package org.adaway.ui.help

import android.text.Html
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.URLSpan
import android.text.style.UnderlineSpan
import android.graphics.Typeface
import androidx.annotation.RawRes
import androidx.annotation.StringRes
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.adaway.R
import org.adaway.ui.compose.ExpressiveFloatingBar
import org.adaway.ui.compose.ExpressiveScaffold
import org.adaway.ui.compose.ExpressiveTopBar
import org.adaway.ui.compose.ExpressiveSection
import org.adaway.ui.compose.ExpressiveAsymmetricShape1
import org.adaway.ui.compose.ExpressiveAsymmetricShape2
import org.adaway.ui.compose.safeClickable
import org.adaway.ui.compose.ExpressiveSelectorButton
import org.adaway.ui.compose.ExpressiveSelectionBottomSheet
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

private data class HelpTab(
    @param:StringRes @field:StringRes val titleRes: Int,
    @param:RawRes @field:RawRes val rawRes: Int
)

private val helpTabs = listOf(
    HelpTab(R.string.help_tab_faq, R.raw.help_faq),
    HelpTab(R.string.help_tab_problems, R.raw.help_problems),
    HelpTab(R.string.help_tab_s_on_s_off, R.raw.help_s_on_s_off)
)

@Composable
internal fun HelpRoute(onNavigateBack: () -> Unit) {
    HelpScreen(onNavigateBack = onNavigateBack)
}

@Composable
private fun HelpScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val tabContents = remember {
        helpTabs.map { tab ->
            Html.fromHtml(readRawResource(context, tab.rawRes), Html.FROM_HTML_MODE_LEGACY)
        }
    }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showHelpSelectionDialog by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    LaunchedEffect(selectedTab) {
        scrollState.scrollTo(0)
    }

    ExpressiveScaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            ExpressiveTopBar(
                title = stringResource(R.string.menu_help),
                onNavigateBack = onNavigateBack
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            ExpressiveFloatingBar(
                horizontalPadding = 16.dp,
                verticalPadding = 10.dp,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
                shape = ExpressiveAsymmetricShape1
            ) {
                ExpressiveSelectorButton(
                    label = stringResource(R.string.menu_help),
                    selectedValueLabel = stringResource(helpTabs[selectedTab].titleRes),
                    onClick = { showHelpSelectionDialog = true }
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ExpressiveSection(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f),
                shape = ExpressiveAsymmetricShape2
            ) {
                HelpHtmlView(
                    html = tabContents[selectedTab],
                    scrollState = scrollState,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    ExpressiveSelectionBottomSheet(
        show = showHelpSelectionDialog,
        onDismissRequest = { showHelpSelectionDialog = false },
        title = stringResource(R.string.menu_help),
        options = helpTabs,
        selectedOption = helpTabs[selectedTab],
        optionLabel = { tab -> stringResource(tab.titleRes) },
        onOptionSelected = { tab ->
            selectedTab = helpTabs.indexOf(tab)
        }
    )
}

@Composable
@Suppress("DEPRECATION")
private fun HelpHtmlView(
    html: Spanned,
    scrollState: ScrollState,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    val linkColor = MaterialTheme.colorScheme.primary
    val contentColor = MaterialTheme.colorScheme.onSurface
    val annotatedText = remember(html, linkColor, contentColor) {
        spannedToAnnotatedString(html, linkColor, contentColor)
    }

    Box(
        modifier = modifier
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        ClickableText(
            text = annotatedText,
            style = MaterialTheme.typography.bodyLarge,
            onClick = { offset ->
                annotatedText
                    .getStringAnnotations(tag = "URL", start = offset, end = offset)
                    .firstOrNull()
                    ?.let { annotation -> uriHandler.openUri(annotation.item) }
            }
        )
    }
}

private fun spannedToAnnotatedString(
    spanned: Spanned,
    linkColor: Color,
    defaultColor: Color
): AnnotatedString {
    val text = spanned.toString()
    val builder = AnnotatedString.Builder(text)
    builder.addStyle(SpanStyle(color = defaultColor), 0, text.length)

    spanned.getSpans(0, spanned.length, Any::class.java).forEach { span ->
        val start = spanned.getSpanStart(span).coerceAtLeast(0)
        val end = spanned.getSpanEnd(span).coerceAtMost(text.length)
        if (start >= end) return@forEach

        when (span) {
            is StyleSpan -> {
                when (span.style) {
                    Typeface.BOLD -> {
                        builder.addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
                    }
                    Typeface.ITALIC -> {
                        builder.addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
                    }
                    Typeface.BOLD_ITALIC -> {
                        builder.addStyle(
                            SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic),
                            start,
                            end
                        )
                    }
                }
            }

            is UnderlineSpan -> {
                builder.addStyle(
                    SpanStyle(textDecoration = TextDecoration.Underline),
                    start,
                    end
                )
            }

            is ForegroundColorSpan -> {
                builder.addStyle(SpanStyle(color = Color(span.foregroundColor)), start, end)
            }

            is URLSpan -> {
                builder.addStyle(
                    SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
                    start,
                    end
                )
                builder.addStringAnnotation("URL", span.url, start, end)
            }
        }
    }

    return builder.toAnnotatedString()
}

private fun readRawResource(context: android.content.Context, @RawRes resourceId: Int): String {
    context.resources.openRawResource(resourceId).use { inputStream: InputStream ->
        BufferedReader(InputStreamReader(inputStream)).use { reader ->
            val content = StringBuilder()
            var line: String? = reader.readLine()
            while (line != null) {
                content.append(line)
                line = reader.readLine()
            }
            return content.toString()
        }
    }
}
