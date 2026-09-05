package com.example.muyinteresante.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.net.ConnectException;
import java.net.SocketTimeoutException;

import org.junit.Test;

public class RemoteRequestPolicyTest {

    @Test
    public void offlineGuardSkipsRemoteRequestImmediately() {
        assertFalse(RemoteRequestPolicy.canStartRequest(false));
    }

    @Test
    public void successfulHttpResponseIsAuthoritative() {
        assertTrue(RemoteRequestPolicy.canStartRequest(true));
        assertEquals(
                RemoteRequestPolicy.Outcome.SUCCESS,
                RemoteRequestPolicy.classifyHttpStatus(200));
        assertEquals(
                RemoteRequestPolicy.Outcome.SUCCESS,
                RemoteRequestPolicy.classifyHttpStatus(204));
    }

    @Test
    public void ambiguousFeedFailureWithGeneralInternetMeansFeedUnavailable() {
        assertTrue(RemoteRequestPolicy.isAmbiguousConnectivityFailure(new ConnectException("refused")));
        assertEquals(
                RemoteRequestPolicy.Outcome.FEED_UNAVAILABLE,
                RemoteRequestPolicy.classifyAmbiguousFailure(true));
    }

    @Test
    public void ambiguousFailureWithoutGeneralInternetMeansNoInternet() {
        assertTrue(RemoteRequestPolicy.isAmbiguousConnectivityFailure(
                new SocketTimeoutException("read timed out")));
        assertEquals(
                RemoteRequestPolicy.Outcome.NO_INTERNET,
                RemoteRequestPolicy.classifyAmbiguousFailure(false));
    }

    @Test
    public void validHttpErrorDoesNotBecomeGeneralConnectivityFailure() {
        assertFalse(RemoteRequestPolicy.isAmbiguousConnectivityFailure(
                new IllegalStateException("HTTP 503")));
        assertEquals(
                RemoteRequestPolicy.Outcome.HTTP_ERROR,
                RemoteRequestPolicy.classifyHttpStatus(503));
    }
}
