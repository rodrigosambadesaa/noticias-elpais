# ConnectivityAndInternetAccess

The Java implementation is integrated once in the app source set at
`app/src/main/java/com/example/muyinteresante/util/ConnectivityAndInternetAccess.java`.
It is adapted only by package name and Android lint annotations from the
canonical gist revision documented in the repository history.

Modernized fork of `str4d/22cac7a3f70bc227cdca`, itself derived from Emil Davtyan's original `emil2k/5130324` `Connectivity.java`.

This fork provides two equivalent implementations:

* `ConnectivityAndInternetAccess.java`
* `ConnectivityAndInternetAccess.kt`

It also includes one lifecycle-aware Activity example for each language:

* `ConnectivityUsageExample.java`
* `ConnectivityUsageExampleKotlin.kt`

Copy the implementation and example for the language used by your app. Do not include both main implementations in the same source set because they intentionally use the same package and class name.

The examples start the built-in passive `NetworkObserver` in `onStart()`, close it in `onStop()`, and only run the active multi-layer diagnostic when the user explicitly taps the status view. They also expose the connection-attempt state (`connecting` / `stalled`) without performing active network probes.

## Features and API compatibility

Both implementations preserve operations for general connectivity, Wi-Fi, mobile data, Ethernet, estimated connection speed, airplane mode, VPN, connection attempts, Android validation/captive-portal snapshots, passive default-network observation, optional ICMP diagnostics, and actual Internet reachability. The active diagnostic now combines effective DNS, explicit DNS, TCP, NTP, HTTP(S), and TLS probes and includes dual-stack IPv4/IPv6 targets.

* **API 16–20:** legacy `NetworkInfo` APIs.
* **API 21+:** `Network` and `NetworkCapabilities` where available.
* **API 24+:** the built-in passive observer uses `registerDefaultNetworkCallback(...)` to follow the application's actual default network.
* **API 16–23:** the observer falls back to a dynamically registered `CONNECTIVITY_ACTION` receiver.
* **API 16–28:** the legacy `CONNECTING` state can still be read from `NetworkInfo`.
* **API 29+:** application-initiated attempts can be tracked explicitly with `beginConnectionAttempt(context)` and `endConnectionAttempt()`.
* Outstanding application connection attempts expire automatically after 30 seconds.
* `isConnectionAttemptStalled(context)` distinguishes a prolonged/failed connection attempt from the ordinary `isConnecting(context)` state.

`isConnected(...)` means that Android exposes a usable network with `NET_CAPABILITY_INTERNET`; it does **not** by itself prove that arbitrary Internet destinations are reachable. Use `checkInternetAsync(...)`, `checkInternetBlocking(...)`, or `isInternetReachable(...)` when a fresh broader reachability diagnostic is actually useful.

## Connection-attempt state

The connection-attempt API deliberately separates three questions:

```text
DISCONNECTED
    |
    v
CONNECTING  -- success --> CONNECTED
    |
    +-- 30 s unresolved --> STALLED
```

* `isConnected(context)`: a usable Android network is available.
* `isConnecting(context)`: a connection attempt is currently in progress.
* `isConnectionAttemptStalled(context)`: the attempt has remained unresolved for at least 30 seconds.
* `clearConnectionAttemptStall()`: explicitly acknowledges/clears a latched timeout.

A successful `isConnected(...)` observation clears pending attempts and any stale timeout marker. Starting a new explicit attempt cycle also clears the previous latched stalled state.

## Android validation and captive-portal signals

On API 23+, `isInternetValidated(...)` reports whether Android most recently validated general Internet access on the application's effective default `Network`, while `isCaptivePortalDetected(...)` reports whether Android detected a captive portal the last time it probed that network. These are system snapshots, not fresh probes.

This separation is deliberate:

* `isConnected(...)`: Android exposes a usable Internet-capable network.
* `isInternetValidated(...)`: Android most recently validated that network.
* `isCaptivePortalDetected(...)`: Android most recently detected a captive portal.
* `checkInternetAsync(...)` / `checkInternetBlocking(...)`: perform a fresh active multi-layer reachability diagnostic.

On Android API 16–22, Android does not expose `NET_CAPABILITY_VALIDATED` or `NET_CAPABILITY_CAPTIVE_PORTAL`, so the two corresponding helpers return `false`.

## Passive network observation

The class implements passive observation directly:

* `snapshotNetworkState(context)` returns a cheap point-in-time `NetworkState` containing `connected`, `internetValidated`, and `captivePortalDetected`.
* `observeNetwork(context, callback)` immediately posts the current state to the main thread and then emits changes.
* API 24+ uses the application's default `ConnectivityManager.NetworkCallback`.
* API 16–23 uses a dynamically registered legacy connectivity receiver.
* `NetworkObserver.close()` unregisters the callback/receiver.

The passive observer itself generates **no DNS, TCP, NTP, HTTP, TLS, or ICMP traffic**.

## How reachability is checked

Internet reachability is checked asynchronously or synchronously with a short-lived bounded probe executor dedicated to each check. The first successful probe wins and outstanding probes in that stage are cancelled.

### Stage 1 — effective/system DNS

When the default `DnsProbeStrategy` is in use and the DNS-resolver list is non-empty, resolve `example.com` using the DNS configuration of the selected Android `Network`. On API 21+ this uses `Network.getAllByName(...)`, so it follows the app's effective network path and is compatible with VPN/Private-DNS routing.

The effective-DNS preflight has a **1.5 second** budget. A success is reported as:

```text
dns://system/example.com
```

### Stage 2 — transport layer race

If Stage 1 does not establish reachability, the engine races these probes in parallel:

1. **Explicit DNS over UDP** to the configured public resolvers.
2. **TCP connect** to the configured host/port targets.
3. **NTP over UDP** using a minimal 48-byte client request to port 123.

The complete transport stage is bounded to **3.5 seconds from the beginning of the check**, subject to the global deadline.

Default explicit DNS targets:

```text
1.1.1.1
8.8.8.8
9.9.9.9
208.67.222.222
[2606:4700:4700::1111]
```

Default TCP targets:

```text
1.1.1.1:53
8.8.8.8:443
[2606:4700:4700::1111]:53
```

Default NTP targets:

```text
time.google.com
pool.ntp.org
```

A structurally valid DNS response, including a negative DNS result such as NXDOMAIN, proves that the configured resolver was reached. A successful TCP connection or NTP reply likewise establishes basic Internet reachability without requiring an HTTP response.

### Stage 3 — application layer race

Only when the transport stage does not produce a success does the engine race:

1. the existing HTTP(S) endpoints; and
2. direct TLS handshakes.

Default HTTP(S) endpoints remain Google, Facebook, Wolfram Alpha, Apple, and Amazon.

Default TLS targets:

```text
www.google.com:443
cloudflare.com:443
```

A TLS probe opens a TCP socket, wraps it in an `SSLSocket`, enables TLS 1.2 on Android versions where the existing compatibility factory is required, and calls `startHandshake()`.

### Deadlines and slow-network behavior

The active diagnostic uses monotonic deadlines rather than accumulating every endpoint timeout:

| Limit | Value |
|---|---:|
| Effective/system DNS stage | 1,500 ms |
| Explicit DNS socket timeout | 2,500 ms |
| TCP/TLS connect timeout | 3,000 ms |
| HTTP/TLS read timeout | 3,000 ms |
| Transport-stage deadline | 3,500 ms from probe start |
| Global deadline | 6,000 ms |
| Maximum parallel probes | 16 |

These longer limits are deliberate: the previous sub-second DNS/connect limits were too aggressive for high-latency 2G/EDGE and degraded 3G networks. They do **not** make fast networks wait six seconds; a successful early probe returns immediately.

`attemptedHosts` contains probes that actually started executing. Labels identify the winning protocol, for example `dns://...`, `tcp://...`, `ntp://...`, `tls://...`, or an HTTP(S) URL.

## Dual-stack IPv6 support

Endpoint parsing accepts host names, IPv4, bracketed IPv6, and optional ports. Examples:

```text
1.1.1.1
1.1.1.1:53
www.google.com:443
[2606:4700:4700::1111]
[2606:4700:4700::1111]:53
```

Brackets are treated as endpoint syntax and are stripped before passing an IPv6 literal to sockets or `ping`. The default DNS, TCP, and ICMP configurations include a Cloudflare IPv6 target so IPv6-capable networks are exercised without removing the existing IPv4 paths.

On API 21+ TCP/TLS sockets and HTTP(S) connections use the selected Android `Network` where supported. On API 22+ UDP DNS/NTP sockets can also be bound to that selected `Network`.

## Builder configuration and custom strategies

`Builder` provides isolated per-instance configuration for:

* `setHosts(...)`
* `setDnsResolvers(...)`
* `setTcpTargets(...)`
* `setNtpTargets(...)`
* `setTlsTargets(...)`
* `setIcmpTargets(...)`
* `setDnsProbeStrategy(...)`
* `setHttpProbeStrategy(...)`
* `setTcpProbeStrategy(...)`
* `setNtpProbeStrategy(...)`
* `setTlsProbeStrategy(...)`

The corresponding built-in default lists are available through `defaultHosts()`, `defaultDnsResolvers()`, `defaultTcpTargets()`, `defaultNtpTargets()`, `defaultTlsTargets()`, and `defaultIcmpTargets()`.

Supplying a custom `DnsProbeStrategy` takes ownership of explicit DNS behavior and intentionally skips the built-in effective/system-DNS preflight.

### Kotlin example

```kotlin
val customConnectivity = ConnectivityAndInternetAccess.Builder()
    .setTcpTargets(listOf("1.1.1.1:53", "[2606:4700:4700::1111]:53"))
    .setNtpTargets(listOf("time.google.com"))
    .setTlsTargets(listOf("www.google.com:443"))
    .build()
```

### Java example

```java
ConnectivityAndInternetAccess customConnectivity =
        new ConnectivityAndInternetAccess.Builder()
                .setTcpTargets(Arrays.asList(
                        "1.1.1.1:53",
                        "[2606:4700:4700::1111]:53"))
                .setNtpTargets(Collections.singletonList("time.google.com"))
                .setTlsTargets(Collections.singletonList("www.google.com:443"))
                .build();
```

## Captive Portal strict mode

The default probe answers a relaxed question: can the app reach real Internet infrastructure? A captive portal can sometimes satisfy that relaxed definition.

For transparent Internet/captive-portal-oriented diagnostics use:

```kotlin
val strictConnectivity =
    ConnectivityAndInternetAccess.strictCaptivePortalBuilder().build()
```

Strict mode explicitly disables **DNS, TCP, NTP, and TLS targets** and probes only Google's `generate_204` connectivity endpoint without following redirects. Only an actual HTTP `204 No Content` response is accepted, so the new fallback probes cannot accidentally bypass strict captive-portal semantics.

## Optional ICMP diagnostic

ICMP remains deliberately independent from normal `checkInternetAsync(...)` / `checkInternetBlocking(...)` results. Use `checkIcmpReachabilityAsync(...)` or `checkIcmpReachabilityBlocking()` only for troubleshooting.

The default targets are:

```text
1.1.1.1
8.8.8.8
[2606:4700:4700::1111]
```

They are tried sequentially with the existing 800 ms per-attempt budget and 1.5 s global deadline. A failed ICMP probe does **not** mean Internet access is unavailable; many networks filter ICMP while DNS/TCP/TLS/HTTPS remain usable.

## Recommended application pattern

For ordinary application logic, do not treat active probes as a mandatory gate before every API call:

1. Start one lifecycle-appropriate `NetworkObserver` (or one application-level observer).
2. Use its `NetworkState`, or the snapshot helpers, as cheap passive signals.
3. Let the application's real backend request answer the service-specific question.
4. If that request fails in a network-like way and broader diagnosis is useful, run `checkInternetAsync(...)` / `checkInternetBlocking(...)` explicitly.

```text
passive default-network state
            ↓
    real backend request
            ↓
active generic diagnosis only when useful
```

## Basic active-diagnostic usage

Kotlin:

```kotlin
val connectivity = ConnectivityAndInternetAccess.Builder().build()
val request = connectivity.checkInternetAsync(context) { result ->
    println("reachable=${result.reachable}")
    println("via=${result.reachedHost}")
    println("attempted=${result.attemptedHosts}")
    println("elapsedMs=${result.elapsedMilliseconds}")
}
```

Java:

```java
ConnectivityAndInternetAccess connectivity =
        new ConnectivityAndInternetAccess.Builder().build();

ConnectivityAndInternetAccess.Request request =
        connectivity.checkInternetAsync(context, result -> {
            System.out.println("reachable=" + result.isReachable());
            System.out.println("via=" + result.getReachedHost());
            System.out.println("attempted=" + result.getAttemptedHosts());
            System.out.println("elapsedMs=" + result.getElapsedMilliseconds());
        });
```

Cancel the request when its owning lifecycle no longer needs the result.

## Required permissions in AndroidManifest.xml

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

`observeNetwork(...)` requires `ACCESS_NETWORK_STATE`. Active DNS/TCP/NTP/HTTP/TLS diagnostics additionally require `INTERNET`.

## Testing degraded 2G/3G networks

For reproducible emulator testing, Android Emulator network shaping can be changed while the AVD is running. For example:

```bash
adb -s emulator-5554 emu network speed gprs
adb -s emulator-5554 emu network delay gprs

adb -s emulator-5554 emu network speed edge
adb -s emulator-5554 emu network delay edge

adb -s emulator-5554 emu network speed umts
adb -s emulator-5554 emu network delay umts
```

Restore normal conditions with:

```bash
adb -s emulator-5554 emu network speed full
adb -s emulator-5554 emu network delay none
```

For each profile, repeat the diagnostic many times and record `reachedHost`, `attemptedHosts`, and `elapsedMilliseconds`; a single successful run does not expose jitter-related false negatives.

A physical retail phone cannot normally be per-app throttled by Android Studio itself. For app-only shaping without root, a debug `VpnService` conditioner is the cleanest approach; on a rooted laboratory device, Linux `tc`/`netem` can provide lower-level shaping.

## Threading and cancellation

* `NetworkObserver` receives OS connectivity events without polling and posts callbacks to the main thread.
* `NetworkObserver.close()` unregisters the underlying callback/receiver and is idempotent.
* `checkInternetAsync(...)` runs work off the caller thread.
* Async callbacks are posted to the main thread.
* `Request.cancel()` suppresses future callback delivery and interrupts the outer task.
* Each active reachability check owns a bounded executor with up to 16 probe threads.
* Individual network operations retain bounded socket/connect/read timeouts because interrupting a blocked Java networking operation does not necessarily close it immediately.
* Connection-attempt timing uses `SystemClock.elapsedRealtime()` so wall-clock changes cannot manufacture or suppress a stall timeout.

## License and attribution

MIT. The original work is by Emil Davtyan (`emil2k`), with subsequent modifications by `str4d`, followed by the modernized Java/Kotlin work in this fork. See `LICENSE` for the retained notices and license text.

The optional ICMP diagnostic is an independent implementation inspired by the connectivity-checking idea shown in `kreempuff/01d542d5c910382a59391f916b1bf945`; it does not copy that implementation verbatim.

Both variants target Android projects with `minSdk 16` and Java 8-compatible bytecode. Applications should test the exact version they ship on their own target API/device matrix, especially captive portals, VPNs, dual-network devices, IPv6-only/dual-stack networks, degraded cellular connections, OEM-specific networking behavior, and connection-attempt timeout transitions.
