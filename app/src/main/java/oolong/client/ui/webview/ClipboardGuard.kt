package oolong.client.ui.webview

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.webkit.WebView
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import oolong.client.R
import oolong.client.ui.designsystem.AppDialog
import oolong.client.ui.designsystem.AppDialogs
import oolong.client.ui.designsystem.theme.Symbols

/**
 * Keeps web pages from writing the clipboard unasked, as a page does to hand an app a link when
 * its 「打开 App」 is pressed. A page's own copying, by `execCommand("copy")` or the async
 * clipboard, goes to the app instead, which asks once per site and writes what was meant if
 * allowed. Copying what one selected oneself goes through neither, and is left alone.
 */
internal object ClipboardGuard {
    fun install(webView: WebView) {
        // Older web views can't run a script before the page's own; pages there copy unasked
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT) &&
            WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)
        ) {
            guard(webView)
        }
    }

    @SuppressLint("RequiresFeature") // Checked by install, before
    private fun guard(webView: WebView) {
        WebViewCompat.addWebMessageListener(webView, Bridge, setOf("*")) { view, message, origin, _, reply ->
            val text = message.data ?: return@addWebMessageListener
            val host = origin.toString().toUri().host ?: view.url?.toUri()?.host
            if (host == null) {
                reply.postMessage(Denied)
                return@addWebMessageListener
            }
            val context = view.context
            AppDialogs.ask(
                context,
                AppDialog.Permission(
                    key = permissionKey(host, PERMISSION_CLIPBOARD_COPY),
                    title = context.getString(R.string.title_ask_permission_clipboard_copy, host),
                    icon = Symbols.ContentCopy,
                ) { granted, _ ->
                    if (granted) context.getSystemService<ClipboardManager>()?.setPrimaryClip(ClipData.newPlainText(null, text))
                    reply.postMessage(if (granted) Allowed else Denied)
                }
            )
        }
        WebViewCompat.addDocumentStartJavaScript(webView, Script, setOf("*"))
    }

    private const val Bridge = "OolongClipboard"
    private const val Allowed = "1"
    private const val Denied = "0"

    /**
     * Runs before the page's own scripts in every frame. What a page meant to copy is what its copy
     * handlers set on the event, or else what it selected; the copy is reported done at once, as
     * the answer may take longer than the page's patience.
     */
    private val Script = """
        (function () {
          var bridge = window.$Bridge;
          if (!bridge || window.__oolongClipboardGuard) return;
          window.__oolongClipboardGuard = true;
          var waiting = [];
          bridge.onmessage = function (e) { var done = waiting.shift(); if (done) done(e.data === '$Allowed'); };
          function ask(text) {
            return new Promise(function (resolve) { waiting.push(resolve); bridge.postMessage(String(text)); });
          }
          function denied() { return new DOMException('Write permission denied.', 'NotAllowedError'); }
          function selected() {
            var el = document.activeElement;
            if (el && (el.tagName === 'TEXTAREA' || el.tagName === 'INPUT') && typeof el.selectionStart === 'number') {
              return el.value.substring(el.selectionStart, el.selectionEnd);
            }
            var s = window.getSelection();
            return s ? s.toString() : '';
          }
          var exec = Document.prototype.execCommand;
          Document.prototype.execCommand = function (command) {
            if (String(command).toLowerCase() !== 'copy') return exec.apply(this, arguments);
            var text = selected();
            try {
              var data = new DataTransfer();
              var event = new ClipboardEvent('copy', { clipboardData: data, bubbles: true, cancelable: true });
              (document.activeElement || document.body || document).dispatchEvent(event);
              text = data.getData('text/plain') || text;
            } catch (e) {}
            if (text) ask(text);
            return true;
          };
          var clipboard = navigator.clipboard;
          if (!clipboard) return;
          Object.defineProperty(clipboard, 'writeText', {
            configurable: true,
            value: function (text) {
              return ask(text).then(function (ok) { if (!ok) throw denied(); });
            },
          });
          Object.defineProperty(clipboard, 'write', {
            configurable: true,
            value: function (items) {
              var item = items && items[0];
              if (!item || item.types.indexOf('text/plain') < 0) return Promise.reject(denied());
              return item.getType('text/plain')
                .then(function (blob) { return blob.text(); })
                .then(ask)
                .then(function (ok) { if (!ok) throw denied(); });
            },
          });
        })();
    """.trimIndent()
}
