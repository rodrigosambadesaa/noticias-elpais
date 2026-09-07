# Noticias EL PAÍS
Independent Android RSS reader for EL PAÍS.

RSS: https://feeds.elpais.com/mrss-s/pages/ep/site/elpais.com/portada

Search, refresh, sharing, article view, offline cache, passive NetworkObserver and full connectivity diagnostics. The current multi-layer DNS/TCP/NTP/HTTPS/TLS/ICMP connectivity helper from the referenced gist is vendored under third_party/connectivity and adapted into the app package.

Release 1.5.0 applies the connectivity helper as a cheap network guard plus request-first RSS flow, with active diagnostics only after ambiguous transport failures. It also keeps offline cache and progressive infinite scrolling. Orientation changes restore the cached list, loaded pages, and current scroll position without starting another RSS request.

This is not an official EL PAÍS application.
