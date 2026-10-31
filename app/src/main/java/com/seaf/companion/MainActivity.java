package com.seaf.companion;

import android.app.Activity;
import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Environment;
import android.provider.MediaStore;
import java.io.OutputStream;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.content.res.Configuration;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final String GAME_URL = "https://seaf.se/";
    private WebView webView;
    private FrameLayout root;
    private boolean immersive = true;
    private SessionRecorder recorder;
    private CompanionDashboard dashboard;
    private MobileControls mobileControls;
    private boolean portraitFit = false;
    private final Handler layoutHandler = new Handler(Looper.getMainLooper());
    private final Runnable layoutTick = new Runnable() {
        @Override public void run() {
            applyPortraitFit();
            layoutHandler.postDelayed(this, 2200);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setMediaPlaybackRequiresUserGesture(false);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        webView.setWebChromeClient(new WebChromeClient());
        recorder = new SessionRecorder(this, webView);
        mobileControls = new MobileControls(this, webView);
        dashboard = new CompanionDashboard(this, () -> {}, () -> {portraitFit=false;getPreferences(0).edit().putBoolean("portraitFit",false).apply();applyPortraitFit();});
        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                applyPortraitFit();
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme();
                if (!"http".equals(scheme) && !"https".equals(scheme)) {
                    openBrowser(uri);
                    return true;
                }
                // Keep OAuth authorization and its HTTPS callback in this WebView.
                // The external browser has a separate cookie jar and cannot return
                // ownership of seaf.se to this third-party Android application.
                return false;
            }
        });
        root.addView(webView, new FrameLayout.LayoutParams(-1, -1));
        TextView menu = new TextView(this);
        menu.setText("⋮");
        menu.setTextColor(Color.WHITE);
        menu.setTextSize(25);
        menu.setGravity(Gravity.CENTER);
        menu.setBackgroundColor(0xBB191919);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.TOP | Gravity.END);
        params.topMargin = dp(8);
        params.rightMargin = dp(8);
        root.addView(menu, params);
        menu.setOnClickListener(this::showMenu);
        setContentView(root);
        int orientation = getPreferences(0).getInt("orientation", 0);
        setOrientation(orientation);
        immersive = getPreferences(0).getBoolean("immersive", true);
        updateFullscreen();
        portraitFit = getPreferences(0).getBoolean("portraitFit", false);
        layoutHandler.post(layoutTick);
        webView.loadUrl(GAME_URL);
    }
    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }
    private void openBrowser(Uri uri) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
        catch (Exception e) { Toast.makeText(this, "Cannot open link", Toast.LENGTH_SHORT).show(); }
    }
    private void showMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add("Mobile Game Controls").setOnMenuItemClickListener(i -> {mobileControls.open();return true;});
        popup.getMenu().add("Companion Dashboard").setOnMenuItemClickListener(i -> {dashboard.show();return true;});
        popup.getMenu().add("Original Game (reset portrait fit)").setOnMenuItemClickListener(i -> {portraitFit=false;getPreferences(0).edit().putBoolean("portraitFit",false).apply();applyPortraitFit();return true;});
        popup.getMenu().add("Auto rotate").setOnMenuItemClickListener(i -> { setOrientation(0); return true; });
        popup.getMenu().add("Portrait").setOnMenuItemClickListener(i -> { setOrientation(1); return true; });
        popup.getMenu().add("Landscape").setOnMenuItemClickListener(i -> { setOrientation(2); return true; });
        popup.getMenu().add(immersive ? "Exit fullscreen" : "Enter fullscreen").setOnMenuItemClickListener(i -> {
            immersive = !immersive;
            getPreferences(0).edit().putBoolean("immersive", immersive).apply();
            updateFullscreen(); return true;
        });
        popup.getMenu().add(portraitFit ? "Portrait fit: ON (tap to disable)" : "Portrait fit: OFF (tap to enable)")
            .setOnMenuItemClickListener(i -> {
                portraitFit = !portraitFit;
                getPreferences(0).edit().putBoolean("portraitFit", portraitFit).apply();
                applyPortraitFit();
                return true;
            });
        popup.getMenu().add("Zoom in").setOnMenuItemClickListener(i -> { webView.zoomIn(); return true; });
        popup.getMenu().add("Zoom out").setOnMenuItemClickListener(i -> { webView.zoomOut(); return true; });
        popup.getMenu().add(recorder.isRecording() ? "Stop capture session + export ZIP" : "Start capture session").setOnMenuItemClickListener(i -> {
            if(recorder.isRecording()) recorder.stop(); else recorder.start();
            return true;
        });
        if (recorder.isRecording()) popup.getMenu().add("Capture current screen now").setOnMenuItemClickListener(i -> {
            recorder.recordNow(); return true;
        });
        popup.getMenu().add("Capture layout + screenshot").setOnMenuItemClickListener(i -> {
            captureDiagnostics(); return true;
        });
        popup.getMenu().add("Refresh").setOnMenuItemClickListener(i -> { webView.reload(); return true; });
        popup.getMenu().add("Browser login (fallback)").setOnMenuItemClickListener(i -> {
            openBrowser(Uri.parse(webView.getUrl() == null ? GAME_URL : webView.getUrl())); return true;
        });
        popup.show();
    }

    // One tap exports a screenshot plus a structural layout/CSS inventory.
    // No network requests, cookies, local storage, input values, or auth tokens are collected.
    private static final String INSPECT_JS = """
        (function () {
          const safe = s => String(s || '').slice(0, 160);
          const els = Array.from(document.querySelectorAll('*')).slice(0, 1800);
          const rows = els.map((el, idx) => {
            const r = el.getBoundingClientRect(), s = getComputedStyle(el);
            const parts = [];
            let n = el;
            for (let j = 0; j < 4 && n && n.nodeType === 1; j++, n = n.parentElement) {
              let part = n.tagName.toLowerCase();
              if (n.id) part += '#' + CSS.escape(n.id).slice(0, 80);
              else if (n.classList.length) part += '.' + Array.from(n.classList).slice(0, 3).map(x => CSS.escape(x)).join('.');
              parts.unshift(part);
            }
            return {
              i: idx, selector: safe(parts.join(' > ')),
              tag: el.tagName.toLowerCase(),
              rect: {x: Math.round(r.x), y: Math.round(r.y), w: Math.round(r.width), h: Math.round(r.height)},
              style: {
                display: s.display, position: s.position, overflow: s.overflow,
                width: s.width, height: s.height, minWidth: s.minWidth,
                maxWidth: s.maxWidth, fontSize: s.fontSize,
                transform: s.transform, flexDirection: s.flexDirection,
                gridTemplateColumns: s.gridTemplateColumns,
                zIndex: s.zIndex, visibility: s.visibility
              }
            };
          });
          const v = window.visualViewport;
          return JSON.stringify({
            capturedAt: new Date().toISOString(),
            origin: location.origin,
            pathname: location.pathname,
            viewport: {
              innerWidth: innerWidth, innerHeight: innerHeight,
              devicePixelRatio: devicePixelRatio,
              screenWidth: screen.width, screenHeight: screen.height,
              visualWidth: v ? v.width : null, visualHeight: v ? v.height : null,
              visualScale: v ? v.scale : null,
              orientation: screen.orientation ? screen.orientation.type : null
            },
            document: {
              scrollWidth: document.documentElement.scrollWidth,
              scrollHeight: document.documentElement.scrollHeight,
              bodyWidth: document.body ? document.body.scrollWidth : null,
              bodyHeight: document.body ? document.body.scrollHeight : null
            },
            nodesScanned: els.length, elements: rows
          });
        })();
        """;

    private void captureDiagnostics() {
        if (!"seaf.se".equalsIgnoreCase(Uri.parse(webView.getUrl() == null ? GAME_URL : webView.getUrl()).getHost())) {
            Toast.makeText(this, "Capture only supported on seaf.se", Toast.LENGTH_LONG).show();
            return;
        }
        String stamp = new java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US)
                .format(new java.util.Date());
        int width = webView.getWidth(), height = webView.getHeight();
        if (width > 0 && height > 0) {
            try {
                Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                webView.draw(new Canvas(bitmap));
                Uri image = createExport(MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        "seaf-" + stamp + ".png", "image/png");
                try (OutputStream out = getContentResolver().openOutputStream(image)) {
                    if (out == null || !bitmap.compress(Bitmap.CompressFormat.PNG, 100, out))
                        throw new Exception("PNG export failed");
                }
                bitmap.recycle();
            } catch (Exception e) {
                Toast.makeText(this, "Screenshot failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
        webView.evaluateJavascript(INSPECT_JS, encoded -> {
            try {
                String json = (String) new org.json.JSONTokener(encoded).nextValue();
                Uri output = createExport(MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        "seaf-" + stamp + ".json", "application/json");
                try (OutputStream out = getContentResolver().openOutputStream(output)) {
                    if (out == null) throw new Exception("JSON export failed");
                    out.write(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
                Toast.makeText(this, "Saved seaf-" + stamp + " files in Downloads/SEAFCompanion",
                        Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(this, "Layout export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private Uri createExport(Uri collection, String name, String mime) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        values.put(MediaStore.MediaColumns.MIME_TYPE, mime);
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/SEAFCompanion");
        Uri uri = getContentResolver().insert(collection, values);
        if (uri == null) throw new IllegalStateException("Could not create export");
        return uri;
    }

    private void applyPortraitFit() {
        if (webView == null || webView.getUrl() == null) return;
        Uri uri = Uri.parse(webView.getUrl());
        if (!"seaf.se".equalsIgnoreCase(uri.getHost())) return;
        boolean enabled = portraitFit &&
            getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
        String js = "(function(){"+
            "var e=document.querySelector('#page-home > .scale-me.pos-abs-c');"+
            "if(!e)return;"+
            "var key='seaf-companion-original';"+
            "if(!e.hasAttribute('data-'+key)){"+
            "e.setAttribute('data-'+key,'1');"+
            "e.dataset.seafOriginalZoom=e.style.getPropertyValue('zoom');"+
            "e.dataset.seafOriginalTransform=e.style.getPropertyValue('transform');"+
            "e.dataset.seafOriginalLeft=e.style.getPropertyValue('left');"+
            "e.dataset.seafOriginalTop=e.style.getPropertyValue('top');"+
            "e.dataset.seafOriginalOrigin=e.style.getPropertyValue('transform-origin');"+
            "}"+
            "if("+enabled+"){"+
            "var frame=document.getElementById('page-home');"+
            "var w=frame.getBoundingClientRect().width;"+
            "if(w<100)return;"+
            "var scale=Math.max(0.16,Math.min(1,(w-4)/1600));"+
            "e.style.setProperty('zoom','1','important');"+
            "e.style.setProperty('left','50%','important');"+
            "e.style.setProperty('top','50%','important');"+
            "e.style.setProperty('transform-origin','center center','important');"+
            "e.style.setProperty('transform','translate(-50%, -50%) scale('+scale+')','important');"+
            "}else if(e.hasAttribute('data-'+key)){"+
            "['zoom','transform','left','top','transform-origin'].forEach(function(p){e.style.removeProperty(p)});"+
            "var names={'zoom':'Zoom','transform':'Transform','left':'Left','top':'Top','transform-origin':'Origin'};"+
            "Object.keys(names).forEach(function(p){var v=e.dataset['seafOriginal'+names[p]];if(v)e.style.setProperty(p,v)});"+
            "e.removeAttribute('data-'+key);"+
            "}"+
            "})();";
        webView.evaluateJavascript(js, null);
    }
    @Override public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        webView.postDelayed(this::applyPortraitFit, 350);
    }
    private void setOrientation(int mode) {
        getPreferences(0).edit().putInt("orientation", mode).apply();
        setRequestedOrientation(mode == 1 ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT :
            mode == 2 ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE : ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
    }
    private void updateFullscreen() {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                if (immersive) controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                else controller.show(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(immersive ?
                (View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY) : 0);
        }
    }
    @Override public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
    @Override protected void onPause() { CookieManager.getInstance().flush(); super.onPause(); }
    @Override protected void onDestroy() { layoutHandler.removeCallbacks(layoutTick); recorder.close(); root.removeView(webView); webView.destroy(); super.onDestroy(); }
}
