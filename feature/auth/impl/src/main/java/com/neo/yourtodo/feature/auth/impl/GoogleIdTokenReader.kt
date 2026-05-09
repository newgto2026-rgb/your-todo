package com.neo.yourtodo.feature.auth.impl

import android.annotation.SuppressLint
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GoogleIdTokenReader(
    context: Context
) {
    private val credentialManager = CredentialManager.create(context)

    suspend fun readIdToken(
        context: Context,
        serverClientId: String
    ): Result<String> = withContext(Dispatchers.Main) {
        runCatching {
            requestIdToken(
                context = context,
                serverClientId = serverClientId
            )
        }
    }

    @SuppressLint("CredentialManagerSignInWithGoogle")
    private suspend fun requestIdToken(
        context: Context,
        serverClientId: String
    ): String {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setServerClientId(serverClientId)
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
        val response = try {
            credentialManager.getCredential(
                context = context,
                request = request
            )
        } catch (error: NoCredentialException) {
            throw IllegalStateException("No Google credential was returned.", error)
        }
        val credential = response.credential
        if (
            credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            throw IllegalStateException("Google ID token credential was not returned.")
        }
        return try {
            GoogleIdTokenCredential.createFrom(credential.data).idToken
        } catch (error: GoogleIdTokenParsingException) {
            throw IllegalStateException("Unable to parse Google ID token.", error)
        }
    }
}
