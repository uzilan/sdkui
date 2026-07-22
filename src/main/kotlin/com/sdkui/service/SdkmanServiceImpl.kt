package com.sdkui.service

import com.sdkui.model.Sdk
import com.sdkui.model.SdkmanUpdateStatus
import com.sdkui.model.Version
import com.sdkui.model.VersionStatus
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class SdkmanServiceImpl(
    private val sdkmanRoot: Path = Path.of(System.getProperty("user.home"), ".sdkman"),
    private val apiBaseUrl: String = System.getenv("SDKMAN_CANDIDATES_API") ?: "https://api.sdkman.io/2",
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3))
        .build()
) : SdkmanService {
    private val sdkmanInit = sdkmanRoot.resolve("bin/sdkman-init.sh")

    internal fun runSdk(vararg args: String): String {
        val cmd = "source $sdkmanInit && sdk ${args.joinToString(" ") { "'$it'" }}"
        val proc = ProcessBuilder("/bin/bash", "-c", cmd)
            .redirectErrorStream(true)
            .start()
        val output = proc.inputStream.bufferedReader().readText()
        proc.waitFor()
        return output
    }

    override suspend fun listCandidates(): Result<List<Sdk>> = withContext(Dispatchers.IO) {
        runCatching { parseCandidates(runSdk("list")) }
    }

    override suspend fun getCurrentDefaults(): Result<Map<String, String>> = withContext(Dispatchers.IO) {
        runCatching { parseDefaults(runSdk("current")) }
    }

    override suspend fun checkForUpdate(): Result<SdkmanUpdateStatus> = withContext(Dispatchers.IO) {
        runCatching {
            val channel = if (isBetaChannel(Files.readString(sdkmanRoot.resolve("etc/config")))) "beta" else "stable"
            SdkmanUpdateStatus(
                localScript = Files.readString(sdkmanRoot.resolve("var/version")).trim(),
                remoteScript = fetchVersion("script", channel),
                localNative = Files.readString(sdkmanRoot.resolve("var/version_native")).trim(),
                remoteNative = fetchVersion("native", channel)
            )
        }
    }

    private fun fetchVersion(component: String, channel: String): String {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("${apiBaseUrl.trimEnd('/')}/broker/version/sdkman/$component/$channel"))
            .timeout(Duration.ofSeconds(5))
            .GET()
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() in 200..299) { "SDKMAN update check failed (${response.statusCode()})" }
        return response.body().trim().also { check(it.isNotEmpty()) { "SDKMAN update check returned an empty version" } }
    }

    override suspend fun listVersions(candidate: String, vendor: String?): Result<List<Version>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val raw = runSdk("list", candidate)
                if (candidate == "java") parseJavaVersions(raw, vendor) else parseGenericVersions(raw)
            }
        }

    override suspend fun setDefault(candidate: String, identifier: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching { runSdk("default", candidate, identifier); Unit }
        }

    override fun selfUpdate(): Flow<String> = flow {
        val proc = ProcessBuilder("/bin/bash", "-c", "source $sdkmanInit && sdk selfupdate")
            .redirectErrorStream(true).start()
        proc.outputStream.close()
        proc.inputStream.bufferedReader().useLines { it.forEach { line -> emit(line) } }
        check(proc.waitFor() == 0) { "SDKMAN update failed" }
    }.flowOn(Dispatchers.IO)

    override fun install(candidate: String, identifier: String?): Flow<String> = flow {
        val versionArg = if (identifier != null) " '$identifier'" else ""
        val proc = ProcessBuilder("/bin/bash", "-c", "source $sdkmanInit && sdk install '$candidate'$versionArg")
            .redirectErrorStream(true).start()
        proc.outputStream.close()
        proc.inputStream.bufferedReader().useLines { it.forEach { line -> emit(line) } }
        proc.waitFor()
    }.flowOn(Dispatchers.IO)

    override fun uninstall(candidate: String, identifier: String): Flow<String> = flow {
        val proc = ProcessBuilder("/bin/bash", "-c", "source $sdkmanInit && sdk uninstall '$candidate' '$identifier'")
            .redirectErrorStream(true).start()
        proc.inputStream.bufferedReader().useLines { it.forEach { line -> emit(line) } }
        proc.waitFor()
    }.flowOn(Dispatchers.IO)

    companion object {
        // Old format: " Java (21.0.11-tem)    (j) java"
        private val CANDIDATE_RE = Regex("""^\s+\S.*?\(([^)]+)\)\s+\(\w+\)\s+(\w+)\s*$""")
        // New format helpers
        private val SEPARATOR_RE = Regex("""-{10,}""")
        private val INSTALL_RE = Regex("""^\s*\$\s+sdk install (\w+)\s*$""")
        private val PAREN_VERSION_RE = Regex("""\(([^)]+)\)""")
        private val BETA_CHANNEL_RE = Regex("""(?m)^\s*sdkman_beta_channel\s*=\s*true\s*$""")

        internal fun isBetaChannel(config: String): Boolean = BETA_CHANNEL_RE.containsMatchIn(config)

        fun parseCandidates(raw: String): List<Sdk> {
            val lines = raw.lines()
            // Try old format first (unit-test fixtures use this)
            val oldFormat = lines.mapNotNull { line ->
                CANDIDATE_RE.find(line)?.let { m ->
                    Sdk(name = m.groupValues[2], version = m.groupValues[1], description = "")
                }
            }
            if (oldFormat.isNotEmpty()) return oldFormat

            // New format: split on --- separators, find install line + header version per block
            val result = mutableListOf<Sdk>()
            var block = mutableListOf<String>()

            fun flushBlock() {
                val name = block.mapNotNull { INSTALL_RE.find(it)?.groupValues?.get(1) }.firstOrNull()
                if (name != null) {
                    val headerIdx = block.indexOfFirst { it.isNotBlank() }
                    val installIdx = block.indexOfFirst { INSTALL_RE.find(it) != null }
                    val header = if (headerIdx >= 0) block[headerIdx] else null
                    val version = header?.let { PAREN_VERSION_RE.findAll(it).lastOrNull()?.groupValues?.get(1) } ?: ""
                    val description = if (headerIdx >= 0 && installIdx > headerIdx) {
                        block.subList(headerIdx + 1, installIdx)
                            .map { it.trim() }
                            .dropWhile { it.isBlank() }
                            .dropLastWhile { it.isBlank() }
                            .joinToString("\n")
                            .replace(Regex("""(?<!\n)\n(?!\n)"""), " ")
                    } else ""
                    result.add(Sdk(name = name, version = version, description = description))
                }
                block = mutableListOf()
            }

            for (line in lines) {
                if (SEPARATOR_RE.matches(line.trim())) flushBlock() else block.add(line)
            }
            flushBlock()
            return result
        }

        fun parseDefaults(raw: String): Map<String, String> =
            raw.lines().mapNotNull { line ->
                val parts = line.trim().split(Regex("""\s+"""), limit = 2)
                if (parts.size == 2 && parts[0].isNotBlank() && parts[0][0].isLowerCase())
                    parts[0] to parts[1].trim()
                else null
            }.toMap()

        fun parseJavaVersions(raw: String, vendor: String?): List<Version> {
            var currentVendor = ""
            return raw.lines()
                .filter { it.contains("|") && !it.trimStart().startsWith("-") && !it.trimStart().startsWith("Vendor") }
                .mapNotNull { line ->
                    val cols = line.split("|").map { it.trim() }
                    if (cols.size < 6) return@mapNotNull null
                    val v = cols[0].takeIf { it.isNotEmpty() }?.also { currentVendor = it } ?: currentVendor
                    if (v.isEmpty()) return@mapNotNull null
                    val number = cols[2].takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                    val identifier = cols[5].takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                    if (vendor != null && !v.equals(vendor, ignoreCase = true)) return@mapNotNull null
                    Version(number = number, vendor = v, identifier = identifier, status = VersionStatus.AVAILABLE)
                }
        }

        private val VERSION_TOKEN = Regex("""^\d+\.\d[\d.]*$""")

        fun parseGenericVersions(raw: String): List<Version> =
            raw.lines()
                .flatMap { it.trim().split(Regex("""\s+""")) }
                .filter { VERSION_TOKEN.matches(it) }
                .map { Version(number = it, identifier = it, status = VersionStatus.AVAILABLE) }
    }
}
