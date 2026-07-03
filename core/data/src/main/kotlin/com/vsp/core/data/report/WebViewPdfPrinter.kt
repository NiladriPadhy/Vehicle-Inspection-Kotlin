package com.vsp.core.data.report

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.print.PdfPrint
import android.print.PrintAttributes
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Renders an HTML document to a paginated A4 PDF fully offline, using an off-screen [WebView] and
 * its print adapter (no system print dialog). The actual layout/write is delegated to [PdfPrint]
 * (Java, due to a Kotlin subclassing limitation). This class hops to the main looper internally,
 * so callers may invoke it from any dispatcher.
 */
@Singleton
class WebViewPdfPrinter @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** Strong references so off-screen WebViews are not GC'd before their callbacks fire. */
    private val inFlight = Collections.synchronizedSet(mutableSetOf<WebView>())

    /** Standard export location for a generated report file. */
    fun outputFile(fileName: String): File =
        File(File(context.cacheDir, "exports").apply { mkdirs() }, fileName)

    suspend fun render(html: String, outFile: File, documentName: String = "inspection-report"): File =
        suspendCancellableCoroutine { cont ->
            Handler(Looper.getMainLooper()).let { handler ->
                handler.post {
                    Log.d(TAG, "render: starting, html=${html.length} chars, out=${outFile.absolutePath}")
                    var webView: WebView? = null
                    var settled = false

                    fun finish(result: Result<File>) {
                        if (settled) return
                        settled = true
                        handler.removeCallbacksAndMessages(TIMEOUT_TOKEN)
                        webView?.let { inFlight.remove(it); it.destroy() }
                        webView = null
                        if (cont.isActive) {
                            result.fold(
                                onSuccess = { Log.d(TAG, "render: success -> ${it.absolutePath} (${it.length()} bytes)"); cont.resume(it) },
                                onFailure = { Log.e(TAG, "render: failed", it); cont.resumeWithException(it) },
                            )
                        }
                    }

                    // Watchdog so a stalled WebView can never hang the export forever.
                    handler.postAtTime({ finish(Result.failure(RuntimeException("PDF render timed out"))) }, TIMEOUT_TOKEN, android.os.SystemClock.uptimeMillis() + TIMEOUT_MS)

                    try {
                        webView = WebView(context).also { inFlight.add(it) }.apply {
                            settings.javaScriptEnabled = false
                            settings.loadWithOverviewMode = true
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView, url: String?) {
                                    Log.d(TAG, "onPageFinished, scheduling print in ${RENDER_SETTLE_MS}ms")
                                    // NOTE: must use the main Handler, not view.postDelayed — an
                                    // unattached WebView parks its posted runnables until it is
                                    // attached to a window, which never happens off-screen.
                                    handler.postDelayed({
                                        try {
                                            val attributes = PrintAttributes.Builder()
                                                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                                                .setResolution(PrintAttributes.Resolution("pdf", "pdf", 300, 300))
                                                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                                                .build()
                                            Log.d(TAG, "invoking PdfPrint")
                                            PdfPrint(attributes).print(
                                                view.createPrintDocumentAdapter(documentName),
                                                outFile,
                                                object : PdfPrint.OnResult {
                                                    override fun onSuccess(file: File) = finish(Result.success(file))
                                                    override fun onFailure(error: String) =
                                                        finish(Result.failure(RuntimeException(error)))
                                                },
                                            )
                                        } catch (t: Throwable) {
                                            finish(Result.failure(t))
                                        }
                                    }, RENDER_SETTLE_MS)
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: android.webkit.WebResourceRequest?,
                                    error: android.webkit.WebResourceError?,
                                ) {
                                    Log.w(TAG, "onReceivedError: ${error?.description}")
                                }
                            }
                        }
                        Log.d(TAG, "loading HTML into WebView")
                        webView!!.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
                    } catch (t: Throwable) {
                        finish(Result.failure(t))
                    }

                    cont.invokeOnCancellation {
                        handler.post { finish(Result.failure(RuntimeException("cancelled"))) }
                    }
                }
            }
        }

    private companion object {
        const val TAG = "WebViewPdfPrinter"
        const val RENDER_SETTLE_MS = 350L
        const val TIMEOUT_MS = 30_000L
        val TIMEOUT_TOKEN = Any()
    }
}
