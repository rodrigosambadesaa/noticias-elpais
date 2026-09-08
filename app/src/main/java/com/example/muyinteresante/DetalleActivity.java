package com.example.muyinteresante;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.view.OnApplyWindowInsetsListener;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.WindowInsetsCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.example.muyinteresante.util.ConnectivityAndInternetAccess;
import com.example.muyinteresante.util.RemoteConnectivityDiagnostics;

public class DetalleActivity extends AppCompatActivity {

    public static final String EXTRA_URL = "extra_url";
    public static final String EXTRA_TITULO = "extra_titulo";

    private WebView webView;
    private ProgressBar progressBar;
    private String articleUrl;
    private String articleTitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detalle);

        articleUrl = getIntent().getStringExtra(EXTRA_URL);
        articleTitle = getIntent().getStringExtra(EXTRA_TITULO);

        final Toolbar toolbar = findViewById(R.id.toolbarDetalle);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(articleTitle != null ? articleTitle : "Muy Interesante");
        }

        progressBar = findViewById(R.id.progressBarWeb);
        webView = findViewById(R.id.webViewDetalle);

        // Soporte para márgenes de ventana/cámara (S25 Ultra Notch/Cutout & Insets)
        final View rootView = findViewById(android.R.id.content);
        if (rootView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(rootView, new OnApplyWindowInsetsListener() {
                @Override
                public WindowInsetsCompat onApplyWindowInsets(View v, WindowInsetsCompat insets) {
                    int top = insets.getSystemWindowInsetTop();
                    int bottom = insets.getSystemWindowInsetBottom();
                    int left = insets.getSystemWindowInsetLeft();
                    int right = insets.getSystemWindowInsetRight();

                    if (toolbar != null && top > 0) {
                        toolbar.setPadding(left, top, right, 0);
                    }
                    if (webView != null && bottom > 0) {
                        webView.setPadding(left, 0, right, bottom);
                    }
                    return insets;
                }
            });
        }

        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress == 100) {
                    progressBar.setVisibility(View.GONE);
                } else {
                    progressBar.setVisibility(View.VISIBLE);
                    progressBar.setProgress(newProgress);
                }
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                progressBar.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progressBar.setVisibility(View.GONE);
            }

            @Override
            @SuppressLint("NewApi")
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request != null && request.isForMainFrame()
                        && isAmbiguousWebError(error != null ? error.getErrorCode() : 0)) {
                    RemoteConnectivityDiagnostics.checkIfNeeded(DetalleActivity.this, result -> {
                        String message = result != null && result.isReachable()
                                ? "El artículo no está disponible, pero Internet responde."
                                : "Problema de conectividad al cargar el artículo.";
                        Toast.makeText(DetalleActivity.this, message, Toast.LENGTH_LONG).show();
                    });
                }
            }
        });

        if (articleUrl != null && !articleUrl.isEmpty()) {
            cargarArticulo();
        } else {
            Toast.makeText(this, "URL no válida", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void cargarArticulo() {
        if (!ConnectivityAndInternetAccess.isConnected(this)
                || !ConnectivityAndInternetAccess.hasPhysicalNetwork(this)) {
            Toast.makeText(this, "Sin conexión. No se carga el artículo remoto.", Toast.LENGTH_LONG).show();
            return;
        }
        webView.loadUrl(articleUrl);
    }

    private boolean isAmbiguousWebError(int errorCode) {
        return errorCode == WebViewClient.ERROR_HOST_LOOKUP
                || errorCode == WebViewClient.ERROR_CONNECT
                || errorCode == WebViewClient.ERROR_TIMEOUT
                || errorCode == WebViewClient.ERROR_IO
                || errorCode == WebViewClient.ERROR_FAILED_SSL_HANDSHAKE;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        menu.findItem(R.id.action_buscar).setVisible(false);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
            return true;
        } else if (id == R.id.menu_actualizar) {
            if (webView != null) {
                cargarArticulo();
            }
            return true;
        } else if (id == R.id.action_abrir_navegador || id == R.id.action_test_conectividad) {
            if (articleUrl != null && !articleUrl.isEmpty()) {
                try {
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(articleUrl));
                    Intent chooser = Intent.createChooser(browserIntent, "Abrir noticia en...");
                    startActivity(chooser);
                } catch (Exception e) {
                    Toast.makeText(this, "No se pudo abrir el navegador", Toast.LENGTH_SHORT).show();
                }
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
