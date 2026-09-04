# Validation — passive Android network observer, stalled attempts, and multi-layer reachability

## Previously validated observer behavior

The Java/Kotlin implementations expose point-in-time helpers such as `isConnected(...)`, `isInternetValidated(...)`, and `isCaptivePortalDetected(...)`, plus a lifecycle-aware passive observer.

The observer provides:

* `NetworkState` with connected, Internet validated (API 23+), captive portal detected (API 23+), and monotonic observation timestamp.
* `snapshotNetworkState(context)`.
* `observeNetwork(context, callback)`.
* `NetworkObserver.close()` for lifecycle-safe unregistration.
* API 24+: `registerDefaultNetworkCallback(...)`.
* API 16–23: dynamically registered `CONNECTIVITY_ACTION` fallback.
* Main-thread observer callback delivery.
* No DNS/TCP/NTP/HTTP/TLS/ICMP work inside passive observation.

The API 21–23 fallback intentionally does not use `registerNetworkCallback(NetworkRequest, ...)`: that API can report multiple matching networks and is not equivalent to observing the application's default network. `registerDefaultNetworkCallback(...)` was added in API 24.

## Stalled connection-attempt detection

The existing implementation separates a normal in-progress connection from an attempt that remains unresolved for too long:

* `isConnecting(context)` retains its existing meaning.
* `isConnectionAttemptStalled(context)` reports an unresolved attempt after the 30-second threshold.
* `clearConnectionAttemptStall()` explicitly clears a latched application-attempt timeout.
* Explicit attempts store their monotonic start time with `SystemClock.elapsedRealtime()`.
* Delayed timeouts belong to the exact `ConnectionAttempt` that created them.
* A successful connection clears pending attempts and the stalled marker.
* API 16–28 can additionally time continuous observations of `NetworkInfo.State.CONNECTING`.

## Previously performed validation

The previous observer/ICMP revisions were validated with Java/Kotlin source compilation and Android API stubs, observer simulations, and physical-device checks. A real Samsung Galaxy S25 Ultra (`SM-S938B`) running Android 16/API 36 was previously used to validate installation, startup, passive observer behavior, Wi-Fi/mobile transitions, and ICMP scenarios.

Those historical checks remain relevant to the untouched observer, ICMP, lifecycle, and connection-attempt code, but they must not be presented as runtime validation of the newly added TCP/NTP/TLS/IPv6 engine.

## Multi-layer reachability upgrade — source validation performed

The current upgrade adds:

* `TcpProbeStrategy`, `NtpProbeStrategy`, and `TlsProbeStrategy` in both Java and Kotlin.
* Default TCP, NTP, and TLS strategies.
* Dual-stack IPv4/IPv6 DNS, TCP, and ICMP target configuration.
* Generic endpoint parsing for host names, IPv4, bracketed IPv6, and optional ports.
* Three ordered stages: effective/system DNS → explicit DNS/TCP/NTP race → HTTP/TLS race.
* Immediate short-circuit on the first successful probe in each stage.
* Strict captive-portal mode explicitly disabling DNS/TCP/NTP/TLS fallback targets.
* A 6-second monotonic global deadline and larger per-operation timeouts intended to avoid false negatives on high-latency 2G/EDGE and degraded 3G networks.
* `MAX_PARALLEL_PROBES = 16`.

### Java

The complete upgraded Java source was compiled against Android API-compatible stubs using Java 8-compatible source/bytecode assumptions. The source-level validation passed without syntax or type errors.

### Kotlin

The complete upgraded Kotlin source was compiled with Kotlin/JVM target 1.8 against equivalent Android API stubs. The local validation compiler predates the source's pre-existing `@ConsistentCopyVisibility` annotation, so that annotation was removed only from the temporary validation copy; it remains unchanged in the distributed source. The resulting compile passed with no source warnings/errors after the upgrade was finalized.

### Static parity checks

Java and Kotlin were checked for the same public/configuration surface introduced by this revision:

* TCP/NTP/TLS strategy interfaces.
* TCP/NTP/TLS default implementations.
* TCP/NTP/TLS Builder setters.
* TCP/NTP/TLS default-target accessors.
* IPv6 Cloudflare defaults.
* `Endpoint` parser replacing the DNS-specific endpoint helper.
* 16-probe executor limit.
* strict-mode disabling of all non-HTTP active fallback families.
* matching stage ordering and global deadlines.

## Deterministic engine validation performed

In addition to compilation, equivalent Java and Kotlin harnesses were executed with controlled probe strategies and an API-16-style connected-network stub.

Java result: `ENGINE_TEST_PASS`.

Kotlin result: `KOTLIN_ENGINE_TEST_PASS`.

Both harnesses verified:

* a TCP success in the transport stage returns immediately and prevents the HTTP/TLS stage from running;
* strict captive-portal mode does not execute TCP, NTP, or TLS even when custom strategies for those families would return success;
* TLS can win the application-stage race and is labelled `tls://cloudflare.com:443`;
* `[2606:4700:4700::1111]:53` is parsed to the unbracketed IPv6 host plus port 53 and is reported as `tcp://[2606:4700:4700::1111]:53`;
* the IPv6 defaults are exposed by the corresponding default-target accessors.

## Runtime validation still recommended for the new engine

Source compilation is not equivalent to real-network validation. Before treating this revision as production-tested, run an instrumented/device matrix covering at least:

1. normal Wi-Fi;
2. normal cellular data;
3. GPRS/EDGE-class emulator shaping;
4. UMTS/3G-class shaping;
5. high-latency/jitter stress beyond the stock presets;
6. IPv4-only, IPv6-only where available, and dual-stack networks;
7. VPN and Private DNS configurations;
8. captive portal / strict-204 behavior;
9. networks blocking UDP/53 or UDP/123 while TCP/TLS still work;
10. networks blocking ICMP while normal Internet reachability remains available.

For degraded-network tests, repeat each condition many times and record `InternetResult.reachedHost`, `attemptedHosts`, and `elapsedMilliseconds`. The relevant property is not merely whether one probe succeeds, but the false-negative rate under latency, jitter, loss, and protocol-specific filtering.

## Important interpretation rule

The active diagnostic answers whether the app can establish broader Internet reachability through at least one configured strategy. It does **not** prove that the application's own backend is reachable. The real backend request remains the source of truth for service-specific availability.
