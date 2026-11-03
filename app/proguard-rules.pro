# Tối ưu hóa và giữ an toàn cho WebView trên Android 5.0
-keepattributes JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Giữ nguyên các lớp liên quan đến WebKit
-keep class android.webkit.** { *; }

# Rút gọn các dòng log dư thừa trong bản Release để tăng tốc CPU máy cũ
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}
