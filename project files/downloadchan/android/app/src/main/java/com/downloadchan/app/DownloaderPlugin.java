package com.downloadchan.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import android.webkit.MimeTypeMap;

import androidx.core.content.FileProvider;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLException;
import com.yausername.youtubedl_android.YoutubeDLRequest;
import com.yausername.youtubedl_android.YoutubeDLResponse;
import com.yausername.youtubedl_android.mapper.VideoInfo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import kotlin.Unit;
import kotlin.jvm.functions.Function3;

@CapacitorPlugin(name = "Downloader")
public class DownloaderPlugin extends Plugin {

    public static final String DIR_NAME = "Download-Chan";

    private static volatile String pendingShare = null;
    private static volatile DownloaderPlugin instance = null;

    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);
    private static final Object INIT_LOCK = new Object();

    @Override
    public void load() {
        instance = this;
    }

    public static void setPendingShare(String url) {
        pendingShare = url;
    }

    /* ---- helpers ------------------------------------------------------- */

    private File downloadDir() {
        File dir = new File(getContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), DIR_NAME);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private void ensureInit() throws YoutubeDLException {
        if (INITIALIZED.get()) return;
        synchronized (INIT_LOCK) {
            if (INITIALIZED.get()) return;
            YoutubeDL.getInstance().init(getContext());
            INITIALIZED.set(true);
        }
    }

    private void runAsync(final PluginCall call, final ThrowingRunnable task) {
        new Thread(() -> {
            try {
                task.run();
            } catch (Throwable t) {
                Log.e("DownloaderPlugin", "task failed", t);
                call.reject(fullMessage(t));
            }
        }).start();
    }

    static String fullMessage(Throwable t) {
        StringBuilder sb = new StringBuilder();
        Throwable c = t;
        int depth = 0;
        while (c != null && depth < 6) {
            if (sb.length() > 0) sb.append("  <-  ");
            sb.append(c.getClass().getSimpleName()).append(": ").append(c.getMessage());
            c = c.getCause();
            depth++;
        }
        return sb.toString();
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static String mimeFor(File f) {
        String name = f.getName();
        int dot = name.lastIndexOf('.');
        if (dot >= 0) {
            String ext = name.substring(dot + 1).toLowerCase();
            String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
            if (mime != null) return mime;
        }
        return "application/octet-stream";
    }

    private static List<File> mediaFiles(File dir) {
        File[] all = dir.listFiles();
        List<File> out = new ArrayList<>();
        if (all == null) return out;
        for (File f : all) {
            if (!f.isFile()) continue;
            String n = f.getName().toLowerCase();
            if (n.endsWith(".part") || n.endsWith(".ytdl") || n.endsWith(".tmp")) continue;
            out.add(f);
        }
        out.sort((a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        return out;
    }

    /* ---- plugin methods ------------------------------------------------ */

    @PluginMethod
    public void isReady(PluginCall call) {
        JSObject r = new JSObject();
        r.put("ready", INITIALIZED.get());
        call.resolve(r);
    }

    @PluginMethod
    public void init(final PluginCall call) {
        runAsync(call, () -> {
            ensureInit();
            JSObject r = new JSObject();
            r.put("ready", true);
            call.resolve(r);
        });
    }

    @PluginMethod
    public void updateEngine(final PluginCall call) {
        runAsync(call, () -> {
            ensureInit();
            JSObject r = new JSObject();
            try {
                YoutubeDL.UpdateStatus status = YoutubeDL.getInstance()
                        .updateYoutubeDL(getContext(), YoutubeDL.UpdateChannel.STABLE.INSTANCE);
                r.put("updated", status == YoutubeDL.UpdateStatus.DONE);
                r.put("status", String.valueOf(status));
            } catch (Throwable t) {
                r.put("updated", false);
                r.put("status", "error");
                r.put("message", fullMessage(t));
            }
            call.resolve(r);
        });
    }

    @PluginMethod
    public void getSharedUrl(PluginCall call) {
        String url = pendingShare;
        pendingShare = null;
        JSObject r = new JSObject();
        r.put("url", url == null ? "" : url);
        call.resolve(r);
    }

    @PluginMethod
    public void getInfo(final PluginCall call) {
        final String url = call.getString("url");
        if (url == null || url.isEmpty()) {
            call.reject("url required");
            return;
        }
        runAsync(call, () -> {
            ensureInit();
            YoutubeDLRequest request = new YoutubeDLRequest(url);
            request.addOption("--no-playlist");
            VideoInfo info = YoutubeDL.getInstance().getInfo(request);
            JSObject r = new JSObject();
            r.put("title", info.getTitle() == null ? info.getFulltitle() : info.getTitle());
            r.put("thumbnail", info.getThumbnail());
            r.put("duration", info.getDuration());
            r.put("uploader", info.getUploader());
            r.put("extractor", info.getExtractor());
            call.resolve(r);
        });
    }

    /* Build a format selector that prefers widely-compatible H.264 mp4 video
       + m4a audio (plays everywhere, remuxes reliably), with graceful fallback
       to whatever is best available. */
    private static String videoFormat(String quality) {
        String cap;
        switch (quality) {
            case "1080": cap = "[height<=1080]"; break;
            case "720":  cap = "[height<=720]";  break;
            case "480":  cap = "[height<=480]";  break;
            case "360":  cap = "[height<=360]";  break;
            default:     cap = "";               break;
        }
        if (cap.isEmpty()) {
            return "bv*[ext=mp4]+ba[ext=m4a]/b[ext=mp4]/bv*+ba/b";
        }
        return "bv*" + cap + "[ext=mp4]+ba[ext=m4a]/b" + cap + "[ext=mp4]/bv*" + cap + "+ba/b" + cap;
    }

    private static void addCommon(YoutubeDLRequest request, String outputTemplate) {
        request.addOption("-o", outputTemplate);
        request.addOption("--no-playlist");
        request.addOption("--newline");
        request.addOption("--no-mtime");
        request.addOption("--retries", 10);
        request.addOption("--fragment-retries", 10);
        request.addOption("--socket-timeout", 30);
        // Force the player client that reliably exposes a directly-downloadable
        // stream URL (avoids the SABR/403 paths) and silences the JS-runtime warning.
        request.addOption("--extractor-args", "youtube:player_client=default");
    }

    private YoutubeDLResponse execDownload(String url, String outputTemplate, String mode,
                                           String quality, boolean retry, String id,
                                           Function3<Float, Long, String, Unit> cb) throws Exception {
        YoutubeDLRequest request = new YoutubeDLRequest(url);
        addCommon(request, outputTemplate);
        if (retry) {
            // YouTube's anti-bot can 403 a flagged connection; retrying over
            // IPv4 with the android client often gets a working URL.
            request.addOption("--force-ipv4");
            request.addOption("--extractor-args", "youtube:player_client=android,default");
        }
        if ("audio".equals(mode)) {
            request.addOption("-f", "ba/b");
            request.addOption("-x");
            request.addOption("--audio-format", "mp3");
            request.addOption("--audio-quality", "0");
        } else {
            request.addOption("-f", videoFormat(quality));
            request.addOption("--merge-output-format", "mp4");
        }
        return YoutubeDL.getInstance().execute(request, id, cb);
    }

    @PluginMethod
    public void download(final PluginCall call) {
        final String url = call.getString("url");
        if (url == null || url.trim().isEmpty()) {
            call.reject("url required");
            return;
        }
        final String id = call.getString("id", String.valueOf(System.currentTimeMillis()));
        final String mode = call.getString("mode", "video");
        final String quality = call.getString("quality", "best");
        final File dir = downloadDir();
        final String outputTemplate = dir.getAbsolutePath() + "/%(title).100s [%(id)s].%(ext)s";

        runAsync(call, () -> {
            ensureInit();

            final long[] lastEmit = {0L};
            Function3<Float, Long, String, Unit> cb = (progress, eta, line) -> {
                long now = System.currentTimeMillis();
                if (now - lastEmit[0] >= 250 || progress >= 100 || progress < 0) {
                    lastEmit[0] = now;
                    JSObject d = new JSObject();
                    d.put("id", id);
                    d.put("progress", (double) progress);
                    d.put("eta", eta);
                    d.put("line", line == null ? "" : line);
                    emit("progress", d);
                }
                return Unit.INSTANCE;
            };

            YoutubeDLResponse response = null;
            Exception failure = null;
            try {
                response = execDownload(url, outputTemplate, mode, quality, false, id, cb);
            } catch (YoutubeDL.CanceledException ce) {
                emitCancelled(id);
                call.reject("cancelled");
                return;
            } catch (Exception e) {
                Log.w("DownloaderPlugin", "download attempt 1 failed: " + fullMessage(e));
                try {
                    response = execDownload(url, outputTemplate, mode, quality, true, id, cb);
                } catch (YoutubeDL.CanceledException ce) {
                    emitCancelled(id);
                    call.reject("cancelled");
                    return;
                } catch (Exception e2) {
                    failure = e2;
                }
            }

            if (failure != null) {
                Log.e("DownloaderPlugin", "download failed", failure);
                String msg = fullMessage(failure);
                JSObject d = new JSObject();
                d.put("id", id);
                d.put("message", msg);
                emit("error", d);
                call.reject(msg);
                return;
            }

            File result = null;
            List<File> files = mediaFiles(dir);
            if (!files.isEmpty()) result = files.get(0);

            JSObject d = new JSObject();
            d.put("id", id);
            d.put("success", true);
            d.put("exitCode", response.getExitCode());
            if (result != null) {
                d.put("path", result.getAbsolutePath());
                d.put("name", result.getName());
                d.put("size", result.length());
            }
            emit("completed", d);
            call.resolve(d);
        });
    }

    private void emitCancelled(final String id) {
        JSObject d = new JSObject();
        d.put("id", id);
        d.put("cancelled", true);
        emit("cancelled", d);
    }

    @PluginMethod
    public void cancel(final PluginCall call) {
        final String id = call.getString("id");
        JSObject r = new JSObject();
        if (id != null && YoutubeDL.getInstance().destroyProcessById(id)) {
            r.put("cancelled", true);
        } else {
            r.put("cancelled", false);
        }
        call.resolve(r);
    }

    @PluginMethod
    public void listFiles(PluginCall call) {
        JSArray arr = new JSArray();
        for (File f : mediaFiles(downloadDir())) {
            JSObject o = new JSObject();
            o.put("name", f.getName());
            o.put("path", f.getAbsolutePath());
            o.put("size", f.length());
            o.put("date", f.lastModified());
            o.put("mime", mimeFor(f));
            arr.put(o);
        }
        JSObject r = new JSObject();
        r.put("files", arr);
        r.put("dir", downloadDir().getAbsolutePath());
        call.resolve(r);
    }

    @PluginMethod
    public void deleteFile(final PluginCall call) {
        final String path = call.getString("path");
        if (path == null) {
            call.reject("path required");
            return;
        }
        try {
            File f = new File(path);
            File dir = downloadDir();
            if (!f.getCanonicalPath().startsWith(dir.getCanonicalPath())) {
                call.reject("refused: outside download dir");
                return;
            }
            boolean ok = f.exists() && f.delete();
            JSObject r = new JSObject();
            r.put("deleted", ok);
            call.resolve(r);
        } catch (Exception e) {
            call.reject(e.getMessage());
        }
    }

    @PluginMethod
    public void openFile(final PluginCall call) {
        final String path = call.getString("path");
        if (path == null) {
            call.reject("path required");
            return;
        }
        try {
            File f = new File(path);
            Uri uri = FileProvider.getUriForFile(getContext(), getContext().getPackageName() + ".fileprovider", f);
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, mimeFor(f));
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(i);
            call.resolve();
        } catch (Exception e) {
            call.reject(e.getMessage());
        }
    }

    @PluginMethod
    public void shareFile(final PluginCall call) {
        final String path = call.getString("path");
        if (path == null) {
            call.reject("path required");
            return;
        }
        try {
            File f = new File(path);
            Uri uri = FileProvider.getUriForFile(getContext(), getContext().getPackageName() + ".fileprovider", f);
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType(mimeFor(f));
            i.putExtra(Intent.EXTRA_STREAM, uri);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Intent chooser = Intent.createChooser(i, "Share");
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(chooser);
            call.resolve();
        } catch (Exception e) {
            call.reject(e.getMessage());
        }
    }

    @PluginMethod
    public void openExternal(final PluginCall call) {
        final String url = call.getString("url");
        if (url == null || url.isEmpty()) {
            call.reject("url required");
            return;
        }
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(i);
            call.resolve();
        } catch (Exception e) {
            call.reject(e.getMessage());
        }
    }

    /* ---- remote resolver (online fallback) ----------------------------- */

    private static final String DEFAULT_RESOLVER = "https://gendownload.com";
    private static final String UA =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile Safari/537.36";

    private static int labelHeight(String label) {
        if (label == null) return -1;
        String digits = label.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return -1;
        try { return Integer.parseInt(digits); } catch (Exception e) { return -1; }
    }

    private static String sanitize(String s) {
        if (s == null || s.trim().isEmpty()) s = "download";
        s = s.replaceAll("[\\\\/:*?\"<>|]", "_").replaceAll("\\s+", " ").trim();
        if (s.length() > 80) s = s.substring(0, 80).trim();
        return s.isEmpty() ? "download" : s;
    }

    private static String readAll(InputStream in) throws IOException {
        if (in == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    private JSONObject resolveRemote(String resolver, String url) throws Exception {
        URL endpoint = new URL(resolver.replaceAll("/+$", "") + "/api/extract");
        HttpURLConnection conn = (HttpURLConnection) endpoint.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(45000);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("User-Agent", UA);
        byte[] body = ("{\"url\":" + JSONObject.quote(url) + "}").getBytes("UTF-8");
        try (OutputStream os = conn.getOutputStream()) { os.write(body); }
        int code = conn.getResponseCode();
        String resp = readAll(code >= 400 ? conn.getErrorStream() : conn.getInputStream());
        conn.disconnect();
        if (code < 200 || code >= 300) {
            throw new IOException("resolver HTTP " + code + ": "
                    + (resp.length() > 200 ? resp.substring(0, 200) : resp));
        }
        return new JSONObject(resp);
    }

    /** Pick the best matching format from the resolver's JSON. */
    private static JSONObject pickRemote(JSONObject info, String mode, String quality) throws Exception {
        JSONArray formats = info.optJSONArray("formats");
        if (formats == null || formats.length() == 0) {
            throw new Exception("resolver returned no formats");
        }
        JSONObject audioFallback = null;
        JSONObject videoBest = null;
        int videoBestH = -1;
        int videoAnyBestH = -1;
        JSONObject videoAnyBest = null;
        int wantH = labelHeight(quality);

        for (int i = 0; i < formats.length(); i++) {
            JSONObject f = formats.getJSONObject(i);
            String type = f.optString("type", "");
            if ("audio".equals(type)) {
                if (audioFallback == null || "mp3".equalsIgnoreCase(f.optString("ext"))) audioFallback = f;
                continue;
            }
            if (!"video".equals(type)) continue;
            int h = labelHeight(f.optString("label", ""));
            if (h > videoAnyBestH) { videoAnyBestH = h; videoAnyBest = f; }
            if (wantH <= 0 || h <= wantH) {
                if (h > videoBestH) { videoBestH = h; videoBest = f; }
            }
        }

        if ("audio".equals(mode)) {
            if (audioFallback == null) throw new Exception("no audio format available");
            return audioFallback;
        }
        if (videoBest != null) return videoBest;
        if (videoAnyBest != null) return videoAnyBest;
        throw new Exception("no video format available");
    }

    private void emitProgress(final String id, final double pct, final long eta) {
        JSObject d = new JSObject();
        d.put("id", id);
        d.put("progress", pct);
        d.put("eta", eta);
        d.put("line", "");
        emit("progress", d);
    }

    private void downloadHttp(String mediaUrl, File out, String id) throws Exception {
        File part = new File(out.getAbsolutePath() + ".part");
        HttpURLConnection conn = (HttpURLConnection) new URL(mediaUrl).openConnection();
        conn.setInstanceFollowRedirects(true);
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(60000);
        conn.setRequestProperty("User-Agent", UA);
        conn.setRequestProperty("Accept", "*/*");
        conn.connect();
        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) {
            conn.disconnect();
            throw new IOException("HTTP " + code + " downloading media");
        }
        long total = conn.getContentLengthLong();
        byte[] buf = new byte[65536];
        long got = 0, last = 0;
        try (InputStream in = conn.getInputStream();
             OutputStream fos = new FileOutputStream(part)) {
            int n;
            while ((n = in.read(buf)) > 0) {
                fos.write(buf, 0, n);
                got += n;
                long now = System.currentTimeMillis();
                if (now - last >= 250) {
                    last = now;
                    emitProgress(id, total > 0 ? (got * 100.0 / total) : -1, 0);
                }
            }
        } finally {
            conn.disconnect();
        }
        if (!part.renameTo(out)) {
            try (InputStream in = new java.io.FileInputStream(part);
                 OutputStream fos = new FileOutputStream(out)) {
                byte[] b = new byte[65536];
                int n;
                while ((n = in.read(b)) > 0) fos.write(b, 0, n);
            }
            part.delete();
        }
    }

    @PluginMethod
    public void downloadRemote(final PluginCall call) {
        final String url = call.getString("url");
        if (url == null || url.trim().isEmpty()) {
            call.reject("url required");
            return;
        }
        final String id = call.getString("id", String.valueOf(System.currentTimeMillis()));
        final String mode = call.getString("mode", "video");
        final String quality = call.getString("quality", "best");
        final String resolver = call.getString("resolver", DEFAULT_RESOLVER);
        final File dir = downloadDir();

        runAsync(call, () -> {
            JSONObject info = resolveRemote(resolver, url);
            JSONObject chosen = pickRemote(info, mode, quality);
            String mediaUrl = chosen.getString("url");
            String ext = chosen.optString("ext", "audio".equals(mode) ? "mp3" : "mp4");
            String title = sanitize(info.optString("title", "download"));
            File out = new File(dir, title + " [" + id + "]." + ext);

            downloadHttp(mediaUrl, out, id);

            JSObject d = new JSObject();
            d.put("id", id);
            d.put("success", true);
            d.put("path", out.getAbsolutePath());
            d.put("name", out.getName());
            d.put("size", out.length());
            emit("completed", d);
            call.resolve(d);
        });
    }

    /* ---- event emission ------------------------------------------------ */

    private void emit(final String event, final JSObject data) {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(() -> {
            if (getActivity() != null) notifyListeners(event, data);
        });
    }

    public static void emitShareToJs(final String url) {
        DownloaderPlugin p = instance;
        if (p == null) return;
        JSObject d = new JSObject();
        d.put("url", url);
        p.emit("sharedUrl", d);
    }
}
