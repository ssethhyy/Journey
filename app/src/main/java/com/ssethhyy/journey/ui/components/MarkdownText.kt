/*
 * Feature Module: UI Components
 * File: MarkdownText.kt
 * Description: Renders the simple Markdown used in Journey's release notes (headings, bullet and numbered lists,
 * quotes, rules, bold, italic, code and links) with the app's type scale. Not a full Markdown engine.
 */

package com.ssethhyy.journey.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/** A block of the parsed notes. */
sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Bullet(val text: String, val marker: String) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data object Rule : MdBlock
}

object Markdown {
    private val HEADING = Regex("^(#{1,6})\\s+(.*)$")
    private val BULLET = Regex("^\\s*[-*+]\\s+(.*)$")
    private val NUMBERED = Regex("^\\s*(\\d+)[.)]\\s+(.*)$")

    fun parse(source: String): List<MdBlock> {
        val blocks = mutableListOf<MdBlock>()
        val paragraph = StringBuilder()
        val quote = StringBuilder()
        fun flush() {
            if (paragraph.isNotBlank()) blocks += MdBlock.Paragraph(paragraph.toString().trim())
            if (quote.isNotBlank()) blocks += MdBlock.Quote(quote.toString().trim())
            paragraph.clear()
            quote.clear()
        }
        source.replace("\r\n", "\n").lines().forEach { raw ->
            val line = raw.trimEnd()
            when {
                line.isBlank() -> flush()
                line.trim().matches(Regex("^(-{3,}|\\*{3,}|_{3,})$")) -> { flush(); blocks += MdBlock.Rule }
                HEADING.matches(line) -> { flush(); HEADING.find(line)!!.let { blocks += MdBlock.Heading(it.groupValues[1].length, it.groupValues[2]) } }
                line.trimStart().startsWith(">") -> { if (paragraph.isNotBlank()) flush(); quote.append(line.trimStart().removePrefix(">").trim()).append(' ') }
                BULLET.matches(line) -> { flush(); blocks += MdBlock.Bullet(BULLET.find(line)!!.groupValues[1], "•") }
                NUMBERED.matches(line) -> { flush(); NUMBERED.find(line)!!.let { blocks += MdBlock.Bullet(it.groupValues[2], "${it.groupValues[1]}.") } }
                // A wrapped bullet continues the bullet before it.
                raw.startsWith("  ") && blocks.lastOrNull() is MdBlock.Bullet && paragraph.isEmpty() -> {
                    val last = blocks.removeAt(blocks.lastIndex) as MdBlock.Bullet
                    blocks += last.copy(text = last.text + " " + line.trim())
                }
                else -> paragraph.append(line.trim()).append(' ')
            }
        }
        flush()
        return blocks
    }

    /** Bold, italic, inline code and [links](url) (shown as their text). */
    fun inline(text: String, codeBackground: androidx.compose.ui.graphics.Color, linkColor: androidx.compose.ui.graphics.Color): AnnotatedString = buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            when {
                text.startsWith("**", i) && text.indexOf("**", i + 2) > 0 -> {
                    val end = text.indexOf("**", i + 2)
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(inline(text.substring(i + 2, end), codeBackground, linkColor)) }
                    i = end + 2
                }
                (text[i] == '*' || text[i] == '_') && text.indexOf(text[i], i + 1) > i + 1 -> {
                    val end = text.indexOf(text[i], i + 1)
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(text.substring(i + 1, end)) }
                    i = end + 1
                }
                text[i] == '`' && text.indexOf('`', i + 1) > 0 -> {
                    val end = text.indexOf('`', i + 1)
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground)) { append(text.substring(i + 1, end)) }
                    i = end + 1
                }
                text[i] == '[' && text.indexOf("](", i) > 0 && text.indexOf(')', text.indexOf("](", i)) > 0 -> {
                    val mid = text.indexOf("](", i)
                    val end = text.indexOf(')', mid)
                    withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) { append(text.substring(i + 1, mid)) }
                    i = end + 1
                }
                else -> {
                    append(text[i])
                    i++
                }
            }
        }
    }
}

@Composable
fun MarkdownText(source: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val code = colors.surfaceContainerHighest
    val link = colors.primary
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Markdown.parse(source).forEach { block ->
            when (block) {
                is MdBlock.Heading -> Text(
                    Markdown.inline(block.text, code, link),
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.titleLargeEmphasized
                        2 -> MaterialTheme.typography.titleMediumEmphasized
                        else -> MaterialTheme.typography.titleSmallEmphasized
                    },
                    color = if (block.level <= 2) colors.onSurface else colors.primary,
                    modifier = Modifier.padding(top = if (block.level <= 3) 8.dp else 0.dp),
                )
                is MdBlock.Bullet -> Row {
                    Text(block.marker, style = MaterialTheme.typography.bodyMedium, color = colors.primary, modifier = Modifier.width(20.dp))
                    Text(Markdown.inline(block.text, code, link), style = MaterialTheme.typography.bodyMedium)
                }
                is MdBlock.Quote -> Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                    Box(Modifier.width(4.dp).fillMaxHeight().background(colors.tertiary, RoundedCornerShape(2.dp)))
                    Spacer(Modifier.width(10.dp))
                    Text(Markdown.inline(block.text, code, link), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                }
                is MdBlock.Paragraph -> Text(Markdown.inline(block.text, code, link), style = MaterialTheme.typography.bodyMedium)
                MdBlock.Rule -> HorizontalDivider(Modifier.padding(vertical = 4.dp))
            }
        }
    }
}
