package com.example

import com.example.util.GitHubReleaseUpdater
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubReleaseUpdaterTest {

  @Test
  fun normalizeRepoSlug_acceptsSlugAndGitHubUrls() {
    assertEquals(
      "axeldelvalle/malvinas-pro-service",
      GitHubReleaseUpdater.normalizeRepoSlug("axeldelvalle/malvinas-pro-service"),
    )
    assertEquals(
      "axeldelvalle/malvinas-pro-service",
      GitHubReleaseUpdater.normalizeRepoSlug("https://github.com/axeldelvalle/malvinas-pro-service"),
    )
    assertEquals(
      "axeldelvalle/malvinas-pro-service",
      GitHubReleaseUpdater.normalizeRepoSlug("https://github.com/axeldelvalle/malvinas-pro-service.git"),
    )
    assertEquals(
      "axeldelvalle/malvinas-pro-service",
      GitHubReleaseUpdater.normalizeRepoSlug("https://github.com/axeldelvalle/malvinas-pro-service/releases"),
    )
  }

  @Test
  fun normalizeRepoSlug_rejectsTraversalAndInvalidInputs() {
    assertNull(GitHubReleaseUpdater.normalizeRepoSlug(""))
    assertNull(GitHubReleaseUpdater.normalizeRepoSlug("only-owner"))
    assertNull(GitHubReleaseUpdater.normalizeRepoSlug("../evil/repo"))
    assertNull(GitHubReleaseUpdater.normalizeRepoSlug("owner/.."))
    assertNull(GitHubReleaseUpdater.normalizeRepoSlug("owner/repo with spaces"))
  }

  @Test
  fun isTrustedGitHubUrl_allowsOnlyOfficialHttpsGitHubHosts() {
    assertTrue(
      GitHubReleaseUpdater.isTrustedGitHubUrl(
        "https://github.com/axeldelvalle/malvinas-pro-service/releases/download/v1.1.0/app.apk"
      )
    )
    assertTrue(
      GitHubReleaseUpdater.isTrustedGitHubUrl(
        "https://objects.githubusercontent.com/github-production-release-asset-123/app.apk"
      )
    )
    assertFalse(GitHubReleaseUpdater.isTrustedGitHubUrl("http://github.com/a/b"))
    assertFalse(GitHubReleaseUpdater.isTrustedGitHubUrl("https://evil.com/github.com/a/b.apk"))
    assertFalse(GitHubReleaseUpdater.isTrustedGitHubUrl("javascript:alert(1)"))
    assertFalse(GitHubReleaseUpdater.isTrustedGitHubUrl(""))
  }

  @Test
  fun compareVersions_handlesSemanticVersionsProperly() {
    assertEquals(
      "Axl-Tx0/malvinas-pro-service",
      GitHubReleaseUpdater.normalizeRepoSlug(GitHubReleaseUpdater.DEFAULT_GITHUB_REPO),
    )
    assertTrue(GitHubReleaseUpdater.compareVersions("v1.0.1", "1.0") > 0)
    assertTrue(GitHubReleaseUpdater.compareVersions("v1.0.2", "1.0.1") > 0)
    assertTrue(GitHubReleaseUpdater.compareVersions("v1.1.0", "1.0") > 0)
    assertTrue(GitHubReleaseUpdater.compareVersions("v1.10.0", "1.2.9") > 0)
    assertTrue(GitHubReleaseUpdater.compareVersions("2.0", "1.9.9") > 0)
    assertEquals(0, GitHubReleaseUpdater.compareVersions("v1.0.0", "1.0"))
    assertTrue(GitHubReleaseUpdater.compareVersions("v1.0.0", "1.1.0") < 0)
  }
}
