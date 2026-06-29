# TakeMotions Media Bridge

A small, **single-purpose** Android helper that shares your phone's **now-playing media** with
**TakeMotions apps running in Even Hub** (the Even Realities companion).

Apps inside the Even Hub in-app browser can't read what's playing directly, so apps that show
your current track (such as **Now Playing**) rely on this bridge. It shows what's playing and
relays playback commands from the glasses' R1 ring — and it does **nothing else**. The full
source is here so you can verify exactly that.

## How it works

While running, the bridge serves your current media info at `http://127.0.0.1:8766/media`
(reachable only from your own phone) and accepts playback commands (play / pause / next /
previous / volume) at `http://127.0.0.1:8766/media/<action>`. A small foreground service runs a
tiny [NanoHTTPD](https://github.com/NanoHttpd/nanohttpd) server bound to `127.0.0.1` only, so
the glasses app can reach it even with the screen off.

## Why it needs "Notification access"

Android only lets an app see active media sessions if it has an **enabled notification
listener**. So Media Bridge registers one — but that listener is **empty and does nothing**
(see [`MediaAccessService.kt`](app/src/main/java/com/takemotions/mediabridge/MediaAccessService.kt)).
The app **never reads, stores, or sends your notifications or messages**; the permission is only
the key Android requires to see media playback. You can confirm this in the source.

## Privacy

- Media info is served **only to localhost on your own device** — never sent anywhere, never
  stored.
- The notification listener is empty: **no notification or message content is ever read.**
- ⚠️ While the bridge is running, **any app on your device could read your now-playing info via
  localhost.** Turn it on while you're using a TakeMotions app, and turn it off when you're done.
- A track you pause **from the ring** is kept so you can resume it; a player you close on the
  phone disappears.
- The app requests only **Notification access** (for media sessions, as above).

## Install (use it)

1. Download the latest **`media-bridge-x.y.z.apk`** from the [Releases](../../releases) page.
2. Open the downloaded file. Android will ask permission to install from this source — allow it
   for your browser or Files app.
3. Open **Media Bridge**, turn on **Enable media bridge**, and grant **Notification access**.
4. (Screen-off / pocket use) Set the app's **Battery** to **Unrestricted** so it keeps serving
   with the screen off (wording varies by Android version).

The "Now playing" box on the app's screen lets you confirm it's working without the glasses.

## Endpoint contract (for app developers)

```
GET http://127.0.0.1:8766/media
  -> { "enabled": true,
       "playing": true, "state": "playing",          // playing|paused|stopped|buffering|none
       "package": "com.spotify.music", "app": "Spotify",
       "title": "Song", "artist": "Artist", "album": "Album",
       "position": 12345, "duration": 210000,         // ms; duration<=0 => live
       "live": false }
  -> { "enabled": false }                             // when the bridge switch is off

GET http://127.0.0.1:8766/media/<action>              // play|pause|playpause|next|prev|volup|voldown
  -> { "ok": true, "action": "next" }

GET http://127.0.0.1:8766/health
  -> { "ok": true, "media": true }
```

CORS `*` + `Cache-Control: no-store` on every response, so an Even Hub WebView companion can
`fetch()` it with no extra setup.

## Build from source

Open in Android Studio and Run, or:

```
./gradlew assembleDebug      # debug APK
./gradlew assembleRelease    # release APK (configure signing first — see below)
```

- Package: `com.takemotions.mediabridge`
- minSdk 26, targetSdk 36

For a signed release build, create `keystore.properties` in the project root (it is git-ignored)
with `storeFile`, `storePassword`, `keyAlias`, `keyPassword`; the build picks it up
automatically. Without it, `assembleDebug` still works.

## License

[MIT](LICENSE) © TakeMotions

---

Made by **TakeMotions** · [@r_tkbyc](https://x.com/r_tkbyc)
