package com.example.ai.chat.formatter

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AIMessageFormatterTest {

    @Test
    fun testBoldTextFormatting() {
        val input = "Here is **important** information and __critical__ advice."
        val annotated = AIMessageFormatter.formatParagraph(input)
        val text = annotated.text

        // Asterisks and underscores should be removed from the visible string
        assertEquals("Here is important information and critical advice.", text)

        // Verify span styles applied
        val spans = annotated.spanStyles
        assertEquals(2, spans.size)
        assertEquals(FontWeight.Bold, spans[0].item.fontWeight)
        assertEquals(8, spans[0].start)
        assertEquals(17, spans[0].end) // "important"

        assertEquals(FontWeight.Bold, spans[1].item.fontWeight)
        assertEquals(34, spans[1].start)
        assertEquals(42, spans[1].end) // "critical"
    }

    @Test
    fun testBulletListFormatting() {
        val input = "* First point\n* Second point\n- Third point\n+ Fourth point"
        val annotated = AIMessageFormatter.formatParagraph(input)
        val lines = annotated.text.lines()

        assertEquals(4, lines.size)
        assertEquals("• First point", lines[0])
        assertEquals("• Second point", lines[1])
        assertEquals("• Third point", lines[2])
        assertEquals("• Fourth point", lines[3])
    }

    @Test
    fun testHeadingFormatting() {
        val input = "### Overview\nThis is a standard paragraph.\n# Main Title\nContent below title."
        val annotated = AIMessageFormatter.formatParagraph(input)
        val lines = annotated.text.lines()

        assertEquals(4, lines.size)
        // Raw '#' tokens should be stripped
        assertEquals("Overview", lines[0])
        assertEquals("This is a standard paragraph.", lines[1])
        assertEquals("Main Title", lines[2])
        assertEquals("Content below title.", lines[3])

        // Verify bold styling on headings
        val heading1Span = annotated.spanStyles.find { it.start == 0 && it.end == 8 }
        assertNotNull(heading1Span)
        assertEquals(FontWeight.Bold, heading1Span?.item?.fontWeight)
    }

    @Test
    fun testInlineCodeFormatting() {
        val input = "Run `val result = calculate()` to get output."
        val annotated = AIMessageFormatter.formatParagraph(input)

        // Raw backticks should be removed from visible text
        assertEquals("Run val result = calculate() to get output.", annotated.text)

        val codeSpan = annotated.spanStyles.find { it.item.fontFamily == FontFamily.Monospace }
        assertNotNull(codeSpan)
        assertEquals(4, codeSpan?.start)
        assertEquals(28, codeSpan?.end) // "val result = calculate()"
    }

    @Test
    fun testFencedCodeBlocksPreserveVerbatimContent() {
        val rawMessage = """
            Here is the code snippet:
            ```kotlin
            fun compute(a: Int, b: Int): Int {
                // Symbols: * and | and # should NOT be altered
                return a * b | 0x01
            }
            ```
            Hope this helps!
        """.trimIndent()

        val blocks = AIMessageFormatter.parseMessageBlocks(rawMessage)
        assertEquals(3, blocks.size)

        assertTrue(blocks[0] is FormattedBlock.Paragraph)
        assertEquals("Here is the code snippet:", (blocks[0] as FormattedBlock.Paragraph).text.text)

        assertTrue(blocks[1] is FormattedBlock.CodeBlock)
        val codeBlock = blocks[1] as FormattedBlock.CodeBlock
        assertEquals("kotlin", codeBlock.language)
        val expectedCode = "fun compute(a: Int, b: Int): Int {\n    // Symbols: * and | and # should NOT be altered\n    return a * b | 0x01\n}"
        assertEquals(expectedCode, codeBlock.code)

        assertTrue(blocks[2] is FormattedBlock.Paragraph)
        assertEquals("Hope this helps!", (blocks[2] as FormattedBlock.Paragraph).text.text)
    }

    @Test
    fun testOrdinaryTextContainingSymbolsIsNotCorrupted() {
        val input = "Calculation: 5 * 10 = 50. Options: Red | Green | Blue. Path: root/sub_dir/item."
        val annotated = AIMessageFormatter.formatParagraph(input)

        // Single * (not pairs), |, and _ (not pairs) must be preserved verbatim
        assertEquals(input, annotated.text)
    }

    @Test
    fun testHorizontalRuleCollapse() {
        val input = "Before divider\n---\nAfter divider"
        val annotated = AIMessageFormatter.formatParagraph(input)
        val lines = annotated.text.lines()

        // HR line should be empty/collapsed
        assertEquals(3, lines.size)
        assertEquals("Before divider", lines[0])
        assertEquals("", lines[1])
        assertEquals("After divider", lines[2])
    }
}
