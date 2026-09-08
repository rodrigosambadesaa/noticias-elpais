package com.example.muyinteresante;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.view.OnApplyWindowInsetsListener;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.WindowInsetsCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.muyinteresante.util.ConnectivityAndInternetAccess;
import com.example.muyinteresante.util.ActivityRecreationPolicy;
import com.example.muyinteresante.util.NewsCacheManager;
import com.example.muyinteresanteNoTocar.DescargaNoticiasRSS;
import com.example.muyinteresanteNoTocar.NoticiaRSS;
import com.example.muyinteresanteNoTocar.iNoticiaRSS;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity implements iNoticiaRSS {

    private static final String TAG = "MainActivity";
    private static final String RSS_URL = "https://feeds.elpais.com/mrss-s/pages/ep/site/elpais.com/portada";
    private static final String STATE_VISIBLE_NEWS_COUNT = "main_visible_news_count";
    private static final String STATE_FIRST_VISIBLE_POSITION = "main_first_visible_position";
    private static final String STATE_FIRST_VISIBLE_OFFSET = "main_first_visible_offset";
    private static final int LOAD_MORE_THRESHOLD = 4;
    private static final int NEWS_PAGE_SIZE = 20;

    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView rvNoticias;
    private LinearLayoutManager layoutManager;
    private NoticiasAdapter adapter;

    private LinearLayout bannerNetworkNotice;
    private TextView tvBannerText;
    private Button btnDiagnosticarRed;

    private LinearLayout layoutNetworkStatusPill;
    private View viewNetworkDot;
    private TextView tvNetworkStatusText;

    private LinearLayout layoutEmptyState;
    private Button btnReintentar;

    private ConnectivityAndInternetAccess.NetworkObserver networkObserver;
    private ConnectivityAndInternetAccess.NetworkState currentNetworkState;

    private boolean isLoadingMore = false;
    private boolean hasMoreNews = false;
    private final ArrayList<NoticiaRSS> noticiasPendientes = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        rvNoticias = findViewById(R.id.rvNoticias);
        bannerNetworkNotice = findViewById(R.id.bannerNetworkNotice);
        tvBannerText = findViewById(R.id.tvBannerText);
        btnDiagnosticarRed = findViewById(R.id.btnDiagnosticarRed);

        layoutNetworkStatusPill = findViewById(R.id.layoutNetworkStatusPill);
        viewNetworkDot = findViewById(R.id.viewNetworkDot);
        tvNetworkStatusText = findViewById(R.id.tvNetworkStatusText);

        layoutEmptyState = findViewById(R.id.layoutEmptyState);
        btnReintentar = findViewById(R.id.btnReintentar);

        // Soporte para márgenes de ventana/cámara en smartphones tipo S25 Ultra
        final View rootView = findViewById(android.R.id.content);
        if (rootView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(rootView, new OnApplyWindowInsetsListener() {
                @Override
                public WindowInsetsCompat onApplyWindowInsets(View v, WindowInsetsCompat insets) {
                    int top = insets.getSystemWindowInsetTop();
                    int bottom = insets.getSystemWindowInsetBottom();
                    int left = insets.getSystemWindowInsetLeft();
                    int right = insets.getSystemWindowInsetRight();

                    // El contenido se inseta como un bloque. Aplicar el inset
                    // dentro de la Toolbar comprimía sus hijos y desplazaba
                    // visualmente el título/menú en dispositivos con notch.
                    // También evita acumular padding en cada callback.
                    v.setPadding(left, top, right, bottom);
                    return insets;
                }
            });
        }

        layoutManager = new LinearLayoutManager(this);
        rvNoticias.setLayoutManager(layoutManager);
        adapter = new NoticiasAdapter(this, new ArrayList<NoticiaRSS>(), new NoticiasAdapter.OnNoticiaClickListener() {
            @Override
            public void onNoticiaClick(NoticiaRSS noticia) {
                if (noticia != null && noticia.getEnlace() != null) {
                    Intent intent = new Intent(MainActivity.this, DetalleActivity.class);
                    intent.putExtra(DetalleActivity.EXTRA_URL, noticia.getEnlace());
                    intent.putExtra(DetalleActivity.EXTRA_TITULO, noticia.getTitulo());
                    startActivity(intent);
                }
            }
        });
        rvNoticias.setAdapter(adapter);

        // Infinite scroll: el RSS oficial entrega un lote amplio y estable. Lo
        // mostramos por páginas locales para que la interfaz no cargue 150 tarjetas
        // de golpe y para que el gesto de scroll siga siendo progresivo.
        rvNoticias.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (dy <= 0 || isLoadingMore || !hasMoreNews || adapter == null) {
                    return;
                }

                int totalItems = adapter.getItemCount();
                int lastVisibleItem = layoutManager.findLastVisibleItemPosition();
                if (totalItems > 0 && lastVisibleItem >= totalItems - 1 - LOAD_MORE_THRESHOLD) {
                    cargarMasNoticias();
                }
            }
        });

        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                ejecutarDescargarNoticias();
            }
        });

        btnReintentar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ejecutarDescargarNoticias();
            }
        });

        View.OnClickListener listenerDiagnostico = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ejecutarDiagnosticoRedCompleto();
            }
        };
        layoutNetworkStatusPill.setOnClickListener(listenerDiagnostico);
        btnDiagnosticarRed.setOnClickListener(listenerDiagnostico);

        if (ActivityRecreationPolicy.shouldLoadInitialNews(savedInstanceState != null)) {
            // Cargar noticias iniciales (intenta descargar o usa caché offline)
            cargarNoticiasIniciales();
        } else {
            restaurarEstadoTrasRecreacion(savedInstanceState);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (layoutManager == null || adapter == null) {
            return;
        }

        int firstVisiblePosition = layoutManager.findFirstVisibleItemPosition();
        View firstVisibleView = layoutManager.findViewByPosition(firstVisiblePosition);
        int firstVisibleOffset = firstVisibleView != null
                ? layoutManager.getDecoratedTop(firstVisibleView) - rvNoticias.getPaddingTop()
                : 0;

        // La lista completa ya está en caché; solo guardamos el tamaño visible
        // y la posición para evitar inflar el Bundle con todo el contenido RSS.
        outState.putInt(STATE_VISIBLE_NEWS_COUNT, adapter.getAllData().size());
        outState.putInt(STATE_FIRST_VISIBLE_POSITION, Math.max(firstVisiblePosition, 0));
        outState.putInt(STATE_FIRST_VISIBLE_OFFSET, firstVisibleOffset);
    }

    private void restaurarEstadoTrasRecreacion(Bundle savedInstanceState) {
        ArrayList<NoticiaRSS> cached = NewsCacheManager.loadNewsFromCache(this);
        if (cached == null || cached.isEmpty()) {
            // No se inicia una descarga aquí: el cambio de orientación no es
            // una operación remota nueva. El usuario puede reintentar después.
            usarNoticiasOffline();
            return;
        }

        int visibleCount = ActivityRecreationPolicy.visibleNewsCount(
                savedInstanceState.getInt(STATE_VISIBLE_NEWS_COUNT, NEWS_PAGE_SIZE),
                NEWS_PAGE_SIZE,
                cached.size());
        mostrarNoticiasHasta(cached, visibleCount);
        layoutEmptyState.setVisibility(View.GONE);
        rvNoticias.setVisibility(View.VISIBLE);

        final int firstVisiblePosition = savedInstanceState.getInt(STATE_FIRST_VISIBLE_POSITION, 0);
        final int firstVisibleOffset = savedInstanceState.getInt(STATE_FIRST_VISIBLE_OFFSET, 0);
        rvNoticias.post(new Runnable() {
            @Override
            public void run() {
                if (adapter != null && adapter.getItemCount() > 0) {
                    int safePosition = Math.min(firstVisiblePosition, adapter.getItemCount() - 1);
                    layoutManager.scrollToPositionWithOffset(safePosition, firstVisibleOffset);
                }
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Iniciar el observador pasivo de conectividad basado en el Gist
        networkObserver = ConnectivityAndInternetAccess.observeNetwork(this, new ConnectivityAndInternetAccess.NetworkStateCallback() {
            @Override
            public void onStateChanged(ConnectivityAndInternetAccess.NetworkState state) {
                currentNetworkState = state;
                actualizarInterfazEstadoRed(state);
            }
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (networkObserver != null) {
            networkObserver.close();
            networkObserver = null;
        }
    }

    private void actualizarInterfazEstadoRed(ConnectivityAndInternetAccess.NetworkState state) {
        // El snapshot pasivo es la autoridad para pintar la UI. No dejamos
        // que un intento antiguo o una interfaz en transición pinte Online.
        ConnectivityAndInternetAccess.NetworkState effectiveState = state != null
                ? state : ConnectivityAndInternetAccess.snapshotNetworkState(this);
        boolean isConnected = effectiveState.isConnected()
                && ConnectivityAndInternetAccess.hasPhysicalNetwork(this);
        boolean isConnectedOrConnecting = isConnected
                || ConnectivityAndInternetAccess.isConnecting(this);
        boolean isWifi = ConnectivityAndInternetAccess.isConnectedWifi(this);
        boolean isMobile = ConnectivityAndInternetAccess.isConnectedMobile(this);
        boolean isVpn = ConnectivityAndInternetAccess.vpnActive(this);
        boolean isAirplane = ConnectivityAndInternetAccess.isAirplaneModeOn(this);
        boolean isFast = ConnectivityAndInternetAccess.isConnectedFast(this);
        boolean isCaptive = effectiveState.isCaptivePortalDetected();
        boolean isValidated = effectiveState.isInternetValidated();

        Log.d(TAG, "Chequeo de red: ConnectedOrConnecting=" + isConnectedOrConnecting +
                ", Connected=" + isConnected + ", Wifi=" + isWifi + ", Mobile=" + isMobile +
                ", VPN=" + isVpn + ", Airplane=" + isAirplane + ", Fast=" + isFast);

        if (!isConnected) {
            // Disconnected / Offline
            swipeRefreshLayout.setRefreshing(false);
            viewNetworkDot.setBackgroundResource(R.color.status_offline);
            tvNetworkStatusText.setText(isAirplane ? "Modo Avión" : "Sin red");
            tvNetworkStatusText.setTextColor(getResources().getColor(R.color.status_offline));

            bannerNetworkNotice.setVisibility(View.VISIBLE);
            bannerNetworkNotice.setBackgroundResource(R.color.status_offline_bg);
            tvBannerText.setText(isAirplane ?
                    "Modo Avión activado. Mostrando noticias guardadas en caché." :
                    "Dispositivo sin conexión a internet. Mostrando noticias guardadas en caché.");
        } else if (isCaptive) {
            // Captive Portal
            viewNetworkDot.setBackgroundResource(R.color.status_warning);
            tvNetworkStatusText.setText("Portal Cautivo");
            tvNetworkStatusText.setTextColor(getResources().getColor(R.color.status_warning));

            bannerNetworkNotice.setVisibility(View.VISIBLE);
            bannerNetworkNotice.setBackgroundResource(R.color.status_warning_bg);
            tvBannerText.setText("Se requiere inicio de sesión en red (Portal Cautivo detectado).");
        } else if (!isValidated && !isConnected) {
            // Connected without validated internet
            viewNetworkDot.setBackgroundResource(R.color.status_warning);
            tvNetworkStatusText.setText("Conectando...");
            tvNetworkStatusText.setTextColor(getResources().getColor(R.color.status_warning));

            bannerNetworkNotice.setVisibility(View.VISIBLE);
            bannerNetworkNotice.setBackgroundResource(R.color.status_warning_bg);
            tvBannerText.setText("Conectado a la interfaz de red pero sin acceso verificado a internet.");
        } else {
            // Fully connected & validated
            viewNetworkDot.setBackgroundResource(R.color.status_online);

            String statusType = "Online";
            if (isVpn) {
                statusType = "Online (VPN)";
            } else if (isWifi) {
                statusType = "Online (Wi-Fi)";
            } else if (isMobile) {
                statusType = isFast ? "Online (4G/5G)" : "Online (Móvil Lento)";
            }
            tvNetworkStatusText.setText(statusType);
            tvNetworkStatusText.setTextColor(getResources().getColor(R.color.status_online));

            bannerNetworkNotice.setVisibility(View.GONE);
        }
    }

    private void cargarNoticiasIniciales() {
        // Cargar desde caché offline primero para renderizado instantáneo
        ArrayList<NoticiaRSS> cached = NewsCacheManager.loadNewsFromCache(this);
        if (cached != null && !cached.isEmpty()) {
            mostrarPrimeraPagina(cached);
            layoutEmptyState.setVisibility(View.GONE);
            rvNoticias.setVisibility(View.VISIBLE);
        }

        // Luego lanzar la descarga del RSS
        ejecutarDescargarNoticias();
    }

    private void ejecutarDescargarNoticias() {
        // Guard barato basado en la red utilizable. No sustituye al GET real.
        if (!ConnectivityAndInternetAccess.isConnected(this)
                || !ConnectivityAndInternetAccess.hasPhysicalNetwork(this)) {
            swipeRefreshLayout.setRefreshing(false);
            Toast.makeText(this, "Sin conexión disponible. Mostrando caché offline.", Toast.LENGTH_SHORT).show();
            usarNoticiasOffline();
            return;
        }

        swipeRefreshLayout.setRefreshing(true);

        // La petición RSS real es la prueba definitiva del servicio y conserva
        // redirects, códigos HTTP, timeouts y excepciones de transporte.
        // El spinner integrado de SwipeRefreshLayout es suficiente; evitar el
        // ProgressDialog modal mejora el estado offline y no bloquea la UI.
        new DescargaNoticiasRSS(this, this, false).execute(
                RSS_URL, NoticiaRSS.RSS_MUY_INTERESANTE);
    }

    /** Añade el siguiente lote local sin bloquear la interfaz ni abrir diálogos. */
    private void cargarMasNoticias() {
        if (isLoadingMore || noticiasPendientes.isEmpty() || adapter == null) {
            return;
        }

        isLoadingMore = true;
        int end = Math.min(NEWS_PAGE_SIZE, noticiasPendientes.size());
        ArrayList<NoticiaRSS> siguientePagina = new ArrayList<>(noticiasPendientes.subList(0, end));
        noticiasPendientes.subList(0, end).clear();
        int added = adapter.appendData(siguientePagina);
        hasMoreNews = !noticiasPendientes.isEmpty();
        isLoadingMore = false;
        if (added > 0) {
            NewsCacheManager.saveNewsToCache(this, adapter.getAllData());
            Log.d(TAG, "Scroll infinito: añadidas " + added + " noticias; pendientes=" + noticiasPendientes.size());
        }
    }

    private void mostrarPrimeraPagina(ArrayList<NoticiaRSS> noticias) {
        mostrarNoticiasHasta(noticias, NEWS_PAGE_SIZE);
    }

    private void mostrarNoticiasHasta(ArrayList<NoticiaRSS> noticias, int requestedCount) {
        noticiasPendientes.clear();
        int end = Math.min(Math.max(requestedCount, NEWS_PAGE_SIZE), noticias.size());
        adapter.updateData(new ArrayList<>(noticias.subList(0, end)));
        if (end < noticias.size()) {
            noticiasPendientes.addAll(noticias.subList(end, noticias.size()));
        }
        hasMoreNews = !noticiasPendientes.isEmpty();
    }

    private void usarNoticiasOffline() {
        ArrayList<NoticiaRSS> cached = NewsCacheManager.loadNewsFromCache(this);
        if (cached != null && !cached.isEmpty()) {
            mostrarPrimeraPagina(cached);
            layoutEmptyState.setVisibility(View.GONE);
            rvNoticias.setVisibility(View.VISIBLE);
        } else {
            rvNoticias.setVisibility(View.GONE);
            layoutEmptyState.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onRecibeNoticiasRSS(ArrayList<NoticiaRSS> listaNoticias) {
        swipeRefreshLayout.setRefreshing(false);

        if (listaNoticias != null && !listaNoticias.isEmpty()) {
            mostrarPrimeraPagina(listaNoticias);
            NewsCacheManager.saveNewsToCache(this, listaNoticias);
            layoutEmptyState.setVisibility(View.GONE);
            rvNoticias.setVisibility(View.VISIBLE);

            // Una actualización completa reinicia el recorrido del archivo.
            isLoadingMore = false;

            Log.d(TAG, "Noticias recibidas con éxito: " + listaNoticias.size());
        } else {
            Toast.makeText(this, "No se pudieron obtener nuevas noticias del canal RSS", Toast.LENGTH_SHORT).show();
            usarNoticiasOffline();
        }
    }

    @Override
    public void onError(DescargaNoticiasRSS.Failure failure) {
        swipeRefreshLayout.setRefreshing(false);
        String message;
        if (failure == null) {
            message = "No se pudo cargar el feed. Mostrando caché offline.";
        } else {
            switch (failure.getKind()) {
                case NO_NETWORK:
                    message = "Sin conectividad. Mostrando caché offline.";
                    break;
                case FEED_UNAVAILABLE:
                    message = "El feed no está disponible, pero Internet sí responde. Mostrando caché.";
                    break;
                case NO_INTERNET:
                    message = "Problema de conectividad a Internet. Mostrando caché offline.";
                    break;
                case HTTP_ERROR:
                    message = "El feed respondió con un error. Mostrando caché offline.";
                    break;
                default:
                    message = "El feed devolvió una respuesta no válida. Mostrando caché offline.";
                    break;
            }
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        usarNoticiasOffline();
    }

    private void ejecutarDiagnosticoRedCompleto() {
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Diagnóstico de Conectividad")
                .setMessage("Ejecutando diagnóstico multicapa DNS/TCP/NTP/HTTP/TLS...")
                .setPositiveButton("Cerrar", null)
                .show();

        // Diagnóstico activo multicapa DNS/TCP/NTP/HTTP/TLS
        ConnectivityAndInternetAccess.checkInternetAsyncDefault(this, new ConnectivityAndInternetAccess.InternetCallback() {
            @Override
            public void onResult(ConnectivityAndInternetAccess.InternetResult result) {
                if (dialog != null && dialog.isShowing()) {
                    // La red puede cambiar mientras se ejecuta el diagnóstico;
                    // mostrar siempre un snapshot tomado al finalizar.
                    ConnectivityAndInternetAccess.NetworkState state =
                            ConnectivityAndInternetAccess.snapshotNetworkState(MainActivity.this);
                    boolean isConnected = state.isConnected()
                            && ConnectivityAndInternetAccess.hasPhysicalNetwork(MainActivity.this);
                    boolean isConnectedOrConnecting = isConnected
                            || ConnectivityAndInternetAccess.isConnecting(MainActivity.this);
                    boolean isWifi = isConnected
                            && ConnectivityAndInternetAccess.isConnectedWifi(MainActivity.this);
                    boolean isMobile = isConnected
                            && ConnectivityAndInternetAccess.isConnectedMobile(MainActivity.this);
                    boolean isFast = isConnected
                            && ConnectivityAndInternetAccess.isConnectedFast(MainActivity.this);
                    boolean isVpn = isConnected
                            && ConnectivityAndInternetAccess.vpnActive(MainActivity.this);
                    boolean isAirplane = ConnectivityAndInternetAccess.isAirplaneModeOn(MainActivity.this);
                    boolean reachable = result != null && result.isReachable();
                    String reachedHost = result != null ? result.getReachedHost() : "Ninguno";
                    long time = result != null ? result.getElapsedMilliseconds() : 0;

                    StringBuilder sb = new StringBuilder();
                    sb.append("📡 ESTADO DE INTERFAZ DE RED:\n");
                    String interfaceStatus = !isConnected
                            ? "Desconectado"
                            : (reachable ? "Conectado" : "Red activa sin Internet demostrado");
                    sb.append("• Estado general: ").append(interfaceStatus).append("\n");
                    sb.append("• Tipo de red: ").append(isWifi ? "Wi-Fi" : (isMobile ? "Móvil / Celular" : "Otra / Ninguna")).append("\n");
                    sb.append("• Velocidad estimada: ").append(isFast ? "Rápida (High Speed)" : "Lenta / Desconocida").append("\n");
                    sb.append("• Red VPN Activa: ").append(isVpn ? "SÍ" : "No").append("\n");
                    sb.append("• Modo Avión: ").append(isAirplane ? "ACTIVADO" : "Desactivado").append("\n\n");

                    sb.append("🔍 DIAGNÓSTICO ACTIVO MULTICAPA (GIST):\n");
                    sb.append("• Internet Real: ").append(reachable ? "SÍ (Internet Verificado)" : "NO (Sin Internet)").append("\n");
                    sb.append("• Servidor alcanzado: ").append(reachedHost).append("\n");
                    sb.append("• Latencia de respuesta: ").append(time).append(" ms\n");

                    if (currentNetworkState != null) {
                        sb.append("\n📋 REGISTRO DE RED (NetworkState):\n");
                        sb.append(currentNetworkState.toString());
                    }

                    dialog.setMessage(sb.toString());
                }
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);

        MenuItem searchItem = menu.findItem(R.id.action_buscar);
        if (searchItem != null) {
            SearchView searchView = (SearchView) searchItem.getActionView();
            if (searchView != null) {
                searchView.setQueryHint("Buscar noticia...");
                searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                    @Override
                    public boolean onQueryTextSubmit(String query) {
                        if (adapter != null) {
                            adapter.filter(query);
                        }
                        return true;
                    }

                    @Override
                    public boolean onQueryTextChange(String newText) {
                        if (adapter != null) {
                            adapter.filter(newText);
                        }
                        return true;
                    }
                });
            }
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_actualizar) {
            ejecutarDescargarNoticias();
            return true;
        } else if (id == R.id.action_test_conectividad) {
            ejecutarDiagnosticoRedCompleto();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
