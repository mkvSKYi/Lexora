# R8 keeps Compose, Hilt, Room, ML Kit and Readium working via the libraries' own consumer rules.
# Below are only the app-specific keeps R8 cannot infer.

# EpubReaderFragment.webViewOf() reaches the page WebView reflectively via getWebView(), so the
# method must survive shrinking/renaming even though nothing calls it by name.
-keep class org.readium.r2.navigator.pager.R2EpubPageFragment {
    public *** getWebView();
}

# Readium bridges its WebView JS to native and (de)serializes Locator / EpubPreferences as JSON;
# silence its optional/desugared references.
-dontwarn org.readium.**
