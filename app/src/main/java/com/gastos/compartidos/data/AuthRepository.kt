package com.gastos.compartidos.data

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

object AuthRepository {

    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    val currentUser: FirebaseUser? get() = auth.currentUser

    suspend fun signIn(email: String, password: String) {
        auth.signInWithEmailAndPassword(email.trim(), password).await()
    }

    suspend fun register(name: String, email: String, password: String) {
        val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
        val user = result.user ?: error("No se pudo crear el usuario")
        user.updateProfile(userProfileChangeRequest { displayName = name.trim() }).await()
        saveUserProfile(user.uid, name.trim(), email.trim())
    }

    /**
     * Inicio de sesión con Google mediante Credential Manager.
     * Requiere que Google esté habilitado como proveedor en Firebase y que el
     * google-services.json incluya el cliente OAuth web (default_web_client_id).
     */
    suspend fun signInWithGoogle(context: Context) {
        val resId = context.resources.getIdentifier(
            "default_web_client_id", "string", context.packageName
        )
        check(resId != 0) {
            "Google Sign-In no está configurado: habilita Google en Firebase, " +
                "agrega la huella SHA-1 y vuelve a descargar google-services.json"
        }
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(context.getString(resId))
            .setFilterByAuthorizedAccounts(false)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()
        val result = CredentialManager.create(context).getCredential(context, request)
        val googleCredential = GoogleIdTokenCredential.createFrom(result.credential.data)
        val firebaseCredential = GoogleAuthProvider.getCredential(googleCredential.idToken, null)
        val user = auth.signInWithCredential(firebaseCredential).await().user
            ?: error("No se pudo iniciar sesión con Google")
        saveUserProfile(
            uid = user.uid,
            name = user.displayName ?: googleCredential.displayName.orEmpty(),
            email = user.email.orEmpty(),
        )
    }

    private suspend fun saveUserProfile(uid: String, name: String, email: String) {
        db.collection("users").document(uid)
            .set(mapOf("name" to name, "email" to email), SetOptions.merge())
            .await()
    }

    fun signOut() = auth.signOut()
}
