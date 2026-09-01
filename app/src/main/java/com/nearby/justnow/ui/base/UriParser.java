package com.nearby.justnow.ui.base;

import android.content.Intent;
import android.net.Uri;

/**
 * Parses URI strings into launchable {@link Intent}s.
 * Handles http/https, intent://, and other schemes (weixin://, alipay://, etc.).
 */
public final class UriParser {

    private UriParser() {
    }

    /**
     * Parse a URI string into an Intent suitable for {@code startActivity()}.
     *
     * @param uri the raw URI string
     * @return a launchable Intent, or {@code null} if the input is null/empty
     * @throws Exception if intent:// scheme parsing fails
     */
    public static Intent parse(String uri) throws Exception {
        if (uri == null || uri.isEmpty()) {
            return null;
        }

        if (uri.startsWith("intent:") || uri.contains("#Intent;")) {
            return Intent.parseUri(uri, Intent.URI_INTENT_SCHEME);
        }

        // http, https, weixin, alipay, etc. — all use ACTION_VIEW
        return new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
    }
}
