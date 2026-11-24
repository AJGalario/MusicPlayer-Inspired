# Neon Player

Java backend + HTML/CSS/JS front end. Works on PC and phones (same Wi-Fi).

## Run
1. Put songs in `music/`  (name them `Artist - Title.mp3` to get artist/title)
   - Optional cover art: `Artist - Title.jpg` next to the song, or `cover.jpg` in the folder
   - Optional background: save any picture as `public/bg.jpg`
2. Start the server (JDK 17+):
       java MusicServer.java            # port 8080, ./music, ./public
       java MusicServer.java 9000 /path/to/music
3. Open the address it prints. On your phone use the "Phone/other" address.
   Tap the menu icon (top-left) for the playlist, then the rescan button after adding songs.

## Keyboard (PC)
Space play/pause · ←/→ seek 5s · ↑/↓ volume · N next · P previous · S shuffle · R repeat · M mute

## IntelliJ IDEA
1. File > Open > select the `neon-player` folder.
2. Right-click the `neon-player` folder in the Project panel > Mark Directory as > Sources Root.
3. File > Project Structure > Project: set SDK to JDK 17 or newer.
4. Open `MusicServer.java`, click the green arrow next to `main`, then Run.
5. The server finds the `public/` and `music/` folders by itself. The console prints the paths it uses; if it says index.html NOT FOUND, check that you opened the right folder.

## VS Code
1. Install the "Extension Pack for Java" (Microsoft).
2. File > Open Folder > `neon-player` (open the folder itself, not a parent).
3. Press F5 (uses .vscode/launch.json), or click Run above `main` in MusicServer.java.
4. Open http://localhost:8080 .
Edits to public/*.html/css/js apply on browser refresh. Java edits need a restart.
