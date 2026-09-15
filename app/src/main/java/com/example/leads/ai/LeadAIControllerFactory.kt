package com.example.leads.ai

import android.content.Context
import com.example.data.database.LeadEntity
import com.example.data.repository.LeadRepository
import com.example.leads.operation.LeadOperationService

/**
 * Creates the complete Leads AI dependency chain in one safe place.
 *
 * CRMViewModel will use this factory instead of manually constructing:
 * LeadOperationService -> LeadAIWorkflow -> LeadAIChatController
 *
 * No database action happens while creating the controller.
 */
object LeadAIControllerFactory {

    fun create(
        context: Context,
        repository: LeadRepository,
        onLeadSaved: (LeadEntity) -> Unit = {}
    ): LeadAIChatController {
        val operationService = LeadOperationService(
            context = context.applicationContext,
            repository = repository,
            onLeadSaved = onLeadSaved
        )

        val workflow = LeadAIWorkflow(
            operationService = operationService
        )

        return LeadAIChatController(
            workflow = workflow
        )
    }
}
