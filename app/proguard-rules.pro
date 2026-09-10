# MD Reader release shrinking rules.
# Keep JavaScript bridge entry points exposed deliberately to the local WebView.
-keepattributes *Annotation*
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
