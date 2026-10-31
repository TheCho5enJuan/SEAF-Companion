package com.seaf.companion;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
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
        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme();
                if (!"http".equals(scheme) && !"https".equals(scheme)) {
                    openBrowser(uri);
                    return true;
                }
                String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(java.util.Locale.ROOT);
                if (host.equals("discord.com") || host.equals("discordapp.com") ||
                    host.endsWith(".playstation.com") || host.endsWith(".sony.com") ||
                    host.equals("login.live.com") || host.equals("account.microsoft.com") ||
                    host.equals("xbox.com")) {
                    openBrowser(uri);
                    return true;
                }
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
        webView.loadUrl(GAME_URL);
    }
    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }
    private void openBrowser(Uri uri) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
        catch (Exception e) { Toast.makeText(this, "Cannot open link", Toast.LENGTH_SHORT).show(); }
    }
    private void showMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add("Auto rotate").setOnMenuItemClickListener(i -> { setOrientation(0); return true; });
        popup.getMenu().add("Portrait").setOnMenuItemClickListener(i -> { setOrientation(1); return true; });
        popup.getMenu().add("Landscape").setOnMenuItemClickListener(i -> { setOrientation(2); return true; });
        popup.getMenu().add(immersive ? "Exit fullscreen" : "Enter fullscreen").setOnMenuItemClickListener(i -> {
            immersive = !immersive;
            getPreferences(0).edit().putBoolean("immersive", immersive).apply();
            updateFullscreen(); return true;
        });
        popup.getMenu().add("Zoom in").setOnMenuItemClickListener(i -> { webView.zoomIn(); return true; });
        popup.getMenu().add("Zoom out").setOnMenuItemClickListener(i -> { webView.zoomOut(); return true; });
        popup.getMenu().add("Refresh").setOnMenuItemClickListener(i -> { webView.reload(); return true; });
        popup.getMenu().add("Open in browser").setOnMenuItemClickListener(i -> {
            openBrowser(Uri.parse(webView.getUrl() == null ? GAME_URL : webView.getUrl())); return true;
        });
        popup.show();
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
    @Override protected void onDestroy() { root.removeView(webView); webView.destroy(); super.onDestroy(); }
}
