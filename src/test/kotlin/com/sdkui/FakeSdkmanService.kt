package com.sdkui

import com.sdkui.model.Sdk
import com.sdkui.model.SdkmanUpdateStatus
import com.sdkui.model.Version
import com.sdkui.model.VersionStatus
import com.sdkui.service.SdkmanService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class FakeSdkmanService : SdkmanService {
    var candidatesResult: Result<List<Sdk>> = Result.success(CANDIDATES)
    var defaultsResult: Result<Map<String, String>> = Result.success(DEFAULTS)
    var updateStatusResult: Result<SdkmanUpdateStatus> = Result.success(UP_TO_DATE)
    var versionsResult: Result<List<Version>> = Result.success(JAVA_VERSIONS)
    var setDefaultResult: Result<Unit> = Result.success(Unit)
    var selfUpdateLines: List<String> = listOf("Updating SDKMAN...", "Done!")
    var installLines: List<String> = listOf("Downloading...", "Installing...", "Done!")
    var uninstallLines: List<String> = listOf("Uninstalling...", "Done!")

    override suspend fun listCandidates() = candidatesResult

    override suspend fun getCurrentDefaults() = defaultsResult

    override suspend fun checkForUpdate() = updateStatusResult

    override suspend fun listVersions(
        candidate: String,
        vendor: String?,
    ) = versionsResult

    override suspend fun setDefault(
        candidate: String,
        identifier: String,
    ) = setDefaultResult

    override fun selfUpdate(): Flow<String> = flowOf(*selfUpdateLines.toTypedArray())

    override fun install(
        candidate: String,
        identifier: String?,
    ): Flow<String> = flowOf(*installLines.toTypedArray())

    override fun uninstall(
        candidate: String,
        identifier: String,
    ): Flow<String> = flowOf(*uninstallLines.toTypedArray())

    companion object {
        val UP_TO_DATE = SdkmanUpdateStatus("5.23.0", "5.23.0", "0.7.34", "0.7.34")
        val CANDIDATES =
            listOf(
                Sdk("java", "21.0.11-tem", "Java Platform, Standard Edition"),
                Sdk("kotlin", "2.1.20", "The Kotlin Programming Language"),
                Sdk("maven", "3.9.9", "Apache Maven"),
                Sdk("gradle", "8.13", "Gradle Build Tool"),
                Sdk("groovy", "4.0.24", "The Groovy Programming Language"),
            )
        val DEFAULTS = mapOf("java" to "21.0.11-tem", "kotlin" to "2.1.20")
        val JAVA_VERSIONS =
            listOf(
                Version("26.0.1", "Temurin", "26.0.1-tem", VersionStatus.AVAILABLE),
                Version("25.0.3", "Temurin", "25.0.3-tem", VersionStatus.AVAILABLE),
                Version("21.0.11", "Temurin", "21.0.11-tem", VersionStatus.AVAILABLE),
                Version("17.0.19", "Temurin", "17.0.19-tem", VersionStatus.AVAILABLE),
                Version("11.0.31", "Temurin", "11.0.31-tem", VersionStatus.AVAILABLE),
                Version("21.0.11", "Corretto", "21.0.11-amzn", VersionStatus.AVAILABLE),
                Version("17.0.15", "Corretto", "17.0.15-amzn", VersionStatus.AVAILABLE),
                Version("21.0.7", "Zulu", "21.0.7-zulu", VersionStatus.AVAILABLE),
                Version("17.0.14", "Zulu", "17.0.14-zulu", VersionStatus.AVAILABLE),
                Version("11.0.26", "Zulu", "11.0.26-zulu", VersionStatus.AVAILABLE),
            )
        val KOTLIN_VERSIONS =
            listOf(
                Version("2.1.20", null, "2.1.20", VersionStatus.AVAILABLE),
                Version("2.0.21", null, "2.0.21", VersionStatus.AVAILABLE),
                Version("1.9.25", null, "1.9.25", VersionStatus.AVAILABLE),
            )
    }
}
