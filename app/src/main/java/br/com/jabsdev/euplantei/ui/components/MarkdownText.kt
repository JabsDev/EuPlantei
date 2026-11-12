package br.com.jabsdev.euplantei.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private sealed interface MdBlock {
    data class Heading(val text: String, val level: Int) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class Bullet(val text: String) : MdBlock
    data class Numbered(val number: String, val text: String) : MdBlock
}

/**
 * Renderizador de markdown básico para o conselho do LLM: títulos, listas,
 * parágrafos e **negrito**. Evita uma dependência pesada e mantém a seleção de texto.
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    val blocks = remember(markdown) { parseMarkdown(markdown) }
    SelectionContainer {
        Column(modifier = modifier.fillMaxWidth()) {
            blocks.forEachIndexed { index, block ->
                when (block) {
                    is MdBlock.Heading -> Text(
                        text = inline(block.text),
                        style = when (block.level) {
                            1 -> MaterialTheme.typography.headlineSmall
                            2 -> MaterialTheme.typography.titleLarge
                            else -> MaterialTheme.typography.titleMedium
                        },
                        color = color,
                        modifier = Modifier.padding(top = if (index == 0) 0.dp else 10.dp),
                    )
                    is MdBlock.Paragraph -> Text(
                        text = inline(block.text),
                        style = textStyle,
                        color = color,
                    )
                    is MdBlock.Bullet -> Text(
                        text = buildAnnotatedString {
                            append("•  ")
                            append(inline(block.text))
                        },
                        style = textStyle,
                        color = color,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                    is MdBlock.Numbered -> Text(
                        text = buildAnnotatedString {
                            withStyleBold(block.number + "  ")
                            append(inline(block.text))
                        },
                        style = textStyle,
                        color = color,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
                if (index != blocks.lastIndex) Spacer(Modifier.height(6.dp))
            }
        }
    }
}

private fun parseMarkdown(markdown: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val paragraph = StringBuilder()

    fun flush() {
        if (paragraph.isNotBlank()) {
            blocks += MdBlock.Paragraph(paragraph.toString().trim())
            paragraph.clear()
        }
    }

    markdown.lines().forEach { rawLine ->
        val line = rawLine.trimEnd()
        val trimmed = line.trimStart()
        when {
            trimmed.isBlank() -> flush()
            trimmed.startsWith("### ") -> {
                flush()
                blocks += MdBlock.Heading(trimmed.removePrefix("### "), 3)
            }
            trimmed.startsWith("## ") -> {
                flush()
                blocks += MdBlock.Heading(trimmed.removePrefix("## "), 2)
            }
            trimmed.startsWith("# ") -> {
                flush()
                blocks += MdBlock.Heading(trimmed.removePrefix("# "), 1)
            }
            trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("• ") -> {
                flush()
                blocks += MdBlock.Bullet(trimmed.drop(2))
            }
            else -> {
                val numbered = Regex("^(\\d+)[.)]\\s+(.+)$").find(trimmed)
                if (numbered != null) {
                    flush()
                    blocks += MdBlock.Numbered(
                        number = numbered.groupValues[1] + ".",
                        text = numbered.groupValues[2],
                    )
                } else {
                    if (paragraph.isNotEmpty()) paragraph.append(' ')
                    paragraph.append(trimmed)
                }
            }
        }
    }
    flush()
    return blocks
}

private fun inline(text: String): AnnotatedString = buildAnnotatedString {
    val regex = Regex("\\*\\*(.+?)\\*\\*")
    var cursor = 0
    regex.findAll(text).forEach { match ->
        append(text.substring(cursor, match.range.first))
        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
            append(match.groupValues[1])
        }
        cursor = match.range.last + 1
    }
    append(text.substring(cursor))
}

private fun androidx.compose.ui.text.AnnotatedString.Builder.withStyleBold(text: String) {
    pushStyle(SpanStyle(fontWeight = FontWeight.SemiBold))
    append(text)
    pop()
}
