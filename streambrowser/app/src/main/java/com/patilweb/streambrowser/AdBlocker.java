package com.patilweb.streambrowser;

import android.net.Uri;
import android.text.TextUtils;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;

import java.io.ByteArrayInputStream;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Blocks common ad / tracker hosts and strips popup / overlay junk via injected JS.
 * Cinegram gets the aggressive path (extra hosts + DOM cleanup).
 * <p>
 * Streaming platforms (Kick, Twitch, YouTube, …) are protected: no DOM scrubbing and
 * only hard ad-network blocking, so players / Pusher / CSS-in-JS keep working.
 */
final class AdBlocker {

    private static final WebResourceResponse EMPTY =
            new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream(new byte[0]));

    private static final Set<String> HARD_AD_HOSTS = new HashSet<>();
    private static final Set<String> BLOCKED_HOSTS = new HashSet<>();
    private static final Set<String> BLOCKED_PATH_HINTS = new HashSet<>();
    private static final Set<String> PROTECTED_HOSTS = new HashSet<>();

    static {
        // Always blocked, even on Kick/Twitch.
        String[] hard = {
                "doubleclick.net", "googleadservices.com", "googlesyndication.com",
                "pagead2.googlesyndication.com", "adservice.google.com",
                "googletagservices.com",
                "amazon-adsystem.com", "aaxads.com",
                "exoclick.com", "exosrv.com", "juicyads.com", "popads.net", "popcash.net",
                "propellerads.com", "propellerclick.com", "popunderjs.com",
                "onclickads.net", "onclickmega.com",
                "trafficjunky.net", "trafficfactory.biz", "adsterra.com",
                "hilltopads.com", "clickadu.com", "mgid.com", "revcontent.com",
                "adskeeper.com", "bidvertiser.com", "adcash.com",
                "tsyndicate.com", "trafficstars.com", "ero-advertising.com",
                "imasdk.googleapis.com", "moatads.com", "fwmrm.net",
                "spotxchange.com", "spotx.tv", "teads.tv", "stickyadstv.com",
                "outbrain.com", "taboola.com", "criteo.com", "criteo.net",
                "adnxs.com", "adsrvr.org", "adform.net", "pubmatic.com",
                "rubiconproject.com", "openx.net", "casalemedia.com",
                "smartadserver.com", "yieldmo.com", "media.net",
                "ads.yahoo.com", "ads.twitter.com", "static.ads-twitter.com", "ads-twitter.com",
                "ads.msn.com", "bat.bing.com",
                "yandexadexchange.net", "mc.yandex.ru",
                "stripchat.com", "livejasmin.com", "chaturbate.com",
                "contentabc.com", "adtng.com", "realsrv.com", "natsdk.com",
                "ad-maven.com", "admaven.com", "adbro.me", "admicro.vn",
        };
        for (String h : hard) HARD_AD_HOSTS.add(h.toLowerCase(Locale.US));

        // Extra hosts for junk stream sites — NOT applied on protected platforms.
        String[] soft = {
                "googletagmanager.com", "google-analytics.com",
                "facebook.net", "connect.facebook.net", "scorecardresearch.com",
                "adsymptotic.com", "advertising.com", "adcolony.com", "admob.com", "mopub.com",
                "quantserve.com", "adserver.adtechus.com", "adtech.com", "serving-sys.com",
                "hotjar.com", "clarity.ms", "fullstory.com", "mixpanel.com",
                "segment.io", "segment.com", "amplitude.com",
                "histats.com", "statcounter.com", "addthis.com", "sharethis.com",
                "disqus.com", "zergnet.com",
                "pushanotify.com", "pushengage.com", "onesignal.com",
                // NOTE: do NOT block pusher.com — Kick chat / realtime needs it.
                "browser-update.org", "cookiebot.com", "cookielaw.org",
                "onetrust.com", "trustarc.com",
                "affiliatefuture.com", "clickbank.net", "digidip.net",
                "yandex.ru",
                // bing.com blocked wholesale broke random embeds — keep only bat.bing.com above
        };
        BLOCKED_HOSTS.addAll(HARD_AD_HOSTS);
        for (String h : soft) BLOCKED_HOSTS.add(h.toLowerCase(Locale.US));

        // Path hints: avoid ultra-broad "/ad/" which matched legitimate URLs.
        String[] hints = {
                "/ads/", "/advert", "/adsense", "/adserver", "popunder",
                "adsense", "adserver", "pop.js", "popup.js", "prebid",
                "doubleclick", "pagead", "adsbygoogle", "taboola", "outbrain",
                "propeller", "exoclick", "juicyads", "popads", "popcash",
                "push-notification", "onesignal", "fingerprint2", "fingerprintjs"
        };
        for (String h : hints) BLOCKED_PATH_HINTS.add(h.toLowerCase(Locale.US));

        String[] protectedHosts = {
                "kick.com", "twitch.tv", "youtube.com", "youtu.be", "youtube-nocookie.com",
                "googlevideo.com", "ytimg.com", "netflix.com", "nflxvideo.net",
                "disneyplus.com", "hulu.com", "spotify.com", "soundcloud.com",
                "vimeo.com", "dailymotion.com", "rumble.com",
                "mux.com", "cloudflarestream.com",
        };
        for (String h : protectedHosts) PROTECTED_HOSTS.add(h.toLowerCase(Locale.US));
    }

    private AdBlocker() {}

    static boolean isProtectedPage(String pageUrl) {
        String host = hostOf(pageUrl);
        return host != null && isProtectedHost(host);
    }

    static boolean shouldBlock(String url) {
        return shouldBlock(url, null);
    }

    static boolean shouldBlock(String url, String pageUrl) {
        if (TextUtils.isEmpty(url)) return false;
        String lower = url.toLowerCase(Locale.US);
        String host = hostOf(url);
        if (host == null) return false;

        boolean protectedPage = isProtectedPage(pageUrl);
        // Never block the streaming site's own assets.
        if (isProtectedHost(host)) return false;

        if (protectedPage) {
            return isHardAdHost(host);
        }

        if (isBlockedHost(host, BLOCKED_HOSTS)) return true;

        String path = null;
        String query = null;
        try {
            Uri uri = Uri.parse(url);
            path = uri.getPath();
            query = uri.getQuery();
        } catch (Exception ignored) { }
        String hay = (path == null ? "" : path) + "?" + (query == null ? "" : query);
        hay = hay.toLowerCase(Locale.US);
        for (String hint : BLOCKED_PATH_HINTS) {
            if (hay.contains(hint) || lower.contains(hint)) return true;
        }
        return false;
    }

    static boolean shouldBlock(WebResourceRequest request, String pageUrl) {
        if (request == null || request.getUrl() == null) return false;
        return shouldBlock(request.getUrl().toString(), pageUrl);
    }

    static WebResourceResponse emptyResponse() {
        return EMPTY;
    }

    private static boolean isHardAdHost(String host) {
        return isBlockedHost(host, HARD_AD_HOSTS);
    }

    private static boolean isBlockedHost(String host, Set<String> set) {
        if (set.contains(host)) return true;
        for (String blocked : set) {
            if (host.endsWith("." + blocked)) return true;
        }
        return false;
    }

    private static boolean isProtectedHost(String host) {
        if (PROTECTED_HOSTS.contains(host)) return true;
        for (String p : PROTECTED_HOSTS) {
            if (host.endsWith("." + p)) return true;
        }
        return false;
    }

    private static String hostOf(String url) {
        if (TextUtils.isEmpty(url)) return null;
        try {
            String host = Uri.parse(url).getHost();
            if (host == null) return null;
            host = host.toLowerCase(Locale.US);
            if (host.startsWith("www.")) host = host.substring(4);
            return host;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * JS cleanup. On Kick/Twitch/etc. only disable popup spam — DOM scrubbing
     * destroys React overlays and leaves an unstyled broken page.
     */
    static String cleanupScript(boolean aggressive, String pageUrl) {
        if (isProtectedPage(pageUrl)) {
            return "(function(){try{"
                    + "window.open=function(){return null;};"
                    + "}catch(e){}})();";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("(function(){try{");
        sb.append("window.open=function(){return null;};");
        sb.append("window.alert=function(){};window.confirm=function(){return true;};");
        sb.append("window.prompt=function(){return null;};");
        sb.append("if(window.Notification){try{Notification.requestPermission=function(){return Promise.resolve('denied');};}catch(e){}}");
        sb.append("document.addEventListener('click',function(e){");
        sb.append("var t=e.target;while(t&&t!==document){");
        sb.append("if(t.tagName==='A'&&t.target==='_blank'){t.target='_self';}");
        sb.append("t=t.parentElement;}},true);");

        sb.append("function scrub(){");
        sb.append("var sels=[");
        sb.append("'iframe[src*=\"doubleclick\"]','iframe[src*=\"googlesyndication\"]',");
        sb.append("'iframe[id*=\"google_ads\"]','iframe[src*=\"exoclick\"]',");
        sb.append("'iframe[src*=\"juicy\"]','iframe[src*=\"propeller\"]',");
        sb.append("'ins.adsbygoogle','.adsbygoogle'");
        if (aggressive) {
            sb.append(",'iframe[src*=\"popads\"]','iframe[src*=\"popcash\"]'");
        }
        sb.append("];");
        sb.append("sels.forEach(function(s){try{document.querySelectorAll(s).forEach(function(n){");
        sb.append("if(n.closest&&(n.closest('video')||n.closest('.jwplayer')||n.closest('#player')||n.closest('.plyr')||n.tagName==='VIDEO'))return;");
        sb.append("n.remove();});}catch(e){}});");
        sb.append("try{document.documentElement.style.overflow='auto';document.body.style.overflow='auto';");
        sb.append("document.body.style.pointerEvents='auto';}catch(e){}");
        sb.append("}");
        sb.append("scrub();");
        sb.append("var mo=new MutationObserver(function(){scrub();});");
        sb.append("mo.observe(document.documentElement,{childList:true,subtree:true});");
        sb.append("setInterval(scrub,").append(aggressive ? "800" : "2000").append(");");
        sb.append("}catch(e){}})();");
        return sb.toString();
    }

    /** @deprecated use {@link #cleanupScript(boolean, String)} */
    @Deprecated
    static String cleanupScript(boolean aggressive) {
        return cleanupScript(aggressive, null);
    }
}
