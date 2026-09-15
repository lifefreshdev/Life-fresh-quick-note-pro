package com.example.sync.remote

import com.example.sync.model.PullPage
import com.example.sync.model.PushBatchResult
import com.example.sync.model.RemoteMutation

interface RemoteSyncDataSource {
    suspend fun push(
        uid: String,
        mutations: List<RemoteMutation>
    ): PushBatchResult

    suspend fun pull(
        uid: String,
        cursor: String?,
        pageSize: Int
    ): PullPage
}
