# HeadSet — Music Widget App

Compact standalone-style music widget for Windows. **No homepage, dashboard or side navigation.** Designed for the small, separate Edge / Chrome **app window** (about 440 × 520 px) opened by the Java server. The player works offline on your computer and needs no account.

## Features
- Now Playing widget: cover art, title, artist, scrub, play/pause, previous/next, shuffle and repeat.
- Music library: search, choose songs, refresh folder and add audio files.
- Sound controls: volume, mute, 5-band EQ presets, and playback speed.
- Stores your last song and sound settings locally.
- Uses the Java backend already in your project; supports files in `music/` and uploads via the UI.
- Works on the local network from your phone too, using the address the Java server prints; phone access uses the same compact UI in a normal mobile browser.

## Start on Windows
1. Extract the ZIP and double-click **`START_WIDGET_WINDOWS.bat`**.
2. Keep the terminal window open for the local music server, or use the tray icon's **Quit HeadSet** to close it.
3. A small HeadSet window opens in **Microsoft Edge** or **Google Chrome** in app mode (without tabs or an address bar). If neither is installed, it opens in your default browser.
4. Add audio using **Add music** or put audio files in the `music/` folder and click the Library's refresh button.

Requires **Java JDK 17+** and Edge or Chrome for the separate widget window. It is **not** a native Windows taskbar widget or phone homescreen widget. You can resize/move its window. If you have Microsoft PowerToys, press **Win+Ctrl+T** to keep the window on top.

To launch from your VS Code terminal with Java 17+: `set HEADSET_WIDGET=1` (Command Prompt) or `$env:HEADSET_WIDGET="1"` (PowerShell), then `java MusicServer.java`.

If port 8080 is occupied, the Java server selects the next available port automatically, then opens the correct widget URL.

## Project files
- `public/index.html` — compact widget interface only
- `public/style.css` — dark lavender widget styling
- `public/app.js` — playback, music queue, EQ, search and file upload
- `MusicServer.java` — original local Java music server with widget-window launch support
- `music/` — local music. A supplied sample file is included for local testing.

## Keyboard shortcuts
Space = pause/resume; N/P = next/previous; arrows = seek; S = shuffle; R = repeat; M = mute; L = library; E = equalizer; Esc = close panel.

Only share/distribute audio for which you have appropriate rights. The project `.gitignore` excludes the music files.
