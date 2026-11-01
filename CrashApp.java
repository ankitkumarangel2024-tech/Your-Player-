package __PKG__;

import android.app.Application;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;

public class CrashApp extends Application {
  @Override
  public void onCreate() {
    super.onCreate();
    final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
    Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
      @Override
      public void uncaughtException(Thread t, Throwable e) {
        try {
          StringWriter sw = new StringWriter();
          e.printStackTrace(new PrintWriter(sw));
          String text = "Your Player crash\nAndroid " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ") "
              + Build.MANUFACTURER + " " + Build.MODEL + "\nthread: " + t.getName() + "\n\n" + sw.toString();
          if (Build.VERSION.SDK_INT >= 29) {
            ContentValues v = new ContentValues();
            v.put(MediaStore.MediaColumns.DISPLAY_NAME, "vibeplayer-crash-" + System.currentTimeMillis() + ".txt");
            v.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
            v.put(MediaStore.MediaColumns.RELATIVE_PATH, "Download");
            Uri u = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (u != null) {
              OutputStream o = getContentResolver().openOutputStream(u);
              o.write(text.getBytes("UTF-8"));
              o.close();
            }
          }
        } catch (Throwable x) {
        }
        if (prev != null) {
          prev.uncaughtException(t, e);
        }
      }
    });
  }
}
