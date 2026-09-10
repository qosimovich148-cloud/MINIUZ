package uz.miniuz.browser;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.JsResult;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final int FILE_CHOOSER_REQUEST = 7001;
    private static final int STORAGE_REQUEST = 7002;

    private LinearLayout browserRoot;
    private LinearLayout toolbar;
    private LinearLayout tabBar;
    private FrameLayout webContainer;
    private EditText addressBar;
    private ProgressBar progressBar;
    private TextView securityView;
    private TextView backButton;
    private TextView forwardButton;
    private TextView homeButton;
    private TextView refreshButton;
    private TextView menuButton;

    private final ArrayList<WebView> tabs = new ArrayList<>();
    private final ArrayList<String> tabTitles = new ArrayList<>();
    private int currentTab = -1;

    private SharedPreferences prefs;
    private boolean darkMode = false;

    private ValueCallback<Uri[]> fileCallback;

    private PopupWindow activeMenu;
    private View activeMenuPanel;

    private final Handler handler = new Handler();

    private static final String HOME_URL = "file:///android_asset/home.html";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences("miniuz", MODE_PRIVATE);
        darkMode = prefs.getBoolean("dark_mode", false);

        getWindow().setStatusBarColor(Color.rgb(30, 120, 65));

        setContentView(new SplashView(this));

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                showBrowser();
            }
        }, 4200);
    }

    private void showBrowser() {
        browserRoot = new LinearLayout(this);
        browserRoot.setOrientation(LinearLayout.VERTICAL);
        browserRoot.setBackgroundColor(bgColor());

        toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(4), dp(4), dp(4), dp(4));
        toolbar.setBackgroundColor(toolbarColor());

        backButton = toolbarButton("‹");
        forwardButton = toolbarButton("›");
        homeButton = toolbarButton("⌂");
        securityView = toolbarButton("🔒");
        securityView.setTextSize(17);

        addressBar = new EditText(this);
        addressBar.setSingleLine(true);
        addressBar.setTextSize(15);
        addressBar.setHint("Manzil yoki qidiruv...");
        addressBar.setPadding(dp(12), 0, dp(8), 0);
        addressBar.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_GO);
        addressBar.setTextColor(textColor());
        addressBar.setHintTextColor(hintColor());
        addressBar.setBackground(roundDrawable(addressColor(), 22));

        refreshButton = toolbarButton("↻");
        menuButton = toolbarButton("⋮");

        toolbar.addView(backButton, fixed(42));
        toolbar.addView(forwardButton, fixed(42));
        toolbar.addView(homeButton, fixed(42));
        toolbar.addView(securityView, fixed(38));
        toolbar.addView(addressBar, new LinearLayout.LayoutParams(0, dp(44), 1));
        toolbar.addView(refreshButton, fixed(42));
        toolbar.addView(menuButton, fixed(42));

        // toolbar hidden - fullscreen mode

        HorizontalScrollView tabScroll = new HorizontalScrollView(this);
        tabScroll.setHorizontalScrollBarEnabled(false);

        tabBar = new LinearLayout(this);
        tabBar.setOrientation(LinearLayout.HORIZONTAL);
        tabBar.setGravity(Gravity.CENTER_VERTICAL);
        tabBar.setPadding(dp(4), dp(3), dp(4), dp(3));
        tabBar.setBackgroundColor(tabColor());

        tabScroll.addView(tabBar,
                new ViewGroup.LayoutParams(-2, dp(42)));

        // tabScroll hidden - fullscreen mode

        progressBar = new ProgressBar(
                this,
                null,
                android.R.attr.progressBarStyleHorizontal
        );
        progressBar.setMax(100);
        progressBar.setProgress(0);

        browserRoot.addView(progressBar,
                new LinearLayout.LayoutParams(-1, dp(3)));

        webContainer = new FrameLayout(this);
        webContainer.setBackgroundColor(bgColor());

        browserRoot.addView(webContainer,
                new LinearLayout.LayoutParams(-1, 0, 1));

        TextView floatingMenuBtn = toolbarButton("⋮");
        floatingMenuBtn.setBackground(roundDrawable(Color.argb(160,0,0,0), 20));
        floatingMenuBtn.setTextColor(Color.WHITE);
        FrameLayout.LayoutParams fmp = new FrameLayout.LayoutParams(dp(40), dp(40));
        fmp.gravity = Gravity.TOP | Gravity.END;
        fmp.topMargin = dp(14);
        fmp.rightMargin = dp(10);
        floatingMenuBtn.setOnClickListener(v -> showMenu());
        webContainer.addView(floatingMenuBtn, fmp);

        setContentView(browserRoot);

        backButton.setOnClickListener(v -> {
            WebView w = currentWebView();
            if (w != null && w.canGoBack()) w.goBack();
        });

        forwardButton.setOnClickListener(v -> {
            WebView w = currentWebView();
            if (w != null && w.canGoForward()) w.goForward();
        });

        homeButton.setOnClickListener(v -> {
            WebView w = currentWebView();
            if (w != null) w.loadUrl(HOME_URL);
        });

        refreshButton.setOnClickListener(v -> {
            WebView w = currentWebView();
            if (w != null) w.reload();
        });

        menuButton.setOnClickListener(v -> showMenu());

        addressBar.setOnEditorActionListener((v, actionId, event) -> {
            openAddress(addressBar.getText().toString());
            return true;
        });

        newTab(HOME_URL);
        applyTheme();
        floatingMenuBtn.bringToFront();
        webContainer.requestLayout();
        webContainer.invalidate();
    }

    private void newTab(String url) {
        WebView webView = createWebView();

        tabs.add(webView);
        tabTitles.add("Yangi tab");

        webContainer.addView(webView,
                new FrameLayout.LayoutParams(-1, -1));

        currentTab = tabs.size() - 1;

        selectTab(currentTab);

        if (url == null || url.trim().isEmpty()) {
            webView.loadUrl(HOME_URL);
        } else {
            webView.loadUrl(url);
        }

        refreshTabs();
    }

    private class AndroidBridge {
        @android.webkit.JavascriptInterface
        public void openHistory() { runOnUiThread(MainActivity.this::showHistory); }
        @android.webkit.JavascriptInterface
        public void openBookmarks() { runOnUiThread(MainActivity.this::showBookmarks); }
        @android.webkit.JavascriptInterface
        public void openDownloads() { runOnUiThread(MainActivity.this::showDownloads); }
        @android.webkit.JavascriptInterface
        public void toast(String msg) { runOnUiThread(() -> Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show()); }
    }

    private WebView createWebView() {
        WebView webView = new WebView(this);

        WebSettings s = webView.getSettings();

        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setSupportMultipleWindows(false);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        }

        CookieManager.getInstance().setAcceptCookie(true);
        webView.addJavascriptInterface(new AndroidBridge(), "Android");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            s.setForceDark(
                    darkMode
                            ? WebSettings.FORCE_DARK_ON
                            : WebSettings.FORCE_DARK_OFF
            );
        }

        webView.setBackgroundColor(darkMode ? Color.rgb(25,25,25) : Color.WHITE);

        webView.setWebViewClient(new WebViewClient() {

            @Override
            public boolean shouldOverrideUrlLoading(
                    WebView view,
                    WebResourceRequest request
            ) {
                return handleUrl(request.getUrl().toString());
            }

            @Override
            public boolean shouldOverrideUrlLoading(
                    WebView view,
                    String url
            ) {
                return handleUrl(url);
            }

            @Override
            public void onPageStarted(
                    WebView view,
                    String url,
                    android.graphics.Bitmap favicon
            ) {
                progressBar.setVisibility(View.VISIBLE);
                updateAddress(url);
                updateSecurity(url);
            }

            @Override
            public void onPageFinished(
                    WebView view,
                    String url
            ) {
                progressBar.setVisibility(View.GONE);
                updateAddress(url);
                updateSecurity(url);

                String title = view.getTitle();

                if (title == null || title.trim().isEmpty()) {
                    title = "Yangi tab";
                }

                int index = tabs.indexOf(view);

                if (index >= 0) {
                    tabTitles.set(index, title);
                    refreshTabs();
                }

                addHistory(url, title);

                boolean isFileUrl = (url != null) && url.startsWith("file://");

                if (darkMode && !isFileUrl) {
                    String css =
                        "(function(){" +
                        "var id='__miniuz_dark_css__';" +
                        "if(document.getElementById(id))return;" +
                        "var s=document.createElement('style');" +
                        "s.id=id;" +
                        "s.innerHTML='html{filter:invert(1) hue-rotate(180deg);background:#111;}' +" +
                        "'img,video,picture,canvas,svg,iframe{filter:invert(1) hue-rotate(180deg);}';" +
                        "document.documentElement.appendChild(s);" +
                        "})();";
                    view.evaluateJavascript(css, null);
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {

            @Override
            public void onProgressChanged(
                    WebView view,
                    int newProgress
            ) {
                progressBar.setProgress(newProgress);

                if (newProgress >= 100) {
                    progressBar.setVisibility(View.GONE);
                } else {
                    progressBar.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public boolean onShowFileChooser(
                    WebView webView,
                    ValueCallback<Uri[]> callback,
                    FileChooserParams params
            ) {
                if (fileCallback != null) {
                    fileCallback.onReceiveValue(null);
                }

                fileCallback = callback;

                try {
                    Intent intent = params.createIntent();
                    startActivityForResult(
                            intent,
                            FILE_CHOOSER_REQUEST
                    );
                    return true;
                } catch (ActivityNotFoundException e) {
                    fileCallback = null;
                    Toast.makeText(
                            MainActivity.this,
                            "Fayl tanlash oynasi ochilmadi",
                            Toast.LENGTH_SHORT
                    ).show();
                    return false;
                }
            }

            @Override
            public boolean onJsAlert(
                    WebView view,
                    String url,
                    String message,
                    JsResult result
            ) {
                new AlertDialog.Builder(MainActivity.this)
                        .setMessage(message)
                        .setPositiveButton("OK",
                                (dialog, which) -> result.confirm())
                        .setOnCancelListener(
                                dialog -> result.cancel()
                        )
                        .show();

                return true;
            }
        });

        webView.setDownloadListener(
                new DownloadListener() {
                    @Override
                    public void onDownloadStart(
                            String url,
                            String userAgent,
                            String contentDisposition,
                            String mimeType,
                            long contentLength
                    ) {
                        downloadFile(
                                url,
                                userAgent,
                                contentDisposition,
                                mimeType
                        );
                    }
                }
        );

        return webView;
    }

    private boolean handleUrl(String url) {
        if (url == null) return false;

        String lower = url.toLowerCase(Locale.US);

        if (lower.startsWith("http://") ||
                lower.startsWith("https://")) {
            return false;
        }

        try {
            Intent intent = new Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(url)
            );
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(
                    this,
                    "Bu havola ochilmadi",
                    Toast.LENGTH_SHORT
            ).show();
        }

        return true;
    }

    private void selectTab(int index) {
        if (index < 0 || index >= tabs.size()) return;

        currentTab = index;

        for (int i = 0; i < tabs.size(); i++) {
            tabs.get(i).setVisibility(
                    i == index ? View.VISIBLE : View.GONE
            );
        }

        WebView w = currentWebView();

        if (w != null) {
            updateAddress(w.getUrl());
            updateSecurity(w.getUrl());
        }

        refreshTabs();
    }

    private void closeTab(int index) {
        if (tabs.size() <= 1) {
            tabs.get(0).loadUrl(HOME_URL);
            return;
        }

        WebView w = tabs.get(index);

        webContainer.removeView(w);
        w.stopLoading();
        w.destroy();

        tabs.remove(index);
        tabTitles.remove(index);

        if (currentTab >= tabs.size()) {
            currentTab = tabs.size() - 1;
        } else if (index < currentTab) {
            currentTab--;
        }

        selectTab(currentTab);
    }

    private void refreshTabs() {
        if (tabBar == null) return;

        tabBar.removeAllViews();

        for (int i = 0; i < tabs.size(); i++) {
            final int index = i;

            LinearLayout tab = new LinearLayout(this);
            tab.setOrientation(LinearLayout.HORIZONTAL);
            tab.setGravity(Gravity.CENTER_VERTICAL);
            tab.setPadding(dp(10), 0, dp(5), 0);

            boolean selected = i == currentTab;

            tab.setBackground(
                    roundDrawable(
                            selected
                                    ? (darkMode
                                    ? Color.rgb(60,60,60)
                                    : Color.WHITE)
                                    : (darkMode
                                    ? Color.rgb(40,40,40)
                                    : Color.rgb(225,225,225)),
                            18
                    )
            );

            TextView title = new TextView(this);

            String t = tabTitles.get(i);

            if (t == null || t.isEmpty()) {
                t = "Yangi tab";
            }

            if (t.length() > 18) {
                t = t.substring(0, 18) + "…";
            }

            title.setText(t);
            title.setTextSize(13);
            title.setTextColor(
                    darkMode ? Color.WHITE : Color.DKGRAY
            );
            title.setSingleLine(true);
            title.setGravity(Gravity.CENTER_VERTICAL);

            TextView close = new TextView(this);
            close.setText("×");
            close.setTextSize(20);
            close.setGravity(Gravity.CENTER);
            close.setTextColor(
                    darkMode ? Color.LTGRAY : Color.DKGRAY
            );
            close.setPadding(dp(7), 0, dp(3), 0);

            close.setOnClickListener(v -> closeTab(index));

            tab.addView(title,
                    new LinearLayout.LayoutParams(
                            0,
                            -1,
                            1
                    ));

            tab.addView(close,
                    new LinearLayout.LayoutParams(
                            dp(28),
                            -1
                    ));

            tab.setOnClickListener(v -> selectTab(index));

            LinearLayout.LayoutParams lp =
                    new LinearLayout.LayoutParams(
                            dp(155),
                            dp(36)
                    );

            lp.setMargins(dp(3), 0, dp(3), 0);

            tabBar.addView(tab, lp);
        }

        TextView plus = new TextView(this);
        plus.setText("+");
        plus.setTextSize(25);
        plus.setGravity(Gravity.CENTER);
        plus.setTextColor(
                darkMode ? Color.WHITE : Color.DKGRAY
        );
        plus.setBackground(
                roundDrawable(
                        darkMode
                                ? Color.rgb(55,55,55)
                                : Color.rgb(230,230,230),
                        18
                )
        );

        plus.setOnClickListener(v -> newTab(HOME_URL));

        LinearLayout.LayoutParams plusLp =
                new LinearLayout.LayoutParams(
                        dp(48),
                        dp(36)
                );

        plusLp.setMargins(dp(4), 0, dp(4), 0);

        tabBar.addView(plus, plusLp);
    }

    private void showAddressInput() {
        EditText input = new EditText(this);
        input.setHint("Manzil yoki qidiruv...");
        WebView w = currentWebView();
        if (w != null && w.getUrl() != null) {
            input.setText(w.getUrl());
        }

        new AlertDialog.Builder(this)
                .setTitle("Manzilga o‘tish")
                .setView(input)
                .setPositiveButton("O‘tish", (dialog, which) -> {
                    openAddress(input.getText().toString());
                })
                .setNegativeButton("Bekor qilish", null)
                .show();
    }

    private void showMenu() {
        if (activeMenu != null) return;

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(6), dp(6), dp(6), dp(6));
        panel.setBackground(
                roundDrawable(
                        darkMode
                                ? Color.rgb(38,38,38)
                                : Color.WHITE,
                        18
                )
        );

        String darkText = darkMode
                ? "☀ Kunduzgi rejim"
                : "🌙 Tungi rejim";

        addMenuItem(panel, "➕ Yangi tab",
                () -> newTab(HOME_URL));

        addMenuItem(panel, "🔗 Manzil kiritish",
                this::showAddressInput);

        addMenuItem(panel, "⭐ Bookmark qo‘shish",
                this::addBookmark);

        addMenuItem(panel, "🔖 Bookmarklar",
                this::showBookmarks);

        addMenuItem(panel, "🕘 Tarix",
                this::showHistory);

        addMenuItem(panel, "🗑 Tarixni tozalash",
                this::clearHistory);

        addMenuItem(panel, "⬇ Yuklamalar",
                this::showDownloads);

        addMenuItem(panel, "📤 Sahifani ulashish",
                this::sharePage);

        addMenuItem(panel, darkText,
                this::toggleDarkMode);

        addMenuItem(panel, "⚙ Brauzer haqida",
                this::showAbout);

        PopupWindow popup = new PopupWindow(
                panel,
                dp(285),
                WindowManagerLayout.WRAP_CONTENT,
                true
        );

        popup.setBackgroundDrawable(
                new android.graphics.drawable.ColorDrawable(
                        Color.TRANSPARENT
                )
        );

        popup.setOutsideTouchable(true);

        if (Build.VERSION.SDK_INT >= 21) {
            popup.setElevation(dp(10));
        }

        activeMenu = popup;
        activeMenuPanel = panel;

        popup.setOnDismissListener(() -> {
            if (activeMenu == popup) {
                activeMenu = null;
                activeMenuPanel = null;
            }
        });

        popup.showAsDropDown(
                menuButton,
                -dp(245),
                dp(4)
        );

        panel.post(() -> animateMenuIn(panel));
    }

    private void addMenuItem(
            LinearLayout parent,
            String text,
            Runnable action
    ) {
        TextView item = new TextView(this);

        item.setText(text);
        item.setTextSize(15);
        item.setTextColor(
                darkMode ? Color.WHITE : Color.rgb(35,35,35)
        );
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(dp(14), 0, dp(10), 0);
        item.setSingleLine(true);

        item.setBackground(
                roundDrawable(
                        darkMode
                                ? Color.rgb(48,48,48)
                                : Color.rgb(248,248,248),
                        12
                )
        );

        item.setOnClickListener(v ->
                closeMenuThen(action)
        );

        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(
                        -1,
                        dp(47)
                );

        lp.setMargins(0, dp(2), 0, dp(2));

        parent.addView(item, lp);
    }

    private void animateMenuIn(View panel) {
        if (menuButton == null) return;

        int[] p = new int[2];
        int[] a = new int[2];

        panel.getLocationOnScreen(p);
        menuButton.getLocationOnScreen(a);

        float pivotX =
                a[0] +
                menuButton.getWidth() / 2f -
                p[0];

        float pivotY =
                a[1] +
                menuButton.getHeight() / 2f -
                p[1];

        panel.setPivotX(pivotX);
        panel.setPivotY(pivotY);
        panel.setScaleX(0.72f);
        panel.setScaleY(0.72f);
        panel.setAlpha(0f);

        panel.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(190)
                .setInterpolator(
                        new DecelerateInterpolator()
                )
                .start();
    }

    private void closeMenuThen(Runnable action) {
        if (activeMenu == null ||
                activeMenuPanel == null) {
            if (action != null) action.run();
            return;
        }

        PopupWindow popup = activeMenu;
        View panel = activeMenuPanel;

        activeMenu = null;
        activeMenuPanel = null;

        int[] p = new int[2];
        int[] a = new int[2];

        panel.getLocationOnScreen(p);
        menuButton.getLocationOnScreen(a);

        panel.setPivotX(
                a[0] +
                menuButton.getWidth() / 2f -
                p[0]
        );

        panel.setPivotY(
                a[1] +
                menuButton.getHeight() / 2f -
                p[1]
        );

        panel.animate()
                .scaleX(0.72f)
                .scaleY(0.72f)
                .alpha(0f)
                .setDuration(190)
                .setInterpolator(
                        new DecelerateInterpolator()
                )
                .withEndAction(() -> {
                    popup.dismiss();

                    if (action != null) {
                        action.run();
                    }
                })
                .start();
    }

    private void addBookmark() {
        WebView w = currentWebView();

        if (w == null || w.getUrl() == null) {
            Toast.makeText(
                    this,
                    "Saqlanadigan sahifa yo‘q",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        String url = w.getUrl();

        if (!url.startsWith("http://") &&
                !url.startsWith("https://")) {
            Toast.makeText(
                    this,
                    "Bu sahifani bookmark qilib bo‘lmaydi",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        String title = w.getTitle();

        if (title == null || title.isEmpty()) {
            title = url;
        }

        JSONArray old = readArray("bookmarks");
        JSONArray result = new JSONArray();

        try {
            JSONObject first = new JSONObject();
            first.put("title", title);
            first.put("url", url);
            result.put(first);

            for (int i = 0; i < old.length(); i++) {
                JSONObject o = old.getJSONObject(i);

                if (!url.equals(o.optString("url"))) {
                    result.put(o);
                }
            }

            prefs.edit()
                    .putString(
                            "bookmarks",
                            result.toString()
                    )
                    .apply();

            Toast.makeText(
                    this,
                    "⭐ Bookmark saqlandi",
                    Toast.LENGTH_SHORT
            ).show();

        } catch (Exception e) {
            Toast.makeText(
                    this,
                    "Bookmark saqlanmadi",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    private void showBookmarks() {
        JSONArray array = readArray("bookmarks");

        if (array.length() == 0) {
            showMessage(
                    "Bookmarklar",
                    "Hozircha bookmark yo‘q."
            );
            return;
        }

        List<String> titles = new ArrayList<>();
        List<String> urls = new ArrayList<>();

        try {
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.getJSONObject(i);

                String title = o.optString(
                        "title",
                        "Bookmark"
                );

                String url = o.optString("url");

                titles.add(title);
                urls.add(url);
            }
        } catch (Exception ignored) {
        }

        String[] items = titles.toArray(
                new String[0]
        );

        new AlertDialog.Builder(this)
                .setTitle("🔖 Bookmarklar")
                .setItems(items, (dialog, which) -> {
                    WebView w = currentWebView();

                    if (w != null) {
                        w.loadUrl(urls.get(which));
                    }
                })
                .setNegativeButton(
                        "Yopish",
                        null
                )
                .show();
    }

    private void addHistory(String url, String title) {
        if (url == null ||
                (!url.startsWith("http://") &&
                 !url.startsWith("https://"))) {
            return;
        }

        JSONArray old = readArray("history");
        JSONArray result = new JSONArray();

        try {
            JSONObject first = new JSONObject();
            first.put("title",
                    title == null || title.isEmpty()
                            ? url
                            : title);
            first.put("url", url);

            result.put(first);

            int count = 0;

            for (int i = 0; i < old.length(); i++) {
                JSONObject o = old.getJSONObject(i);

                if (url.equals(o.optString("url"))) {
                    continue;
                }

                if (count >= 99) break;

                result.put(o);
                count++;
            }

            prefs.edit()
                    .putString(
                            "history",
                            result.toString()
                    )
                    .apply();

        } catch (Exception ignored) {
        }
    }

    private void showHistory() {
        JSONArray array = readArray("history");

        if (array.length() == 0) {
            showMessage(
                    "🕘 Tarix",
                    "Tarix bo‘sh."
            );
            return;
        }

        List<String> titles = new ArrayList<>();
        List<String> urls = new ArrayList<>();

        try {
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.getJSONObject(i);

                titles.add(o.optString(
                        "title",
                        o.optString("url")
                ));

                urls.add(o.optString("url"));
            }
        } catch (Exception ignored) {
        }

        new AlertDialog.Builder(this)
                .setTitle("🕘 Tarix")
                .setItems(
                        titles.toArray(new String[0]),
                        (dialog, which) -> {
                            WebView w = currentWebView();

                            if (w != null) {
                                w.loadUrl(urls.get(which));
                            }
                        }
                )
                .setNegativeButton(
                        "Yopish",
                        null
                )
                .show();
    }

    private void clearHistory() {
        new AlertDialog.Builder(this)
                .setTitle("Tarixni tozalash")
                .setMessage(
                        "Barcha ko‘rish tarixini o‘chirasizmi?"
                )
                .setNegativeButton(
                        "Yo‘q",
                        null
                )
                .setPositiveButton(
                        "Ha",
                        (dialog, which) -> {
                            prefs.edit()
                                    .remove("history")
                                    .apply();

                            Toast.makeText(
                                    this,
                                    "Tarix tozalandi",
                                    Toast.LENGTH_SHORT
                            ).show();
                        }
                )
                .show();
    }

    private void downloadFile(
            String url,
            String userAgent,
            String contentDisposition,
            String mimeType
    ) {
        if (Build.VERSION.SDK_INT <= 28 &&
                checkSelfPermission(
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED) {

            prefs.edit()
                    .putString("pending_download_url", url)
                    .putString(
                            "pending_download_agent",
                            userAgent == null ? "" : userAgent
                    )
                    .putString(
                            "pending_download_disposition",
                            contentDisposition == null
                                    ? ""
                                    : contentDisposition
                    )
                    .putString(
                            "pending_download_mime",
                            mimeType == null ? "" : mimeType
                    )
                    .apply();

            requestPermissions(
                    new String[]{
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                    },
                    STORAGE_REQUEST
            );

            return;
        }

        try {
            DownloadManager.Request request =
                    new DownloadManager.Request(
                            Uri.parse(url)
                    );

            request.setNotificationVisibility(
                    DownloadManager.Request
                            .VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            );

            request.setTitle(
                    "MINI UZ yuklamasi"
            );

            request.setDescription(url);

            if (userAgent != null &&
                    !userAgent.isEmpty()) {
                request.addRequestHeader(
                        "User-Agent",
                        userAgent
                );
            }
                    String fileName = android.webkit.URLUtil.guessFileName(url, contentDisposition, mimeType);
                    request.setMimeType(mimeType);
                    request.setDescription("MINI UZ yuklamasi");
                    request.setTitle(fileName);
                    request.setTitle(fileName);
            if (fileName == null ||
                    fileName.trim().isEmpty()) {
                fileName = guessFileName(url);
            }

            if (mimeType != null &&
                    !mimeType.trim().isEmpty()) {
                request.setMimeType(mimeType);
            }

            request.setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    fileName
            );

            DownloadManager manager =
                    (DownloadManager)
                            getSystemService(
                                    DOWNLOAD_SERVICE
                            );

            if (manager != null) {
                manager.enqueue(request);

                Toast.makeText(
                        this,
                        "⬇ Yuklash boshlandi",
                        Toast.LENGTH_SHORT
                ).show();
            }

        } catch (Exception e) {
            Toast.makeText(
                    this,
                    "Yuklashni boshlashda xato",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    private String guessFileName(String url) {
        try {
            String path = Uri.parse(url).getPath();

            if (path != null) {
                int slash = path.lastIndexOf('/');

                if (slash >= 0 &&
                        slash < path.length() - 1) {
                    String name =
                            path.substring(slash + 1);

                    if (!name.isEmpty()) {
                        return name;
                    }
                }
            }
        } catch (Exception ignored) {
        }

        return "MINIUZ_download";
    }

    private void showDownloads() {
        try {
            Intent intent = new Intent(
                    DownloadManager.ACTION_VIEW_DOWNLOADS
            );

            startActivity(intent);

        } catch (Exception e) {
            Toast.makeText(
                    this,
                    "Yuklamalar oynasi ochilmadi",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    private void sharePage() {
        WebView w = currentWebView();

        if (w == null || w.getUrl() == null) {
            return;
        }

        String title = w.getTitle();

        if (title == null || title.isEmpty()) {
            title = "MINI UZ";
        }

        Intent share = new Intent(
                Intent.ACTION_SEND
        );

        share.setType("text/plain");

        share.putExtra(
                Intent.EXTRA_SUBJECT,
                title
        );

        share.putExtra(
                Intent.EXTRA_TEXT,
                w.getUrl()
        );

        startActivity(
                Intent.createChooser(
                        share,
                        "Sahifani ulashish"
                )
        );
    }

    private void toggleDarkMode() {
        darkMode = !darkMode;

        prefs.edit()
                .putBoolean("dark_mode", darkMode)
                .apply();

        applyTheme();

        for (WebView w : tabs) {
            WebSettings s = w.getSettings();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                s.setForceDark(
                        darkMode
                                ? WebSettings.FORCE_DARK_ON
                                : WebSettings.FORCE_DARK_OFF
                );
            }

            w.setBackgroundColor(
                    darkMode
                            ? Color.rgb(25,25,25)
                            : Color.WHITE
            );
        }
    }

    private void applyTheme() {
        if (browserRoot == null) return;

        browserRoot.setBackgroundColor(bgColor());

        if (toolbar != null) {
            toolbar.setBackgroundColor(toolbarColor());
        }

        if (tabBar != null) {
            tabBar.setBackgroundColor(tabColor());
        }

        if (addressBar != null) {
            addressBar.setTextColor(textColor());
            addressBar.setHintTextColor(hintColor());
            addressBar.setBackground(
                    roundDrawable(
                            addressColor(),
                            22
                    )
            );
        }

        if (progressBar != null) {
            progressBar.setProgressTintList(
                    android.content.res.ColorStateList.valueOf(
                            Color.rgb(25, 120, 70)
                    )
            );
        }

        getWindow().setStatusBarColor(
                darkMode
                        ? Color.rgb(20,20,20)
                        : Color.rgb(30,120,65)
        );

        refreshTabs();
    }

    private void showAbout() {
        new AlertDialog.Builder(this)
                .setTitle("MINI UZ")
                .setMessage(
                        "MINI UZ — tezkor Android WebView brauzer.\n\n" +
                        "Versiya: 1.0\n\n" +
                        "Bookmark, tarix, tablar, yuklamalar, " +
                        "fayl yuklash, ulashish va tungi rejim mavjud."
                )
                .setPositiveButton("OK", null)
                .show();
    }

    private void showMessage(
            String title,
            String message
    ) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    private JSONArray readArray(String key) {
        try {
            return new JSONArray(
                    prefs.getString(key, "[]")
            );
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private void openAddress(String input) {
        String text = input == null
                ? ""
                : input.trim();

        if (text.isEmpty()) return;

        WebView w = currentWebView();

        if (w == null) return;

        String url;

        if (text.startsWith("http://") ||
                text.startsWith("https://")) {

            url = text;

        } else if (text.contains(".") &&
                !text.contains(" ")) {

            url = "https://" + text;

        } else {

            url =
                    "https://www.google.com/search?q=" +
                    Uri.encode(text);
        }

        w.loadUrl(url);
    }

    private void updateAddress(String url) {
        if (addressBar == null || url == null) return;

        addressBar.setText(url);
        addressBar.setSelection(
                addressBar.getText().length()
        );
    }

    private void updateSecurity(String url) {
        if (securityView == null) return;

        if (url == null) {
            securityView.setText("•");
            return;
        }

        if (url.startsWith("https://")) {
            securityView.setText("🔒");
        } else if (url.startsWith("http://")) {
            securityView.setText("⚠");
        } else {
            securityView.setText("•");
        }
    }

    private WebView currentWebView() {
        if (currentTab < 0 ||
                currentTab >= tabs.size()) {
            return null;
        }

        return tabs.get(currentTab);
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data
    ) {
        super.onActivityResult(
                requestCode,
                resultCode,
                data
        );

        if (requestCode == FILE_CHOOSER_REQUEST) {

            if (fileCallback == null) {
                return;
            }

            Uri[] results = null;

            if (resultCode == RESULT_OK &&
                    data != null) {

                if (data.getClipData() != null) {

                    int count =
                            data.getClipData().getItemCount();

                    results = new Uri[count];

                    for (int i = 0; i < count; i++) {
                        results[i] =
                                data.getClipData()
                                        .getItemAt(i)
                                        .getUri();
                    }

                } else if (data.getData() != null) {
                    results = new Uri[]{
                            data.getData()
                    };
                }
            }

            fileCallback.onReceiveValue(results);
            fileCallback = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {
        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
        );

        if (requestCode == STORAGE_REQUEST &&
                grantResults.length > 0 &&
                grantResults[0] ==
                        PackageManager.PERMISSION_GRANTED) {

            String url = prefs.getString(
                    "pending_download_url",
                    ""
            );

            if (!url.isEmpty()) {
                downloadFile(
                        url,
                        prefs.getString(
                                "pending_download_agent",
                                ""
                        ),
                        prefs.getString(
                                "pending_download_disposition",
                                ""
                        ),
                        prefs.getString(
                                "pending_download_mime",
                                ""
                        )
                );
            }
        }
    }

    @Override
    public void onBackPressed() {

        if (activeMenu != null) {
            closeMenuThen(null);
            return;
        }

        WebView w = currentWebView();

        if (w != null && w.canGoBack()) {
            w.goBack();
            return;
        }

        if (tabs.size() > 1) {
            closeTab(currentTab);
            return;
        }

        super.onBackPressed();
    }

    private TextView toolbarButton(String text) {
        TextView b = new TextView(this);

        b.setText(text);
        b.setTextSize(22);
        b.setGravity(Gravity.CENTER);
        b.setTextColor(
                darkMode ? Color.WHITE : Color.WHITE
        );
        b.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        return b;
    }

    private LinearLayout.LayoutParams fixed(
            int width
    ) {
        return new LinearLayout.LayoutParams(
                dp(width),
                dp(48)
        );
    }

    private int dp(int value) {
        return (int) (
                value *
                getResources()
                        .getDisplayMetrics()
                        .density
        );
    }

    private int bgColor() {
        return darkMode
                ? Color.rgb(24,24,24)
                : Color.WHITE;
    }

    private int toolbarColor() {
        return darkMode
                ? Color.rgb(30,30,30)
                : Color.rgb(35,145,75);
    }

    private int tabColor() {
        return darkMode
                ? Color.rgb(28,28,28)
                : Color.rgb(235,235,235);
    }

    private int addressColor() {
        return darkMode
                ? Color.rgb(55,55,55)
                : Color.rgb(245,245,245);
    }

    private int textColor() {
        return darkMode
                ? Color.WHITE
                : Color.rgb(30,30,30);
    }

    private int hintColor() {
        return darkMode
                ? Color.rgb(180,180,180)
                : Color.rgb(120,120,120);
    }

    private android.graphics.drawable.GradientDrawable
    roundDrawable(
            int color,
            float radius
    ) {
        android.graphics.drawable.GradientDrawable d =
                new android.graphics.drawable.GradientDrawable();

        d.setColor(color);
        d.setCornerRadius(dp((int) radius));

        return d;
    }

    private static class WindowManagerLayout {
        static final int WRAP_CONTENT =
                ViewGroup.LayoutParams.WRAP_CONTENT;
    }

    public class SplashView extends View {

        private final Paint paint = new Paint(
                Paint.ANTI_ALIAS_FLAG
        );

        private long startTime;

        public SplashView(Context context) {
            super(context);

            paint.setTypeface(
                    Typeface.create(
                            Typeface.DEFAULT,
                            Typeface.BOLD
                    )
            );

            startTime = System.currentTimeMillis();

            setBackgroundColor(
                    Color.rgb(35,145,75)
            );

            postInvalidateDelayed(30);
        }

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);

            float w = getWidth();
            float h = getHeight();

            long elapsed =
                    System.currentTimeMillis() -
                    startTime;

            float centerX = w / 2f;
            float centerY = h / 2f - dp(50);

            float birdMove =
                    (float) Math.sin(
                            elapsed / 500.0
                    ) * dp(9);

            float bx = centerX;
            float by = centerY + birdMove;

            paint.setStyle(Paint.Style.FILL);

            paint.setColor(Color.YELLOW);

            PathHelper.drawBird(
                    c,
                    paint,
                    bx,
                    by,
                    dp(115)
            );

            paint.setColor(Color.BLACK);

            c.drawCircle(
                    bx + dp(34),
                    by - dp(10),
                    dp(5),
                    paint
            );

            paint.setColor(Color.rgb(255,170,0));

            PathHelper.drawTriangle(
                    c,
                    paint,
                    bx + dp(52),
                    by,
                    dp(22)
            );

            String letters = "MINIUZ";

            float letterSize = dp(45);

            paint.setTextSize(letterSize);
            paint.setTextAlign(
                    Paint.Align.CENTER
            );
            paint.setTypeface(
                    Typeface.create(
                            Typeface.DEFAULT,
                            Typeface.BOLD
                    )
            );
            paint.setColor(Color.YELLOW);

            float spacing = dp(48);
            float total = spacing *
                    (letters.length() - 1);

            long letterDelay = 250;

            for (int i = 0;
                    i < letters.length();
                    i++) {

                long local =
                        elapsed -
                        900 -
                        i * letterDelay;

                if (local <= 0) continue;

                float alpha =
                        Math.min(
                                1f,
                                local / 220f
                        );

                paint.setAlpha(
                        (int) (255 * alpha)
                );

                float x =
                        centerX -
                        total / 2f +
                        i * spacing;

                c.drawText(
                        String.valueOf(
                                letters.charAt(i)
                        ),
                        x,
                        h - dp(130),
                        paint
                );
            }

            paint.setAlpha(255);

            paint.setTextSize(dp(17));
            paint.setColor(
                    Color.rgb(235,255,235)
            );

            c.drawText(
                    "Tezkor va yengil brauzer",
                    centerX,
                    h - dp(78),
                    paint
            );

            postInvalidateDelayed(30);
        }
    }

    private static class PathHelper {

        static void drawBird(
                Canvas c,
                Paint p,
                float x,
                float y,
                float size
        ) {
            android.graphics.Path path =
                    new android.graphics.Path();

            float s = size / 115f;

            path.moveTo(
                    x - 55 * s,
                    y
            );

            path.cubicTo(
                    x - 25 * s,
                    y - 40 * s,
                    x + 25 * s,
                    y - 40 * s,
                    x + 55 * s,
                    y
            );

            path.cubicTo(
                    x + 25 * s,
                    y + 35 * s,
                    x - 25 * s,
                    y + 35 * s,
                    x - 55 * s,
                    y
            );

            c.drawPath(path, p);

            android.graphics.Path wing =
                    new android.graphics.Path();

            wing.moveTo(
                    x - 8 * s,
                    y - 5 * s
            );

            wing.lineTo(
                    x - 48 * s,
                    y - 42 * s
            );

            wing.lineTo(
                    x - 22 * s,
                    y + 3 * s
            );

            wing.close();

            c.drawPath(wing, p);
        }

        static void drawTriangle(
                Canvas c,
                Paint p,
                float x,
                float y,
                float size
        ) {
            android.graphics.Path path =
                    new android.graphics.Path();

            path.moveTo(
                    x,
                    y
            );

            path.lineTo(
                    x + size,
                    y + size / 3
            );

            path.lineTo(
                    x,
                    y + size * 2 / 3
            );

            path.close();

            c.drawPath(path, p);
        }
    }
}
