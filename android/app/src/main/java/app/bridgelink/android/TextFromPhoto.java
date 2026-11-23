package app.bridgelink.android;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import app.bridgelink.android.protocol.TextCap;

/** Reads words from a picture on the phone. No network. The model is inside the app. */
public final class TextFromPhoto {
    public interface Done {
        void text(String value);

        void failed();
    }

    private static final ExecutorService IO = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "bridgelink-ocr");
        thread.setDaemon(true);
        return thread;
    });

    private TextFromPhoto() {}

    public static void read(Context context, Uri uri, Done done) {
        Context app = context.getApplicationContext();
        IO.execute(() -> {
            TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
            Bitmap bitmap = null;
            try (InputStream in = app.getContentResolver().openInputStream(uri)) {
                bitmap = BitmapFactory.decodeStream(in);
                if (bitmap == null) {
                    recognizer.close();
                    done.failed();
                    return;
                }
                Bitmap held = bitmap;
                recognizer.process(InputImage.fromBitmap(held, 0))
                        .addOnSuccessListener(result -> {
                            held.recycle();
                            recognizer.close();
                            done.text(TextCap.trimCap(result.getText()));
                        })
                        .addOnFailureListener(error -> {
                            held.recycle();
                            recognizer.close();
                            done.failed();
                        });
            } catch (Exception ignored) {
                if (bitmap != null) bitmap.recycle();
                try {
                    recognizer.close();
                } catch (Exception ignoredClose) {
                }
                done.failed();
            }
        });
    }
}
