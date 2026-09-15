package com.example.ai.chat.formatter

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

sealed interface FormattedBlock {
    data class Paragraph(val text: AnnotatedString) : FormattedBlock
    data class CodeBlock(val code: String, val language: String? = null) : FormattedBlock
}

/**
 * Dependency-free, lightweight Markdown formatter tailored for assistant mobile chat responses.
 *
 * Capabilities:
 * - Bold: Converts **text** and __text__ to bold AnnotatedString spans.
 * - Bullets: Converts line-leading *, -, + into clean bullet points (•).
 * - Headings: Strips raw # markers (e.g. ### Title) and renders the heading text in bold.
 * - Inline code: Encloses `code` in Monospace font family.
 * - Fenced code blocks: Preserves code verbatim within FormattedBlock.CodeBlock without corrupting symbols.
 * - Literal symbols: Preserves *, |, _, `, etc. when not part of valid markdown syntax.
 */
object AIMessageFormatter {

    private val HEADING_REGEX = Regex("""^(#{1,6})\s+(.*)$""")
    private val BULLET_REGEX = Regex("""^(\s*)[*+-]\s+(.*)$""")
    private val HR_REGEX = Regex("""^\s*([*\-_])\s*(\1\s*){2,}$""")

    /**
     * Splits raw assistant message into formatted blocks (paragraphs and fenced code blocks).
     */
    fun parseMessageBlocks(content: String): List<FormattedBlock> {
        if (content.isBlank()) return emptyList()

        val lines = content.lines()
        val blocks = mutableListOf<FormattedBlock>()

        var inCodeBlock = false
        var codeBlockLanguage: String? = null
        val codeLines = mutableListOf<String>()
        val paragraphLines = mutableListOf<String>()

        fun flushParagraph() {
            if (paragraphLines.isNotEmpty()) {
                val paragraphText = paragraphLines.joinToString("\n")
                if (paragraphText.isNotBlank()) {
                    blocks.add(FormattedBlock.Paragraph(formatParagraph(paragraphText)))
                }
                paragraphLines.clear()
            }
        }

        for (line in lines) {
            val trimmed = line.trimEnd()
            if (!inCodeBlock) {
                if (trimmed.startsWith("```")) {
                    flushParagraph()
                    inCodeBlock = true
                    val lang = trimmed.removePrefix("```").trim()
                    codeBlockLanguage = if (lang.isNotBlank()) lang else null
                    codeLines.clear()
                } else {
                    paragraphLines.add(line)
                }
            } else {
                if (trimmed.startsWith("```")) {
                    blocks.add(
                        FormattedBlock.CodeBlock(
                            code = codeLines.joinToString("\n"),
                            language = codeBlockLanguage
                        )
                    )
                    codeLines.clear()
                    inCodeBlock = false
                    codeBlockLanguage = null
                } else {
                    codeLines.add(line)
                }
            }
        }

        if (inCodeBlock && codeLines.isNotEmpty()) {
            blocks.add(
                FormattedBlock.CodeBlock(
                    code = codeLines.joinToString("\n"),
                    language = codeBlockLanguage
                )
            )
        } else {
            flushParagraph()
        }

        return blocks
    }

    /**
     * Formats paragraph text line-by-line into an AnnotatedString.
     */
    fun formatParagraph(raw: String): AnnotatedString {
        val lines = raw.lines()
        return buildAnnotatedString {
            for ((index, line) in lines.withIndex()) {
                if (index > 0) {
                    append("\n")
                }
                formatLine(line, this)
            }
        }
    }

    private fun formatLine(line: String, builder: AnnotatedString.Builder) {
        // 1. Check Horizontal Rule (e.g. --- or *** or ___)
        if (HR_REGEX.matches(line)) {
            return
        }

        // 2. Check Heading: e.g. "### My Heading"
        val headingMatch = HEADING_REGEX.matchEntire(line)
        if (headingMatch != null) {
            val headingContent = headingMatch.groupValues[2]
            builder.pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
            formatInline(headingContent, builder)
            builder.pop()
            return
        }

        // 3. Check Bullet: e.g. "  * Bullet text" or "- Bullet text"
        val bulletMatch = BULLET_REGEX.matchEntire(line)
        if (bulletMatch != null) {
            val indent = bulletMatch.groupValues[1]
            val bulletContent = bulletMatch.groupValues[2]
            builder.append("$indent• ")
            formatInline(bulletContent, builder)
            return
        }

        // 4. Regular line
        formatInline(line, builder)
    }

    /**
     * Scans inline string and applies bold, inline code, and literal text preservation.
     */
    fun formatInline(text: String, builder: AnnotatedString.Builder) {
        var i = 0
        val len = text.length

        while (i < len) {
            // Check for inline code: `code`
            if (text[i] == '`' && (i + 1 < len) && text[i + 1] != '`') {
                val closeIndex = text.indexOf('`', i + 1)
                if (closeIndex != -1) {
                    val codeSnippet = text.substring(i + 1, closeIndex)
                    builder.pushStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium
                        )
                    )
                    builder.append(codeSnippet)
                    builder.pop()
                    i = closeIndex + 1
                    continue
                }
            }

            // Check for bold: **text**
            if (i + 1 < len && text[i] == '*' && text[i + 1] == '*') {
                val closeIndex = text.indexOf("**", i + 2)
                if (closeIndex != -1 && closeIndex > i + 2) {
                    val boldContent = text.substring(i + 2, closeIndex)
                    builder.pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    formatInline(boldContent, builder)
                    builder.pop()
                    i = closeIndex + 2
                    continue
                }
            }

            // Check for bold: __text__
            if (i + 1 < len && text[i] == '_' && text[i + 1] == '_') {
                val closeIndex = text.indexOf("__", i + 2)
                if (closeIndex != -1 && closeIndex > i + 2) {
                    val boldContent = text.substring(i + 2, closeIndex)
                    builder.pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    formatInline(boldContent, builder)
                    builder.pop()
                    i = closeIndex + 2
                    continue
                }
            }

            // Normal character (including standalone *, |, _, etc.)
            builder.append(text[i])
            i++
        }
    }
}
