package com.example.muyinteresante.util;

import android.content.Context;
import android.os.SystemClock;

import java.util.concurrent.atomic.AtomicLong;

/** Runs the broad connectivity probe sparingly after ambiguous remote failures. */
public final class RemoteConnectivityDiagnostics {
    private static final long COOLDOWN_MS = 10_000L;
    private static final AtomicLong LAST_STARTED = new AtomicLong(Long.MIN_VALUE);

    private RemoteConnectivityDiagnostics() {
    }

    public static boolean checkIfNeeded(
            Context context,
            ConnectivityAndInternetAccess.InternetCallback callback) {
        if (context == null || callback == null) {
            return false;
        }

        long now = SystemClock.elapsedRealtime();
        long last = LAST_STARTED.get();
        if (last != Long.MIN_VALUE && now - last < COOLDOWN_MS) {
            return false;
        }
        if (!LAST_STARTED.compareAndSet(last, now)) {
            return false;
        }

        ConnectivityAndInternetAccess.checkInternetAsyncDefault(context, callback);
        return true;
    }
}
