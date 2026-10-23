package com.longynus.churrasco.ui

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.longynus.churrasco.KeyboardInsetsHelper
import com.longynus.churrasco.R
import com.longynus.churrasco.TopBarHelper
import com.longynus.churrasco.data.ApiError
import com.longynus.churrasco.hideConnectionState
import com.longynus.churrasco.hideLoading
import com.longynus.churrasco.showConnectionState
import com.longynus.churrasco.showLoading
import com.longynus.churrasco.showSnackbar
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Base das telas montadas com [Ui.kt]: barra superior, conteúdo rolável, barra de ação
 * na parte de baixo e os estados de carregando/sem conexão. As chamadas à API rodam em
 * corrotinas ligadas ao ciclo de vida, então não há callbacks soltos depois que a tela fecha.
 */
abstract class ScreenActivity : AppCompatActivity() {

    protected lateinit var root: View
    protected lateinit var content: LinearLayout
    protected lateinit var bottomBar: LinearLayout
    protected lateinit var scroll: ScrollView
    private lateinit var swipe: SwipeRefreshLayout

    protected fun setupScreen(title: String, onRefresh: (() -> Unit)? = null) {
        setContentView(R.layout.activity_screen)
        root = findViewById(R.id.rootLayout)
        content = findViewById(R.id.content)
        bottomBar = findViewById(R.id.bottomBar)
        scroll = findViewById(R.id.scroll)
        swipe = findViewById(R.id.swipeRefresh)
        TopBarHelper.setup(this, title)
        KeyboardInsetsHelper.setup(this)
        swipe.isEnabled = onRefresh != null
        swipe.setColorSchemeResources(R.color.mainPrimary)
        swipe.setOnRefreshListener { onRefresh?.invoke() }
    }

    protected fun setTitleText(title: String) {
        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.topToolbar).title = title
    }

    protected fun stopRefreshing() { swipe.isRefreshing = false }

    /** Recria o conteúdo e a barra inferior. */
    protected fun render(bottom: (LinearLayout.() -> Unit)? = null, body: LinearLayout.() -> Unit) {
        content.removeAllViews()
        content.body()
        bottomBar.removeAllViews()
        if (bottom != null) {
            bottomBar.bottom()
            bottomBar.getChildAt(0)?.let { (it.layoutParams as? LinearLayout.LayoutParams)?.topMargin = 0 }
        }
        bottomBar.visible(bottom != null)
    }

    protected fun <T> collect(flow: Flow<T>, block: suspend (T) -> Unit) {
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) { flow.collect { block(it) } } }
    }

    /**
     * Roda [block] mostrando o carregando. Em falha: sem internet vira a tela de
     * conexão com "Tentar de novo"; erro do servidor vira um diálogo com a mensagem dele.
     */
    protected fun <T> call(
        block: suspend () -> Result<T>,
        retry: Boolean = false,
        silent: Boolean = false,
        onSuccess: (T) -> Unit,
    ) {
        lifecycleScope.launch {
            if (!silent) root.showLoading()
            val result = block()
            root.hideLoading()
            stopRefreshing()
            result.onSuccess(onSuccess).onFailure { error -> showError(error, if (retry) ({ call(block, retry, silent, onSuccess) }) else null) }
        }
    }

    protected fun showError(error: Throwable, retry: (() -> Unit)? = null) {
        val api = error as? ApiError
        if (api?.isNetwork == true && retry != null) {
            root.showConnectionState(getString(R.string.error_network_retry)) { root.hideConnectionState(); retry() }
            return
        }
        val message = api?.serverMessage ?: getString(if (api?.isNetwork == true) R.string.error_network else R.string.error_generic)
        AlertDialog.Builder(this).setTitle(R.string.error_title).setMessage(message).setPositiveButton(R.string.ok, null).show()
    }

    protected fun toast(message: String) = root.showSnackbar(message)

    protected fun confirm(title: String, message: String, action: String, danger: Boolean = false, onConfirm: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(action) { _, _ -> onConfirm() }
            .show()
            .also { dialog ->
                if (danger) dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(getColor(R.color.mainDanger))
            }
    }
}
