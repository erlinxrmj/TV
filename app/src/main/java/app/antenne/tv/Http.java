package app.antenne.tv;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/** Netzwerk: ein gemeinsamer Client mit IPTV-freundlichem User-Agent. */
public final class Http {

    private static OkHttpClient client;

    private Http() {}

    public static synchronized OkHttpClient client() {
        if (client == null) {
            client = new OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .retryOnConnectionFailure(true)
                    .build();
        }
        return client;
    }

    private static Request req(String url) {
        return new Request.Builder()
                .url(url)
                .header("User-Agent", Prefs.userAgent())
                .header("Accept", "*/*")
                .build();
    }

    public static String get(String url) throws IOException {
        try (Response r = client().newCall(req(url)).execute()) {
            if (!r.isSuccessful()) throw new IOException("HTTP " + r.code());
            ResponseBody b = r.body();
            return b == null ? "" : b.string();
        }
    }

    /** Lädt in eine Datei (zuerst .tmp, dann umbenennen, damit der Cache nie halb ist). */
    public static void download(String url, File target) throws IOException {
        File tmp = new File(target.getPath() + ".tmp");
        try (Response r = client().newCall(req(url)).execute()) {
            if (!r.isSuccessful()) throw new IOException("HTTP " + r.code());
            ResponseBody b = r.body();
            if (b == null) throw new IOException("Leere Antwort");
            try (InputStream in = b.byteStream(); OutputStream out = new FileOutputStream(tmp)) {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
        }
        if (target.exists()) target.delete();
        if (!tmp.renameTo(target)) throw new IOException("Cache konnte nicht gespeichert werden");
    }
}
