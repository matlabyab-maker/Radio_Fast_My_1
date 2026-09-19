# Fast Radio

Dashboard-oriented internet radio Android project.

## Included
- Custom Radio list
- Iran Radio list from RadioBrowser country search
- World Radio: America, Europe, Africa, Asia, National country grouping
- RadioBrowser station search and saved JSON lists
- Favorites stored in app-private `favorites.json`
- Special News Stations panel: Sputnik Persian, BBC Persian, Iran International, VOA Persian, BBC News, NHK Japan
- Vertical ruler-style bitrate selector from 1 to 400 kbps
- Estimated radio data usage per minute, highlighted with an orange bar
- 5–10 second low-buffer playback target
- Media3 ExoPlayer 1.11.0 + HLS + DASH + FFmpeg decoder extension
- Android MediaPlayer compatibility class retained for direct-stream fallback integration
- MediaSession background playback
- GitHub Actions debug APK and build log

## Important
The 1–400 kbps ruler is a target/selection control. It cannot transcode an arbitrary source stream locally. Actual lower-bitrate restreaming requires a real server-side transcoder/restream endpoint; this project does not insert fake endpoints.

Build with GitHub Actions using Java 17 and Gradle 8.13.


Build fix: the FFmpeg decoder uses the Jellyfin Media3 FFmpeg decoder artifact because the AndroidX Media3 1.11.0 Maven coordinates do not publish `androidx.media3:media3-decoder-ffmpeg` as a directly consumable app dependency. The FFmpeg decoder is kept enabled through DefaultRenderersFactory.


Build Fix 2 (2026-09-19):
The unavailable FFmpeg Maven dependency has been removed from the application dependency list. The project uses the standard AndroidX Media3 ExoPlayer/HLS/DASH/Session/UI modules that are available from Google Maven. No fake or unavailable FFmpeg Maven coordinate is required for this build. The playback service keeps Media3's extension renderer preference enabled, so the project remains ready for a locally supplied decoder extension in a future build.


## Changes in BuildFixed3
- Screen orientation changed to full sensor rotation (automatic portrait/landscape).
- Custom Radio now attempts to load live top-voted stations from RadioBrowser and falls back to the bundled list.
- Country lookup uses the documented exact country-code endpoint.
- The 1–400 kbps ruler now applies a Media3 maximum audio bitrate selection when the stream exposes adaptive audio variants. A normal single-bitrate internet radio stream cannot be re-encoded on the phone; true bitrate conversion requires a server-side transcoder.
- Per-minute traffic display now measures the app UID's received bytes and rolls immediately into minute 2, 3, etc. while playback continues.


### BuildFixed4 changes
- Removed the outer vertical ScrollView so the dashboard itself does not scroll; each station ListView scrolls independently.
- Increased RadioBrowser station limits to 500 and added multiple server retries.
- Prefer RadioBrowser reachable stations and deduplicate by station UUID.
- Added alternate raw stream URL and a MediaPlayer compatibility fallback after Media3 playback errors.
- Auto rotation remains enabled via fullSensor.
