package com.example.data.network

import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Url

interface AIProxyService {
    @POST
    suspend fun executeProxyQuery(
        @Url url: String,
        @Body request: AIProxyRequest
    ): AIProxyResponse
}
