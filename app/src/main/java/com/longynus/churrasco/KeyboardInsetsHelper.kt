package com.longynus.churrasco

import android.app.Activity
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ScrollView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

object KeyboardInsetsHelper {
    fun setup(
        activity: Activity,
        hideBottomNavigation: Boolean = false
    ) {
        activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        val root = activity.findViewById<View>(R.id.rootLayout) ?: return
        val scrollView = findFirstScrollView(root)
        val bottomNavigation = activity.findViewById<View?>(R.id.bottomNavigation)

        val target = scrollView ?: root
        val baseBottomPadding = target.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val navInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val keyboardVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            val keyboardBottom = (imeInsets.bottom - navInsets.bottom).coerceAtLeast(0)

            target.setPadding(
                target.paddingLeft,
                target.paddingTop,
                target.paddingRight,
                baseBottomPadding + if (keyboardVisible) keyboardBottom + 24.dp(activity) else 0
            )

            if (hideBottomNavigation && bottomNavigation != null) {
                bottomNavigation.visibility = if (keyboardVisible) View.GONE else View.VISIBLE
            }

            if (keyboardVisible && scrollView != null) {
                scrollFocusedInputIntoView(activity, scrollView)
            }

            insets
        }

        ViewCompat.requestApplyInsets(root)
    }

    private fun scrollFocusedInputIntoView(activity: Activity, scrollView: ScrollView) {
        scrollView.postDelayed({
            val focused = activity.currentFocus ?: return@postDelayed
            if (!isDescendantOf(focused, scrollView)) return@postDelayed

            val rect = Rect()
            focused.getDrawingRect(rect)
            scrollView.offsetDescendantRectToMyCoords(focused, rect)

            val desiredScroll = rect.bottom - scrollView.height + scrollView.paddingBottom
            if (desiredScroll > scrollView.scrollY) {
                scrollView.smoothScrollTo(0, desiredScroll)
            }
        }, 120)
    }

    private fun findFirstScrollView(view: View): ScrollView? {
        if (view is ScrollView) return view
        if (view !is ViewGroup) return null

        for (index in 0 until view.childCount) {
            findFirstScrollView(view.getChildAt(index))?.let { return it }
        }

        return null
    }

    private fun isDescendantOf(child: View, parent: ViewGroup): Boolean {
        var current = child.parent
        while (current is View) {
            if (current == parent) return true
            current = current.parent
        }
        return false
    }

    private fun Int.dp(activity: Activity): Int =
        (this * activity.resources.displayMetrics.density).toInt()
}
