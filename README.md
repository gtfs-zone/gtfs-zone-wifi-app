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

## Build

Needs JDK 17 or newer and the Android SDK (`sdk.dir` in `local.properties`,
e.g. `sdk.dir=/home/you/Android/Sdk`).

```bash
./gradlew check assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

`scripts/check.sh` (`./gradlew check`: lint and unit tests) runs in the
pre-commit hook and CI. Install the hooks as described in
[CONTRIBUTING.md](CONTRIBUTING.md).

The debug build (`zone.gtfs.wifi.debug`) reads its endpoints from
`local.properties` or `-P`:

| Property | Default | |
|---|---|---|
| `wifi.ingestUrl` | `http://10.0.2.2:8000` | gtfs-zone-wifi-ingest, e.g. `http://192.168.1.20:8000` on the LAN |
| `wifi.dataUrl` | `https://data.wifi.gtfs.zone` | Public bucket holding `probe/100k.bin` |

The release build uses `https://ingest.wifi.gtfs.zone` and
`https://data.wifi.gtfs.zone`.

## How it records

- Trip: pick a nearby stop (Transitous `/api/v6/map/stops`), a departure
  (`/api/v6/stoptimes`) and where you get off (`/api/v6/trip`), plus an
  optional car number and class.
- Station: pick a nearby stop and fill in the wifi checklist.
- While a session is active, a location foreground service takes a sample
  every 10 s (location, connected wifi, serving cell) and a network test
  every 60 s over the connected wifi, else over cell:
  - plain HTTP `GET /generate_204` on ingest, redirects not followed: a 204
    is reachable, any other answer is a captive portal
  - a timed fetch of `probe/100k.bin` when reachable
- Everything is stored in Room on the device.

## Upload and privacy

- Submitting a session trims it and builds the session JSON
  (`schemas/session.v1.json` from gtfs-zone-wifi-ingest, pinned in
  `app/src/test/resources/schemas/`); the untrimmed rows are then deleted.
- Trip trim: from the first sample within 300 m of the boarding stop to the
  last within 300 m of the alighting stop, keeping only samples within
  300 m of the trip line between them. Station trim: samples within 300 m
  of the stop. Network tests are kept within 30 s of the kept samples.
- Uploads are gzipped POSTs to `/v1/sessions`, on unmetered networks only,
  retried with backoff. "Upload now" in Sessions ignores the unmetered rule.
- No account, no device id: each session has a random UUID. Android backup
  is off.

## License

AGPL-3.0-or-later. See [LICENSE.txt](LICENSE.txt).
