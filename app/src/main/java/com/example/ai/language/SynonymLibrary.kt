package com.example.ai.language

object SynonymLibrary {
    val TODAY_SYNONYMS = setOf("today", "aaj", "aaj ke", "aaj के", "आज")
    val TOMORROW_SYNONYMS = setOf("tomorrow", "kal", "कल")
    val REMINDER_SYNONYMS = setOf("reminder", "remindar", "yaad dilana", "yaad", "dilana", "alert", "alarm")
    val LEAD_SYNONYMS = setOf("lead", "customer", "client", "prospect")
    val ADD_SYNONYMS = setOf("add", "jodo", "banao", "create", "register", "naya", "new", "nayi", "करो", "जोड़ो", "बनाओ")
    val PENDING_SYNONYMS = setOf("pending", "follow-up", "followup", "baaki", "baki", "बाकी")
    val STATUS_SYNONYMS = setOf("status", "stage", "state")
    val WEEKLY_SYNONYMS = setOf("weekly", "hafta", "hafte", "hafta-wari")
    val REPORT_SYNONYMS = setOf("report", "summary", "stats", "analytics")
    
    // Status mappings
    val COMPLETED_VALUES = setOf("complete", "completed", "done", "complete kar", "khatam", "ho gaya", "kar do")
    val PENDING_VALUES = setOf("pending", "baki", "baaki", "unpaid")
}
