package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CodeSurfaceDark
import com.example.ui.theme.JetBrainsMonoFontFamily
import kotlinx.coroutines.delay

private sealed interface MarkdownSegment {
    data class TextBlock(val text: String) : MarkdownSegment
    data class CodeBlock(val language: String, val code: String, val isClosed: Boolean) : MarkdownSegment
}

private val NUMBERED_LIST_REGEX = Regex("^(\\d+\\.)\\s+(.*)$")

@Composable
fun MarkdownMessageText(
    markdown: String,
    textColor: Color,
    isStreaming: Boolean = false,
    modifier: Modifier = Modifier
) {
    val segments = remember(markdown) { parseMarkdownSegments(markdown) }
    val codeInlineBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    val accentColor = MaterialTheme.colorScheme.primary

    SelectionContainer(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            segments.forEachIndexed { index, segment ->
                val isLast = index == segments.lastIndex
                when (segment) {
                    is MarkdownSegment.TextBlock -> {
                        val displayText = if (isStreaming && isLast) {
                            "${segment.text} ▍"
                        } else {
                            segment.text
                        }
                        FormattedProseBlock(
                            rawText = displayText,
                            textColor = textColor,
                            inlineCodeBg = codeInlineBg,
                            accentColor = accentColor
                        )
                    }
                    is MarkdownSegment.CodeBlock -> {
                        val displayCode = if (isStreaming && isLast && !segment.isClosed) {
                            "${segment.code} ▍"
                        } else {
                            segment.code
                        }
                        CodeBlockCard(
                            language = segment.language,
                            code = displayCode,
                            copyableCode = segment.code
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FormattedProseBlock(
    rawText: String,
    textColor: Color,
    inlineCodeBg: Color,
    accentColor: Color
) {
    val lines = remember(rawText) { rawText.lines() }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        lines.forEach { line ->
            val trimmed = line.trimStart()
            val numberedMatch = NUMBERED_LIST_REGEX.matchEntire(trimmed)
            when {
                trimmed.startsWith("### ") -> {
                    Text(
                        text = buildInlineStyledString(
                            trimmed.removePrefix("### "),
                            textColor,
                            inlineCodeBg,
                            accentColor
                        ),
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        ),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                trimmed.startsWith("## ") -> {
                    Text(
                        text = buildInlineStyledString(
                            trimmed.removePrefix("## "),
                            textColor,
                            inlineCodeBg,
                            accentColor
                        ),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        ),
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                trimmed.startsWith("# ") -> {
                    Text(
                        text = buildInlineStyledString(
                            trimmed.removePrefix("# "),
                            textColor,
                            inlineCodeBg,
                            accentColor
                        ),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        ),
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                trimmed.startsWith("> ") -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                            .padding(vertical = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .fillMaxHeight()
                                .background(accentColor, RoundedCornerShape(2.dp))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = buildInlineStyledString(
                                trimmed.removePrefix("> "),
                                textColor.copy(alpha = 0.88f),
                                inlineCodeBg,
                                accentColor
                            ),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontStyle = FontStyle.Italic,
                                color = textColor.copy(alpha = 0.88f)
                            )
                        )
                    }
                }
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    Row(
                        modifier = Modifier.padding(start = 6.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = accentColor
                            ),
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = buildInlineStyledString(
                                trimmed.drop(2),
                                textColor,
                                inlineCodeBg,
                                accentColor
                            ),
                            style = MaterialTheme.typography.bodyLarge.copy(color = textColor)
                        )
                    }
                }
                numberedMatch != null -> {
                    Row(
                        modifier = Modifier.padding(start = 6.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = numberedMatch.groupValues[1],
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = accentColor
                            ),
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = buildInlineStyledString(
                                numberedMatch.groupValues[2],
                                textColor,
                                inlineCodeBg,
                                accentColor
                            ),
                            style = MaterialTheme.typography.bodyLarge.copy(color = textColor)
                        )
                    }
                }
                trimmed.isEmpty() -> {
                    Spacer(modifier = Modifier.height(2.dp))
                }
                else -> {
                    Text(
                        text = buildInlineStyledString(
                            line,
                            textColor,
                            inlineCodeBg,
                            accentColor
                        ),
                        style = MaterialTheme.typography.bodyLarge.copy(color = textColor)
                    )
                }
            }
        }
    }
}

@Composable
private fun CodeBlockCard(
    language: String,
    code: String,
    copyableCode: String
) {
    val clipboardManager = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1800)
            copied = false
        }
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = CodeSurfaceDark,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF172033))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = language.ifBlank { "code" }.lowercase(),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        color = Color(0xFF94A3B8)
                    )
                )
                IconButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(copyableCode))
                        copied = true
                    },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                        contentDescription = "Copy code",
                        tint = if (copied) Color(0xFF2DD4BF) else Color(0xFFCBD5E1),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(14.dp)
            ) {
                Text(
                    text = code,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = Color(0xFFE2E8F0)
                    )
                )
            }
        }
    }
}

private fun parseMarkdownSegments(rawMarkdown: String): List<MarkdownSegment> {
    val markdown = rawMarkdown.replace("\r\n", "\n")
    val segments = mutableListOf<MarkdownSegment>()
    val closedRegex = Regex("```([a-zA-Z0-9_+#.-]*)\\n([\\s\\S]*?)```")
    var cursor = 0
    for (match in closedRegex.findAll(markdown)) {
        if (match.range.first > cursor) {
            val prose = markdown.substring(cursor, match.range.first).trimEnd()
            if (prose.isNotBlank()) {
                segments.add(MarkdownSegment.TextBlock(prose))
            }
        }
        val lang = match.groupValues[1]
        val code = match.groupValues[2].trimEnd()
        segments.add(MarkdownSegment.CodeBlock(lang, code, isClosed = true))
        cursor = match.range.last + 1
    }
    if (cursor < markdown.length) {
        val tail = markdown.substring(cursor).trimStart('\n')
        val unclosedMatch = Regex("^([\\s\\S]*?)```([a-zA-Z0-9_+#.-]*)\\n([\\s\\S]*)$").matchEntire(tail)
        if (unclosedMatch != null) {
            val proseBefore = unclosedMatch.groupValues[1].trimEnd()
            val lang = unclosedMatch.groupValues[2]
            val partialCode = unclosedMatch.groupValues[3]
            if (proseBefore.isNotBlank()) {
                segments.add(MarkdownSegment.TextBlock(proseBefore))
            }
            segments.add(MarkdownSegment.CodeBlock(lang, partialCode, isClosed = false))
        } else if (tail.isNotBlank()) {
            segments.add(MarkdownSegment.TextBlock(tail))
        }
    }
    if (segments.isEmpty()) {
        segments.add(MarkdownSegment.TextBlock(markdown))
    }
    return segments
}

private fun buildInlineStyledString(
    input: String,
    baseColor: Color,
    inlineCodeBg: Color,
    accentColor: Color
): AnnotatedString = buildAnnotatedString {
    val tokenRegex = Regex("(`[^`]+`|\\*\\*[^*]+\\*\\*|\\*[^*]+\\*)")
    var index = 0
    for (match in tokenRegex.findAll(input)) {
        if (match.range.first > index) {
            withStyle(SpanStyle(color = baseColor)) {
                append(input.substring(index, match.range.first))
            }
        }
        val token = match.value
        when {
            token.startsWith("`") && token.endsWith("`") && token.length >= 2 -> {
                withStyle(
                    SpanStyle(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 13.5.sp,
                        background = inlineCodeBg,
                        color = accentColor
                    )
                ) {
                    append(" ${token.substring(1, token.length - 1)} ")
                }
            }
            token.startsWith("**") && token.endsWith("**") && token.length >= 4 -> {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = baseColor)) {
                    append(token.substring(2, token.length - 2))
                }
            }
            token.startsWith("*") && token.endsWith("*") && token.length >= 2 -> {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = baseColor)) {
                    append(token.substring(1, token.length - 1))
                }
            }
            else -> append(token)
        }
        index = match.range.last + 1
    }
    if (index < input.length) {
        withStyle(SpanStyle(color = baseColor)) {
            append(input.substring(index))
        }
    }
}
