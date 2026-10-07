# Keep WebView JS interface methods if added later.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}