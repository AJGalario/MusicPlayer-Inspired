import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.awt.AWTException;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.SocketException;
import java.net.URLDecoder;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

/**
 * HeadSet Player backend. No dependencies, needs JDK 17+.
 *
 *   java MusicServer.java [port] [musicDir] [publicDir]
 *
 * GET  /api/tracks        -> JSON list of songs found in musicDir (rescanned on every call)
 * POST /api/upload        -> multipart/form-data upload; saves audio files to musicDir
 * GET  /audio/{id}        -> the audio file, with HTTP Range support (seeking + mobile)
 * GET  /cover/{id}        -> cover image next to the song (same name, or cover/folder.jpg)
 * GET  /...               -> static front end from publicDir
 */
public class MusicServer {

    static final Set<String> AUDIO = Set.of("mp3", "m4a", "aac", "wav", "ogg", "oga", "opus", "flac", "webm");
    static final List<String> IMAGE_EXT = List.of("jpg", "jpeg", "png", "webp");
    static final Map<String, String> MIME = Map.ofEntries(
            Map.entry("mp3",  "audio/mpeg"),  Map.entry("m4a",  "audio/mp4"),   Map.entry("aac", "audio/aac"),
            Map.entry("wav",  "audio/wav"),   Map.entry("ogg",  "audio/ogg"),   Map.entry("oga", "audio/ogg"),
            Map.entry("opus", "audio/ogg"),   Map.entry("flac", "audio/flac"),  Map.entry("webm","audio/webm"),
            Map.entry("jpg",  "image/jpeg"),  Map.entry("jpeg", "image/jpeg"),  Map.entry("png", "image/png"),
            Map.entry("webp", "image/webp"),  Map.entry("svg",  "image/svg+xml"),Map.entry("ico","image/x-icon"),
            Map.entry("html", "text/html; charset=utf-8"),
            Map.entry("css",  "text/css; charset=utf-8"),
            Map.entry("js",   "text/javascript; charset=utf-8"),
            Map.entry("json", "application/json; charset=utf-8"),
            Map.entry("webmanifest", "application/manifest+json; charset=utf-8"));

    record Track(String id, String title, String artist, Path file, Path cover) {}

    static Path musicDir;
    static Path publicDir;

    public static void main(String[] args) throws Exception {
        // ---- Resolve directories first so the lock file lives beside the jar ----
        Path root = findRoot();
        musicDir = (args.length > 1 ? Path.of(args[1]) : root.resolve("music")).toAbsolutePath().normalize();
        publicDir = (args.length > 2 ? Path.of(args[2]) : root.resolve("public")).toAbsolutePath().normalize();
        Files.createDirectories(musicDir);

        // ---- Single-instance lock + port discovery ----
        // The lock file stores the port the running instance is on.
        Path lockFile = root.resolve(".headset.lock");
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;

        // Check if another instance is already running by trying to read the lock file
        if (Files.exists(lockFile)) {
            try {
                String stored = Files.readString(lockFile).trim();
                int runningPort = Integer.parseInt(stored);
                // Try to connect — if it succeeds, just open the browser and exit
                try (ServerSocket probe = new ServerSocket()) {
                    probe.setReuseAddress(false);
                    probe.bind(new InetSocketAddress(runningPort), 1);
                    // Bind succeeded → old process is dead, ignore the lock
                    Files.deleteIfExists(lockFile);
                } catch (IOException alreadyBound) {
                    System.out.println("HeadSet is already running on port " + runningPort + " — opening browser.");
                    launchUi("http://localhost:" + runningPort);
                    return;   // <-- exit this new instance cleanly
                }
            } catch (Exception ignoredLockParseError) {
                Files.deleteIfExists(lockFile);
            }
        }

        // ---- Find a free port (8080, 8081, … 8099) ----
        int actualPort = findFreePort(port, port + 20);
        if (actualPort < 0) {
            System.err.println("No free port found in range " + port + "-" + (port + 20));
            System.exit(1);
        }

        // Write lock file so the next launch can re-use our port
        Files.writeString(lockFile, String.valueOf(actualPort),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        // Delete lock on clean exit
        lockFile.toFile().deleteOnExit();

        // ---- Start HTTP server ----
        HttpServer server = HttpServer.create(new InetSocketAddress(actualPort), 0);
        server.createContext("/api/tracks",       safe(MusicServer::tracks));
        server.createContext("/api/upload",       MusicServer::upload);
        server.createContext("/api/open-desktop", safe(MusicServer::openDesktop));
        server.createContext("/audio/",           safe(MusicServer::audio));
        server.createContext("/cover/",           safe(MusicServer::cover));
        server.createContext("/",                 safe(MusicServer::staticFile));
        server.setExecutor(Executors.newFixedThreadPool(32));
        server.start();

        System.out.println("HeadSet Player running");
        System.out.println("  Music folder : " + musicDir);
        System.out.println("  Web files    : " + publicDir
                + (Files.isRegularFile(publicDir.resolve("index.html")) ? "" : "   <-- index.html NOT FOUND"));
        System.out.println("  This device  : http://localhost:" + actualPort);
        for (String ip : lanAddresses()) System.out.println("  Phone/other  : http://" + ip + ":" + actualPort);

        // ---- System tray icon (lets user quit cleanly) ----
        installTrayIcon(server, lockFile, actualPort);

        // ---- Open browser ----
        launchUi("http://localhost:" + actualPort);
    }

    /**
     * Finds the folder that contains public/index.html, so it works no matter which working
     * directory the IDE uses: tries the current directory and the folder the program was
     * started from, going up through parents and also checking direct sub-folders.
     */
    static Path findRoot() {
        List<Path> starts = new ArrayList<>();
        starts.add(Path.of("").toAbsolutePath());
        try {
            starts.add(Path.of(MusicServer.class.getProtectionDomain().getCodeSource().getLocation().toURI()));
        } catch (Exception ignored) { }
        for (Path start : starts) {
            for (Path p = start; p != null; p = p.getParent()) {
                if (Files.isRegularFile(p.resolve("public").resolve("index.html"))) return p;
            }
            try (Stream<Path> kids = Files.list(start)) {
                for (Path k : (Iterable<Path>) kids.filter(Files::isDirectory)::iterator) {
                    if (Files.isRegularFile(k.resolve("public").resolve("index.html"))) return k;
                }
            } catch (IOException | RuntimeException ignored) { }
        }
        return Path.of("").toAbsolutePath();
    }

    // ---------------------------------------------------------------- open browser

    /** Opens the default browser to the given URL. Best-effort — silently ignores failures. */
    static void openBrowser(String url) {
        try { Thread.sleep(400); } catch (InterruptedException ignored) {} // let the server bind first
        try {
            // java.awt.Desktop works on most desktop JDKs
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().browse(java.net.URI.create(url));
                return;
            }
        } catch (Exception ignored) {}
        // Fallback: OS-specific command
        String os = System.getProperty("os.name", "").toLowerCase();
        try {
            if (os.contains("win"))   Runtime.getRuntime().exec(new String[]{"rundll32", "url.dll,FileProtocolHandler", url});
            else if (os.contains("mac")) Runtime.getRuntime().exec(new String[]{"open", url});
            else                         Runtime.getRuntime().exec(new String[]{"xdg-open", url});
        } catch (Exception ignored) {}
    }

    /** Widget mode launches Microsoft Edge or Google Chrome in a compact app window.
     *  This retains normal browser mode when HEADSET_WIDGET is not enabled. */
    static void launchUi(String url) {
        if ("1".equals(System.getenv("HEADSET_NO_LAUNCH"))) return;
        if (!"1".equals(System.getenv("HEADSET_WIDGET"))) { openBrowser(url); return; }
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            String[] candidates = {
                System.getenv("PROGRAMFILES(X86)") + "\\Microsoft\\Edge\\Application\\msedge.exe",
                System.getenv("PROGRAMFILES") + "\\Microsoft\\Edge\\Application\\msedge.exe",
                System.getenv("LOCALAPPDATA") + "\\Microsoft\\Edge\\Application\\msedge.exe",
                System.getenv("PROGRAMFILES") + "\\Google\\Chrome\\Application\\chrome.exe",
                System.getenv("PROGRAMFILES(X86)") + "\\Google\\Chrome\\Application\\chrome.exe",
                System.getenv("LOCALAPPDATA") + "\\Google\\Chrome\\Application\\chrome.exe"
            };
            for (String location : candidates) {
                if (java.nio.file.Files.isRegularFile(java.nio.file.Path.of(location))) {
                    try {
                        new ProcessBuilder(location, "--app=" + url, "--window-size=440,520", "--window-position=110,100").start();
                        return;
                    } catch (IOException ignored) {}
                }
            }
        }
        // If no app-capable browser is available, fall back to the regular browser.
        openBrowser(url);
    }

    static void openDesktop(HttpExchange ex) throws IOException {
        new Thread(() -> {
            try {
                Path root = findRoot();
                Path exe = root.resolve("HeadSet.exe");
                if (Files.isRegularFile(exe)) {
                    new ProcessBuilder(exe.toString(), "--desktop").directory(root.toFile()).start();
                } else {
                    Path runBat = root.resolve("run.bat");
                    if (Files.isRegularFile(runBat)) {
                        new ProcessBuilder("cmd.exe", "/c", runBat.toString(), "desktop").directory(root.toFile()).start();
                    }
                }
            } catch (Exception ignored) {}
        }).start();
        byte[] ok = "{\"status\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(200, ok.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(ok); }
    }

    // ---------------------------------------------------------------- port helper

    /** Returns the first free TCP port in [start, end), or -1 if none available. */
    static int findFreePort(int start, int end) {
        for (int p = start; p < end; p++) {
            try (ServerSocket s = new ServerSocket()) {
                s.setReuseAddress(false);
                s.bind(new InetSocketAddress(p), 1);
                return p;   // bind succeeded → port is free
            } catch (IOException ignored) { /* port taken, try next */ }
        }
        return -1;
    }

    // ---------------------------------------------------------------- system tray

    /**
     * Installs a system-tray icon with an "Open Player" and "Quit" menu.
     * Falls back gracefully if the system tray is unavailable (e.g. headless).
     */
    static void installTrayIcon(HttpServer server, Path lockFile, int port) {
        try {
            if (java.awt.GraphicsEnvironment.isHeadless() || !SystemTray.isSupported()) return;
            // Draw a simple headset icon programmatically
            java.awt.image.BufferedImage img =
                    new java.awt.image.BufferedImage(32, 32, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g = img.createGraphics();
            g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new java.awt.Color(124, 111, 205));
            g.fillOval(0, 0, 31, 31);
            g.setColor(java.awt.Color.WHITE);
            g.setStroke(new java.awt.BasicStroke(2.5f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
            g.drawArc(7, 4, 18, 14, 0, 180);
            g.fillRoundRect(4, 15, 5, 8, 2, 2);
            g.fillRoundRect(23, 15, 5, 8, 2, 2);
            g.dispose();

            String tip = "HeadSet Player — http://localhost:" + port;
            TrayIcon trayIcon = new TrayIcon(img, tip);
            trayIcon.setImageAutoSize(true);

            PopupMenu menu = new PopupMenu();
            MenuItem openItem = new MenuItem("Open Player");
            MenuItem quitItem = new MenuItem("Quit HeadSet");
            menu.add(openItem);
            menu.addSeparator();
            menu.add(quitItem);
            trayIcon.setPopupMenu(menu);

            openItem.addActionListener(e -> launchUi("http://localhost:" + port));
            trayIcon.addActionListener(e -> launchUi("http://localhost:" + port)); // double-click

            quitItem.addActionListener(e -> {
                server.stop(1);
                try { Files.deleteIfExists(lockFile); } catch (IOException ignored) {}
                SystemTray.getSystemTray().remove(trayIcon);
                System.exit(0);
            });

            SystemTray.getSystemTray().add(trayIcon);
            trayIcon.displayMessage("HeadSet Player", "Running at http://localhost:" + port, TrayIcon.MessageType.INFO);
        } catch (Throwable e) {
            System.out.println("  (System tray unavailable — close this window to quit)");
        }
    }

    // ---------------------------------------------------------------- handlers

    static void tracks(HttpExchange ex) throws IOException {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (Track t : scan()) {
            if (!first) sb.append(',');
            first = false;
            sb.append("{\"id\":").append(json(t.id()))
              .append(",\"title\":").append(json(t.title()))
              .append(",\"artist\":").append(json(t.artist()))
              .append(",\"cover\":").append(t.cover() != null).append('}');
        }
        sb.append(']');
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        send(ex, 200, MIME.get("json"), sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Accepts a raw binary POST of a single audio file.
     * The filename is passed in the X-Filename header (URL-encoded).
     * The front-end may also send a multipart request; here we handle the simpler raw-body approach.
     *
     * Usage from JavaScript:
     *   fetch('/api/upload', { method: 'POST', headers: {'X-Filename': encodeURIComponent(file.name)}, body: file })
     */
    static void upload(HttpExchange ex) throws IOException {
        addCors(ex);
        if ("OPTIONS".equals(ex.getRequestMethod())) { ex.sendResponseHeaders(204, -1); ex.close(); return; }
        if (!"POST".equals(ex.getRequestMethod())) {
            ex.getResponseHeaders().set("Allow", "POST, OPTIONS");
            ex.sendResponseHeaders(405, -1); ex.close(); return;
        }
        try {
            String rawName = ex.getRequestHeaders().getFirst("X-Filename");
            if (rawName == null || rawName.isBlank()) rawName = "upload.mp3";
            String fileName;
            try {
                fileName = URLDecoder.decode(rawName, StandardCharsets.UTF_8);
            } catch (Exception e) {
                fileName = rawName;
            }
            fileName = fileName.replaceAll("[/\\\\:*?\"<>|]", "_").trim(); // sanitize
            if (fileName.isEmpty()) fileName = "upload.mp3";

            String ext = ext(Path.of(fileName));
            if (!AUDIO.contains(ext)) {
                send(ex, 415, MIME.get("json"), ("{\"error\":\"Unsupported audio format\"}").getBytes(StandardCharsets.UTF_8));
                return;
            }

            Files.createDirectories(musicDir);
            Path dest = musicDir.resolve(fileName).normalize();
            if (!dest.startsWith(musicDir)) {
                send(ex, 400, MIME.get("json"), ("{\"error\":\"Invalid destination path\"}").getBytes(StandardCharsets.UTF_8));
                return;
            }

            try (InputStream in = ex.getRequestBody();
                 OutputStream out = Files.newOutputStream(dest, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                }
            }

            String rel = musicDir.relativize(dest).toString().replace('\\', '/');
            String trackId = Base64.getUrlEncoder().withoutPadding().encodeToString(rel.getBytes(StandardCharsets.UTF_8));

            byte[] resp = ("{\"ok\":true,\"file\":" + json(fileName) + ",\"id\":" + json(trackId) + "}").getBytes(StandardCharsets.UTF_8);
            send(ex, 200, MIME.get("json"), resp);
        } catch (Exception e) {
            e.printStackTrace();
            try { send(ex, 500, MIME.get("json"), "{\"error\":\"server error\"}".getBytes(StandardCharsets.UTF_8)); } catch (IOException ignored) {}
        } finally {
            ex.close();
        }
    }

    static void audio(HttpExchange ex) throws IOException {
        Path file = resolve(ex.getRequestURI().getPath().substring("/audio/".length()));
        if (file == null) { ex.sendResponseHeaders(404, -1); return; }
        addCors(ex);
        serveFile(ex, file, MIME.get(ext(file)));
    }

    static void cover(HttpExchange ex) throws IOException {
        Path song = resolve(ex.getRequestURI().getPath().substring("/cover/".length()));
        Path img  = song == null ? null : findCover(song);
        if (img == null) { ex.sendResponseHeaders(404, -1); return; }
        ex.getResponseHeaders().set("Cache-Control", "public, max-age=3600");
        serveFile(ex, img, MIME.get(ext(img)));
    }

    static void staticFile(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if (path.equals("/")) path = "/index.html";
        Path f = publicDir.resolve(path.substring(1)).normalize();
        if (!f.startsWith(publicDir) || !Files.isRegularFile(f)) {
            String msg = "<h2>404 — file not found</h2><p>Server is looking for web files in:<br><code>" + publicDir
                    + "</code></p><p>That must be the <b>neon-player/public</b> folder (it contains index.html). "
                    + "Start the server from the neon-player folder, or pass the folder as the 3rd argument.</p>";
            send(ex, 404, MIME.get("html"), msg.getBytes(StandardCharsets.UTF_8));
            return;
        }
        ex.getResponseHeaders().set("Cache-Control", "no-cache");
        serveFile(ex, f, MIME.getOrDefault(ext(f), "application/octet-stream"));
    }

    // ------------------------------------------------------------ file serving

    /** Serves a file and honours "Range: bytes=a-b" so browsers can seek and mobile players can stream. */
    static void serveFile(HttpExchange ex, Path file, String mime) throws IOException {
        long size = Files.size(file);
        Headers h = ex.getResponseHeaders();
        h.set("Content-Type", mime);
        h.set("Accept-Ranges", "bytes");

        long start = 0, end = size - 1;
        int status = 200;
        String range = ex.getRequestHeaders().getFirst("Range");
        if (range != null && range.startsWith("bytes=")) {
            try {
                String[] p = range.substring(6).split("-", 2);
                if (p[0].isEmpty()) {
                    start = Math.max(0, size - Long.parseLong(p[1]));
                } else {
                    start = Long.parseLong(p[0]);
                    if (p.length > 1 && !p[1].isBlank()) end = Math.min(end, Long.parseLong(p[1]));
                }
                if (start > end || start >= size) {
                    h.set("Content-Range", "bytes */" + size);
                    ex.sendResponseHeaders(416, -1);
                    return;
                }
                status = 206;
                h.set("Content-Range", "bytes " + start + "-" + end + "/" + size);
            } catch (NumberFormatException e) {
                start = 0; end = size - 1; status = 200;
            }
        }

        long length = end - start + 1;
        if ("HEAD".equals(ex.getRequestMethod())) { ex.sendResponseHeaders(status, -1); return; }
        ex.sendResponseHeaders(status, length);
        try (OutputStream os = ex.getResponseBody(); RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            raf.seek(start);
            byte[] buf = new byte[64 * 1024];
            long left = length;
            while (left > 0) {
                int n = raf.read(buf, 0, (int) Math.min(buf.length, left));
                if (n < 0) break;
                os.write(buf, 0, n);
                left -= n;
            }
        } catch (IOException clientClosed) {
            // browser cancelled request (normal when seeking or skipping)
        }
    }

    static void send(HttpExchange ex, int status, String type, byte[] body) throws IOException {
        ex.getResponseHeaders().set("Content-Type", type);
        addCors(ex);
        if ("HEAD".equals(ex.getRequestMethod())) { ex.sendResponseHeaders(status, -1); return; }
        ex.sendResponseHeaders(status, body.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(body); }
    }

    static void addCors(HttpExchange ex) {
        ex.getResponseHeaders().set("Access-Control-Allow-Origin",  "*");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "X-Filename, Content-Type");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, HEAD, POST, OPTIONS");
    }

    static HttpHandler safe(HttpHandler inner) {
        return ex -> {
            try {
                String m = ex.getRequestMethod();
                if (!m.equals("GET") && !m.equals("HEAD")) {
                    ex.getResponseHeaders().set("Allow", "GET, HEAD");
                    ex.sendResponseHeaders(405, -1);
                    return;
                }
                inner.handle(ex);
            } catch (Exception e) {
                try { ex.sendResponseHeaders(500, -1); } catch (IOException ignored) { }
            } finally {
                ex.close();
            }
        };
    }

    // ----------------------------------------------------------------- library

    static List<Track> scan() throws IOException {
        try (Stream<Path> s = Files.walk(musicDir)) {
            return s.filter(Files::isRegularFile)
                    .filter(p -> AUDIO.contains(ext(p)))
                    .sorted()
                    .map(MusicServer::toTrack)
                    .toList();
        }
    }

    static Track toTrack(Path p) {
        String rel    = musicDir.relativize(p).toString().replace('\\', '/');
        String id     = Base64.getUrlEncoder().withoutPadding().encodeToString(rel.getBytes(StandardCharsets.UTF_8));
        String name   = baseName(p);
        String artist = "", title = name;
        int sep = name.indexOf(" - ");
        if (sep > 0) { artist = name.substring(0, sep).trim(); title = name.substring(sep + 3).trim(); }
        return new Track(id, title, artist, p, findCover(p));
    }

    /** song.mp3 -> song.jpg|png|webp, otherwise cover.* or folder.* in the same directory. */
    static Path findCover(Path song) {
        Path dir = song.getParent();
        for (String base : List.of(baseName(song), "cover", "folder")) {
            for (String e : IMAGE_EXT) {
                Path c = dir.resolve(base + "." + e);
                if (Files.isRegularFile(c)) return c;
            }
        }
        return null;
    }

    /** Turns an id from the URL back into a file, refusing anything outside the music folder. */
    static Path resolve(String id) {
        try {
            String rel = new String(Base64.getUrlDecoder().decode(id), StandardCharsets.UTF_8);
            Path p     = musicDir.resolve(rel).normalize();
            return p.startsWith(musicDir) && Files.isRegularFile(p) && AUDIO.contains(ext(p)) ? p : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ----------------------------------------------------------------- helpers

    static String ext(Path p) {
        String n = p.getFileName().toString();
        int d = n.lastIndexOf('.');
        return d < 0 ? "" : n.substring(d + 1).toLowerCase();
    }

    static String baseName(Path p) {
        String n = p.getFileName().toString();
        int d = n.lastIndexOf('.');
        return d > 0 ? n.substring(0, d) : n;
    }

    static String json(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"'  -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default   -> { if (c < 0x20) b.append(String.format("\\u%04x", (int) c)); else b.append(c); }
            }
        }
        return b.append('"').toString();
    }

    static List<String> lanAddresses() {
        List<String> out = new ArrayList<>();
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses()))
                    if (a instanceof Inet4Address && a.isSiteLocalAddress()) out.add(a.getHostAddress());
            }
        } catch (SocketException ignored) { }
        return out;
    }
}
