package com.longynus.churrasco.ui

import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.longynus.churrasco.BottomNavHelper
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
 * Base das telas montadas com [Ui.kt]: barra superior (ou a barra de baixo, nas quatro telas
 * principais), conteúdo rolável, a ação fixa no rodapé e os estados de carregando/sem conexão.
 * As chamadas à API rodam em corrotinas ligadas ao ciclo de vida, então não há callbacks
 * soltos depois que a tela fecha.
 */
abstract class ScreenActivity : AppCompatActivity() {

    protected lateinit var root: View
    protected lateinit var content: LinearLayout
    protected lateinit var bottomBar: LinearLayout
    protected lateinit var scroll: ScrollView
    protected lateinit var fab: ExtendedFloatingActionButton
    private lateinit var swipe: SwipeRefreshLayout
    private lateinit var bottomNav: BottomNavigationView

    /**
     * Monta o esqueleto. Com [navItem], a tela é um dos quatro destinos da barra de baixo: sem
     * barra superior nem voltar, e com a barra de baixo marcando [navItem].
     */
    protected fun setupScreen(title: String, onRefresh: (() -> Unit)? = null, @IdRes navItem: Int? = null) {
        setContentView(R.layout.activity_screen)
        root = findViewById(R.id.rootLayout)
        content = findViewById(R.id.content)
        bottomBar = findViewById(R.id.bottomBar)
        scroll = findViewById(R.id.scroll)
        swipe = findViewById(R.id.swipeRefresh)
        fab = findViewById(R.id.fab)
        bottomNav = findViewById(R.id.bottomNavigation)
        val toolbar = findViewById<View>(R.id.topToolbar)
        if (navItem == null) {
            TopBarHelper.setup(this, title)
        } else {
            toolbar.visibility = View.GONE
            bottomNav.visibility = View.VISIBLE
            BottomNavHelper.setup(this, bottomNav, navItem)
            // Sem a barra superior, o conteúdo desce a altura da barra de status.
            val baseTop = content.paddingTop
            ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
                view.setPadding(view.paddingLeft, baseTop + insets.getInsets(WindowInsetsCompat.Type.statusBars()).top, view.paddingRight, view.paddingBottom)
                insets
            }
        }
        // Android 15+ desenha atrás da barra de navegação: a coluna sobe a altura dela (ou a do
        // teclado). Com a barra de baixo, ela mesma cuida da barra de navegação e some com o teclado.
        @Suppress("DEPRECATION")
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        val column = bottomBar.parent as View
        ViewCompat.setOnApplyWindowInsetsListener(column) { view, insets ->
            val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val keyboard = ime > nav
            if (navItem != null) bottomNav.visibility = if (keyboard) View.GONE else View.VISIBLE
            val bottom = if (navItem != null && !keyboard) 0 else maxOf(nav, ime)
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, bottom)
            insets
        }
        ViewCompat.requestApplyInsets(column)
        // O botão flutuante fica acima da barra de baixo e da ação fixa.
        column.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val above = (if (bottomNav.visibility == View.VISIBLE) bottomNav.height else 0) +
                (if (bottomBar.visibility == View.VISIBLE) bottomBar.height else 0) + column.paddingBottom
            val margin = above + dp(16)
            if ((fab.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin != margin) {
                fab.post { fab.updateLayoutParams<ViewGroup.MarginLayoutParams> { bottomMargin = margin } }
            }
        }
        swipe.isEnabled = onRefresh != null
        swipe.setColorSchemeResources(R.color.brasa)
        swipe.setOnRefreshListener { onRefresh?.invoke() }
    }

    protected fun setTitleText(title: String) {
        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.topToolbar).title = title
    }

    /** Mostra o botão flutuante; ele recolhe para o redondo ao rolar para baixo. */
    protected fun showFab(label: String, @DrawableRes icon: Int = R.drawable.ic_plus, @IdRes id: Int? = null, onClick: () -> Unit) {
        fab.text = label
        fab.setIconResource(icon)
        fab.contentDescription = label
        id?.let { fab.id = it }
        fab.visibility = View.VISIBLE
        fab.setOnClickListener { onClick() }
        scroll.setOnScrollChangeListener { _, _, y, _, oldY -> if (y > oldY + 8) fab.shrink() else if (y < oldY - 8) fab.extend() }
    }

    protected fun stopRefreshing() { swipe.isRefreshing = false }

    /** Recria o conteúdo e a ação fixa no rodapé. */
    protected fun render(bottom: (LinearLayout.() -> Unit)? = null, body: LinearLayout.() -> Unit) {
        content.removeAllViews()
        content.body()
        setBottom(bottom)
    }

    /** Troca só a ação fixa no rodapé (null esconde). */
    protected fun setBottom(bottom: (LinearLayout.() -> Unit)?) {
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
        MaterialAlertDialogBuilder(this).setTitle(R.string.error_title).setMessage(message).setPositiveButton(R.string.ok, null).show()
    }

    protected fun toast(message: String) = root.showSnackbar(message)

    protected fun confirm(title: String, message: String, action: String, danger: Boolean = false, onConfirm: () -> Unit) {
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(action) { _, _ -> onConfirm() }
            .show()
            .also { dialog ->
                if (danger) dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(getColor(R.color.danger))
            }
    }
}
