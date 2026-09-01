# Validation — built-in passive Android network observer and stalled connection attempts

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
* No DNS/HTTP/ICMP work inside passive observation.

The API 21–23 fallback intentionally does not use `registerNetworkCallback(NetworkRequest, ...)`: that API can report multiple matching networks and is not equivalent to observing the application's default network. `registerDefaultNetworkCallback(...)` was added in API 24.

## Stalled connection-attempt detection added

This revision additionally separates a normal in-progress connection from an attempt that has remained unresolved for too long:

* `isConnecting(context)` retains its existing meaning: a connection is currently being attempted.
* `isConnectionAttemptStalled(context)` reports an unresolved attempt after the 30-second threshold.
* `clearConnectionAttemptStall()` explicitly clears a latched application-attempt timeout.
* Explicit API 29+ attempts store their monotonic start time with `SystemClock.elapsedRealtime()`.
* The delayed timeout belongs to the exact `ConnectionAttempt` object that created it, so a callback from an older attempt cannot decrement a newer attempt.
* Before latching an explicit timeout, the delayed callback checks `isConnected(...)`; a connection that succeeded meanwhile is therefore not misreported as stalled.
* A successful connection clears pending attempts and the stalled marker.
* A new explicit attempt cycle clears the previous latched timeout.
* API 16–28 additionally time continuous observations of `NetworkInfo.State.CONNECTING`. Since Android does not expose the original transition timestamp through this API, the 30-second clock begins with this helper's first observation of that state.

## Validation performed on the previous observer revision

* Java core + Java example: `javac --release 8 -Xlint:all,-options` against Android API stubs: PASS, no Java source warnings/errors.
* Java API 24 observer simulation: initial state delivery, `onCapabilitiesChanged` update, duplicate-state suppression, captive-portal update, idempotent `close()` and callback unregister: PASS.
* Java API 23 fallback simulation: initial state, dynamic receiver update, receiver unregister: PASS.
* Kotlin core + Kotlin example: `kotlinc`, JVM target 1.8, against equivalent stubs: PASS on the previously validated revision.

## Physical-device validation already performed

Validated successfully on 2026-08-18 using a real Samsung Galaxy S25 Ultra:

* Model: `SM-S938B` (`samsung/pa3q`), Android 16, API 36.
* Security patch level: `2026-07-05`.
* A temporary signed APK containing `ConnectivityAndInternetAccess.java` and `ConnectivityUsageExample.java` installed successfully.
* The launcher activity started successfully and no application crash was reported.
* Passive observer result: `network available`; Android reported `Internet validated by Android`.
* After tapping the status view, the active diagnostic succeeded with `Diagnostic reached dns://system/example.com`.

## Validation status of this revision

The stalled-attempt implementation has been reviewed for state-transition and concurrency semantics in this revision, but the new 30-second transition itself has **not** been re-run on the physical-device/API matrix represented by the older validation above. The earlier physical test therefore must not be interpreted as proof of the newly added timeout behavior.

Recommended regression matrix:

1. API 16 / legacy `NetworkInfo.State.CONNECTING` held for less than and greater than 30 seconds.
2. API 23/24 transition around the observer implementation boundary.
3. API 28 legacy `CONNECTING` behavior.
4. API 29+ explicit `beginConnectionAttempt(...)` timeout.
5. Explicit attempt that succeeds just before the 30-second callback.
6. Explicit attempt manually ended before timeout.
7. Old delayed callback firing after a newer attempt has begun.
8. Successful connection after a stalled state, verifying automatic clearing.
9. `clearConnectionAttemptStall()` followed by a fresh attempt.
10. Wi-Fi/mobile handover, VPN default-network changes, captive portals, and observer start/stop cycles.


## Combined ICMP + stalled-connection source validation

The final tree contains both independent feature sets:

* ICMP: `IcmpCallback`, `IcmpResult`, `Builder.setIcmpTargets(...)`, `checkIcmpReachabilityAsync(...)`, `checkIcmpReachabilityBlocking()`, default targets `1.1.1.1` / `8.8.8.8`, bounded process cleanup, and target validation.
* Connection stall: `isConnectionAttemptStalled(context)`, `clearConnectionAttemptStall()`, 30-second explicit-attempt tracking, and API 16-28 legacy `CONNECTING` timing.

The normal DNS/HTTP Internet result remains independent from ICMP, and `isConnecting()` retains its ordinary in-progress semantics rather than being redefined as "stalled".

The Java/Kotlin ICMP fallback intentionally avoids `Throwable.addSuppressed()` so the source does not introduce an API-19-only method into a helper whose documented minimum is API 16.


## Validation performed on this combined revision

The exact combined source tree packaged in the result was checked after merging the ICMP and stalled-connection features:

* Java core + Java example: `javac --release 8` against equivalent Android API stubs: **PASS**.
* Kotlin core + Kotlin example: Kotlin/JVM 1.8 type/syntax check against equivalent Android API stubs: **PASS**. The local compiler required a temporary compatibility declaration for the pre-existing `@ConsistentCopyVisibility` annotation; that annotation is unrelated to the stalled-connection or ICMP changes and is not included in the Gist.
* Deterministic Java state-transition harness: **PASS** (`STALL_TEST_PASS`). It verified:
  * API 29+ explicit attempt is ordinary `connecting` before 30 seconds;
  * it becomes `stalled` at 30 seconds and is no longer reported as an ordinary active attempt after expiry;
  * `clearConnectionAttemptStall()` clears the latched timeout;
  * a new explicit attempt cycle clears a previous latch;
  * a successful connection clears pending/stalled state;
  * API 28 legacy `NetworkInfo.State.CONNECTING` starts its timer on first observation, remains non-stalled before 30 seconds, becomes stalled at 30 seconds, and resets when `CONNECTING` ends.
* Connection-attempt timestamps are assigned while holding the queue lock, so enqueue order and timeout order remain consistent even with concurrent callers.
* The ICMP fallback no longer calls `Throwable.addSuppressed()`, avoiding an API-19-only method on the documented API-16 minimum.

This is a deterministic source/stub validation, not a replacement for running the final merged revision on the Android API/device matrix.
