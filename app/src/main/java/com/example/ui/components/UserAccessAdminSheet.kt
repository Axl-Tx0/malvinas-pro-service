package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.AuthorizedUser
import com.example.data.repository.AccessControlRepository
import com.example.util.AccessControlConfig
import com.example.util.PasswordHasher
import com.example.util.WhatsAppHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

private data class ShareableCredentials(
  val displayName: String,
  val email: String,
  val password: String,
  val isReset: Boolean,
)

private sealed interface AdminUsersUiState {
  data object Loading : AdminUsersUiState

  data class Ready(val users: List<AuthorizedUser>) : AdminUsersUiState

  data class Error(val message: String) : AdminUsersUiState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserAccessAdminDialog(
  defaultCountryCode: String,
  businessName: String,
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val accessRepo = remember(context) { AccessControlRepository(context) }
  val snackbarHostState = remember { SnackbarHostState() }

  val usersState by
    produceState<AdminUsersUiState>(initialValue = AdminUsersUiState.Loading, key1 = accessRepo) {
      accessRepo
        .observeAllAuthorizedUsers()
        .catch { err ->
          value =
            AdminUsersUiState.Error(
              err.localizedMessage ?: "No se pudo cargar la lista de usuarios."
            )
        }
        .collect { list -> value = AdminUsersUiState.Ready(list) }
    }

  val savedAdminPhone by
    produceState(initialValue = "", key1 = accessRepo) {
      accessRepo.observeAdminWhatsAppPhone().collect { phone -> value = phone }
    }
  var adminPhoneDraft by remember(savedAdminPhone) { mutableStateOf(savedAdminPhone) }
  var isSavingAdminPhone by remember { mutableStateOf(false) }

  var showCreateDialog by remember { mutableStateOf(false) }
  var editingUser by remember { mutableStateOf<AuthorizedUser?>(null) }
  var resetPasswordTarget by remember { mutableStateOf<AuthorizedUser?>(null) }
  var deleteTarget by remember { mutableStateOf<AuthorizedUser?>(null) }
  var shareCredentials by remember { mutableStateOf<ShareableCredentials?>(null) }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Scaffold(
      modifier = Modifier.fillMaxSize(),
      snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
      topBar = {
        Column {
          TopAppBar(
            title = {
              Column {
                Text(
                  text = "Usuarios y accesos",
                  style = MaterialTheme.typography.titleLarge,
                  fontWeight = FontWeight.Bold,
                )
                Text(
                  text = "Panel administrador · Habilitá o bloqueá al instante",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            },
            navigationIcon = {
              IconButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("close_user_access_admin_button"),
              ) {
                Icon(
                  imageVector = Icons.Filled.Close,
                  contentDescription = "Cerrar gestión de usuarios",
                )
              }
            },
            colors =
              TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
              ),
          )
          AlbicelesteRibbonStripe(height = 4.dp, showSunBadge = true)
        }
      },
      floatingActionButton = {
        ExtendedFloatingActionButton(
          onClick = { showCreateDialog = true },
          icon = { Icon(Icons.Filled.PersonAdd, contentDescription = null) },
          text = { Text("Crear usuario") },
          containerColor = MaterialTheme.colorScheme.primary,
          contentColor = MaterialTheme.colorScheme.onPrimary,
          modifier = Modifier.testTag("admin_add_user_fab"),
        )
      },
    ) { innerPadding ->
      when (val state = usersState) {
        AdminUsersUiState.Loading -> {
          Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentAlignment = Alignment.Center,
          ) {
            CircularProgressIndicator()
          }
        }

        is AdminUsersUiState.Error -> {
          Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = state.message,
              style = MaterialTheme.typography.bodyLarge,
              color = MaterialTheme.colorScheme.error,
            )
          }
        }

        is AdminUsersUiState.Ready -> {
          val users = state.users
          val activeCount = users.count { it.enabled }
          val disabledCount = users.size - activeCount

          LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            item {
              Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors =
                  CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                  ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
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
                      imageVector = Icons.Filled.AdminPanelSettings,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                      text = "Control de acceso en tiempo real",
                      style = MaterialTheme.typography.titleMedium,
                      fontWeight = FontWeight.Bold,
                    )
                  }
                  Text(
                    text =
                      "Creá un usuario con su correo de Google y una contraseña. Al compartirle los datos podrá ingresar; si apagás su interruptor, la app se bloquea al instante.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                  ) {
                    AdminMetricPill(
                      label = "Total",
                      value = "${users.size}",
                      containerColor = MaterialTheme.colorScheme.surface,
                      contentColor = MaterialTheme.colorScheme.onSurface,
                      modifier = Modifier.weight(1f),
                    )
                    AdminMetricPill(
                      label = "Habilitados",
                      value = "$activeCount",
                      containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                      contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                      modifier = Modifier.weight(1f),
                    )
                    AdminMetricPill(
                      label = "Bloqueados",
                      value = "$disabledCount",
                      containerColor = MaterialTheme.colorScheme.errorContainer,
                      contentColor = MaterialTheme.colorScheme.onErrorContainer,
                      modifier = Modifier.weight(1f),
                    )
                  }
                }
              }
            }

            // Pinned Admin Account Identity Card
            item {
              Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors =
                  CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                  ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f)),
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth().padding(14.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  Column(
                    modifier = Modifier.weight(1f).padding(end = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                  ) {
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                      Text(
                        text = AccessControlConfig.ADMIN_EMAIL,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                      )
                    }
                    Text(
                      text = "Cuenta propietaria · Acceso permanente",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                  Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.secondary,
                  ) {
                    Text(
                      text = AccessControlConfig.ADMIN_ROLE_LABEL,
                      style = MaterialTheme.typography.labelMedium,
                      fontWeight = FontWeight.Bold,
                      color = MaterialTheme.colorScheme.onSecondary,
                      modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                  }
                }
              }
            }

            // Admin WhatsApp number for disabled users' "Hablar por WhatsApp" button
            item {
              Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
              ) {
                Column(
                  modifier = Modifier.fillMaxWidth().padding(14.dp),
                  verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                  ) {
                    Icon(
                      imageVector = Icons.AutoMirrored.Filled.Send,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.tertiary,
                      modifier = Modifier.size(18.dp),
                    )
                    Text(
                      text = "Tu WhatsApp (botón de usuarios deshabilitados)",
                      style = MaterialTheme.typography.titleSmall,
                      fontWeight = FontWeight.SemiBold,
                    )
                  }
                  Text(
                    text =
                      "Cuando un usuario deshabilitado toque \"Hablar por WhatsApp\", se abrirá tu chat con el mensaje: \"${AccessControlConfig.DISABLED_WHATSAPP_MESSAGE}\".",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    OutlinedTextField(
                      value = adminPhoneDraft,
                      onValueChange = { if (it.length <= 30) adminPhoneDraft = it },
                      label = { Text("Tu número con código de país") },
                      placeholder = { Text("Ej: +5491155551234") },
                      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                      singleLine = true,
                      modifier = Modifier.weight(1f).testTag("admin_whatsapp_phone_input"),
                    )
                    Button(
                      onClick = {
                        isSavingAdminPhone = true
                        coroutineScope.launch {
                          val res = accessRepo.saveAdminWhatsAppPhone(adminPhoneDraft)
                          isSavingAdminPhone = false
                          snackbarHostState.showSnackbar(
                            if (res.isSuccess) "WhatsApp de Admin guardado"
                            else res.exceptionOrNull()?.localizedMessage
                              ?: "No se pudo guardar el número."
                          )
                        }
                      },
                      enabled = !isSavingAdminPhone,
                      modifier = Modifier.height(54.dp).testTag("save_admin_whatsapp_button"),
                      shape = RoundedCornerShape(12.dp),
                    ) {
                      Icon(
                        imageVector = Icons.Filled.Save,
                        contentDescription = "Guardar WhatsApp de Admin",
                        modifier = Modifier.size(18.dp),
                      )
                    }
                  }
                }
              }
            }

            if (users.isEmpty()) {
              item {
                Card(
                  modifier = Modifier.fillMaxWidth(),
                  shape = MaterialTheme.shapes.large,
                  colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                  border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                  Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                  ) {
                    Icon(
                      imageVector = Icons.Filled.PersonAdd,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.size(40.dp),
                    )
                    Text(
                      text = "Todavía no creaste usuarios",
                      style = MaterialTheme.typography.titleMedium,
                      fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                      text =
                        "Tocá 'Crear usuario' para generar un acceso con correo y contraseña y compartirlo con otra persona.",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }
              }
            } else {
              items(users, key = { it.email }) { user ->
                AuthorizedUserCard(
                  user = user,
                  onToggleEnabled = { newEnabled ->
                    coroutineScope.launch {
                      val res = accessRepo.setUserEnabled(user.email, newEnabled)
                      snackbarHostState.showSnackbar(
                        if (res.isSuccess) {
                          if (newEnabled) {
                            "Acceso habilitado para ${user.displayName}"
                          } else {
                            "Acceso deshabilitado al instante para ${user.displayName}"
                          }
                        } else {
                          res.exceptionOrNull()?.localizedMessage
                            ?: "No se pudo actualizar el estado."
                        }
                      )
                    }
                  },
                  onResetPassword = { resetPasswordTarget = user },
                  onEdit = { editingUser = user },
                  onDelete = { deleteTarget = user },
                )
              }
            }
          }
        }
      }
    }
  }

  if (showCreateDialog) {
    CreateAuthorizedUserDialog(
      onCreate = { name, email, password, notes, onError ->
        coroutineScope.launch {
          val res =
            accessRepo.createAuthorizedUser(
              displayName = name,
              rawEmail = email,
              rawPassword = password,
              notes = notes,
            )
          res.fold(
            onSuccess = { created ->
              showCreateDialog = false
              shareCredentials =
                ShareableCredentials(
                  displayName = created.displayName,
                  email = created.email,
                  password = password,
                  isReset = false,
                )
            },
            onFailure = { err ->
              onError(err.localizedMessage ?: "No se pudo crear el usuario.")
            },
          )
        }
      },
      onDismiss = { showCreateDialog = false },
    )
  }

  editingUser?.let { target ->
    EditAuthorizedUserDialog(
      user = target,
      onSave = { newName, newNotes, onError ->
        coroutineScope.launch {
          val res = accessRepo.updateAuthorizedUserProfile(target.email, newName, newNotes)
          res.fold(
            onSuccess = {
              editingUser = null
              snackbarHostState.showSnackbar("Usuario actualizado")
            },
            onFailure = { err ->
              onError(err.localizedMessage ?: "No se pudo actualizar el usuario.")
            },
          )
        }
      },
      onDismiss = { editingUser = null },
    )
  }

  resetPasswordTarget?.let { target ->
    ResetPasswordDialog(
      user = target,
      onConfirmReset = { newPassword, onError ->
        coroutineScope.launch {
          val res =
            accessRepo.resetUserPassword(
              rawEmail = target.email,
              newRawPassword = newPassword,
              currentVersion = target.passwordVersion,
            )
          res.fold(
            onSuccess = {
              resetPasswordTarget = null
              shareCredentials =
                ShareableCredentials(
                  displayName = target.displayName,
                  email = target.email,
                  password = newPassword,
                  isReset = true,
                )
            },
            onFailure = { err ->
              onError(err.localizedMessage ?: "No se pudo cambiar la contraseña.")
            },
          )
        }
      },
      onDismiss = { resetPasswordTarget = null },
    )
  }

  deleteTarget?.let { target ->
    AlertDialog(
      onDismissRequest = { deleteTarget = null },
      title = { Text("Eliminar acceso") },
      text = {
        Text(
          "¿Seguro que querés eliminar a ${target.displayName} (${target.email})? Perderá el acceso inmediatamente."
        )
      },
      confirmButton = {
        TextButton(
          onClick = {
            val emailToDelete = target.email
            deleteTarget = null
            coroutineScope.launch {
              val res = accessRepo.deleteAuthorizedUser(emailToDelete)
              snackbarHostState.showSnackbar(
                if (res.isSuccess) "Usuario eliminado"
                else res.exceptionOrNull()?.localizedMessage ?: "No se pudo eliminar."
              )
            }
          }
        ) {
          Text("Eliminar", color = MaterialTheme.colorScheme.error)
        }
      },
      dismissButton = {
        TextButton(onClick = { deleteTarget = null }) { Text("Cancelar") }
      },
    )
  }

  shareCredentials?.let { creds ->
    ShareCredentialsDialog(
      credentials = creds,
      businessName = businessName,
      onCopy = { shareText ->
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Acceso Malvinas Pro", shareText))
        coroutineScope.launch { snackbarHostState.showSnackbar("Datos de acceso copiados") }
      },
      onShareWhatsApp = { shareText ->
        val opened =
          WhatsAppHelper.openWhatsApp(
            context = context,
            rawPhone = "",
            message = shareText,
            defaultCountryCode = defaultCountryCode,
          )
        if (!opened) {
          coroutineScope.launch {
            snackbarHostState.showSnackbar("No se pudo abrir WhatsApp en este dispositivo")
          }
        }
      },
      onDismiss = { shareCredentials = null },
    )
  }
}

@Composable
private fun AdminMetricPill(
  label: String,
  value: String,
  containerColor: androidx.compose.ui.graphics.Color,
  contentColor: androidx.compose.ui.graphics.Color,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(12.dp),
    color = containerColor,
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(
        text = value,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = contentColor,
      )
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = contentColor.copy(alpha = 0.85f),
      )
    }
  }
}

@Composable
private fun AuthorizedUserCard(
  user: AuthorizedUser,
  onToggleEnabled: (Boolean) -> Unit,
  onResetPassword: () -> Unit,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
) {
  val lastAccessLabel =
    remember(user.lastAccessAt) {
      if (user.lastAccessAt > 0L) {
        val formatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es", "AR"))
        "Último ingreso: ${formatter.format(Date(user.lastAccessAt))}"
      } else {
        "Todavía no ingresó"
      }
    }

  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border =
      BorderStroke(
        width = 1.dp,
        color =
          if (user.enabled) MaterialTheme.colorScheme.outlineVariant
          else MaterialTheme.colorScheme.error.copy(alpha = 0.45f),
      ),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text(
              text = user.displayName,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
            Surface(
              shape = RoundedCornerShape(8.dp),
              color =
                if (user.enabled) MaterialTheme.colorScheme.tertiaryContainer
                else MaterialTheme.colorScheme.errorContainer,
            ) {
              Text(
                text = if (user.enabled) "Habilitado" else "Deshabilitado",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color =
                  if (user.enabled) MaterialTheme.colorScheme.onTertiaryContainer
                  else MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
              )
            }
          }
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = user.email,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
          )
          Text(
            text = lastAccessLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          if (user.notes.isNotBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text = user.notes,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        Switch(
          checked = user.enabled,
          onCheckedChange = onToggleEnabled,
          modifier = Modifier.testTag("user_access_toggle_${user.email}"),
        )
      }

      HorizontalDivider()

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        OutlinedButton(
          onClick = onResetPassword,
          modifier = Modifier.testTag("user_reset_password_${user.email}"),
        ) {
          Icon(
            imageVector = Icons.Filled.Key,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text("Cambiar clave")
        }

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          IconButton(
            onClick = onEdit,
            modifier = Modifier.testTag("user_edit_${user.email}"),
          ) {
            Icon(
              imageVector = Icons.Filled.Edit,
              contentDescription = "Editar usuario ${user.displayName}",
            )
          }
          IconButton(
            onClick = onDelete,
            modifier = Modifier.testTag("user_delete_${user.email}"),
          ) {
            Icon(
              imageVector = Icons.Filled.DeleteOutline,
              contentDescription = "Eliminar usuario ${user.displayName}",
              tint = MaterialTheme.colorScheme.error,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun CreateAuthorizedUserDialog(
  onCreate: (
    name: String,
    email: String,
    password: String,
    notes: String,
    onError: (String) -> Unit,
  ) -> Unit,
  onDismiss: () -> Unit,
) {
  var name by remember { mutableStateOf("") }
  var email by remember { mutableStateOf("") }
  var password by remember { mutableStateOf(PasswordHasher.generateSecurePassword(10)) }
  var passwordVisible by remember { mutableStateOf(true) }
  var notes by remember { mutableStateOf("") }
  var errorText by remember { mutableStateOf<String?>(null) }
  var isSaving by remember { mutableStateOf(false) }

  AlertDialog(
    onDismissRequest = { if (!isSaving) onDismiss() },
    title = { Text("Crear nuevo usuario") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = name,
          onValueChange = { if (it.length <= 80) name = it },
          label = { Text("Nombre de la persona") },
          placeholder = { Text("Ej: Lucas Revendedor") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("create_user_name_input"),
        )
        OutlinedTextField(
          value = email,
          onValueChange = { if (it.length <= 128) email = it },
          label = { Text("Correo electrónico (Google)") },
          placeholder = { Text("ejemplo@gmail.com") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("create_user_email_input"),
        )
        OutlinedTextField(
          value = password,
          onValueChange = { if (it.length <= AccessControlConfig.MAX_PASSWORD_LENGTH) password = it },
          label = { Text("Contraseña de acceso (mín. 6)") },
          singleLine = true,
          visualTransformation =
            if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
          trailingIcon = {
            Row {
              IconButton(
                onClick = { password = PasswordHasher.generateSecurePassword(10) },
              ) {
                Icon(
                  imageVector = Icons.Filled.AutoFixHigh,
                  contentDescription = "Generar contraseña aleatoria",
                )
              }
              IconButton(onClick = { passwordVisible = !passwordVisible }) {
                Icon(
                  imageVector =
                    if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                  contentDescription =
                    if (passwordVisible) "Ocultar contraseña" else "Mostrar contraseña",
                )
              }
            }
          },
          modifier = Modifier.fillMaxWidth().testTag("create_user_password_input"),
        )
        OutlinedTextField(
          value = notes,
          onValueChange = { if (it.length <= 200) notes = it },
          label = { Text("Notas (opcional)") },
          placeholder = { Text("Ej: Equipo sucursal norte") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("create_user_notes_input"),
        )

        if (errorText != null) {
          Text(
            text = errorText!!,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          errorText = null
          isSaving = true
          onCreate(name, email, password, notes) { err ->
            isSaving = false
            errorText = err
          }
        },
        enabled = !isSaving,
        modifier = Modifier.testTag("confirm_create_user_button"),
      ) {
        if (isSaving) {
          CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.onPrimary,
          )
          Spacer(modifier = Modifier.width(8.dp))
        }
        Text("Crear acceso")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Cancelar") }
    },
  )
}

@Composable
private fun EditAuthorizedUserDialog(
  user: AuthorizedUser,
  onSave: (name: String, notes: String, onError: (String) -> Unit) -> Unit,
  onDismiss: () -> Unit,
) {
  var name by remember(user) { mutableStateOf(user.displayName) }
  var notes by remember(user) { mutableStateOf(user.notes) }
  var errorText by remember { mutableStateOf<String?>(null) }
  var isSaving by remember { mutableStateOf(false) }

  AlertDialog(
    onDismissRequest = { if (!isSaving) onDismiss() },
    title = { Text("Editar usuario") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text = user.email,
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.primary,
        )
        OutlinedTextField(
          value = name,
          onValueChange = { if (it.length <= 80) name = it },
          label = { Text("Nombre") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = notes,
          onValueChange = { if (it.length <= 200) notes = it },
          label = { Text("Notas") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        if (errorText != null) {
          Text(
            text = errorText!!,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          errorText = null
          isSaving = true
          onSave(name, notes) { err ->
            isSaving = false
            errorText = err
          }
        },
        enabled = !isSaving,
      ) {
        Text("Guardar")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Cancelar") }
    },
  )
}

@Composable
private fun ResetPasswordDialog(
  user: AuthorizedUser,
  onConfirmReset: (newPassword: String, onError: (String) -> Unit) -> Unit,
  onDismiss: () -> Unit,
) {
  var newPassword by remember { mutableStateOf(PasswordHasher.generateSecurePassword(10)) }
  var passwordVisible by remember { mutableStateOf(true) }
  var errorText by remember { mutableStateOf<String?>(null) }
  var isSaving by remember { mutableStateOf(false) }

  AlertDialog(
    onDismissRequest = { if (!isSaving) onDismiss() },
    title = { Text("Cambiar contraseña") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text =
            "Al cambiar la contraseña de ${user.displayName} (${user.email}), se le pedirá ingresar la nueva clave para continuar usando la app.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
          value = newPassword,
          onValueChange = {
            if (it.length <= AccessControlConfig.MAX_PASSWORD_LENGTH) newPassword = it
          },
          label = { Text("Nueva contraseña (mín. 6)") },
          singleLine = true,
          visualTransformation =
            if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
          trailingIcon = {
            Row {
              IconButton(
                onClick = { newPassword = PasswordHasher.generateSecurePassword(10) },
              ) {
                Icon(
                  imageVector = Icons.Filled.AutoFixHigh,
                  contentDescription = "Generar contraseña aleatoria",
                )
              }
              IconButton(onClick = { passwordVisible = !passwordVisible }) {
                Icon(
                  imageVector =
                    if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                  contentDescription =
                    if (passwordVisible) "Ocultar contraseña" else "Mostrar contraseña",
                )
              }
            }
          },
          modifier = Modifier.fillMaxWidth().testTag("reset_password_input"),
        )
        if (errorText != null) {
          Text(
            text = errorText!!,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          errorText = null
          isSaving = true
          onConfirmReset(newPassword) { err ->
            isSaving = false
            errorText = err
          }
        },
        enabled = !isSaving,
        modifier = Modifier.testTag("confirm_reset_password_button"),
      ) {
        Text("Actualizar clave")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Cancelar") }
    },
  )
}

@Composable
private fun ShareCredentialsDialog(
  credentials: ShareableCredentials,
  businessName: String,
  onCopy: (String) -> Unit,
  onShareWhatsApp: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  val appBrand = businessName.ifBlank { "Malvinas Pro Service" }
  val shareMessage =
    remember(credentials, appBrand) {
      """
      👋 ¡Hola, ${credentials.displayName}!

      🔐 Te comparto tus datos de acceso para $appBrand:
      📧 Correo: ${credentials.email}
      🔑 Contraseña: ${credentials.password}

      Pasos para entrar:
      1) Iniciá sesión con Google usando el correo ${credentials.email}.
      2) Cuando te pida la clave de acceso, escribí la contraseña de arriba.
      """.trimIndent()
    }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        if (credentials.isReset) "Contraseña actualizada" else "Credenciales listas para compartir"
      )
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
          text =
            "Guardá o compartí estos datos ahora. Por seguridad, la contraseña se guarda cifrada y no volverá a mostrarse (siempre podés generar una nueva).",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(12.dp),
          color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
        ) {
          Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Text(
              text = "Usuario: ${credentials.displayName}",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = "Correo: ${credentials.email}",
              style = MaterialTheme.typography.bodyMedium,
              fontFamily = FontFamily.Monospace,
            )
            Text(
              text = "Contraseña: ${credentials.password}",
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.Bold,
              fontFamily = FontFamily.Monospace,
              color = MaterialTheme.colorScheme.primary,
            )
          }
        }
        Button(
          onClick = { onShareWhatsApp(shareMessage) },
          modifier = Modifier.fillMaxWidth().testTag("share_credentials_whatsapp_button"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.Send,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text("Enviar por WhatsApp")
        }
        OutlinedButton(
          onClick = { onCopy(shareMessage) },
          modifier = Modifier.fillMaxWidth().testTag("copy_credentials_button"),
        ) {
          Icon(
            imageVector = Icons.Filled.ContentCopy,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text("Copiar acceso")
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) { Text("Listo") }
    },
  )
}
