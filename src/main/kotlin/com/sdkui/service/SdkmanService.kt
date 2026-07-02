package com.sdkui.service

import com.sdkui.model.Sdk
import com.sdkui.model.Version
import kotlinx.coroutines.flow.Flow

interface SdkmanService {
    suspend fun listCandidates(): Result<List<Sdk>>
    suspend fun getCurrentDefaults(): Result<Map<String, String>>
    suspend fun listVersions(candidate: String, vendor: String? = null): Result<List<Version>>
    suspend fun setDefault(candidate: String, identifier: String): Result<Unit>
    fun install(candidate: String, identifier: String): Flow<String>
    fun uninstall(candidate: String, identifier: String): Flow<String>
}
