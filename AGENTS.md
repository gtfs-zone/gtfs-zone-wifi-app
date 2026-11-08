# AGENTS.md

Android (Kotlin) collector for wifi.gtfs.zone. Records location, wifi, cell
info and light network tests during a rider-picked trip, plus manual
amenity, captive portal and station wifi inputs. Sessions queue offline and
upload anonymously to gtfs-zone-wifi-ingest in the session schema that repo
owns.

- **Privacy**: no account, no device id. Trim each session to the trip and
  stations on the device before upload.
- **Trip pick**: departures come from the Transitous API; send a User-Agent
  with the app name, version and contact.
- **Borrowed code**: scanning patterns may come from NeoStumbler (MIT); keep
  its copyright notice and add a REUSE annotation for copied files.

## Conventions

- **Commits**: Conventional Commits, enforced by the `commit-msg` hook. Never add
  Co-Authored-By trailers. Setup is in [CONTRIBUTING.md](CONTRIBUTING.md).
- **Cross-repo work**: sibling gtfs.zone repos live under the same parent
  directory and may be read and edited when a change spans repos.
- **Plans**: the central plan is `CURRENT_PLAN.md` in gtfs-zone-wifi-pipeline
  (phases across all wifi repos). A local `CURRENT_PLAN.md` here only points
  there. It is neither tracked nor gitignored: never stage or commit it.
