package com.example.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.database.LeadEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OcrIntelligenceTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var engine: OcrIntelligenceEngine

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        AIManager.initialize(context)
        database = AppDatabase.getDatabase(context)
        OcrRegistry.clear()
        engine = OcrIntelligenceEngine(
            provider = MlKitOcrProvider(),
            stateMachine = AIManager.stateMachine,
            eventBus = AIManager.eventBus
        )
    }

    @After
    fun tearDown() {
        runTest(testDispatcher) {
            database.leadDao.clearAllLeads()
        }
        OcrRegistry.clear()
        AIManager.shutdown()
    }

    @Test
    fun testFileExtensionValidation() {
        assertTrue(ImageValidator.validateFileExtension("photo.jpg"))
        assertTrue(ImageValidator.validateFileExtension("scan.png"))
        assertTrue(ImageValidator.validateFileExtension("document.webp"))
        assertFalse(ImageValidator.validateFileExtension("report.pdf"))
        assertFalse(ImageValidator.validateFileExtension("notes.txt"))
    }

    @Test
    fun testImageValidation_Corrupted() {
        // Zero dimensions bitmap behaves as invalid
        val mockBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        // Let's force a failure or mock validation
        val result = ImageValidator.validateBitmap(mockBitmap)
        // A 1x1 bitmap where all pixels are same will be caught as EMPTY_IMAGE
        assertFalse(result.isValid)
        assertEquals(ImageValidationError.EMPTY_IMAGE, result.error)
    }

    @Test
    fun testImageValidation_ExtremelyDark() {
        // Bitmap where all pixels are extremely dark (e.g. gray value 5)
        val bitmap = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888)
        for (y in 0 until 50) {
            for (x in 0 until 50) {
                // Vary slightly to not trigger empty image, but keep it extremely dark
                val color = if ((x + y) % 2 == 0) Color.rgb(4, 4, 4) else Color.rgb(6, 6, 6)
                bitmap.setPixel(x, y, color)
            }
        }

        val result = ImageValidator.validateBitmap(bitmap)
        assertFalse(result.isValid)
        assertEquals(ImageValidationError.EXTREMELY_DARK, result.error)
    }

    @Test
    fun testImageValidation_ExtremelyBlurred() {
        // Bitmap with extremely low contrast / variation among adjacent pixels
        val bitmap = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888)
        for (y in 0 until 50) {
            for (x in 0 until 50) {
                // Very high overall intensity (not dark), but almost identical adjacent values (diff < 1)
                val color = if ((x + y) % 2 == 0) Color.rgb(150, 150, 150) else Color.rgb(151, 151, 151)
                bitmap.setPixel(x, y, color)
            }
        }

        val result = ImageValidator.validateBitmap(bitmap)
        assertFalse(result.isValid)
        assertEquals(ImageValidationError.EXTREMELY_BLURRED, result.error)
    }

    @Test
    fun testImageValidation_Success() {
        // Valid high contrast bitmap
        val bitmap = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888)
        for (y in 0 until 50) {
            for (x in 0 until 50) {
                // Alternate black and bright white to create high contrast and non-dark image
                val color = if ((x + y) % 2 == 0) Color.rgb(20, 20, 20) else Color.rgb(220, 220, 220)
                bitmap.setPixel(x, y, color)
            }
        }

        val result = ImageValidator.validateBitmap(bitmap)
        assertTrue(result.isValid)
        assertEquals(ImageValidationError.NONE, result.error)
    }

    @Test
    fun testOcrTextCleanupAndNormalization() {
        val rawText = """
            Ravi Shankar
            +91 9988776655
            ravi.shankar@gmail.com
            Diabetes, High BP
            Please call in the evening.
            Follow-Up: tomorrow
        """.trimIndent()

        val cleanText = engine.cleanAndPreprocessOcrText(rawText)
        
        assertTrue(cleanText.contains("Name: Ravi Shankar"))
        assertTrue(cleanText.contains("Phone: 919988776655"))
        assertTrue(cleanText.contains("Email: ravi.shankar@gmail.com"))
        assertTrue(cleanText.contains("Disease: Diabetes, High BP"))
        assertTrue(cleanText.contains("Follow-Up: tomorrow"))
    }

    @Test
    fun testOcrProvider_CustomRegister() = runTest(testDispatcher) {
        val bitmap = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888)
        val expectedText = "Name: Aman Gupta\nPhone: 9876543210\nDisease: Thyroid"
        
        OcrRegistry.register(bitmap, expectedText)
        val provider = MlKitOcrProvider()
        val text = provider.recognizeText(bitmap)
        assertEquals(expectedText, text)
    }

    @Test
    fun testEntityExtraction_Success() = runTest(testDispatcher) {
        val bitmap = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888)
        // High contrast to pass image validation
        for (y in 0 until 50) {
            for (x in 0 until 50) {
                val color = if ((x + y) % 2 == 0) Color.rgb(20, 20, 20) else Color.rgb(220, 220, 220)
                bitmap.setPixel(x, y, color)
            }
        }

        val rawText = """
            Name: Ramesh Khanna
            Phone: 9876543211
            Email: ramesh@example.com
            Disease: Asthma
            Notes: Regular checkup.
            Follow-Up: tomorrow
        """.trimIndent()

        OcrRegistry.register(bitmap, rawText)

        val summary = engine.processImage(bitmap)

        assertEquals(1, summary.totalImages)
        assertEquals(1, summary.validatedCount)
        assertEquals(1, summary.importedCount)
        assertEquals(0, summary.failedCount)

        val leads = database.leadDao.getAllLeadsList()
        val ramesh = leads.find { it.name == "Ramesh Khanna" }
        assertNotNull(ramesh)
        assertEquals("9876543211", ramesh?.mobile)
        assertEquals("Regular checkup.", ramesh?.notes)
        assertEquals(LocalDate.now().plusDays(1).toString(), ramesh?.reminderDate)
    }

    @Test
    fun testDuplicateDetection() = runTest(testDispatcher) {
        // Pre-insert a client with same mobile
        val existingClient = LeadEntity(
            id = UUID.randomUUID().toString(),
            name = "Ramesh Khanna",
            mobile = "9876543211",
            diseases = "[]",
            otherDisease = "",
            relation = "Self",
            otherRelation = "",
            status = "Pending",
            reminderDate = "",
            reminderTime = "",
            reminderNote = "",
            reminderStatus = "Pending"
        )
        database.leadDao.insertLead(existingClient)

        // Process same client via OCR
        val bitmap = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888)
        for (y in 0 until 50) {
            for (x in 0 until 50) {
                val color = if ((x + y) % 2 == 0) Color.rgb(20, 20, 20) else Color.rgb(220, 220, 220)
                bitmap.setPixel(x, y, color)
            }
        }

        val rawText = """
            Name: Ramesh Khanna
            Phone: 9876543211
        """.trimIndent()

        OcrRegistry.register(bitmap, rawText)

        val summary = engine.processImage(bitmap)

        assertEquals(1, summary.totalImages)
        assertEquals(1, summary.validatedCount)
        assertEquals(0, summary.importedCount)
        assertEquals(1, summary.duplicateCount)
    }

    @Test
    fun testBulkImageImport() = runTest(testDispatcher) {
        val bitmap1 = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888)
        val bitmap2 = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888)

        for (y in 0 until 50) {
            for (x in 0 until 50) {
                val color = if ((x + y) % 2 == 0) Color.rgb(20, 20, 20) else Color.rgb(220, 220, 220)
                bitmap1.setPixel(x, y, color)
                bitmap2.setPixel(x, y, color)
            }
        }

        val rawText1 = "Name: Alice\nPhone: 9988776601"
        val rawText2 = "Name: Bob\nPhone: 9988776602"

        OcrRegistry.register(bitmap1, rawText1)
        OcrRegistry.register(bitmap2, rawText2)

        val summary = engine.processImages(listOf(bitmap1, bitmap2))

        assertEquals(2, summary.totalImages)
        assertEquals(2, summary.validatedCount)
        assertEquals(2, summary.importedCount)
        assertEquals(0, summary.duplicateCount)

        val leads = database.leadDao.getAllLeadsList()
        assertTrue(leads.any { it.name == "Alice" })
        assertTrue(leads.any { it.name == "Bob" })
    }
}
