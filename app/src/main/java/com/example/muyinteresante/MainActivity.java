package com.example.muyinteresante;

import android.content.Intent;
import android.os.Bundle;
import androidx.lifecycle.ViewModelProvider;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
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

    private NewsViewModel newsState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        newsState = new ViewModelProvider(this).get(NewsViewModel.class);

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
                if (dy <= 0 || newsState.isLoadingMore || !newsState.hasMoreNews || adapter == null) {
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

        if (savedInstanceState == null && !newsState.visibleNews.isEmpty()) {
            adapter.updateData(newsState.visibleNews);
            layoutEmptyState.setVisibility(View.GONE);
            rvNoticias.setVisibility(View.VISIBLE);
        } else if (ActivityRecreationPolicy.shouldLoadInitialNews(savedInstanceState != null)) {
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
        newsState.visibleNews.clear();
        newsState.visibleNews.addAll(adapter.getAllData());
        outState.putInt(STATE_VISIBLE_NEWS_COUNT, adapter.getAllData().size());
        outState.putInt(STATE_FIRST_VISIBLE_POSITION, Math.max(firstVisiblePosition, 0));
        outState.putInt(STATE_FIRST_VISIBLE_OFFSET, firstVisibleOffset);
    }

    private void restaurarEstadoTrasRecreacion(Bundle savedInstanceState) {
        if (!newsState.visibleNews.isEmpty()) {
            adapter.updateData(newsState.visibleNews);
            rvNoticias.setVisibility(View.VISIBLE);
            layoutEmptyState.setVisibility(View.GONE);
            restoreScrollPosition(savedInstanceState);
            return;
        }
        NewsCacheManager.loadNewsFromCache(this, cached -> {
            if (isFinishing() || isDestroyed() || newsState.hasFreshFeed) return;
            if (cached == null || cached.isEmpty()) {
                usarNoticiasOffline();
                return;
            }
            int count = ActivityRecreationPolicy.visibleNewsCount(
                    savedInstanceState.getInt(STATE_VISIBLE_NEWS_COUNT, NEWS_PAGE_SIZE),
                    NEWS_PAGE_SIZE, cached.size());
            mostrarNoticiasHasta(cached, count);
            layoutEmptyState.setVisibility(View.GONE);
            rvNoticias.setVisibility(View.VISIBLE);
            restoreScrollPosition(savedInstanceState);
        });
    }

    private void restoreScrollPosition(Bundle savedInstanceState) {
        final int position = savedInstanceState.getInt(STATE_FIRST_VISIBLE_POSITION, 0);
        final int offset = savedInstanceState.getInt(STATE_FIRST_VISIBLE_OFFSET, 0);
        rvNoticias.post(() -> {
            if (adapter != null && adapter.getItemCount() > 0) {
                layoutManager.scrollToPositionWithOffset(
                    Math.min(position, adapter.getItemCount() - 1), offset);
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
        if (adapter != null) {
            newsState.visibleNews.clear();
            newsState.visibleNews.addAll(adapter.getAllData());
        }
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

        Log.d(TAG, getString(R.string.network_text_01) + isConnectedOrConnecting +
                getString(R.string.network_text_02) + isConnected + getString(R.string.network_text_03) + isWifi + getString(R.string.network_text_04) + isMobile +
                getString(R.string.network_text_05) + isVpn + getString(R.string.network_text_06) + isAirplane + getString(R.string.network_text_07) + isFast);

        if (!isConnected) {
            // Disconnected / Offline
            swipeRefreshLayout.setRefreshing(false);
            viewNetworkDot.setBackgroundResource(R.color.status_offline);
            tvNetworkStatusText.setText(isAirplane ? getString(R.string.network_text_08) : getString(R.string.network_text_09));
            tvNetworkStatusText.setTextColor(getResources().getColor(R.color.status_offline));

            bannerNetworkNotice.setVisibility(View.VISIBLE);
            bannerNetworkNotice.setBackgroundResource(R.color.status_offline_bg);
            tvBannerText.setText(isAirplane ?
                    getString(R.string.network_text_10) :
                    getString(R.string.network_text_11));
        } else if (isCaptive) {
            // Captive Portal
            viewNetworkDot.setBackgroundResource(R.color.status_warning);
            tvNetworkStatusText.setText(getString(R.string.network_text_12));
            tvNetworkStatusText.setTextColor(getResources().getColor(R.color.status_warning));

            bannerNetworkNotice.setVisibility(View.VISIBLE);
            bannerNetworkNotice.setBackgroundResource(R.color.status_warning_bg);
            tvBannerText.setText(getString(R.string.network_text_13));
        } else if (!isValidated && !isConnected) {
            // Connected without validated internet
            viewNetworkDot.setBackgroundResource(R.color.status_warning);
            tvNetworkStatusText.setText(getString(R.string.network_text_14));
            tvNetworkStatusText.setTextColor(getResources().getColor(R.color.status_warning));

            bannerNetworkNotice.setVisibility(View.VISIBLE);
            bannerNetworkNotice.setBackgroundResource(R.color.status_warning_bg);
            tvBannerText.setText(getString(R.string.network_text_15));
        } else {
            // Fully connected & validated
            viewNetworkDot.setBackgroundResource(R.color.status_online);

            String statusType = getString(R.string.network_text_16);
            if (isVpn) {
                statusType = getString(R.string.network_text_17);
            } else if (isWifi) {
                statusType = getString(R.string.network_text_18);
            } else if (isMobile) {
                statusType = isFast ? getString(R.string.network_text_19) : getString(R.string.network_text_20);
            }
            tvNetworkStatusText.setText(statusType);
            tvNetworkStatusText.setTextColor(getResources().getColor(R.color.status_online));

            bannerNetworkNotice.setVisibility(View.GONE);
        }
    }

    private void cargarNoticiasIniciales() {
        NewsCacheManager.loadNewsFromCache(this, cached -> {
            if (isFinishing() || isDestroyed() || newsState.hasFreshFeed) return;
            if (cached != null && !cached.isEmpty() && adapter.getAllData().isEmpty()) {
                mostrarPrimeraPagina(cached);
                layoutEmptyState.setVisibility(View.GONE);
                rvNoticias.setVisibility(View.VISIBLE);
            }
        });
        ejecutarDescargarNoticias();
    }



    private void ejecutarDescargarNoticias() {
        // Guard barato basado en la red utilizable. No sustituye al GET real.
        if (!ConnectivityAndInternetAccess.isConnected(this)
                || !ConnectivityAndInternetAccess.hasPhysicalNetwork(this)) {
            swipeRefreshLayout.setRefreshing(false);
            Toast.makeText(this, getString(R.string.ui_text_21), Toast.LENGTH_SHORT).show();
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
        if (newsState.isLoadingMore || newsState.noticiasPendientes.isEmpty() || adapter == null) {
            return;
        }

        newsState.isLoadingMore = true;
        int end = Math.min(NEWS_PAGE_SIZE, newsState.noticiasPendientes.size());
        ArrayList<NoticiaRSS> siguientePagina = new ArrayList<>(newsState.noticiasPendientes.subList(0, end));
        newsState.noticiasPendientes.subList(0, end).clear();
        int added = adapter.appendData(siguientePagina);
        newsState.hasMoreNews = !newsState.noticiasPendientes.isEmpty();
        newsState.isLoadingMore = false;
        if (added > 0) {
            NewsCacheManager.saveNewsToCache(this, adapter.getAllData());
            Log.d(TAG, "Scroll infinito: añadidas " + added + " noticias; pendientes=" + newsState.noticiasPendientes.size());
        }
    }

    private void mostrarPrimeraPagina(ArrayList<NoticiaRSS> noticias) {
        mostrarNoticiasHasta(noticias, NEWS_PAGE_SIZE);
    }

    private void mostrarNoticiasHasta(ArrayList<NoticiaRSS> noticias, int requestedCount) {
        newsState.noticiasPendientes.clear();
        int end = Math.min(Math.max(requestedCount, NEWS_PAGE_SIZE), noticias.size());
        adapter.updateData(new ArrayList<>(noticias.subList(0, end)));
        if (end < noticias.size()) {
            newsState.noticiasPendientes.addAll(noticias.subList(end, noticias.size()));
        }
        newsState.hasMoreNews = !newsState.noticiasPendientes.isEmpty();
    }

    private void usarNoticiasOffline() {
        if (!adapter.getAllData().isEmpty()) {
            layoutEmptyState.setVisibility(View.GONE);
            rvNoticias.setVisibility(View.VISIBLE);
            return;
        }
        NewsCacheManager.loadNewsFromCache(this, cached -> {
            if (isFinishing() || isDestroyed() || newsState.hasFreshFeed) return;
            if (cached != null && !cached.isEmpty()) {
                mostrarPrimeraPagina(cached);
                layoutEmptyState.setVisibility(View.GONE);
                rvNoticias.setVisibility(View.VISIBLE);
            } else {
                rvNoticias.setVisibility(View.GONE);
                layoutEmptyState.setVisibility(View.VISIBLE);
            }
        });
    }



    @Override
    public void onRecibeNoticiasRSS(ArrayList<NoticiaRSS> listaNoticias) {
        swipeRefreshLayout.setRefreshing(false);

        if (listaNoticias != null && !listaNoticias.isEmpty()) {
            newsState.hasFreshFeed = true;
            mostrarPrimeraPagina(listaNoticias);
            NewsCacheManager.saveNewsToCache(this, listaNoticias);
            layoutEmptyState.setVisibility(View.GONE);
            rvNoticias.setVisibility(View.VISIBLE);

            // Una actualización completa reinicia el recorrido del archivo.
            newsState.isLoadingMore = false;

            Log.d(TAG, "Noticias recibidas con éxito: " + listaNoticias.size());
        } else {
            Toast.makeText(this, getString(R.string.ui_text_22), Toast.LENGTH_SHORT).show();
            usarNoticiasOffline();
        }
    }

    @Override
    public void onError(DescargaNoticiasRSS.Failure failure) {
        swipeRefreshLayout.setRefreshing(false);
        String message;
        if (failure == null) {
            message = getString(R.string.ui_text_23);
        } else {
            switch (failure.getKind()) {
                case NO_NETWORK:
                    message = getString(R.string.ui_text_24);
                    break;
                case FEED_UNAVAILABLE:
                    message = getString(R.string.ui_text_25);
                    break;
                case NO_INTERNET:
                    message = getString(R.string.ui_text_26);
                    break;
                case HTTP_ERROR:
                    message = getString(R.string.ui_text_27);
                    break;
                default:
                    message = getString(R.string.ui_text_28);
                    break;
            }
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        usarNoticiasOffline();
    }

    private void ejecutarDiagnosticoRedCompleto() {
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.network_text_29))
                .setMessage(getString(R.string.network_text_30))
                .setPositiveButton(getString(R.string.network_text_31), null)
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
                    String reachedHost = result != null ? result.getReachedHost() : getString(R.string.network_text_32);
                    long time = result != null ? result.getElapsedMilliseconds() : 0;

                    StringBuilder sb = new StringBuilder();
                    sb.append(getString(R.string.network_text_33));
                    String interfaceStatus = !isConnected
                            ? getString(R.string.network_text_34)
                            : (reachable ? getString(R.string.network_text_35) : getString(R.string.network_text_36));
                    sb.append(getString(R.string.network_text_37)).append(interfaceStatus).append("\n");
                    sb.append(getString(R.string.network_text_38)).append(isWifi ? getString(R.string.network_text_39) : (isMobile ? getString(R.string.network_text_40) : getString(R.string.network_text_41))).append("\n");
                    sb.append(getString(R.string.network_text_42)).append(isFast ? getString(R.string.network_text_43) : getString(R.string.network_text_44)).append("\n");
                    sb.append(getString(R.string.network_text_45)).append(isVpn ? getString(R.string.network_text_46) : getString(R.string.network_text_47)).append("\n");
                    sb.append(getString(R.string.network_text_48)).append(isAirplane ? getString(R.string.network_text_49) : getString(R.string.network_text_50)).append("\n\n");

                    sb.append(getString(R.string.network_text_51));
                    sb.append(getString(R.string.network_text_52)).append(reachable ? getString(R.string.network_text_53) : getString(R.string.network_text_54)).append("\n");
                    sb.append(getString(R.string.network_text_55)).append(reachedHost).append("\n");
                    sb.append(getString(R.string.network_text_56)).append(time).append(getString(R.string.network_text_57));

                    if (currentNetworkState != null) {
                        sb.append(getString(R.string.network_text_58));
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
                searchView.setQueryHint(getString(R.string.ui_text_59));
                searchView.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
                searchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
                    @Override public boolean onMenuItemActionExpand(MenuItem item) {
                        searchView.post(new Runnable() {
                            @Override public void run() {
                                searchView.setIconified(false);
                                searchView.requestFocus();
                                android.view.inputmethod.InputMethodManager ime =
                                        (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                                if (ime != null) {
                                    View focused = searchView.findFocus();
                                    ime.showSoftInput(focused != null ? focused : searchView,
                                            android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                                }
                            }
                        });
                        return true;
                    }
                    @Override public boolean onMenuItemActionCollapse(MenuItem item) {
                        searchView.setQuery("", false);
                        if (adapter != null) adapter.filter("");
                        return true;
                    }
                });
                
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
