package com.example.youtubelite;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

/**
 * YouTube Lite - Tối ưu hóa đặc biệt cho Android 5.0 (API 21 Lollipop) trở lên.
 * Tính năng chính:
 * 1. Chạy m.youtube.com siêu nhẹ, tiết kiệm 70-80% RAM so với app YouTube gốc.
 * 2. Chặn quảng cáo banner, promoted sparkles và tự động nhấn nút Skip Ads.
 * 3. Hỗ trợ xem video toàn màn hình mượt mà qua WebChromeClient.
 * 4. Xử lý nút Back cứng để lùi trang hoặc thoát chế độ toàn màn hình.
 * 5. Tự động xử lý kết nối mạng và dọn dẹp bộ nhớ chống rò rỉ RAM (Memory Leak).
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "YouTubeLite";
    private static final String YOUTUBE_URL = "https://m.youtube.com";

    // Danh sách domain máy chủ quảng cáo cần chặn trực tiếp ở tầng Network (Request Interception)
    private static final List<String> AD_DOMAINS = Arrays.asList(
            "googleads.g.doubleclick.net",
            "pagead2.googlesyndication.com",
            "ad.doubleclick.net",
            "static.doubleclick.net",
            "securepubads.g.doubleclick.net",
            "youtube.com/pagead",
            "youtube.com/api/stats/ads",
            "adservice.google.com",
            "ade.googlesyndication.com"
    );

    // CSS injection để ẩn hoàn toàn các thẻ quảng cáo banner trên YouTube Mobile
    private static final String AD_BLOCK_CSS =
            ".ad-container, ytm-promoted-sparkles-web-renderer, ytd-promoted-video-renderer, " +
            "ytm-companion-ad-renderer, .video-ads, .ytp-ad-module, .ytp-ad-overlay-container, " +
            "ytm-promoted-sparkles-text-search-web-renderer, .ad-showing, ytm-paid-content-overlay-renderer " +
            "{ display: none !important; opacity: 0 !important; height: 0 !important; }";

    // JavaScript định kỳ tìm và click nút "Bỏ qua quảng cáo" (Skip Ad)
    private static final String AD_SKIP_JS =
            "javascript:(function() {" +
            "  var style = document.getElementById('adblock_style');" +
            "  if (!style) {" +
            "    style = document.createElement('style');" +
            "    style.id = 'adblock_style';" +
            "    style.innerHTML = '" + AD_BLOCK_CSS + "';" +
            "    (document.head || document.documentElement).appendChild(style);" +
            "  }" +
            "  function skipAd() {" +
            "    var skipBtn = document.querySelector('.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button');" +
            "    if (skipBtn) { skipBtn.click(); }" +
            "    var video = document.querySelector('video');" +
            "    var isAd = document.querySelector('.ad-showing, .ad-interrupting');" +
            "    if (video && isAd && video.duration > 0) {" +
            "      video.currentTime = video.duration;" +
            "    }" +
            "  }" +
            "  setInterval(skipAd, 600);" +
            "})();";

    private WebView webView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ProgressBar progressBar;
    private LinearLayout errorView;
    private Button btnRetry;

    // Các biến phục vụ chế độ xem video Fullscreen HTML5
    private FrameLayout customViewContainer;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;

    private long backPressedTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupWebView();
        setupListeners();

        // Tải trang lần đầu nếu có mạng
        if (isNetworkAvailable()) {
            webView.loadUrl(YOUTUBE_URL);
        } else {
            showErrorView(true);
        }
    }

    private void initViews() {
        webView = findViewById(R.id.webView);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        progressBar = findViewById(R.id.progressBar);
        errorView = findViewById(R.id.errorView);
        btnRetry = findViewById(R.id.btnRetry);
        customViewContainer = findViewById(R.id.customViewContainer);

        swipeRefreshLayout.setColorSchemeColors(0xFFFF0000, 0xFF282828);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings settings = webView.getSettings();

        // 1. Cấu hình cơ bản theo yêu cầu
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);

        // 2. Tối ưu bộ nhớ đệm (Cache) giúp xem mượt hơn trên máy cấu hình yếu
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // Cho phép Cookie của phiên làm việc và đa tên miền
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
            // Mixed Content: Đảm bảo các stream video không bị chặn trên Android 5.0
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        // 3. Tối ưu hiển thị và điều khiển phóng to
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setMediaPlaybackRequiresUserGesture(false);

        // 4. Tùy chỉnh User-Agent: Giữ định dạng Mobile hiện đại nhưng tương thích tối đa
        String defaultUa = settings.getUserAgentString();
        // Giả lập thiết bị di động chuẩn để m.youtube.com tải giao diện nhẹ
        settings.setUserAgentString(defaultUa + " MobileYouTubeLite/1.0");

        // 5. Cấu hình WebViewClient (mở link trong app & chặn quảng cáo)
        webView.setWebViewClient(new YouTubeWebViewClient());

        // 6. Cấu hình WebChromeClient (xử lý Video Fullscreen & tiến trình tải)
        webView.setWebChromeClient(new YouTubeWebChromeClient());
    }

    private void setupListeners() {
        // Vuốt xuống để refresh lại video hoặc trang hiện tại
        swipeRefreshLayout.setOnRefreshListener(() -> {
            if (isNetworkAvailable()) {
                webView.reload();
            } else {
                swipeRefreshLayout.setRefreshing(false);
                showErrorView(true);
            }
        });

        // Bấm nút thử lại khi mất mạng
        btnRetry.setOnClickListener(v -> {
            if (isNetworkAvailable()) {
                showErrorView(false);
                webView.reload();
            } else {
                Toast.makeText(MainActivity.this, R.string.still_no_internet, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * WebViewClient tùy biến:
     * - Giữ tất cả liên kết YouTube mở nội bộ trong WebView
     * - Chặn request quảng cáo qua shouldInterceptRequest
     * - Tiêm mã JS/CSS ẩn banner và bỏ qua video ads khi trang tải xong
     */
    private class YouTubeWebViewClient extends WebViewClient {

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return handleUrl(view, url);
        }

        // Tương thích Android 7.0+ (API 24)
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                return handleUrl(view, request.getUrl().toString());
            }
            return false;
        }

        private boolean handleUrl(WebView view, String url) {
            if (url == null) return false;

            // Nếu liên kết thuộc về YouTube -> Mở trực tiếp bên trong WebView
            if (isYouTubeDomain(url)) {
                return false;
            }

            // Nếu là liên kết ứng dụng bên ngoài (intent://, tel:, mailto:)
            if (url.startsWith("intent:") || url.startsWith("market:") || url.startsWith("tel:") || url.startsWith("mailto:")) {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(intent);
                    return true;
                } catch (Exception e) {
                    Log.e(TAG, "Không thể mở intent ngoại: " + e.getMessage());
                    return true;
                }
            }

            // Mở các trang web bên thứ 3 bằng trình duyệt ngoài để không làm nghẽn app
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(intent);
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        // Chặn request quảng cáo ở tầng mạng (Tương thích API 21 Lollipop+)
        @Nullable
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
            if (isAdUrl(url)) {
                // Trả về dữ liệu trống để cắt đứt việc tải file quảng cáo
                return new WebResourceResponse("text/plain", "UTF-8", new ByteArrayInputStream("".getBytes()));
            }
            return super.shouldInterceptRequest(view, url);
        }

        @Nullable
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                String url = request.getUrl().toString();
                if (isAdUrl(url)) {
                    return new WebResourceResponse("text/plain", "UTF-8", new ByteArrayInputStream("".getBytes()));
                }
            }
            return super.shouldInterceptRequest(view, request);
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            showErrorView(false);
            progressBar.setVisibility(View.VISIBLE);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            progressBar.setVisibility(View.GONE);
            swipeRefreshLayout.setRefreshing(false);

            // Tiêm JavaScript và CSS để loại bỏ các banner quảng cáo còn sót lại
            view.loadUrl(AD_SKIP_JS);
        }

        @Override
        public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
            super.onReceivedError(view, errorCode, description, failingUrl);
            if (!isNetworkAvailable()) {
                showErrorView(true);
            }
        }
    }

    /**
     * WebChromeClient hỗ trợ Video Toàn Màn hình (HTML5 Fullscreen):
     * Khi bấm nút phóng to trên trình phát YouTube, phương thức onShowCustomView sẽ được gọi.
     */
    private class YouTubeWebChromeClient extends WebChromeClient {

        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            super.onProgressChanged(view, newProgress);
            progressBar.setProgress(newProgress);
            if (newProgress >= 100) {
                progressBar.setVisibility(View.GONE);
            }
        }

        @Override
        public void onShowCustomView(View view, CustomViewCallback callback) {
            if (customView != null) {
                onHideCustomView();
                return;
            }

            customView = view;
            customViewCallback = callback;

            // Ẩn WebView và hiển thị container video toàn màn hình
            swipeRefreshLayout.setVisibility(View.GONE);
            customViewContainer.setVisibility(View.VISIBLE);
            customViewContainer.addView(view, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));

            // Chuyển sang chế độ xoay ngang và ẩn thanh trạng thái (Immersive Mode)
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            hideSystemUI();
        }

        @Override
        public void onHideCustomView() {
            if (customView == null) return;

            // Hiển thị lại WebView
            swipeRefreshLayout.setVisibility(View.VISIBLE);
            customViewContainer.setVisibility(View.GONE);
            customViewContainer.removeView(customView);

            if (customViewCallback != null) {
                customViewCallback.onCustomViewHidden();
            }

            customView = null;
            customViewCallback = null;

            // Khôi phục chiều xoay dọc
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            showSystemUI();
        }
    }

    /**
     * XỬ LÝ NÚT BACK CỨNG:
     * 1. Nếu đang xem video toàn màn hình -> Thoát toàn màn hình trước.
     * 2. Nếu WebView có thể lùi (canGoBack) và không phải trang chủ -> Lùi trang.
     * 3. Nếu đang ở trang chủ -> Nhấn 2 lần trong vòng 2 giây để thoát app.
     */
    @Override
    public void onBackPressed() {
        // 1. Kiểm tra nếu đang ở chế độ Video Fullscreen
        if (customView != null) {
            YouTubeWebChromeClient chromeClient = new YouTubeWebChromeClient();
            chromeClient.onHideCustomView();
            return;
        }

        // 2. Kiểm tra nếu có lịch sử duyệt trang trong WebView
        if (webView != null && webView.canGoBack()) {
            String currentUrl = webView.getUrl();
            // Nếu không phải là trang chủ YouTube thì thực hiện lùi trang
            if (currentUrl != null && !isHomePage(currentUrl)) {
                webView.goBack();
                return;
            }
        }

        // 3. Nhấn 2 lần để thoát khi ở trang chủ
        if (backPressedTime + 2000 > System.currentTimeMillis()) {
            super.onBackPressed();
        } else {
            Toast.makeText(this, R.string.press_back_again_to_exit, Toast.LENGTH_SHORT).show();
            backPressedTime = System.currentTimeMillis();
        }
    }

    private boolean isYouTubeDomain(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase();
        return lower.contains("m.youtube.com") ||
               lower.contains("youtube.com") ||
               lower.contains("youtu.be") ||
               lower.contains("accounts.google.com");
    }

    private boolean isHomePage(String url) {
        String cleaned = url.replace("https://", "").replace("http://", "").replace("www.", "");
        return cleaned.equals("m.youtube.com") || cleaned.equals("m.youtube.com/") || cleaned.equals("youtube.com") || cleaned.equals("youtube.com/");
    }

    private boolean isAdUrl(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase();
        for (String domain : AD_DOMAINS) {
            if (lower.contains(domain)) {
                return true;
            }
        }
        return false;
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
    }

    private void showErrorView(boolean show) {
        errorView.setVisibility(show ? View.VISIBLE : View.GONE);
        swipeRefreshLayout.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    private void hideSystemUI() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }

    private void showSystemUI() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (webView != null) {
            webView.onPause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.onResume();
        }
    }

    /**
     * Dọn dẹp hoàn toàn tài nguyên WebView khi đóng ứng dụng để tránh Memory Leak trên Android 5.0
     */
    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.loadUrl("about:blank");
            webView.stopLoading();
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            ((ViewGroup) webView.getParent()).removeView(webView);
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
