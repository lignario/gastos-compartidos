package com.gastos.compartidos.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.gastos.compartidos.data.AuthRepository
import com.gastos.compartidos.R
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(onLoggedIn: () -> Unit) {
    var isRegister by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var loading by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        Text(
            if (isRegister) stringResource(R.string.create_account) else stringResource(R.string.sign_in),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
        )

        if (isRegister) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.your_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer8()
        }
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text(stringResource(R.string.email)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer8()
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.password)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )

        error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        Button(
            onClick = {
                error = null
                if (email.isBlank() || password.isBlank() || (isRegister && name.isBlank())) {
                    error = context.getString(R.string.fill_all_fields)
                    return@Button
                }
                loading = true
                scope.launch {
                    try {
                        if (isRegister) {
                            AuthRepository.register(name, email, password)
                        } else {
                            AuthRepository.signIn(email, password)
                        }
                        onLoggedIn()
                    } catch (e: Exception) {
                        error = e.localizedMessage ?: context.getString(R.string.generic_error)
                    } finally {
                        loading = false
                    }
                }
            },
            enabled = !loading,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp)
                .height(48.dp),
        ) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.height(24.dp))
            } else {
                Text(if (isRegister) stringResource(R.string.sign_up) else stringResource(R.string.enter))
            }
        }

        TextButton(onClick = { isRegister = !isRegister; error = null }) {
            Text(
                if (isRegister) stringResource(R.string.already_have_account)
                else stringResource(R.string.no_account)
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        OutlinedButton(
            onClick = {
                error = null
                loading = true
                scope.launch {
                    try {
                        AuthRepository.signInWithGoogle(context)
                        onLoggedIn()
                    } catch (e: Exception) {
                        error = e.localizedMessage ?: context.getString(R.string.google_sign_in_failed)
                    } finally {
                        loading = false
                    }
                }
            },
            enabled = !loading,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            Text(stringResource(R.string.continue_with_google))
        }
    }
}

@Composable
private fun Spacer8() {
    androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(8.dp))
}
