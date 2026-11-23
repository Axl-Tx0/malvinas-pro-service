package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.viewmodel.CloudSyncUiState
import com.example.util.Formatters
import com.example.util.GitHubReleaseUpdater
import com.example.util.GitHubUpdateUiState
import com.example.util.InstalledAppVersionInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CloudSyncAndBackupCard(
  userEmail: String?,
  userName: String?,
  cloudSyncState: CloudSyncUiState,
  transactionsCount: Int,
  vpnAccountsCount: Int,
  resellersCount: Int,
  onSyncNow: () -> Unit,
  onExportBackup: () -> Unit,
  onImportBackup: () -> Unit,
  onSignOut: () -> Unit,
  isAdmin: Boolean = false,
  modifier: Modifier = Modifier,
) {
  Card(
    modifier = modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Icon(
          imageVector = Icons.Filled.CloudDone,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.tertiary,
          modifier = Modifier.size(22.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Nube de tu cuenta de Google",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
          )
          Text(
            text = "Todos tus ingresos, gastos, clientes, revendedores y plantillas se guardan en tiempo real.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f),
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(12.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Text(
            text = userName?.takeIf { it.isNotBlank() } ?: "Cuenta de Google conectada",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
          )
          if (!userEmail.isNullOrBlank()) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Text(
                text = userEmail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f, fill = false),
              )
              if (isAdmin || com.example.util.AccessControlConfig.isAdminEmail(userEmail)) {
                Surface(
                  shape = RoundedCornerShape(8.dp),
                  color = MaterialTheme.colorScheme.secondary,
                ) {
                  Text(
                    text = com.example.util.AccessControlConfig.ADMIN_ROLE_LABEL,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                  )
                }
              }
            }
          }
          Text(
            text = "$transactionsCount movimientos · $vpnAccountsCount clientes · $resellersCount revendedores",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
          )
          cloudSyncState.lastReport?.let { report ->
            Text(
              text = "Última verificación en servidor: ${formatDateTime(report.syncedAtMillis)} (${report.totalRecords} docs)",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.tertiary,
            )
          }
          cloudSyncState.lastError?.let { errorMsg ->
            Text(
              text = errorMsg,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.error,
            )
          }
        }
      }

      Button(
        onClick = onSyncNow,
        enabled = !cloudSyncState.isSyncing,
        modifier = Modifier.fillMaxWidth().testTag("sync_cloud_now_button"),
        shape = RoundedCornerShape(12.dp),
      ) {
        if (cloudSyncState.isSyncing) {
          CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.onPrimary,
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text("Sincronizando con Google...")
        } else {
          Icon(
            imageVector = Icons.Filled.Sync,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text("Sincronizar con la nube ahora")
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        FilledTonalButton(
          onClick = onExportBackup,
          enabled = !cloudSyncState.isSyncing,
          modifier = Modifier.weight(1f).testTag("export_cloud_backup_button"),
          shape = RoundedCornerShape(12.dp),
        ) {
          Icon(
            imageVector = Icons.Filled.CloudUpload,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text("Copia Drive", maxLines = 1)
        }

        OutlinedButton(
          onClick = onImportBackup,
          enabled = !cloudSyncState.isSyncing,
          modifier = Modifier.weight(1f).testTag("import_cloud_backup_button"),
          shape = RoundedCornerShape(12.dp),
        ) {
          Icon(
            imageVector = Icons.Filled.Restore,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text("Restaurar", maxLines = 1)
        }
      }

      HorizontalDivider()

      OutlinedButton(
        onClick = onSignOut,
        modifier = Modifier.fillMaxWidth().testTag("settings_sign_out_button"),
        colors =
          ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.error
          ),
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.Logout,
          contentDescription = null,
          modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text("Cambiar cuenta de Google / Cerrar sesión")
      }
    }
  }
}

@Composable
fun AppUpdateSectionCard(
  versionInfo: InstalledAppVersionInfo,
  githubRepo: String,
  githubUpdateState: GitHubUpdateUiState,
  isAdmin: Boolean,
  isSavingRepo: Boolean,
  onSaveGitHubRepo: (String) -> Unit,
  onCheckGitHubUpdates: () -> Unit,
  onOpenGitHubDownload: (String) -> Unit,
  onOpenGitHubReleases: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var repoInput by remember(githubRepo) { mutableStateOf(githubRepo) }
  val hasValidRepo = GitHubReleaseUpdater.normalizeRepoSlug(githubRepo) != null

  Card(
    modifier = modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Icon(
          imageVector = Icons.Filled.SystemUpdate,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(22.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Actualizaciones desde GitHub",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
          )
          Text(
            text =
              "Versión instalada v${versionInfo.versionName} (Compilación ${versionInfo.versionCode})",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      if (versionInfo.lastUpdatedMillis > 0L) {
        Text(
          text = "Última instalación: ${Formatters.formatDate(versionInfo.lastUpdatedMillis)}",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      if (hasValidRepo) {
        Surface(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(10.dp),
          color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(
              imageVector = Icons.Filled.Code,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(18.dp),
            )
            Text(
              text = "github.com/$githubRepo",
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.SemiBold,
              color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
          }
        }
      }

      if (isAdmin) {
        OutlinedTextField(
          value = repoInput,
          onValueChange = { if (it.length <= 160) repoInput = it },
          label = { Text("Repositorio GitHub (Admin)") },
          placeholder = { Text("usuario/malvinas-pro-service") },
          supportingText = {
            Text(
              "Pegá usuario/repositorio o el enlace de tu repo público. Se aplica a todos los usuarios."
            )
          },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("settings_github_repo_input"),
        )
        FilledTonalButton(
          onClick = { onSaveGitHubRepo(repoInput) },
          enabled = !isSavingRepo,
          modifier = Modifier.fillMaxWidth().testTag("save_github_repo_button"),
          shape = RoundedCornerShape(12.dp),
        ) {
          if (isSavingRepo) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Guardando repositorio...")
          } else {
            Icon(
              imageVector = Icons.Filled.Save,
              contentDescription = null,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Guardar repositorio de GitHub")
          }
        }
      }

      HorizontalDivider()

      when (githubUpdateState) {
        GitHubUpdateUiState.Idle -> {
          Text(
            text =
              if (hasValidRepo) {
                "Comprobá si hay un nuevo Release con APK disponible en GitHub."
              } else if (isAdmin) {
                "Subí este proyecto a GitHub, guardá arriba tu usuario/repositorio y publicá una versión desde Actions."
              } else {
                "Todavía no se vinculó un repositorio de GitHub para actualizaciones."
              },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        GitHubUpdateUiState.RepoNotConfigured -> {
          Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
          ) {
            Text(
              text =
                if (isAdmin) {
                  "Primero ingresá y guardá tu repositorio de GitHub (ej: tu-usuario/malvinas-pro-service)."
                } else {
                  "El administrador aún no configuró el repositorio de GitHub para buscar actualizaciones."
                },
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(10.dp),
            )
          }
        }
        GitHubUpdateUiState.Checking -> {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Text(
              text = "Consultando último Release en GitHub...",
              style = MaterialTheme.typography.bodySmall,
            )
          }
        }
        is GitHubUpdateUiState.UpToDate -> {
          Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
          ) {
            Text(
              text =
                "Tenés la última versión publicada en GitHub (instalada v${githubUpdateState.installedVersion} · último release ${githubUpdateState.latestTag}).",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onTertiaryContainer,
              modifier = Modifier.padding(10.dp),
            )
          }
        }
        is GitHubUpdateUiState.UpdateAvailable -> {
          val release = githubUpdateState.release
          val sizeLabel = GitHubReleaseUpdater.formatSizeMb(release.apkSizeBytes)
          Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
          ) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(12.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Text(
                text = "Nueva versión disponible: ${release.releaseName}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
              )
              val metaParts =
                buildList {
                  add("Tag ${release.tagName}")
                  if (release.publishedAt.isNotBlank()) add(release.publishedAt)
                  if (sizeLabel.isNotBlank()) add("APK $sizeLabel")
                }
              Text(
                text = metaParts.joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
              )
              if (release.notes.isNotBlank()) {
                Text(
                  text = release.notes,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
              }
              Button(
                onClick = { onOpenGitHubDownload(release.bestOpenUrl) },
                modifier = Modifier.fillMaxWidth().testTag("download_github_update_button"),
                shape = RoundedCornerShape(12.dp),
              ) {
                Icon(
                  imageVector = Icons.Filled.Download,
                  contentDescription = null,
                  modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  if (release.apkDownloadUrl != null) {
                    "Descargar APK desde GitHub"
                  } else {
                    "Abrir versión en GitHub"
                  }
                )
              }
            }
          }
        }
        is GitHubUpdateUiState.Error -> {
          Text(
            text = githubUpdateState.message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedButton(
          onClick = onCheckGitHubUpdates,
          enabled = githubUpdateState !is GitHubUpdateUiState.Checking,
          modifier = Modifier.weight(1f).testTag("check_github_update_button"),
          shape = RoundedCornerShape(12.dp),
        ) {
          Icon(
            imageVector = Icons.Filled.Refresh,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text("Buscar en GitHub", maxLines = 1)
        }

        OutlinedButton(
          onClick = onOpenGitHubReleases,
          enabled = hasValidRepo,
          modifier = Modifier.weight(0.9f).testTag("open_github_releases_button"),
          shape = RoundedCornerShape(12.dp),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text("Ver releases", maxLines = 1)
        }
      }
    }
  }
}

private fun formatDateTime(millis: Long): String {
  if (millis <= 0L) return "Ahora"
  val formatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("es-AR"))
  return formatter.format(Date(millis))
}
