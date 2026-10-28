package hu.blockeater;

import com.sun.net.httpserver.HttpServer;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.security.MessageDigest;

/** Beépített, egyszerű HTTP szerver a resource packhez (opcionális). */
public final class ResourcePackServer {
    private final BlockEaterPlugin plugin;
    private HttpServer server;
    private byte[] data;
    private String sha1 = "";

    public ResourcePackServer(BlockEaterPlugin plugin) { this.plugin = plugin; }

    public String sha1() { return sha1; }

    public boolean start(int port) {
        try {
            File f = new File(plugin.getDataFolder(), "BlockEater-ResourcePack.zip");
            try (InputStream in = plugin.getResource("resourcepack.zip")) {
                if (in == null) throw new IllegalStateException("resourcepack.zip hiányzik a jarból");
                Files.write(f.toPath(), in.readAllBytes());
            }
            data = Files.readAllBytes(f.toPath());
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest(data)) sb.append(String.format("%02x", b));
            sha1 = sb.toString();
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/blockeater.zip", ex -> {
                ex.getResponseHeaders().add("Content-Type", "application/zip");
                ex.sendResponseHeaders(200, data.length);
                try (OutputStream os = ex.getResponseBody()) { os.write(data); }
            });
            server.setExecutor(java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "BlockEater-PackServer");
                t.setDaemon(true);
                return t;
            }));
            server.start();
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("A beépített resource pack szerver nem indult el: " + e.getMessage());
            return false;
        }
    }

    public void stop() {
        if (server != null) { server.stop(0); server = null; }
    }

    public boolean running() { return server != null; }
}
