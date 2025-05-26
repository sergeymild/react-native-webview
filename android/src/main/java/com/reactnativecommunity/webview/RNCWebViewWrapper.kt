package com.reactnativecommunity.webview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewParent
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

/**
 * A [FrameLayout] container to hold the [RNCWebView].
 * We need this to prevent WebView crash when the WebView is out of viewport and
 * [com.facebook.react.views.view.ReactViewGroup] clips the canvas.
 * The WebView will then create an empty offscreen surface and NPE.
 */
@SuppressLint("ClickableViewAccessibility")
class RNCWebViewWrapper(context: Context, webView: RNCWebView) : FrameLayout(context) {
  init {
    // We make the WebView as transparent on top of the container,
    // and let React Native sets background color for the container.
    webView.setBackgroundColor(Color.TRANSPARENT)
    val refresh = SwipeRefreshLayout(context)
    refresh.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
    refresh.addView(webView)
    addView(refresh)
    refresh.setOnRefreshListener { webView.reload() }
    refresh.isEnabled = false

    var downX = 0f
    var downY = 0f
    val touchSlop = ViewConfiguration.get(webView.context).scaledTouchSlop

    webView.setOnTouchListener { v, event ->
      when (event.action) {
        MotionEvent.ACTION_DOWN -> {
          downX = event.x
          downY = event.y
          //refresh.isEnabled = true // Assume vertical until proven otherwise
          v.parent.requestDisallowInterceptTouchEvent(false)
        }

        MotionEvent.ACTION_MOVE -> {
          val deltaX = Math.abs(event.x - downX)
          val deltaY = Math.abs(event.y - downY)

          if (deltaY > touchSlop && deltaY > deltaX) {
            if (webView.canScrollVertically(-1)) {
              // WebView может скроллиться вверх => вложенный скролл
              v.parent.requestDisallowInterceptTouchEvent(true)
            } else {
              // WebView уже на самом верху => разрешаем SwipeRefresh
              v.parent.requestDisallowInterceptTouchEvent(false)
            }
          }
        }

        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
//          refresh.isEnabled = true
          v.parent.requestDisallowInterceptTouchEvent(false)
        }
      }
      false
    }
  }

  val webView: RNCWebView
    get() {
      val group = (getChildAt(0) as ViewGroup)
      if (group.getChildAt(1) !is RNCWebView && group.getChildAt(0) is RNCWebView) {
        val v = group.getChildAt(0)
        group.removeViewAt(0)
        group.addView(v)
      }
      return group.getChildAt(1) as RNCWebView
    }

  companion object {
    /**
     * A helper to get react tag id by given WebView
     */
    @JvmStatic
    fun getReactTagFromWebView(webView: WebView): Int {
      // It is expected that the webView is enclosed by [RNCWebViewWrapper] as the first child.
      // Therefore, it must have a parent, and the parent ID is the reactTag.
      // In exceptional cases, such as receiving WebView messaging after the view has been unmounted,
      // the WebView will not have a parent.
      // In this case, we simply return -1 to indicate that it was not found.
      var parent = webView.parent as? ViewParent
      while (parent !is RNCWebViewWrapper && parent != null) {
        parent = parent?.parent
      }
      return (parent as? ViewGroup)?.id ?: -1
    }
  }
}
