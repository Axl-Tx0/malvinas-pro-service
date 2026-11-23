package com.example.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.core.content.pm.PackageInfoCompat
import com.example.BuildConfig
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallException
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallErrorCode
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability

data class InstalledAppVersionInfo(
  val versionName: String,
  val versionCode: Long,
  val packageName: String,
  val lastUpdatedMillis: Long,
)

sealed interface AppUpdateUiState {
  data object Idle : AppUpdateUiState

  data object Checking : AppUpdateUiState

  data class UpToDate(val versionName: String, val versionCode: Long) : AppUpdateUiState

  data class UpdateAvailable(
    val availableVersionCode: Int,
    val isFlexibleAllowed: Boolean,
    val isImmediateAllowed: Boolean,
    val appUpdateInfo: AppUpdateInfo,
  ) : AppUpdateUiState

  data class Downloading(val bytesDownloaded: Long, val totalBytes: Long) : AppUpdateUiState

  data object DownloadedReadyToInstall : AppUpdateUiState

  data class UnavailableOnDevice(val message: String) : AppUpdateUiState

  data class Error(val message: String) : AppUpdateUiState
}

object AppUpdateHelper {

  fun getInstalledVersionInfo(context: Context): InstalledAppVersionInfo {
    return try {
      val packageInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.PackageInfoFlags.of(0L),
          )
        } else {
          @Suppress("DEPRECATION")
          context.packageManager.getPackageInfo(context.packageName, 0)
        }
      InstalledAppVersionInfo(
        versionName = packageInfo.versionName ?: BuildConfig.VERSION_NAME,
        versionCode = PackageInfoCompat.getLongVersionCode(packageInfo),
        packageName = context.packageName,
        lastUpdatedMillis = packageInfo.lastUpdateTime,
      )
    } catch (_: Exception) {
      InstalledAppVersionInfo(
        versionName = BuildConfig.VERSION_NAME,
        versionCode = BuildConfig.VERSION_CODE.toLong(),
        packageName = context.packageName,
        lastUpdatedMillis = 0L,
      )
    }
  }

  fun checkForUpdates(
    context: Context,
    appUpdateManager: AppUpdateManager,
    onStateChanged: (AppUpdateUiState) -> Unit,
  ) {
    val installed = getInstalledVersionInfo(context)
    onStateChanged(AppUpdateUiState.Checking)
    appUpdateManager.appUpdateInfo
      .addOnSuccessListener { info ->
        when {
          info.installStatus() == InstallStatus.DOWNLOADED -> {
            onStateChanged(AppUpdateUiState.DownloadedReadyToInstall)
          }
          info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE -> {
            onStateChanged(
              AppUpdateUiState.UpdateAvailable(
                availableVersionCode = info.availableVersionCode(),
                isFlexibleAllowed = info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE),
                isImmediateAllowed = info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE),
                appUpdateInfo = info,
              )
            )
          }
          info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS -> {
            onStateChanged(
              AppUpdateUiState.UpdateAvailable(
                availableVersionCode = info.availableVersionCode(),
                isFlexibleAllowed = info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE),
                isImmediateAllowed = info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE),
                appUpdateInfo = info,
              )
            )
          }
          else -> {
            onStateChanged(
              AppUpdateUiState.UpToDate(
                versionName = installed.versionName,
                versionCode = installed.versionCode,
              )
            )
          }
        }
      }
      .addOnFailureListener { error ->
        val installError = error as? InstallException
        val state =
          when (installError?.errorCode) {
            InstallErrorCode.ERROR_APP_NOT_OWNED ->
              AppUpdateUiState.UnavailableOnDevice(
                "Esta compilación no proviene de Google Play Store. Cuando esté publicada o compartida en Play Store, podrás actualizarla desde acá."
              )
            InstallErrorCode.ERROR_PLAY_STORE_NOT_FOUND,
            InstallErrorCode.ERROR_API_NOT_AVAILABLE ->
              AppUpdateUiState.UnavailableOnDevice(
                "Google Play Store no está disponible en este dispositivo para comprobar actualizaciones automáticas."
              )
            else ->
              AppUpdateUiState.Error(
                error.localizedMessage ?: "No se pudo verificar si hay una actualización disponible."
              )
          }
        onStateChanged(state)
      }
  }

  fun startUpdateFlow(
    appUpdateManager: AppUpdateManager,
    updateInfo: AppUpdateInfo,
    launcher: ActivityResultLauncher<IntentSenderRequest>,
    preferFlexible: Boolean = true,
  ): Boolean {
    val updateType =
      when {
        preferFlexible && updateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) ->
          AppUpdateType.FLEXIBLE
        updateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE) ->
          AppUpdateType.IMMEDIATE
        updateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) ->
          AppUpdateType.FLEXIBLE
        else -> return false
      }
    return try {
      appUpdateManager.startUpdateFlowForResult(
        updateInfo,
        launcher,
        AppUpdateOptions.newBuilder(updateType).build(),
      )
    } catch (_: Exception) {
      false
    }
  }

  fun openPlayStoreListing(context: Context): Boolean {
    val pkg = context.packageName
    val marketIntent =
      Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    return try {
      context.startActivity(marketIntent)
      true
    } catch (_: ActivityNotFoundException) {
      val webIntent =
        Intent(
          Intent.ACTION_VIEW,
          Uri.parse("https://play.google.com/store/apps/details?id=$pkg"),
        ).apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
      try {
        context.startActivity(webIntent)
        true
      } catch (_: ActivityNotFoundException) {
        false
      }
    }
  }
}
