package __PKG__;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Color;
import android.media.AudioManager;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import java.io.File;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import androidx.activity.result.ActivityResult;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;
import com.getcapacitor.annotation.ActivityCallback;
import java.util.Collections;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;
import android.content.ContentUris;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Base64;
import android.util.Size;
import android.view.View;
import android.view.WindowManager;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.ByteArrayOutputStream;

@CapacitorPlugin(name = "VibeNative")
public class VibePlugin extends Plugin {
  static VibePlugin inst;
  private ExoPlayer pl;
  private PlayerView pv;
  private Uri curUri;
  private String lastErr = "";
  private String diag = "";
  private boolean ended = false;
  private final Handler hd = new Handler(Looper.getMainLooper());
  private boolean ticking = false;
  private final Runnable tick = new Runnable() {
    @Override
    public void run() {
      if (pl == null) {
        ticking = false;
        return;
      }
      push();
      hd.postDelayed(this, 250);
    }
  };

  @Override
  public void load() {
    inst = this;
  }

  @Override
  protected void handleOnDestroy() {
    try {
      hd.removeCallbacks(tick);
      if (pl != null) {
        pl.release();
        pl = null;
      }
    } catch (Throwable t) {
    }
  }

  private static double num(PluginCall c, String k, double d) {
    try {
      Object o = c.getData().opt(k);
      if (o instanceof Number) {
        return ((Number) o).doubleValue();
      }
      if (o instanceof String) {
        return Double.parseDouble((String) o);
      }
    } catch (Throwable t) {
    }
    return d;
  }

  private MainActivity act() {
    return (MainActivity) getActivity();
  }

  public void pausePlayer() {
    if (pl != null) {
      pl.setPlayWhenReady(false);
    }
  }

  private float sysVol() {
    try {
      AudioManager am = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
      int mx = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
      return mx <= 0 ? 0f : am.getStreamVolume(AudioManager.STREAM_MUSIC) / (float) mx;
    } catch (Throwable t) {
      return 0.5f;
    }
  }

  private float sysBright() {
    try {
      float b = act().getWindow().getAttributes().screenBrightness;
      if (b >= 0) {
        return b;
      }
      return Settings.System.getInt(getContext().getContentResolver(), Settings.System.SCREEN_BRIGHTNESS) / 255f;
    } catch (Throwable t) {
      return 0.6f;
    }
  }

  private void ensure() {
    if (pl != null) {
      return;
    }
    Context ctx = getContext();
    DefaultRenderersFactory rf = new DefaultRenderersFactory(ctx).setEnableDecoderFallback(true);
    pl = new ExoPlayer.Builder(ctx, rf).build();
    pl.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true);
    pl.setHandleAudioBecomingNoisy(true);
    pl.addListener(new Player.Listener() {
      @Override
      public void onPlayerError(PlaybackException e) {
        lastErr = e.getErrorCodeName() + (e.getMessage() != null ? " - " + e.getMessage() : "") + (e.getCause() != null ? " / " + e.getCause().getMessage() : "") + diag;
        push();
      }

      @Override
      public void onTracksChanged(Tracks t) {
        pushTracks();
      }

      @Override
      public void onPlaybackStateChanged(int s) {
        if (s == Player.STATE_ENDED) {
          ended = true;
        }
      }
    });
    pv = new PlayerView(ctx);
    pv.setUseController(false);
    pv.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
    pv.setShutterBackgroundColor(Color.BLACK);
    pv.setBackgroundColor(Color.BLACK);
    pv.setPlayer(pl);
    pv.setVisibility(View.GONE);
    ViewGroup parent = (ViewGroup) getBridge().getWebView().getParent();
    parent.addView(pv, 0, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT));
  }

  private void push() {
    try {
      if (pl == null) {
        return;
      }
      long d = pl.getDuration();
      if (d == C.TIME_UNSET || d < 0) {
        d = 0;
      }
      VideoSize vs = pl.getVideoSize();
      int st = pl.getPlaybackState();
      boolean play = pl.getPlayWhenReady() && st != Player.STATE_ENDED;
      String js = "window.__ps&&window.__ps({p:" + pl.getCurrentPosition() + ",d:" + d + ",pl:" + play + ",st:" + st
          + ",w:" + vs.width + ",h:" + vs.height + ",vl:" + sysVol() + ",e:" + JSONObject.quote(lastErr) + "})";
      act().runJs(js);
    } catch (Throwable t) {
    }
  }

  private String label(Format f, int n) {
    String s = f.label;
    if (s == null || s.length() == 0) {
      if (f.language != null && !"und".equals(f.language)) {
        s = new Locale(f.language).getDisplayLanguage();
      }
    }
    if (s == null || s.length() == 0) {
      s = "Track " + n;
    }
    if (f.channelCount > 0 && MimeTypes.isAudio(f.sampleMimeType)) {
      s = s + " · " + (f.channelCount > 2 ? (f.channelCount - 1) + ".1" : f.channelCount == 2 ? "stereo" : "mono");
    }
    return s;
  }

  private void pushTracks() {
    try {
      if (pl == null) {
        return;
      }
      JSONArray au = new JSONArray();
      JSONArray tx = new JSONArray();
      Tracks t = pl.getCurrentTracks();
      int na = 0;
      int nt = 0;
      for (int gi = 0; gi < t.getGroups().size(); gi++) {
        Tracks.Group g = t.getGroups().get(gi);
        int type = g.getType();
        if (type != C.TRACK_TYPE_AUDIO && type != C.TRACK_TYPE_TEXT) {
          continue;
        }
        for (int i = 0; i < g.length; i++) {
          if (!g.isTrackSupported(i)) {
            continue;
          }
          JSONObject o = new JSONObject();
          o.put("g", gi);
          o.put("t", i);
          o.put("sel", g.isTrackSelected(i));
          if (type == C.TRACK_TYPE_AUDIO) {
            na++;
            o.put("n", label(g.getTrackFormat(i), na));
            au.put(o);
          } else {
            nt++;
            o.put("n", label(g.getTrackFormat(i), nt));
            tx.put(o);
          }
        }
      }
      act().runJs("window.__tr&&window.__tr({a:" + au.toString() + ",s:" + tx.toString() + "})");
    } catch (Throwable e) {
    }
  }

  @PluginMethod
  public void open(final PluginCall call) {
    final long id = (long) num(call, "id", 0);
    final long pos = (long) num(call, "pos", 0);
    final MainActivity a = act();
    a.runOnUiThread(new Runnable() {
      @Override
      public void run() {
        try {
          ensure();
          Uri content = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id);
          curUri = content;
          diag = "";
          String path = null;
          Cursor pc = null;
          try {
            pc = getContext().getContentResolver().query(content, new String[] {"_data"}, null, null, null);
            if (pc != null && pc.moveToFirst()) {
              path = pc.getString(0);
            }
          } catch (Throwable t) {
            diag += " [query: " + t + "]";
          } finally {
            if (pc != null) {
              pc.close();
            }
          }
          try {
            ParcelFileDescriptor pfd = getContext().getContentResolver().openFileDescriptor(content, "r");
            if (pfd != null) {
              pfd.close();
              diag += " [content ok]";
            } else {
              diag += " [content null]";
            }
          } catch (Throwable t) {
            diag += " [content: " + t + "]";
          }
          if (path != null) {
            File f = new File(path);
            diag += " [path=" + path + " exists=" + f.exists() + " read=" + f.canRead() + "]";
            if (f.exists() && f.canRead()) {
              curUri = Uri.fromFile(f);
            }
          } else {
            diag += " [no path]";
          }
          lastErr = "";
          ended = false;
          pl.setPlaybackParameters(new PlaybackParameters(1f));
          pl.setTrackSelectionParameters(pl.getTrackSelectionParameters().buildUpon()
              .clearOverrides().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build());
          pl.setMediaItem(MediaItem.fromUri(curUri), pos);
          pl.prepare();
          pl.setPlayWhenReady(true);
          pv.setVisibility(View.VISIBLE);
          if (!ticking) {
            ticking = true;
            hd.post(tick);
          }
          JSObject res = new JSObject();
          res.put("vol", sysVol());
          res.put("br", sysBright());
          call.resolve(res);
        } catch (Throwable t) {
          call.reject("open failed: " + t);
          lastErr = "open: " + t;
        }
      }
    });
  }

  @PluginMethod
  public void close(final PluginCall call) {
    act().runOnUiThread(new Runnable() {
      @Override
      public void run() {
        try {
          hd.removeCallbacks(tick);
          ticking = false;
          if (pl != null) {
            pl.stop();
            pl.clearMediaItems();
          }
          if (pv != null) {
            pv.setVisibility(View.GONE);
          }
          WindowManager.LayoutParams lp = act().getWindow().getAttributes();
          lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
          act().getWindow().setAttributes(lp);
        } catch (Throwable t) {
        }
        call.resolve();
      }
    });
  }

  @PluginMethod
  public void playing(final PluginCall call) {
    final boolean on = call.getBoolean("on", true);
    act().runOnUiThread(new Runnable() {
      @Override
      public void run() {
        if (pl != null) {
          if (on && ended) {
            pl.seekTo(0);
            ended = false;
          }
          pl.setPlayWhenReady(on);
        }
        call.resolve();
      }
    });
  }

  @PluginMethod
  public void seek(final PluginCall call) {
    final long ms = (long) num(call, "ms", 0);
    act().runOnUiThread(new Runnable() {
      @Override
      public void run() {
        if (pl != null) {
          pl.seekTo(Math.max(0L, ms));
          ended = false;
        }
        call.resolve();
      }
    });
  }

  @PluginMethod
  public void rate(final PluginCall call) {
    final float r = (float) num(call, "v", 1);
    act().runOnUiThread(new Runnable() {
      @Override
      public void run() {
        if (pl != null) {
          pl.setPlaybackParameters(new PlaybackParameters(r));
        }
        call.resolve();
      }
    });
  }

  @PluginMethod
  public void vol(final PluginCall call) {
    final float v = (float) num(call, "v", 0.5);
    try {
      AudioManager am = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
      int mx = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
      am.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(Math.max(0f, Math.min(1f, v)) * mx), 0);
    } catch (Throwable t) {
    }
    call.resolve();
  }

  @PluginMethod
  public void bright(final PluginCall call) {
    final float v = (float) num(call, "v", 0.5);
    act().runOnUiThread(new Runnable() {
      @Override
      public void run() {
        WindowManager.LayoutParams lp = act().getWindow().getAttributes();
        lp.screenBrightness = Math.max(0.02f, Math.min(1f, v));
        act().getWindow().setAttributes(lp);
        call.resolve();
      }
    });
  }

  @PluginMethod
  public void tracks(PluginCall call) {
    act().runOnUiThread(new Runnable() {
      @Override
      public void run() {
        pushTracks();
      }
    });
    call.resolve();
  }

  @PluginMethod
  public void pickAudio(final PluginCall call) {
    final int gi = (int) num(call, "g", -1);
    final int ti = (int) num(call, "t", 0);
    act().runOnUiThread(new Runnable() {
      @Override
      public void run() {
        try {
          Tracks.Group g = pl.getCurrentTracks().getGroups().get(gi);
          pl.setTrackSelectionParameters(pl.getTrackSelectionParameters().buildUpon()
              .setOverrideForType(new TrackSelectionOverride(g.getMediaTrackGroup(), ti)).build());
        } catch (Throwable t) {
        }
        call.resolve();
      }
    });
  }

  @PluginMethod
  public void pickText(final PluginCall call) {
    final int gi = (int) num(call, "g", -1);
    final int ti = (int) num(call, "t", 0);
    act().runOnUiThread(new Runnable() {
      @Override
      public void run() {
        try {
          if (gi < 0) {
            pl.setTrackSelectionParameters(pl.getTrackSelectionParameters().buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build());
          } else {
            Tracks.Group g = pl.getCurrentTracks().getGroups().get(gi);
            pl.setTrackSelectionParameters(pl.getTrackSelectionParameters().buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setOverrideForType(new TrackSelectionOverride(g.getMediaTrackGroup(), ti)).build());
          }
        } catch (Throwable t) {
        }
        call.resolve();
      }
    });
  }

  @PluginMethod
  public void addSub(PluginCall call) {
    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
    i.addCategory(Intent.CATEGORY_OPENABLE);
    i.setType("*/*");
    startActivityForResult(call, i, "subResult");
  }

  @ActivityCallback
  private void subResult(final PluginCall call, ActivityResult r) {
    if (call == null) {
      return;
    }
    if (r == null || r.getResultCode() != Activity.RESULT_OK || r.getData() == null || r.getData().getData() == null) {
      call.resolve();
      return;
    }
    final Uri u = r.getData().getData();
    String name = "sub.srt";
    Cursor c = null;
    try {
      c = getContext().getContentResolver().query(u, new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null);
      if (c != null && c.moveToFirst() && c.getString(0) != null) {
        name = c.getString(0);
      }
    } catch (Throwable t) {
    } finally {
      if (c != null) {
        c.close();
      }
    }
    String low = name.toLowerCase(Locale.ROOT);
    final String mime = low.endsWith(".vtt") ? MimeTypes.TEXT_VTT
        : (low.endsWith(".ass") || low.endsWith(".ssa")) ? MimeTypes.TEXT_SSA
        : (low.endsWith(".ttml") || low.endsWith(".xml")) ? MimeTypes.APPLICATION_TTML
        : MimeTypes.APPLICATION_SUBRIP;
    final String nm = name;
    act().runOnUiThread(new Runnable() {
      @Override
      public void run() {
        try {
          long pos = pl.getCurrentPosition();
          boolean pw = pl.getPlayWhenReady();
          MediaItem.SubtitleConfiguration sc = new MediaItem.SubtitleConfiguration.Builder(u)
              .setMimeType(mime).setLabel(nm).setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build();
          MediaItem mi = new MediaItem.Builder().setUri(curUri).setSubtitleConfigurations(Collections.singletonList(sc))
              .build();
          pl.setTrackSelectionParameters(pl.getTrackSelectionParameters().buildUpon()
              .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).clearOverridesOfType(C.TRACK_TYPE_TEXT).build());
          pl.setMediaItem(mi, pos);
          pl.prepare();
          pl.setPlayWhenReady(pw);
          call.resolve();
        } catch (Throwable t) {
          call.reject("sub failed");
        }
      }
    });
  }

  @PluginMethod
  public void pipArmed(PluginCall call) {
    MainActivity.pipArmed = call.getBoolean("on", false);
    call.resolve();
  }

  @PluginMethod
  public void playerOpen(PluginCall call) {
    MainActivity.playerOpen = call.getBoolean("on", false);
    call.resolve();
  }

  @PluginMethod
  public void ratio(PluginCall call) {
    MainActivity.ratio = (int) num(call, "v", 178);
    call.resolve();
  }

  @PluginMethod
  public void enterPip(PluginCall call) {
    final MainActivity a = act();
    a.runOnUiThread(new Runnable() {
      @Override
      public void run() {
        a.goPip();
      }
    });
    call.resolve();
  }

  @PluginMethod
  public void keepOn(PluginCall call) {
    final boolean on = call.getBoolean("on", false);
    final MainActivity a = act();
    a.runOnUiThread(new Runnable() {
      @Override
      public void run() {
        if (on) {
          a.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
          a.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
      }
    });
    call.resolve();
  }

  @PluginMethod
  public void immersive(PluginCall call) {
    final boolean on = call.getBoolean("on", false);
    final MainActivity a = act();
    a.runOnUiThread(new Runnable() {
      @Override
      public void run() {
        a.setBars(on);
      }
    });
    call.resolve();
  }

  @PluginMethod
  public void landscape(PluginCall call) {
    final boolean on = call.getBoolean("on", false);
    final MainActivity a = act();
    a.runOnUiThread(new Runnable() {
      @Override
      public void run() {
        a.setRequestedOrientation(on ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            : ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
      }
    });
    call.resolve();
  }

  /* all videos on the phone, newest first */
  @PluginMethod
  public void scan(PluginCall call) {
    final MainActivity a = act();
    JSObject res = new JSObject();
    if (!a.hasVideoAccess()) {
      a.runOnUiThread(new Runnable() {
        @Override
        public void run() {
          a.askVideoAccess();
        }
      });
      res.put("ok", false);
      res.put("asked", true);
      call.resolve(res);
      return;
    }
    JSArray list = new JSArray();
    Cursor c = null;
    try {
      String[] proj = {
          MediaStore.Video.Media._ID,
          MediaStore.Video.Media.DISPLAY_NAME,
          MediaStore.Video.Media.SIZE,
          MediaStore.Video.Media.DURATION,
          MediaStore.Video.Media.BUCKET_DISPLAY_NAME
      };
      c = getContext().getContentResolver().query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, proj, null, null,
          MediaStore.Video.Media.DATE_ADDED + " DESC");
      if (c != null) {
        while (c.moveToNext()) {
          JSObject o = new JSObject();
          o.put("id", c.getLong(0));
          o.put("name", c.getString(1));
          o.put("size", c.getLong(2));
          o.put("dur", c.getLong(3));
          o.put("folder", c.getString(4));
          list.put(o);
        }
      }
    } catch (Throwable t) {
      call.reject("scan failed");
      return;
    } finally {
      if (c != null) {
        c.close();
      }
    }
    res.put("ok", true);
    res.put("list", list);
    call.resolve(res);
  }

  /* small preview picture of one video, as a data address */
  @PluginMethod
  public void thumb(PluginCall call) {
    long id = (long) num(call, "id", -1);
    if (id < 0) {
      call.reject("id");
      return;
    }
    ContentResolver cr = getContext().getContentResolver();
    Uri uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id);
    Bitmap bm = null;
    try {
      if (Build.VERSION.SDK_INT >= 29) {
        bm = cr.loadThumbnail(uri, new Size(320, 180), null);
      } else {
        bm = MediaStore.Video.Thumbnails.getThumbnail(cr, id, MediaStore.Video.Thumbnails.MINI_KIND, null);
      }
    } catch (Throwable t) {
    }
    if (bm == null) {
      call.reject("no thumb");
      return;
    }
    ByteArrayOutputStream bo = new ByteArrayOutputStream();
    bm.compress(Bitmap.CompressFormat.JPEG, 72, bo);
    JSObject res = new JSObject();
    res.put("data", "data:image/jpeg;base64," + Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP));
    call.resolve(res);
  }

  @PluginMethod
  public void settings(PluginCall call) {
    try {
      Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
          Uri.parse("package:" + getContext().getPackageName()));
      i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      getActivity().startActivity(i);
    } catch (Throwable t) {
    }
    call.resolve();
  }
}
