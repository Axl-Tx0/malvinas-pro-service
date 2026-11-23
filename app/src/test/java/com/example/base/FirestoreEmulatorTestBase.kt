package com.example.base

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
@LooperMode(LooperMode.Mode.INSTRUMENTATION_TEST)
abstract class FirestoreEmulatorTestBase {

  protected lateinit var firestore: FirebaseFirestore
  protected lateinit var auth: FirebaseAuth
  protected lateinit var databaseId: String

  @Before
  open fun setUpFirebase() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val resId = context.resources.getIdentifier("firestore_database_id", "string", context.packageName)
    databaseId =
      if (resId != 0) context.getString(resId)
      else "ai-studio-android-malvinas-1301e405-a9b2-4cff-a42d-6b6bbe5185b9"

    val app =
      if (FirebaseApp.getApps(context).isEmpty()) {
        val newApp =
          FirebaseApp.initializeApp(
            context,
            FirebaseOptions.Builder()
              .setApplicationId("com.aistudio.malvinaspro.kmpqrz")
              .setProjectId(PROJECT_ID)
              .setApiKey("fake-api-key-for-emulator")
              .build(),
          )
        val newFirestore = FirebaseFirestore.getInstance(newApp, databaseId)
        newFirestore.useEmulator(EMULATOR_HOST, FIRESTORE_PORT)
        newFirestore.firestoreSettings =
          FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
            .build()

        val newAuth = FirebaseAuth.getInstance(newApp)
        newAuth.useEmulator(EMULATOR_HOST, AUTH_PORT)
        newApp
      } else {
        FirebaseApp.getInstance()
      }

    firestore = FirebaseFirestore.getInstance(app, databaseId)
    auth = FirebaseAuth.getInstance(app)
  }

  @After
  open fun tearDownFirebase() {
    auth.signOut()
  }

  protected suspend fun signInTestUser(email: String): String =
    withContext(Dispatchers.IO) {
      withTimeout(AUTH_TIMEOUT_MS) {
        val normalized = email.trim().lowercase()
        val fakeGoogleIdToken =
          """{"sub":"sub_$normalized","email":"$normalized","email_verified":true}"""
        val credential = GoogleAuthProvider.getCredential(fakeGoogleIdToken, null)
        val result = auth.signInWithCredential(credential).await()
        checkNotNull(result.user?.uid) { "User auth failed" }
      }
    }

  protected fun Throwable.unwrapFirestoreException(): FirebaseFirestoreException? {
    var current: Throwable? = this
    while (current != null) {
      if (current is FirebaseFirestoreException) return current
      current = current.cause
    }
    return null
  }

  private companion object {
    const val EMULATOR_HOST = "127.0.0.1"
    const val FIRESTORE_PORT = 8085
    const val AUTH_PORT = 9099
    const val PROJECT_ID = "demo-no-project"
    const val DEFAULT_PASSWORD = "password123"
    const val AUTH_TIMEOUT_MS = 5000L
  }
}
