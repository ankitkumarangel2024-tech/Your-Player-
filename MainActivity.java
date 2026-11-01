package __PKG__;

import android.app.PictureInPictureParams;
import androidx.activity.OnBackPressedCallback;
import androidx.lifecycle.Lifecycle;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.util.Rational;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebView;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
  public static volatile boolean pipArmed = false;
  public static volatile int ratio = 178;
  public static volatile boolean playerOpen = false;
  static final int REQ_VIDEO = 7731;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    registerPlugin(VibePlugin.class);
    super.onCreate(savedInstanceState);
    try {
      if (Build.VERSION.SDK_INT >= 28) {
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        getWindow().setAttributes(lp);
      }
      setBars(false);
    } catch (Throwable t) {
    }
    try {
      getBridge().getWebView().setBackgroundColor(0);
    } catch (Throwable t) {
    }
    getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
      @Override
      public void handleOnBackPressed() {
        if (playerOpen) {
          runJs("window.__back&&window.__back()");
        } else {
          setEnabled(false);
          getOnBackPressedDispatcher().onBackPressed();
          setEnabled(true);
        }
      }
    });
  }

  public String videoPermission() {
    return Build.VERSION.SDK_INT >= 33 ? "android.permission.READ_MEDIA_VIDEO"
        : "android.permission.READ_EXTERNAL_STORAGE";
  }

  public boolean hasVideoAccess() {
    if (Build.VERSION.SDK_INT < 23) {
      return true;
    }
    return checkSelfPermission(videoPermission()) == PackageManager.PERMISSION_GRANTED;
  }

  public void askVideoAccess() {
    if (Build.VERSION.SDK_INT >= 23) {
      requestPermissions(new String[] {videoPermission()}, REQ_VIDEO);
    }
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if (requestCode == REQ_VIDEO) {
      runJs("window.__vperm&&window.__vperm(" + hasVideoAccess() + ")");
    }
  }

  public void runJs(final String js) {
    try {
      if (getBridge() != null && getBridge().getWebView() != null) {
        final WebView w = getBridge().getWebView();
        w.post(new Runnable() {
          @Override
          public void run() {
            w.evaluateJavascript(js, null);
          }
        });
      }
    } catch (Throwable t) {
    }
  }

  public void setBars(boolean immersive) {
    try {
      View d = getWindow().getDecorView();
      if (immersive) {
        d.setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
      } else {
        getWindow().setStatusBarColor(0xFF04060C);
        getWindow().setNavigationBarColor(0xFF04060C);
        d.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
      }
    } catch (Throwable t) {
    }
  }

  public void goPip() {
    if (Build.VERSION.SDK_INT >= 26) {
      try {
        PictureInPictureParams p = new PictureInPictureParams.Builder()
            .setAspectRatio(new Rational(ratio, 100)).build();
        enterPictureInPictureMode(p);
      } catch (Throwable t) {
      }
    }
  }

  private boolean fg = false;
  private final android.os.Handler hh = new android.os.Handler(android.os.Looper.getMainLooper());

  private void pauseIfHidden() {
    boolean pip = false;
    if (Build.VERSION.SDK_INT >= 24) {
      pip = isInPictureInPictureMode();
    }
    if (!pip && !fg && VibePlugin.inst != null) {
      VibePlugin.inst.pausePlayer();
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    fg = true;
  }

  @Override
  public void onStop() {
    super.onStop();
    fg = false;
    pauseIfHidden();
    hh.postDelayed(new Runnable() {
      @Override
      public void run() {
        pauseIfHidden();
      }
    }, 600);
  }

  @Override
  public void onDestroy() {
    if (VibePlugin.inst != null) {
      VibePlugin.inst.pausePlayer();
    }
    super.onDestroy();
  }

  @Override
  protected void onUserLeaveHint() {
    super.onUserLeaveHint();
    if (pipArmed) {
      goPip();
    }
  }

  @Override
  public void onPictureInPictureModeChanged(boolean inPip, Configuration cfg) {
    super.onPictureInPictureModeChanged(inPip, cfg);
    runJs("window.__pip&&window.__pip(" + inPip + ")");
    if (!inPip) {
      hh.postDelayed(new Runnable() {
        @Override
        public void run() {
          pauseIfHidden();
        }
      }, 400);
    }
    if (!inPip && !getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED) && VibePlugin.inst != null) {
      VibePlugin.inst.pausePlayer();
    }
  }
}
