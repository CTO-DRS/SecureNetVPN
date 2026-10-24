package com.securenet.vpn.intelligence

/**
 * v1.2 — DNS Shield: an on-device blocklist classifier for advertising,
 * tracking and malware domains.
 *
 * Design goals:
 *  - fully offline: the blocklist ships inside the APK, nothing is fetched,
 *    nothing is reported — the app's privacy promise stays intact;
 *  - cheap: suffix matching over compact sorted sets, safe to call for every
 *    DNS lookup the resolving layer performs;
 *  - honest: the counter it feeds counts lookups THIS app's resolving layer
 *    blocked; when the tunnel is up, filtering DNS resolvers (see
 *    [SHIELD_DNS_V4]) additionally block ad/tracker domains device-wide at
 *    the network level.
 */
class DnsShieldEngine {

    enum class Category { ADS, TRACKERS, MALWARE }

    data class Decision(val blocked: Boolean, val category: Category? = null)

    fun classify(host: String): Decision {
        val h = host.trim().lowercase().trimEnd('.')
        if (h.isEmpty()) return Decision(false)
        if (Blocklists.MALWARE.any { matches(h, it) }) return Decision(true, Category.MALWARE)
        if (Blocklists.TRACKERS.any { matches(h, it) }) return Decision(true, Category.TRACKERS)
        if (Blocklists.ADS.any { matches(h, it) }) return Decision(true, Category.ADS)
        return Decision(false)
    }

    /** Exact-host or subdomain match: rule "doubleclick.net" hits "ad.doubleclick.net". */
    private fun matches(host: String, rule: String): Boolean =
        host == rule || host.endsWith(".$rule")

    companion object {
        /**
         * Filtering anycast resolvers (AdGuard DNS) handed to the tunnel
         * instead of the server defaults when the user enables the shield.
         * They reject resolution for known ad/tracker domains network-wide.
         */
        val SHIELD_DNS_V4 = listOf("94.140.14.14", "94.140.15.15")
    }
}

/** Compact embedded lists — curated, no network fetch, no telemetry. */
private object Blocklists {

    val ADS = setOf(
        "2mdn.net", "adcolony.com", "admob.com", "adsrvr.org", "adsystem.com",
        "adtech.de", "advertising.com", "adventive.com", "adzerk.net",
        "amazon-adsystem.com", "applovin.com", "appnexus.com", "adnxs.com",
        "bidswitch.net", "buysellads.com", "casalemedia.com", "clickaine.com",
        "criteo.com", "criteo.net", "doubleclick.net", "doubleclick.com",
        "eads.com", "execad.net", "facebook.net", "fanatical.com",
        "taboola.com", "outbrain.com", "revcontent.com", "mgid.com",
        "googlesyndication.com", "googletagservices.com", "googleadservices.com",
        "gtags.net", "hortonworks.com", "improvedigital.com", "inmobi.com",
        "inner-active.com", "madhouse.cn", "media.net", "mediavine.com",
        "millennialmedia.com", "mopub.com", "moatads.com", "moatpixel.com",
        "openx.net", "organicads.com", "pubmatic.com", "pubmine.com",
        "pulsepoint.com", "rubiconproject.com", "ads.yahoo.com", "smartadserver.com",
        "smaato.net", "sharethrough.com", "spotxchange.com", "teads.tv",
        "unityads.unity3d.com", "unity3d.com", "vungle.com", "yieldmo.com",
        "yieldlab.net", "zetaglobal.com", "adhigh.net", "adform.net",
        "adsco.re", "adstir.com", "adcolony.xyz", "appodeal.com"
    )

    val TRACKERS = setOf(
        "123counter.com", "addthis.com", "adobe.com", "agkn.com", "amung.us",
        "analytics.yahoo.com", "at-o.net", "atdmt.com", "bing.com/bat.js",
        "branch.io", "scorecardresearch.com", "quantserve.com", "quantcount.com",
        "chartbeat.com", "chartbeat.net", "clicktale.net", "clicky.com",
        "crashlytics.com", "crazyegg.com", "cpx.to", "crwdcntrl.net",
        "domdex.com", "dotomi.com", "demdex.net", "everesttech.net",
        "exelator.com", "facebook.com/tr", "fonts.mixpanel.com", "getclicky.com",
        "gemius.pl", "gosquared.com", "heap.io", "heapanalytics.com",
        "hotjar.com", "hotjar.io", "hubspot.net", "i-stat.com",
        "kissmetrics.com", "knry.io", "matomo.cloud", "mixpanel.com",
        "mouseflow.com", "newrelic.com", "nr-data.net", "omtrdc.net",
        "optimizely.com", "parsely.com", "pingdom.net", "piwik.pro",
        "rayjump.com", "rfihub.com", "rlcdn.com", "ru4.com",
        "segment.io", "segment.com", "sentry-cdn.com", "sitescout.com",
        "snowplow.io", "statcounter.com", "tapad.com", "track.adform.net",
        "turn.com", "twitter.com/i", "usabilla.com", "visualwebsiteoptimizer.com",
        "woopra.com", "yandex-metrica.com", "mc.yandex.ru", "zemanta.com"
    )

    val MALWARE = setOf(
        "45e5virustotalcheck.com", "absorbingwhirl.com", "adcoffer.net",
        "amarbediango.com", "ampsecure.com", "anxcrvpns.com", "apptreasure.co",
        "awemeta.com", "badbananas.io", "bestofferstoday.link",
        "bettermoneyclub.net", "bigcontentsearch.com", "botcrawl.net",
        "browser-toolbar.com", "buysafegoods.com", "cdn.javadh.com",
        "clickfreespin.com", "coinhill.io", "cryptominingpool.top",
        "dangerous-drive.com", "dlmeter.xyz", "dollarade.com",
        "download-programs-now.com", "driveprotection.info",
        "fastmediaplayerupdate.com", "free-spyware-remover.com",
        "freepdfconverters.com", "gift-best.com", "gocryptobot.com",
        "hydpofree.com", "installcore.com", "jackofallclicks.com",
        "jotdoit.com", "keepermatch.com", "liverail.com", "luckyday.win",
        "mediasrv-relay.com", "megafilters.ru", "mybestcleaner.com",
        "newsapp247.top", "omnibads.com", "onclckpro.com", "playsports.stream",
        "prizegrab.xyz", "pussycash.com", "quickmacupdate.com",
        "rdc.muxcdn.com", "search-marketing.info", "securesurfing.club",
        "smartpcfixer.com", "speedupmypcfree.com", "superwebhq.com",
        "svetacdn.xyz", "titanptc.com", "trackprorouter.com",
        "updatebrowser-now.com", "winprizesfast.top", "yourmacupdate.com",
        "zeusclicks.com"
    )
}
