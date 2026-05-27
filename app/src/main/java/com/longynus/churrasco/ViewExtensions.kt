package com.longynus.churrasco

import android.app.AlertDialog
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.snackbar.Snackbar

/**
 * Exibe o overlay de loading (ProgressBar) dentro de uma view.
 */
fun View.showLoading() {
    this.findViewById<View>(R.id.loadingOverlay)?.visibility = View.VISIBLE
}

/**
 * Esconde o overlay de loading (ProgressBar).
 */
fun View.hideLoading() {
    this.findViewById<View>(R.id.loadingOverlay)?.visibility = View.GONE
}

fun View.showConnectionState(message: String, onRetry: () -> Unit) {
    findViewById<TextView>(R.id.txtConnectionMessage)?.text = message
    findViewById<Button>(R.id.btnRetryConnection)?.setOnClickListener {
        hideConnectionState()
        onRetry()
    }
    findViewById<View>(R.id.connectionStateOverlay)?.visibility = View.VISIBLE
}

fun View.hideConnectionState() {
    findViewById<View>(R.id.connectionStateOverlay)?.visibility = View.GONE
}

/**
 * Exibe um diálogo de erro com o [message] fornecido.
 */
fun View.showErrorDialog(message: String) {
    AlertDialog.Builder(this.context)
        .setTitle("Ops")
        .setMessage(message)
        .setPositiveButton("OK", null)
        .show()
}

/**
 * Exibe um Toast de mensagem curta.
 */
fun View.showToast(message: String) {
    Toast.makeText(this.context, message, Toast.LENGTH_SHORT).show()
}

/**
 * Exibe um Snackbar de mensagem.
 */
fun View.showSnackbar(message: String, length: Int = Snackbar.LENGTH_SHORT) {
    Snackbar.make(this, message, length).show()
}
