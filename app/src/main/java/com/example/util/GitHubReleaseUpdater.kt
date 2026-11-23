package com.example.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.Locale

data class GitHubReleaseInfo(
  val tagName: String,
  val releaseName: String,
  val notes: String,
  val publishedAt: String,
  val htmlUrl: String,
  val apkDownloadUrl: String?,
  val apkFileName: String?,
  val apkSizeBytes: Long,
) {
  val bestOpenUrl: String
    get() = apkDownloadUrl ?: htmlUrl
}

sealed interface GitHubUpdateUiState {
  data object Idle : GitHubUpdateUiState

  data object RepoNotConfigured : GitHubUpdateUiState

  data object Checking : GitHubUpdateUiState

  data class UpToDate(
    val installedVersion: String,
    val latestTag: String,
  ) : GitHubUpdateUiState

  data class UpdateAvailable(
    val installedVersion: String,
    val release: GitHubReleaseInfo,
  ) : GitHubUpdateUiState

  data class Error(val message: String) : GitHubUpdateUiState
}

object GitHubReleaseUpdater {
  const val DEFAULT_GITHUB_REPO = "Axl-Tx0/malvinas-pro-service"
  private val REPO_SLUG_REGEX = Regex("^[A-Za-z0-9_.-]{1,100}/[A-Za-z0-9_.-]{1,100}$")
  private val TRUSTED_GITHUB_HOSTS =
    setOf(
      "github.com",
      "www.github.com",
      "objects.githubusercontent.com",
      "release-assets.githubusercontent.com",
    )
  private const val MAX_RESPONSE_BYTES = 512 * 1024

  /**
   * Normalizes user input such as:
   * - "usuario/mi-repo"
   * - "https://github.com/usuario/mi-repo"
   * - "https://github.com/usuario/mi-repo.git"
   * - "https://github.com/usuario/mi-repo/releases"
   * Returns null if invalid or unsafe.
   */
  fun normalizeRepoSlug(rawInput: String): String? {
    var cleaned = rawInput.trim()
    if (cleaned.isEmpty()) return null

    // Strip protocol & github.com host if user pasted a full URL
    cleaned = cleaned.removePrefix("https://").removePrefix("http://")
    cleaned = cleaned.removePrefix("www.")
    if (cleaned.startsWith("github.com/", ignoreCase = true)) {
      cleaned = cleaned.substring("github.com/".length)
    }

    // Strip query string / fragment and trailing slash
    cleaned = cleaned.substringBefore("?").substringBefore("#").trim('/')

    // Keep only owner/repo segments if user pasted /releases or /tree/...
    val parts = cleaned.split('/').filter { it.isNotBlank() }
    if (parts.size < 2) return null
    val owner = parts[0]
    val repo = parts[1].removeSuffix(".git")
    if (owner == "." || repo == "." || owner.isBlank() || repo.isBlank()) return null
    val slug = "$owner/$repo"

    if (slug.contains("..") || slug.length > 120) return null
    return if (REPO_SLUG_REGEX.matches(slug)) slug else null
  }

  /**
   * Validates that an external download/release link uses HTTPS and points exclusively
   * to official GitHub release hosts.
   */
  fun isTrustedGitHubUrl(rawUrl: String?): Boolean {
    if (rawUrl.isNullOrBlank()) return false
    return try {
      val parsed = URI(rawUrl.trim())
      val scheme = parsed.scheme?.lowercase(Locale.US)
      val host = parsed.host?.lowercase(Locale.US)
      scheme == "https" && host != null && host in TRUSTED_GITHUB_HOSTS
    } catch (_: Exception) {
      false
    }
  }

  /**
   * Compares two version strings like "v1.2.0" and "1.0".
   * Returns > 0 if [latest] is newer than [installed], 0 if equal, < 0 if older.
   */
  fun compareVersions(latest: String, installed: String): Int {
    val latestParts = parseVersionNumbers(latest)
    val installedParts = parseVersionNumbers(installed)
    if (latestParts.isEmpty() || installedParts.isEmpty()) {
      return latest.trim().removePrefix("v").removePrefix("V")
        .compareTo(installed.trim().removePrefix("v").removePrefix("V"))
    }
    val maxLen = maxOf(latestParts.size, installedParts.size)
    for (i in 0 until maxLen) {
      val a = latestParts.getOrElse(i) { 0 }
      val b = installedParts.getOrElse(i) { 0 }
      if (a != b) return a.compareTo(b)
    }
    return 0
  }

  private fun parseVersionNumbers(raw: String): List<Int> {
    val clean = raw.trim().removePrefix("v").removePrefix("V").substringBefore("-")
    if (clean.isBlank()) return emptyList()
    return clean.split('.').mapNotNull { part ->
      part.takeWhile { it.isDigit() }.toIntOrNull()
    }
  }

  suspend fun checkLatestRelease(
    repoSlug: String,
    installedVersionName: String,
  ): GitHubUpdateUiState {
    val normalized = normalizeRepoSlug(repoSlug)
      ?: return GitHubUpdateUiState.RepoNotConfigured

    return withContext(Dispatchers.IO) {
      var connection: HttpURLConnection? = null
      try {
        val apiUrl = URL("https://api.github.com/repos/$normalized/releases/latest")
        connection =
          (apiUrl.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "MalvinasProService-Android")
          }

        val statusCode = connection.responseCode
        if (statusCode == HttpURLConnection.HTTP_NOT_FOUND) {
          return@withContext GitHubUpdateUiState.Error(
            "No se encontró ningún Release publicado en github.com/$normalized. Publicá una versión desde Actions o Releases en GitHub."
          )
        }
        if (statusCode == 403) {
          return@withContext GitHubUpdateUiState.Error(
            "GitHub limitó temporalmente las consultas públicas o el repositorio es privado. Verificá que sea público e intentá de nuevo."
          )
        }
        if (statusCode !in 200..299) {
          return@withContext GitHubUpdateUiState.Error(
            "GitHub respondió con código $statusCode. Verificá tu conexión e intentá nuevamente."
          )
        }

        val body = readBoundedResponse(connection)
        val json = JSONObject(body)
        val tagName = json.optString("tag_name", "").trim()
        if (tagName.isBlank()) {
          return@withContext GitHubUpdateUiState.Error(
            "El último Release de GitHub no contiene una etiqueta de versión válida."
          )
        }

        val htmlUrl = json.optString("html_url", "").trim()
        val safeHtmlUrl =
          if (isTrustedGitHubUrl(htmlUrl)) {
            htmlUrl
          } else {
            "https://github.com/$normalized/releases/latest"
          }

        var apkUrl: String? = null
        var apkName: String? = null
        var apkSize = 0L
        val assets = json.optJSONArray("assets")
        if (assets != null) {
          for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val name = asset.optString("name", "")
            val downloadUrl = asset.optString("browser_download_url", "").trim()
            if (name.endsWith(".apk", ignoreCase = true) && isTrustedGitHubUrl(downloadUrl)) {
              apkUrl = downloadUrl
              apkName = name
              apkSize = asset.optLong("size", 0L)
              break
            }
          }
        }

        val releaseInfo =
          GitHubReleaseInfo(
            tagName = tagName,
            releaseName = json.optString("name", "").trim().ifBlank { tagName },
            notes = json.optString("body", "").trim().take(600),
            publishedAt = json.optString("published_at", "").take(10),
            htmlUrl = safeHtmlUrl,
            apkDownloadUrl = apkUrl,
            apkFileName = apkName,
            apkSizeBytes = apkSize,
          )

        if (compareVersions(tagName, installedVersionName) > 0) {
          GitHubUpdateUiState.UpdateAvailable(
            installedVersion = installedVersionName,
            release = releaseInfo,
          )
        } else {
          GitHubUpdateUiState.UpToDate(
            installedVersion = installedVersionName,
            latestTag = tagName,
          )
        }
      } catch (e: Exception) {
        GitHubUpdateUiState.Error(
          e.localizedMessage ?: "No se pudo consultar GitHub. Revisá tu conexión a internet."
        )
      } finally {
        connection?.disconnect()
      }
    }
  }

  private fun readBoundedResponse(connection: HttpURLConnection): String {
    val sb = StringBuilder()
    BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).use { reader ->
      val buffer = CharArray(4096)
      var total = 0
      while (true) {
        val read = reader.read(buffer)
        if (read <= 0) break
        total += read
        if (total > MAX_RESPONSE_BYTES) break
        sb.append(buffer, 0, read)
      }
    }
    return sb.toString()
  }

  fun openTrustedUrl(context: Context, url: String): Boolean {
    if (!isTrustedGitHubUrl(url)) return false
    val intent =
      Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    return try {
      context.startActivity(intent)
      true
    } catch (_: ActivityNotFoundException) {
      false
    }
  }

  fun openReleasesPage(context: Context, repoSlug: String): Boolean {
    val normalized = normalizeRepoSlug(repoSlug) ?: return false
    return openTrustedUrl(context, "https://github.com/$normalized/releases")
  }

  fun formatSizeMb(bytes: Long): String {
    if (bytes <= 0L) return ""
    val mb = bytes.toDouble() / (1024.0 * 1024.0)
    return String.format(Locale.forLanguageTag("es-AR"), "%.1f MB", mb)
  }
}
