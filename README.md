# Noticias EL PAÍS
Independent Android RSS reader for EL PAÍS.

RSS: https://feeds.elpais.com/mrss-s/pages/ep/site/elpais.com/portada

Search, refresh, sharing, article view, offline cache, passive NetworkObserver and full connectivity diagnostics. The current multi-layer DNS/TCP/NTP/HTTPS/TLS/ICMP connectivity helper from the referenced gist is integrated once under the app package.

Release 2.2.1 keeps the connectivity helper and request-first RSS flow from 2.2.0, and removes the artificial connect/read timeout from news-feed downloads so very slow but usable mobile connections can complete. Passive `NetworkObserver`, just-in-time `isConnected()`/underlying-network guards, and active general diagnostics only after ambiguous transport failures remain unchanged. VPN-only paths without a usable non-VPN underlying network are rejected consistently, while validated/captive-portal state remains a separate diagnostic signal.

This is not an official EL PAÍS application.
