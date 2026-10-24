# SecureNet VPN

**Enterprise-grade privacy for Android** — WireGuard tunneling, on-device
malware auditing and network performance optimization in a single app.

| | |
|---|---|
| Platform | Android 11+ (API 30), target SDK 35 |
| Languages | Kotlin 2.0 + C11/C++17 (NDK) |
| Architecture | MVVM + Clean Architecture (domain / data / presentation) |
| Protocol | WireGuard® via the open-source **wireguard-go** userspace backend over JNI |
| Min toolchain | Android Studio Koala+, JDK 17, AGP 8.5.2, Gradle 8.9 |

---

## 0. What's new in v1.2

| Area | Addition |
|---|---|
| DNS Shield (ad/tracker blocker) | `intelligence/DnsShieldEngine.kt` — 170+ embedded domains across ads/trackers/malware categories, subdomain-suffix matching, blocked-lookup counter; when enabled the tunnel itself is handed filtering resolvers (AdGuard DNS) so blocking works device-wide at the network level |
| Smart Network Rules | `intelligence/NetworkTrustEngine.kt` — privacy-friendly network fingerprinting (transport + metered, no SSID/location), user trust list, auto-connect on untrusted networks wired into `SecureNetVpnService.startNetworkWatcher()`; "Trust this network" card on the dashboard |
| Smart Insights | `intelligence/SmartInsightsEngine.kt` — merges kill switch, DNS shield, scan freshness, smart rules and auto-launch into a severity-ordered, localized recommendation list rendered live on the dashboard |
| Connection history | New **History** tab — `data/local/HistoryParser.kt` reconstructs VPN sessions (open/ok/failed) and per-day usage roll-ups from the encrypted JSONL session log; RecyclerView timeline UI |
| Quick connect widget | `widget/QuickConnectWidgetProvider.kt` — home-screen one-tap connect/disconnect, gradient widget, state synced from every tunnel transition |
| New tests | 38 new unit tests across 4 suites (DnsShieldEngine, NetworkTrustEngine, SmartInsightsEngine, HistoryParser) |

---

## 0. What's new in v1.1

| Area | Addition |
|---|---|
| Arabic localization | Full `values-ar/strings.xml` (every string translated), RTL-aware, in-app language switcher (System / English / العربية) backed by `AppCompatDelegate.setApplicationLocales` + `AppLocalesMetadataHolderService` persistence |
| On-device ML threat classifier | `intelligence/ThreatClassifier.kt` — logistic-regression model with embedded weights; re-scores every app during scans, surfaces top contributing factors, fully offline |
| Smart Connect | `intelligence/ServerRecommender.kt` + `ServerLatencyProber.kt` — parallel TCP latency sweep of all endpoints, on-device scoring (latency + load + stickiness), one-tap best-server pick |
| Network anomaly detection | `intelligence/NetworkAnomalyDetector.kt` — EWMA + prior-variance z-score latency watchdog, DNS-hijack pin table for critical resolvers |
| Composite protection score | `intelligence/SecurityScoreEngine.kt` — 0-100 score across 4 pillars with prioritized, localized action list on the dashboard |
| Speed test | `intelligence/SpeedTestEngine.kt` — ping (TCP median), download/upload throughput via Cloudflare endpoints, 10-entry encrypted history |
| Split tunneling | Whitelist / blacklist per-app routing (`ui/splittunnel/`), applied in `VpnService.Builder` on establish |
| Kill switch | Watchdog reconnect loop with capped backoff in the service when the tunnel dies (`SecureNetVpnService.scheduleKillSwitchRecovery`) |
| Auto-connect on launch | `MainActivity.maybeAutoConnectOnLaunch()` from cached server config |
| Quick Settings tile | `service/VpnTileService.kt` — one-tap connect/disconnect from the notification shade |
| New tests | 35 new unit tests across 5 suites (ThreatClassifier, ServerRecommender, NetworkAnomalyDetector, SecurityScoreEngine, SplitTunnel + SpeedTest) — 78 total, all green |

---

## 1. Feature map (spec -> implementation)

| Requirement | Where it lives |
|---|---|
| WireGuard VPN service (open-source impl) | `service/SecureNetVpnService.kt`, `nativelib/WgNative.kt`, `go/libwg/` |
| Local security scanner (anti-malware) | `security/` (`AppThreatAnalyzer`, `FileThreatScanner`, `RootDetector`, `AttestationChecker`) |
| Network performance optimizer | `network/DnsCache(Manager).kt`, `network/MtuOptimizer.kt` |
| One-click activation + permission flow | `ui/dashboard/DashboardFragment.kt`, `ui/permissions/PermissionManager.kt` |
| Real-time status + animated UI | `StateFlow<ConnectionState>` + Lottie (`res/raw/*.json`) |
| Battery optimization mode | `util/BatteryOptimizationHelper.kt`, sampling interval switch in the service |
| Auto-reconnect on network change | `network/NetworkMonitor.kt` -> `SecureNetVpnService.startNetworkWatcher()` |
| Foreground service, `vpn_channel` | `service/VpnNotificationManager.kt` (channel ID literally `vpn_channel`) |
| EncryptedSharedPreferences | `data/local/SecurePrefs.kt` |
| WorkManager (logs + usage stats) | `work/ConnectionLogWorker.kt`, `work/UsageStatsWorker.kt`, `work/WorkScheduler.kt` |
| MVVM + Clean Architecture | `domain/` (use cases + repository interfaces), `data/` (implementations), `ui/` (ViewModels) |
| Certificate pinning (HPKP-style) | `res/xml/network_security_config.xml` + dynamic OkHttp `CertificatePinner` (`data/remote/RemoteConfigApi.kt`) |
| ProGuard with custom rules | `app/proguard-rules.pro` |
| No hardcoded keys (remote config) | `data/repository/RemoteConfigRepositoryImpl.kt`; device key generated at runtime, stored encrypted |
| User flow: Splash -> Onboarding -> Dashboard -> Activate -> Permissions -> Animation -> Connected | `SplashActivity`, `onboarding/`, `ui/dashboard/`, `MainActivity`, `ui/permissions/` |
| Material Design 3 + dynamic color + gradients + glass-morphism | `res/values/themes.xml`, `DynamicColors` in `SecureNetApp`, `bg_gradient_main` (#1A2A6C -> #11998E), `Widget.SecureNet.GlassCard` |
| Dark/Light theme follows system | `AppCompatDelegate` + `values-night/themes.xml` (Settings exposes system/light/dark) |

---

## 2. Quick start

```bash
# 1. Open the project in Android Studio (Koala or newer).
#    Let it sync: Gradle 8.9 + AGP 8.5.2 + Kotlin 2.0.20 are preconfigured.

# 2. Build the debug APK (signed with the included keystore):
./gradlew assembleDebug
#    -> app/build/outputs/apk/debug/app-debug.apk

# 3. Run the unit tests:
./gradlew testDebugUnitTest

# 4. Install on a device:
adb install app/build/outputs/apk/debug/app-debug.apk
```

The wrapper jar + `gradlew`/`gradlew.bat` are included; JDK 17 is required.

### Signing (debug)

`keystore/debug.keystore` is pre-generated and wired into
`signingConfigs.customDebug`:

```
store:    keystore/debug.keystore
password: android
alias:    androiddebugkey
```

`assembleDebug` and `assembleRelease` both sign with it (release also runs
R8 minification + resource shrinking). **Replace it before any store
upload** — it is a test keystore by design.

---

## 3. Building the real WireGuard Go backend

The C++ core (`chacha20`, `sha256`, key clamping, self-tests) ships in-tree
and builds automatically. The wireguard-go backend is a **Go c-shared
library** that must be built once per machine:

```bash
export ANDROID_NDK_HOME=/path/to/android-ndk-r26
./go/build-wireguard-go.sh
```

This compiles `go/libwg` (a plain C-ABI wrapper around
`golang.zx2c4.com/wireguard`) into
`app/src/main/jniLibs/<abi>/libwgcore.so` for arm64-v8a, armeabi-v7a and
x86_64. At startup the service loads it through the JNI bridge in
`cpp/securenet-jni.cpp` (`dlopen` -> `dlsym("wgTurnOn")`).

**Without the .so the app still runs** — the tunnel enters a clearly-logged
*simulation mode* so the UI, permission flow, scanner and optimizer are
fully demonstrable. A warning is written to the encrypted session log and
shown in Settings > About. Shipping without building the Go backend is not
recommended.

---

## 4. Architecture

```
┌─────────────────────────── presentation ───────────────────────────┐
│  SplashActivity → OnboardingActivity → MainActivity                 │
│  ├─ DashboardFragment + DashboardViewModel                          │
│  ├─ ServersFragment   + ServersViewModel   + ServerAdapter          │
│  ├─ ScannerFragment   + ScannerViewModel   + ThreatAdapter          │
│  └─ SettingsFragment  + SettingsViewModel                           │
│  PermissionManager (sequential rationale → request → next)          │
└──────────────────────────────┬──────────────────────────────────────┘
                               │ StateFlow / use cases
┌──────────────────────────────▼──────────────────────────────────────┐
│ domain/  ConnectVpnUseCase · DisconnectVpnUseCase ·                 │
│          ScanDeviceUseCase · ObserveVpnStateUseCase                 │
│          repository interfaces (Vpn/Server/Scanner/RemoteConfig)    │
└──────────────────────────────┬──────────────────────────────────────┘
┌──────────────────────────────▼──────────────────────────────────────┐
│ data/    VpnRepositoryImpl → SecureNetVpnService (state owner)      │
│          ServerRepositoryImpl · RemoteConfigRepositoryImpl (OkHttp) │
│          ScannerRepositoryImpl (PackageManager + files)             │
│          SecurePrefs (EncryptedSharedPreferences) · ConnectionLogger│
└──────────────────────────────┬──────────────────────────────────────┘
┌──────────────────────────────▼──────────────────────────────────────┐
│ platform  SecureNetVpnService (foreground, specialUse)              │
│           ├─ VpnService.Builder → TUN fd → wireguard-go (JNI)       │
│           ├─ NetworkMonitor → auto-reconnect                        │
│           ├─ TrafficStats sampler → live speeds                     │
│           └─ BootCompletedReceiver → auto-connect after reboot      │
│           WorkManager: ConnectionLogWorker · UsageStatsWorker       │
│ native    libsecurenet.so (ChaCha20/SHA-256/clamp/self-test)        │
│           libwgcore.so (wireguard-go c-shared, built via script)    │
└──────────────────────────────────────────────────────────────────────┘
```

Dependency injection is the Google-recommended manual **AppContainer**
pattern (`di/AppContainer.kt`) — zero reflection, instant cold start.

---

## 5. Permission strategy

Permissions are requested **one at a time, each preceded by an explained
rationale dialog** (Material dialog -> system dialog -> next), registered
through `ActivityResultContracts`:

| Permission | Why the user is told they need it |
|---|---|
| `POST_NOTIFICATIONS` | Android requires a persistent notification to keep a VPN alive |
| `CAMERA` | Import WireGuard configs via QR code |
| `READ_CONTACTS` | Privacy audit: detect apps silently syncing contacts |
| `ACCESS_FINE_LOCATION` | Read the current Wi-Fi name for trusted-network rules |
| Media/Storage (media reader) | Local file malware sweep of Downloads |

"Permanently denied" cases deep-link to app settings instead of looping.
The system **VPN consent dialog** is a separate, dedicated round trip.

---

## 6. Security model

- **Keys**: the device WireGuard private key is generated on first use
  (32 `SecureRandom` bytes, RFC 7748 clamped) and stored only inside
  `EncryptedSharedPreferences` (AES-256-GCM, master key in Android
  Keystore). Nothing cryptographic is hardcoded; peer keys and CA pins
  arrive via remote config.
- **Pinning**: network_security_config pins act as a floor; fresh pins from
  remote config are enforced in OkHttp `CertificatePinner`, enabling pin
  rotation without app updates.
- **Backups disabled**: `allowBackup=false` and both backup/transfer rule
  sets exclude everything, so keys never migrate.
- **Logs**: JSONL inside an `EncryptedFile`; `ConnectionLogWorker` compacts
  it every 6h; users can wipe it in Settings.
- **Attestation**: SafetyNet is deprecated — the optional attestation layer
  probes Play Integrity availability + installer provenance and stays
  advisory (never blocks the VPN).
- **R8**: release builds minify + shrink with keep-rules protecting the JNI
  surface (`-keepclasseswithmembernames class * { native <methods>; }`).

---

## 7. Network optimizer details

- **DNS cache** (`DnsCache`): TTL + LRU (access-order `LinkedHashMap`),
  hit/miss/eviction stats surfaced on the dashboard, pre-warming of top
  server endpoints, hot-path resolution on `Dispatchers.IO`.
- **MTU optimizer**: 60/80-byte WireGuard overhead model + 16-byte safety
  margin, 1280–1420 clamp, binary-search path-MTU probe state machine and a
  battery-saver cap (1380). Fully unit-tested.

---

## 8. Testing

```
app/src/test/java/com/securenet/vpn/
├── MtuOptimizerTest           # overhead math, clamps, probe convergence
├── DnsCacheTest               # TTL expiry, LRU eviction, hit rate (fake clock)
├── AppThreatAnalyzerTest      # risk scoring boundaries + RootDetector logic
├── WireGuardConfigParserTest  # wg-quick INI dialect
├── KeyUtilsTest               # RFC 7748 clamping, IPC settings format
└── ConnectVpnUseCaseTest      # use cases with fake repo + in-memory logger
```

```bash
./gradlew testDebugUnitTest          # JVM suite
./gradlew connectedDebugAndroidTest  # instrumented (device/emulator)
```

---

## 9. Known limitations / production checklist

1. **Build the Go backend** (section 3) — simulation mode is for demos only.
2. Provision the real `API_BASE_URL` + remote-config JSON
   (`servers`, `certificate_pins`) and update the placeholder pins in
   `network_security_config.xml`.
3. Replace `keystore/debug.keystore` with a production signing key.
4. Vendoring wireguard-go: the pinned module in `go/libwg/go.mod` should be
   reviewed + bumped and its license (GPLv2) respected in distribution.
5. Add latency probing (ICMP/TCP handshake timing) to rank servers — the
   field is wired but currently populated only by remote config.
6. QR import flow (camera permission is already wired) can reuse
   `WireGuardConfigParser` with Mobile Vision / ML Kit.

WireGuard is a registered trademark of Jason A. Donenfeld. This project is
not affiliated with the WireGuard project.
