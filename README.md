# Noticias EL PAÍS
Independent Android RSS reader for EL PAÍS.

RSS: https://feeds.elpais.com/mrss-s/pages/ep/site/elpais.com/portada

Search, refresh, sharing, article view, offline cache, passive NetworkObserver and full connectivity diagnostics. The current multi-layer DNS/TCP/NTP/HTTPS/TLS/ICMP connectivity helper from the referenced gist is integrated once under the app package.

Release 2.2.0 updates the connectivity helper to the latest canonical gist revision and keeps the request-first RSS flow: passive `NetworkObserver`, just-in-time `isConnected()`/underlying-network guards, and active general diagnostics only after ambiguous transport failures. It also keeps offline cache and progressive infinite scrolling. Orientation changes restore the cached list, loaded pages, and current scroll position without starting another RSS request. VPN-only paths without a usable non-VPN underlying network are rejected consistently, while validated/captive-portal state remains a separate diagnostic signal.

This is not an official EL PAÍS application.
