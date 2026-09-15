package com.example.ai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AIKnowledgeCacheEngineTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        AIManager.initialize(context)
        database = AppDatabase.getDatabase(context)
    }

    @After
    fun tearDown() {
        runTest(testDispatcher) {
            AIKnowledgeCacheEngine.clearCache(context)
        }
        AIManager.shutdown()
    }

    @Test
    fun testKnowledgeInsertionAndRetrieval() = runTest(testDispatcher) {
        val entry = AIKnowledgeCacheEngine.saveKnowledge(
            context = context,
            topic = "Vitamin D",
            aliases = listOf("Vit D", "25 OH Vit D"),
            question = "What is Vitamin D?",
            answer = "Vitamin D is a fat-soluble vitamin that helps absorb calcium.",
            source = KnowledgeSource.WHO,
            sourceUrl = "https://who.int/vitamind",
            expiryDurationMs = 30L * 24 * 60 * 60 * 1000,
            confidence = KnowledgeConfidence.VERY_HIGH,
            language = "en",
            tags = listOf("vitamins", "nutrition")
        )

        assertNotNull(entry.id)
        assertEquals("Vitamin D", entry.topic)
        assertEquals(1, entry.version)
        assertEquals(KnowledgeSource.WHO, entry.source)
        assertEquals("https://who.int/vitamind", entry.sourceUrl)
        assertEquals("en", entry.language)
        assertEquals(2, entry.aliases.size)
        assertTrue(entry.tags.contains("nutrition"))

        // Verify direct retrieve
        val result = AIKnowledgeCacheEngine.search(context, "Vitamin D")
        assertTrue(result.isCacheHit)
        assertNotNull(result.entry)
        assertEquals("Vitamin D", result.entry?.topic)
        assertEquals(KnowledgeRefreshState.FRESH, result.refreshState)
    }

    @Test
    fun testAliasLookup() = runTest(testDispatcher) {
        AIKnowledgeCacheEngine.saveKnowledge(
            context = context,
            topic = "Vitamin D",
            aliases = listOf("Vit D", "25 OH Vit D", "Vitamin D Test"),
            question = "What is Vitamin D?",
            answer = "Vitamin D is a fat-soluble vitamin.",
            source = KnowledgeSource.MEDICAL_GUIDELINE
        )

        // Resolve by alias "Vit D"
        val res1 = AIKnowledgeCacheEngine.search(context, "Vit D")
        assertTrue(res1.isCacheHit)
        assertEquals("Vitamin D", res1.entry?.topic)

        // Resolve by alias "Vitamin D Test"
        val res2 = AIKnowledgeCacheEngine.search(context, "Vitamin D Test")
        assertTrue(res2.isCacheHit)
        assertEquals("Vitamin D", res2.entry?.topic)
    }

    @Test
    fun testVersionHistory() = runTest(testDispatcher) {
        // Save Version 1
        val entry1 = AIKnowledgeCacheEngine.saveKnowledge(
            context = context,
            topic = "COVID-19",
            aliases = emptyList(),
            question = "What is COVID-19?",
            answer = "COVID-19 is an infectious disease caused by SARS-CoV-2 virus.",
            source = KnowledgeSource.WHO
        )
        assertEquals(1, entry1.version)

        // Save Version 2 (Same topic and question)
        val entry2 = AIKnowledgeCacheEngine.saveKnowledge(
            context = context,
            topic = "COVID-19",
            aliases = emptyList(),
            question = "What is COVID-19?",
            answer = "COVID-19 is a respiratory disease first identified in 2019.",
            source = KnowledgeSource.RESEARCH_PAPER
        )
        assertEquals(2, entry2.version)

        // Default search should yield the latest version (version 2)
        val defaultSearch = AIKnowledgeCacheEngine.search(context, "COVID-19")
        assertTrue(defaultSearch.isCacheHit)
        assertEquals(2, defaultSearch.entry?.version)
        assertEquals("COVID-19 is a respiratory disease first identified in 2019.", defaultSearch.entry?.answer)

        // Specific version search (version 1)
        val specSearch = AIKnowledgeCacheEngine.search(context, "COVID-19", version = 1)
        assertTrue(specSearch.isCacheHit)
        assertEquals(1, specSearch.entry?.version)
        assertEquals("COVID-19 is an infectious disease caused by SARS-CoV-2 virus.", specSearch.entry?.answer)
    }

    @Test
    fun testExpiryManagement() = runTest(testDispatcher) {
        // Create an already expired entry
        AIKnowledgeCacheEngine.saveKnowledge(
            context = context,
            topic = "Flu Shot",
            aliases = emptyList(),
            question = "When to get flu shot?",
            answer = "Get it annually in Autumn.",
            source = KnowledgeSource.GOVERNMENT,
            expiryDurationMs = -5000L // Negative duration -> expired
        )

        val searchResult = AIKnowledgeCacheEngine.search(context, "Flu Shot")
        assertTrue(searchResult.isCacheHit)
        assertNotNull(searchResult.entry)
        assertEquals(KnowledgeRefreshState.EXPIRED, searchResult.refreshState)
    }

    @Test
    fun testUsageTracking() = runTest(testDispatcher) {
        val entry = AIKnowledgeCacheEngine.saveKnowledge(
            context = context,
            topic = "Calcium",
            aliases = emptyList(),
            question = "What is Calcium?",
            answer = "Mineral needed for strong bones.",
            source = KnowledgeSource.MANUAL_ENTRY
        )
        assertEquals(0, entry.usageCount)

        // Search twice to increment usage
        AIKnowledgeCacheEngine.search(context, "Calcium")
        AIKnowledgeCacheEngine.search(context, "Calcium")

        val metrics = AIKnowledgeCacheEngine.getMetrics(context)
        val mostUsed = metrics["mostUsed"] as List<*>
        assertTrue(mostUsed.isNotEmpty())
        val trackedEntry = mostUsed[0] as KnowledgeEntry
        assertEquals("Calcium", trackedEntry.topic)
        assertEquals(2, trackedEntry.usageCount)
        assertTrue(trackedEntry.lastUsedTimestamp >= entry.createdTimestamp)
    }

    @Test
    fun testLanguageMatching() = runTest(testDispatcher) {
        // Insert English version
        AIKnowledgeCacheEngine.saveKnowledge(
            context = context,
            topic = "Water Intake",
            aliases = emptyList(),
            question = "How much water to drink?",
            answer = "Drink 8 glasses a day.",
            source = KnowledgeSource.OFFICIAL_WEBSITE,
            language = "en"
        )

        // Insert Spanish version
        AIKnowledgeCacheEngine.saveKnowledge(
            context = context,
            topic = "Water Intake",
            aliases = emptyList(),
            question = "How much water to drink?",
            answer = "Beber 8 vasos al dia.",
            source = KnowledgeSource.OFFICIAL_WEBSITE,
            language = "es"
        )

        // Filter Spanish
        val spanishSearch = AIKnowledgeCacheEngine.search(context, "Water Intake", language = "es")
        assertTrue(spanishSearch.isCacheHit)
        assertEquals("es", spanishSearch.entry?.language)
        assertEquals("Beber 8 vasos al dia.", spanishSearch.entry?.answer)

        // Filter English
        val englishSearch = AIKnowledgeCacheEngine.search(context, "Water Intake", language = "en")
        assertTrue(englishSearch.isCacheHit)
        assertEquals("en", englishSearch.entry?.language)
        assertEquals("Drink 8 glasses a day.", englishSearch.entry?.answer)
    }

    @Test
    fun testEventBusIntegration() = runTest(testDispatcher) {
        var savedCount = 0
        var loadedCount = 0
        var searchCompletedCount = 0
        var versionCreatedCount = 0

        AIEventBus.subscribe<KnowledgeSavedEvent>(backgroundScope) {
            savedCount++
        }
        AIEventBus.subscribe<KnowledgeLoadedEvent>(backgroundScope) {
            loadedCount++
        }
        AIEventBus.subscribe<KnowledgeSearchCompletedEvent>(backgroundScope) {
            searchCompletedCount++
        }
        AIEventBus.subscribe<KnowledgeVersionCreatedEvent>(backgroundScope) {
            versionCreatedCount++
        }

        // Trigger Save Version 1
        AIKnowledgeCacheEngine.saveKnowledge(
            context = context,
            topic = "Iron",
            aliases = emptyList(),
            question = "What is Iron?",
            answer = "Essential nutrient.",
            source = KnowledgeSource.FUTURE_LLM
        )

        // Trigger Save Version 2
        AIKnowledgeCacheEngine.saveKnowledge(
            context = context,
            topic = "Iron",
            aliases = emptyList(),
            question = "What is Iron?",
            answer = "Essential nutrient for hemoglobin.",
            source = KnowledgeSource.FUTURE_LLM
        )

        // Trigger Search
        AIKnowledgeCacheEngine.search(context, "Iron")

        kotlinx.coroutines.yield()

        assertEquals(2, savedCount)
        assertEquals(1, versionCreatedCount) // only trigger on version > 1
        assertEquals(1, loadedCount)
        assertEquals(1, searchCompletedCount)
    }

    @Test
    fun testStateMachineTransitions() = runTest(testDispatcher) {
        AIKnowledgeCacheEngine.saveKnowledge(
            context = context,
            topic = "Potassium",
            aliases = emptyList(),
            question = "What is Potassium?",
            answer = "K element.",
            source = KnowledgeSource.WHO
        )

        AIManager.stateMachine.reset()

        AIKnowledgeCacheEngine.search(context, "Potassium")

        val history = AIManager.stateMachine.transitionHistory
        assertTrue("History should contain KNOWLEDGE_SEARCH: $history", history.contains(AIState.KNOWLEDGE_SEARCH))
        assertTrue("History should contain KNOWLEDGE_LOADED: $history", history.contains(AIState.KNOWLEDGE_LOADED))
        assertTrue("History should contain COMPLETED: $history", history.contains(AIState.COMPLETED))
        assertEquals(AIState.IDLE, AIManager.stateMachine.currentState.value)
    }

    @Test
    fun testSearchPerformanceTargets() = runTest(testDispatcher) {
        AIKnowledgeCacheEngine.saveKnowledge(
            context = context,
            topic = "Sodium",
            aliases = emptyList(),
            question = "What is Sodium?",
            answer = "Salt element.",
            source = KnowledgeSource.OFFICIAL_WEBSITE
        )

        val startTime = System.nanoTime()
        val result = AIKnowledgeCacheEngine.search(context, "Sodium")
        val durationMs = (System.nanoTime() - startTime) / 1_000_000.0

        AILogger.i("AIKnowledgeCacheEngineTest", "Knowledge lookup took: $durationMs ms")
        assertTrue(result.isCacheHit)
        assertTrue("Lookup must be under 50 ms target", durationMs < 50.0)
    }
}
