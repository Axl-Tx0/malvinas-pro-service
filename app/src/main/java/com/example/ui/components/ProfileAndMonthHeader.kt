package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.ProfileType
import com.example.util.Formatters

@Composable
fun ProfileSwitcherBar(
  activeProfile: ProfileType,
  onSelectProfile: (ProfileType) -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surfaceVariant,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(4.dp),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      ProfilePillOption(
        profile = ProfileType.COMMON,
        selected = activeProfile == ProfileType.COMMON,
        onClick = { onSelectProfile(ProfileType.COMMON) },
        modifier = Modifier.weight(1f).testTag("profile_tab_common"),
      )
      ProfilePillOption(
        profile = ProfileType.PREMIUM,
        selected = activeProfile == ProfileType.PREMIUM,
        onClick = { onSelectProfile(ProfileType.PREMIUM) },
        modifier = Modifier.weight(1f).testTag("profile_tab_premium"),
      )
    }
  }
}

@Composable
private fun ProfilePillOption(
  profile: ProfileType,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val isPremium = profile == ProfileType.PREMIUM
  val targetContainer =
    when {
      selected && isPremium -> MaterialTheme.colorScheme.secondaryContainer
      selected && !isPremium -> MaterialTheme.colorScheme.primaryContainer
      else -> MaterialTheme.colorScheme.surfaceVariant
    }
  val targetContent =
    when {
      selected && isPremium -> MaterialTheme.colorScheme.onSecondaryContainer
      selected && !isPremium -> MaterialTheme.colorScheme.onPrimaryContainer
      else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
  val containerColor by animateColorAsState(targetValue = targetContainer, label = "profileBg")
  val contentColor by animateColorAsState(targetValue = targetContent, label = "profileFg")

  Surface(
    modifier =
      modifier
        .minimumInteractiveComponentSize()
        .clip(RoundedCornerShape(12.dp))
        .clickable(onClick = onClick),
    shape = RoundedCornerShape(12.dp),
    color = containerColor,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
    ) {
      Icon(
        imageVector = if (isPremium) Icons.Filled.Star else Icons.Filled.Workspaces,
        contentDescription = null,
        tint = contentColor,
        modifier = Modifier.size(16.dp),
      )
      Spacer(modifier = Modifier.width(6.dp))
      Text(
        text = profile.label,
        style = MaterialTheme.typography.labelLarge,
        color = contentColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
fun MonthSelectorBar(
  monthKey: String,
  onPreviousMonth: () -> Unit,
  onNextMonth: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(14.dp),
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      IconButton(
        onClick = onPreviousMonth,
        modifier = Modifier.testTag("month_prev_button"),
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
          contentDescription = "Mes anterior",
        )
      }
      Text(
        text = Formatters.formatMonthTitle(monthKey),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
      )
      IconButton(
        onClick = onNextMonth,
        modifier = Modifier.testTag("month_next_button"),
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
          contentDescription = "Mes siguiente",
        )
      }
    }
  }
}

@Composable
fun AccountAndNotificationDialog(
  userEmail: String?,
  userName: String?,
  hasNotificationPermission: Boolean,
  reminderHour: Int = 9,
  isAdmin: Boolean = false,
  onOpenUserAccessManager: () -> Unit = {},
  onRequestNotificationPermission: () -> Unit,
  onSendTestNotification: () -> Unit,
  onOpenSettings: () -> Unit,
  onSignOut: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    icon = {
      Icon(
        imageVector = Icons.Filled.Person,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
      )
    },
    title = {
      Text(
        text = userName?.takeIf { it.isNotBlank() } ?: "Cuenta Malvinas Pro",
        style = MaterialTheme.typography.titleLarge,
      )
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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

        Surface(
          shape = RoundedCornerShape(12.dp),
          color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Filled.NotificationsActive,
                contentDescription = null,
                tint =
                  if (hasNotificationPermission) {
                    MaterialTheme.colorScheme.tertiary
                  } else {
                    MaterialTheme.colorScheme.error
                  },
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text =
                  if (hasNotificationPermission) {
                    "Alertas de clientes activas (1 día antes a las ${reminderHour.toString().padStart(2, '0')}:00 hs)"
                  } else {
                    "Permiso de notificaciones desactivado"
                  },
                style = MaterialTheme.typography.labelLarge,
              )
            }

            if (!hasNotificationPermission) {
              Button(
                onClick = onRequestNotificationPermission,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Text("Permitir notificaciones")
              }
            } else {
              OutlinedButton(
                onClick = onSendTestNotification,
                modifier = Modifier.fillMaxWidth().testTag("test_notification_button"),
              ) {
                Text("Probar notificación de vencimiento")
              }
            }
          }
        }

        if (isAdmin) {
          Button(
            onClick = onOpenUserAccessManager,
            modifier = Modifier.fillMaxWidth().testTag("dialog_open_user_access_admin_button"),
          ) {
            Icon(
              imageVector = Icons.Filled.AdminPanelSettings,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Gestionar usuarios y accesos")
          }
        }

        OutlinedButton(
          onClick = onOpenSettings,
          modifier = Modifier.fillMaxWidth().testTag("dialog_open_settings_button"),
        ) {
          Icon(
            imageVector = Icons.Filled.Settings,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text("Configurar mensajes y ajustes")
        }

        Spacer(modifier = Modifier.height(4.dp))
        OutlinedButton(
          onClick = onSignOut,
          modifier = Modifier.fillMaxWidth().testTag("sign_out_button"),
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
          Text("Cerrar sesión")
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text("Cerrar")
      }
    },
  )
}
