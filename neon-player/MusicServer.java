import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

/**
 * Neon Player backend. No dependencies, needs JDK 17+.
 *
 *   java MusicServer.java [port] [musicDir] [publicDir]
 *
 * GET /api/tracks   -> JSON list of songs found in musicDir (rescanned on every call)
 * GET /audio/{id}   -> the audio file, with HTTP Range support (needed for seeking and mobile)
 * GET /cover/{id}   -> cover image next to the song (same name, or cover/folder.jpg)
 * GET /...          -> static front end from publicDir
 */
public class MusicServer {

    static final Set<String> AUDIO = Set.of("mp3", "m4a", "aac", "wav", "ogg", "oga", "opus", "flac", "webm");
    static final List<String> IMAGE_EXT = List.of("jpg", "jpeg", "png", "webp");
    static final Map<String, String> MIME = Map.ofEntries(
            Map.entry("mp3", "audio/mpeg"), Map.entry("m4a", "audio/mp4"), Map.entry("aac", "audio/aac"),
            Map.entry("wav", "audio/wav"), Map.entry("ogg", "audio/ogg"), Map.entry("oga", "audio/ogg"),
            Map.entry("opus", "audio/ogg"), Map.entry("flac", "audio/flac"), Map.entry("webm", "audio/webm"),
            Map.entry("jpg", "image/jpeg"), Map.entry("jpeg", "image/jpeg"), Map.entry("png", "image/png"),
            Map.entry("webp", "image/webp"), Map.entry("svg", "image/svg+xml"), Map.entry("ico", "image/x-icon"),
            Map.entry("html", "text/html; charset=utf-8"), Map.entry("css", "text/css; charset=utf-8"),
            Map.entry("js", "text/javascript; charset=utf-8"), Map.entry("json", "application/json; charset=utf-8"));

    record Track(String id, String title, String artist, Path file, Path cover) {}

    static Path musicDir;
    static Path publicDir;

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        Path root = findRoot();
        musicDir = (args.length > 1 ? Path.of(args[1]) : root.resolve("music")).toAbsolutePath().normalize();
        publicDir = (args.length > 2 ? Path.of(args[2]) : root.resolve("public")).toAbsolutePath().normalize();
        Files.createDirectories(musicDir);

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0); // 0.0.0.0, so phones on the same Wi-Fi can connect
        server.createContext("/api/tracks", safe(MusicServer::tracks));
        server.createContext("/audio/", safe(MusicServer::audio));
        server.createContext("/cover/", safe(MusicServer::cover));
        server.createContext("/", safe(MusicServer::staticFile));
        server.setExecutor(Executors.newFixedThreadPool(32));
        server.start();

        System.out.println("Neon Player running");
        System.out.println("  Music folder : " + musicDir);
        System.out.println("  Web files    : " + publicDir + (Files.isRegularFile(publicDir.resolve("index.html")) ? "" : "   <-- index.html NOT FOUND"));
        System.out.println("  This device  : http://localhost:" + port);
        for (String ip : lanAddresses()) System.out.println("  Phone/other  : http://" + ip + ":" + port);
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

    static void audio(HttpExchange ex) throws IOException {
        Path file = resolve(ex.getRequestURI().getPath().substring("/audio/".length()));
        if (file == null) { ex.sendResponseHeaders(404, -1); return; }
        serveFile(ex, file, MIME.get(ext(file)));
    }

    static void cover(HttpExchange ex) throws IOException {
        Path song = resolve(ex.getRequestURI().getPath().substring("/cover/".length()));
        Path img = song == null ? null : findCover(song);
        if (img == null) { ex.sendResponseHeaders(404, -1); return; }
        ex.getResponseHeaders().set("Cache-Control", "public, max-age=3600");
        serveFile(ex, img, MIME.get(ext(img)));
    }

    static void staticFile(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if (path.equals("/")) path = "/index.html";
        Path f = publicDir.resolve(path.substring(1)).normalize();
        if (!f.startsWith(publicDir) || !Files.isRegularFile(f)) {
            String msg = "<h2>404 - file not found</h2><p>The server is looking for web files in:<br><code>" + publicDir
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
                if (p[0].isEmpty()) {                                   // "bytes=-500": last 500 bytes
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
                start = 0; end = size - 1; status = 200;                // malformed header: ignore it
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
            // the browser cancelled the request (normal when seeking or skipping tracks)
        }
    }

    static void send(HttpExchange ex, int status, String type, byte[] body) throws IOException {
        ex.getResponseHeaders().set("Content-Type", type);
        if ("HEAD".equals(ex.getRequestMethod())) { ex.sendResponseHeaders(status, -1); return; }
        ex.sendResponseHeaders(status, body.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(body); }
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
        String rel = musicDir.relativize(p).toString().replace('\\', '/');
        String id = Base64.getUrlEncoder().withoutPadding().encodeToString(rel.getBytes(StandardCharsets.UTF_8));
        String name = baseName(p);
        String artist = "", title = name;
        int sep = name.indexOf(" - ");                       // "Artist - Title.mp3"
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
            Path p = musicDir.resolve(rel).normalize();
            return p.startsWith(musicDir) && Files.isRegularFile(p) && AUDIO.contains(ext(p)) ? p : null;
        } catch (IllegalArgumentException e) { // also covers InvalidPathException
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
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> { if (c < 0x20) b.append(String.format("\\u%04x", (int) c)); else b.append(c); }
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
