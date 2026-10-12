package com.gastos.compartidos.ui.groups

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.gastos.compartidos.R
import com.gastos.compartidos.data.AuthRepository

@Composable
fun AccountDeletionDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var failed by remember { mutableStateOf(false) }
    val user = AuthRepository.currentUser
    val subject = stringResource(R.string.deletion_subject)
    val body = stringResource(R.string.deletion_email_body, user?.email.orEmpty(), user?.uid.orEmpty())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_account)) },
        text = {
            Column {
                Text(stringResource(R.string.deletion_explanation))
                SelectionContainer { Text("lmignario@gmail.com", color = MaterialTheme.colorScheme.primary) }
                if (failed) Text(stringResource(R.string.deletion_error), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val uri = Uri.parse("mailto:lmignario@gmail.com?subject=${Uri.encode(subject)}&body=${Uri.encode(body)}")
                failed = runCatching { context.startActivity(Intent(Intent.ACTION_SENDTO, uri)) }.isFailure
            }) { Text(stringResource(R.string.deletion_send)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
