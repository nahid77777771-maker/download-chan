package com.downloadchan.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
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

import java.io.File;
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
                String msg = t.getMessage() == null ? t.toString() : t.getMessage();
                call.reject(msg);
            }
        }).start();
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

            YoutubeDLRequest request = new YoutubeDLRequest(url);
            request.addOption("-o", outputTemplate);
            request.addOption("--no-playlist");
            request.addOption("--newline");
            request.addOption("--no-mtime");
            request.addOption("--retries", 5);
            request.addOption("--fragment-retries", 5);

            if ("audio".equals(mode)) {
                request.addOption("-f", "bestaudio/best");
                request.addOption("-x");
                request.addOption("--audio-format", "mp3");
                request.addOption("--audio-quality", "0");
            } else {
                String fmt;
                switch (quality) {
                    case "1080":
                        fmt = "bv*[height<=1080]+ba/b[height<=1080]";
                        break;
                    case "720":
                        fmt = "bv*[height<=720]+ba/b[height<=720]";
                        break;
                    case "480":
                        fmt = "bv*[height<=480]+ba/b[height<=480]";
                        break;
                    case "360":
                        fmt = "bv*[height<=360]+ba/b[height<=360]";
                        break;
                    default:
                        fmt = "bv*+ba/b";
                }
                request.addOption("-f", fmt);
                request.addOption("--merge-output-format", "mp4");
            }

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

            try {
                YoutubeDLResponse response = YoutubeDL.getInstance().execute(request, id, cb);
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
            } catch (YoutubeDL.CanceledException ce) {
                JSObject d = new JSObject();
                d.put("id", id);
                d.put("cancelled", true);
                emit("cancelled", d);
                call.reject("cancelled");
            } catch (Exception e) {
                String msg = e.getMessage() == null ? e.toString() : e.getMessage();
                JSObject d = new JSObject();
                d.put("id", id);
                d.put("message", msg);
                emit("error", d);
                call.reject(msg);
            }
        });
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
