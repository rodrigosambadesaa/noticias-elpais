package com.example.muyinteresante.util;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import javax.net.ssl.SSLException;

/**
 * Pure decision rules shared by remote loaders and unit tests.
 *
 * <p>The Android connectivity helper is intentionally not called here. A cheap
 * connected-state guard belongs before a request; an active general diagnostic
 * belongs only after an ambiguous request failure.</p>
 */
public final class RemoteRequestPolicy {

    public enum Outcome {
        SUCCESS,
        NO_NETWORK,
        HTTP_ERROR,
        FEED_UNAVAILABLE,
        NO_INTERNET,
        PARSE_ERROR
    }

    private RemoteRequestPolicy() {
    }

    public static boolean canStartRequest(boolean connected) {
        return connected;
    }

    public static boolean canStartRequest(boolean connected, boolean hasPhysicalNetwork) {
        return connected && hasPhysicalNetwork;
    }

    public static Outcome classifyHttpStatus(int statusCode) {
        if (statusCode >= 200 && statusCode < 300) {
            return Outcome.SUCCESS;
        }
        return Outcome.HTTP_ERROR;
    }

    public static Outcome classifyAmbiguousFailure(boolean generalInternetReachable) {
        return generalInternetReachable ? Outcome.FEED_UNAVAILABLE : Outcome.NO_INTERNET;
    }

    public static boolean isAmbiguousConnectivityFailure(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof UnknownHostException
                    || current instanceof ConnectException
                    || current instanceof NoRouteToHostException
                    || current instanceof SocketTimeoutException
                    || current instanceof SSLException
                    || current instanceof SocketException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
