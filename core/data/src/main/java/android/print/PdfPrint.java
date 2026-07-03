package android.print;

import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;

import java.io.File;

/**
 * Drives a {@link PrintDocumentAdapter} (e.g. from a WebView) through layout + write to produce a
 * PDF file with no system print dialog.
 *
 * <p>Declared in the {@code android.print} package on purpose: the constructors of
 * {@code PrintDocumentAdapter.LayoutResultCallback} / {@code WriteResultCallback} are
 * package-private, so a subclass must live in the same package. This is the well-known "PdfPrint"
 * pattern for offline HTML-to-PDF rendering. Must be called on the main thread.
 */
public final class PdfPrint {

    public interface OnResult {
        void onSuccess(File file);

        void onFailure(String error);
    }

    private final PrintAttributes attributes;

    public PdfPrint(PrintAttributes attributes) {
        this.attributes = attributes;
    }

    public void print(final PrintDocumentAdapter adapter, final File outFile, final OnResult callback) {
        adapter.onLayout(null, attributes, null, new PrintDocumentAdapter.LayoutResultCallback() {
            @Override
            public void onLayoutFinished(PrintDocumentInfo info, boolean changed) {
                try {
                    File parent = outFile.getParentFile();
                    if (parent != null) {
                        parent.mkdirs();
                    }
                    final ParcelFileDescriptor fd = ParcelFileDescriptor.open(
                            outFile,
                            ParcelFileDescriptor.MODE_CREATE
                                    | ParcelFileDescriptor.MODE_TRUNCATE
                                    | ParcelFileDescriptor.MODE_READ_WRITE);
                    adapter.onWrite(
                            new PageRange[]{PageRange.ALL_PAGES},
                            fd,
                            new CancellationSignal(),
                            new PrintDocumentAdapter.WriteResultCallback() {
                                @Override
                                public void onWriteFinished(PageRange[] pages) {
                                    closeQuietly(fd);
                                    callback.onSuccess(outFile);
                                }

                                @Override
                                public void onWriteFailed(CharSequence error) {
                                    closeQuietly(fd);
                                    callback.onFailure("PDF write failed: " + error);
                                }

                                @Override
                                public void onWriteCancelled() {
                                    closeQuietly(fd);
                                    callback.onFailure("PDF write cancelled");
                                }
                            });
                } catch (Exception e) {
                    callback.onFailure("PDF open failed: " + e.getMessage());
                }
            }

            @Override
            public void onLayoutFailed(CharSequence error) {
                callback.onFailure("PDF layout failed: " + error);
            }
        }, null);
    }

    private static void closeQuietly(ParcelFileDescriptor fd) {
        try {
            fd.close();
        } catch (Exception ignored) {
        }
    }
}
