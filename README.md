# gtfs-zone-wifi-app

Android app that records the wifi, cell coverage and seat amenities a rider
actually gets on a transit trip, for [wifi.gtfs.zone](https://github.com/gtfs-zone/gtfs-zone-wifi-pipeline/blob/main/docs/overview.md).

- Pick the trip you are on from nearby departures
  ([Transitous](https://transitous.org))
- While the trip is active, log location, wifi and cell info, and run light
  network tests (reachability, captive portal, latency, small timed fetch)
- Report outlets, wifi status and captive portal details; rate station wifi
  with a checklist
- Sessions are stored offline and uploaded anonymously, trimmed to the trip,
  when on an unmetered network

Uploads go to gtfs-zone-wifi-ingest. See the [project overview](https://github.com/gtfs-zone/gtfs-zone-wifi-pipeline/blob/main/docs/overview.md).

## License

AGPL-3.0-or-later. See [LICENSE.txt](LICENSE.txt).
