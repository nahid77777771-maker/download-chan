/* Download-Chan — UI + download orchestration */
(function () {
  "use strict";

  var APP_VERSION = "1.0.0";
  var SITES = [
    { name: "YouTube", url: "https://m.youtube.com", color: "#ff0033" },
    { name: "TikTok", url: "https://www.tiktok.com", color: "#25f4ee" },
    { name: "Instagram", url: "https://www.instagram.com", color: "#e1306c" },
    { name: "Facebook", url: "https://m.facebook.com", color: "#1877f2" },
    { name: "Douyin", url: "https://www.douyin.com", color: "#fe2c55" },
    { name: "X / Twitter", url: "https://mobile.twitter.com", color: "#eef0ff" },
    { name: "Vimeo", url: "https://vimeo.com", color: "#1ab7ea" },
    { name: "Reddit", url: "https://www.reddit.com", color: "#ff4500" }
  ];

  var settings = { mode: "video", quality: "best" };
  var activeJob = null;
  var pending = [];
  var downloadDir = "";

  var $ = function (sel) { return document.querySelector(sel); };
  var $$ = function (sel) { return Array.prototype.slice.call(document.querySelectorAll(sel)); };

  /* ---------------- toast ---------------- */
  var toastTimer;
  function toast(msg) {
    var t = $("#toast");
    t.textContent = msg;
    t.classList.add("show");
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () { t.classList.remove("show"); }, 2600);
  }

  /* ---------------- helpers ---------------- */
  function fmtBytes(b) {
    if (!b && b !== 0) return "";
    if (b < 1024) return b + " B";
    if (b < 1048576) return (b / 1024).toFixed(0) + " KB";
    if (b < 1073741824) return (b / 1048576).toFixed(1) + " MB";
    return (b / 1073741824).toFixed(2) + " GB";
  }
  function fmtDate(ms) {
    try { return new Date(ms).toLocaleString(); } catch (e) { return ""; }
  }
  function esc(s) {
    return String(s == null ? "" : s)
      .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;");
  }
  function firstUrl(text) {
    if (!text) return "";
    var m = String(text).match(/https?:\/\/[^\s"'<>]+/i);
    return m ? m[0] : String(text).trim();
  }
  function shortUrl(u) {
    try { var x = new URL(u); return x.hostname + (x.pathname.length > 1 ? x.pathname.slice(0, 26) + "…" : ""); }
    catch (e) { return u.length > 42 ? u.slice(0, 42) + "…" : u; }
  }

  /* ---------------- tabs ---------------- */
  function switchTab(name) {
    $$(".tab").forEach(function (t) { t.classList.toggle("active", t.id === "tab-" + name); });
    $$(".tabbtn").forEach(function (b) { b.classList.toggle("active", b.dataset.tab === name); });
  }
  $$(".tabbtn").forEach(function (btn) {
    btn.addEventListener("click", function () { switchTab(btn.dataset.tab); });
  });

  /* ---------------- settings ---------------- */
  function loadSettings() {
    try {
      var s = JSON.parse(localStorage.getItem("dc_settings") || "{}");
      if (s.mode) settings.mode = s.mode;
      if (s.quality) settings.quality = s.quality;
    } catch (e) {}
    applyModeUI();
    applyDefaultUI();
  }
  function saveSettings() {
    localStorage.setItem("dc_settings", JSON.stringify(settings));
  }
  function applyModeUI() {
    $$("#mode-seg .seg-btn").forEach(function (b) { b.classList.toggle("active", b.dataset.mode === settings.mode); });
    $("#quality-wrap").style.display = settings.mode === "audio" ? "none" : "block";
  }
  function applyDefaultUI() {
    $$("#def-mode-seg .seg-btn").forEach(function (b) { b.classList.toggle("active", b.dataset.defmode === settings.mode); });
    $("#def-quality").value = settings.quality;
  }

  $$("#mode-seg .seg-btn").forEach(function (b) {
    b.addEventListener("click", function () { settings.mode = b.dataset.mode; applyModeUI(); saveSettings(); });
  });
  $("#quality").addEventListener("change", function () { settings.quality = this.value; saveSettings(); });
  $$("#def-mode-seg .seg-btn").forEach(function (b) {
    b.addEventListener("click", function () { settings.mode = b.dataset.defmode; applyModeUI(); applyDefaultUI(); saveSettings(); });
  });
  $("#def-quality").addEventListener("change", function () { settings.quality = this.value; applyDefaultUI(); saveSettings(); });

  /* ---------------- download tab ---------------- */
  $("#paste").addEventListener("click", function () {
    if (navigator.clipboard && navigator.clipboard.readText) {
      navigator.clipboard.readText().then(function (txt) {
        if (txt) { $("#url").value = firstUrl(txt); }
        else toast("Clipboard is empty");
      }).catch(function () { toast("Long-press the box and paste"); });
    } else {
      toast("Long-press the box and paste");
    }
  });
  $("#clear").addEventListener("click", function () { $("#url").value = ""; });
  $("#download").addEventListener("click", function () {
    var url = firstUrl($("#url").value);
    if (!url) { toast("Paste a link first"); switchTab("download"); return; }
    $("#url").value = url;
    startDownload(url);
  });

  /* ---------------- job engine ---------------- */
  function startDownload(url) {
    if (!window.DC.available) { toast("Native engine unavailable"); return; }
    var job = {
      id: "j" + Date.now() + Math.random().toString(36).slice(2, 6),
      url: url,
      mode: settings.mode,
      quality: settings.quality,
      progress: -1,
      status: "queued",
      path: null,
      name: null,
      error: null
    };
    if (activeJob) { pending.push(job); renderJobs(); }
    else runJob(job);
  }

  function runJob(job) {
    activeJob = job;
    job.status = "downloading";
    job.progress = -1;
    renderJobs();

    window.DC.download({ url: job.url, id: job.id, mode: job.mode, quality: job.quality })
      .then(function (res) {
        job.status = "done";
        job.progress = 100;
        if (res && res.path) { job.path = res.path; job.name = res.name; }
        finishActive();
        toast("Saved: " + (job.name || "file"));
        refreshLibrary();
      })
      .catch(function (err) {
        if (job.status === "cancelled") { finishActive(); return; }
        job.status = "error";
        job.error = (err && (err.message || err.errorMessage)) || "Download failed";
        finishActive();
        toast("Failed: " + job.error);
      });
  }

  function finishActive() {
    renderJobs();
    activeJob = null;
    if (pending.length) {
      var next = pending.shift();
      runJob(next);
    } else {
      renderJobs();
    }
  }

  function renderJobs() {
    var a = $("#active"), q = $("#queue");
    a.innerHTML = "";
    q.innerHTML = "";
    if (activeJob) a.appendChild(jobCard(activeJob, true));
    pending.forEach(function (j) { q.appendChild(jobCard(j, false)); });
  }

  function jobCard(job, active) {
    var el = document.createElement("div");
    el.className = "job" + (job.status === "done" ? " ok" : "") + (job.status === "error" ? " err" : "");
    var pct = job.progress < 0 ? 0 : Math.min(100, job.progress);
    var indet = active && job.status === "downloading" && job.progress < 0;
    var status;
    if (job.status === "queued") status = "Queued";
    else if (job.status === "done") status = "Completed";
    else if (job.status === "error") status = job.error;
    else status = pct > 0 ? pct.toFixed(0) + "%" : "Starting…";

    el.innerHTML =
      '<div class="title"><span>' + (job.mode === "audio" ? "Audio" : "Video") + ' · ' + esc(shortUrl(job.url)) +
      '</span></div>' +
      '<div class="bar' + (indet ? " indet" : "") + '"><i style="width:' + pct + '%"></i></div>' +
      '<div class="meta"><span>' + esc(status) + '</span><span>' + (job.status === "downloading" ? "…" : "") + '</span></div>';

    if (active && job.status === "downloading") {
      var acts = document.createElement("div");
      acts.className = "actions";
      var cancel = document.createElement("button");
      cancel.className = "btn danger small";
      cancel.textContent = "Cancel";
      cancel.addEventListener("click", function () {
        job.status = "cancelled";
        window.DC.cancel({ id: job.id }).catch(function () {});
        finishActive();
      });
      acts.appendChild(cancel);
      el.appendChild(acts);
    }
    if (job.status === "done" && job.path) {
      var acts2 = document.createElement("div");
      acts2.className = "actions";
      acts2.appendChild(iconAction("Open", "open", function () { window.DC.openFile({ path: job.path }).catch(function () {}); }));
      acts2.appendChild(iconAction("Share", "share", function () { window.DC.shareFile({ path: job.path }).catch(function () {}); }));
      el.appendChild(acts2);
    }
    return el;
  }

  function iconAction(label, kind, fn) {
    var b = document.createElement("button");
    b.className = "btn ghost small";
    b.textContent = label;
    b.addEventListener("click", fn);
    return b;
  }

  /* ---------------- library ---------------- */
  function refreshLibrary() {
    window.DC.listFiles().then(function (res) {
      downloadDir = (res && res.dir) || downloadDir;
      var files = (res && res.files) || [];
      var box = $("#files");
      box.innerHTML = "";
      $("#folder").textContent = downloadDir || "—";
      if (!files.length) {
        box.innerHTML = '<div class="empty">No downloads yet.<br>Share a link to get started.</div>';
        return;
      }
      files.forEach(function (f) {
        box.appendChild(fileRow(f));
      });
    }).catch(function () {});
  }

  function fileRow(f) {
    var ext = (f.name.split(".").pop() || "").slice(0, 4).toUpperCase();
    var el = document.createElement("div");
    el.className = "file";
    el.innerHTML =
      '<div class="ic">' + esc(ext) + '</div>' +
      '<div class="info"><div class="name">' + esc(f.name) + '</div>' +
      '<div class="sub">' + fmtBytes(f.size) + ' · ' + esc(fmtDate(f.date)) + '</div></div>' +
      '<div class="acts"></div>';
    var acts = el.querySelector(".acts");
    acts.appendChild(svgBtn('<path d="M5 3l14 9-14 9z"/>', function () { window.DC.openFile({ path: f.path }).catch(function () {}); }));
    acts.appendChild(svgBtn('<path d="M12 3v12m0 0l-4-4m4 4l4-4M5 21h14"/>', function () { window.DC.shareFile({ path: f.path }).catch(function () {}); }));
    acts.appendChild(svgBtn('<path d="M4 7h16M9 7V5h6v2m-8 0l1 13h8l1-13"/>', function () {
      if (!confirm("Delete " + f.name + "?")) return;
      window.DC.deleteFile({ path: f.path }).then(refreshLibrary).catch(function () {});
    }));
    return el;
  }

  function svgBtn(path, fn) {
    var b = document.createElement("button");
    b.className = "icon-btn";
    b.innerHTML = '<svg viewBox="0 0 24 24">' + path + '</svg>';
    b.addEventListener("click", fn);
    return b;
  }

  $("#refresh").addEventListener("click", refreshLibrary);

  /* ---------------- browse tab ---------------- */
  (function buildSites() {
    var box = $("#sites");
    SITES.forEach(function (s) {
      var el = document.createElement("div");
      el.className = "site";
      el.innerHTML = '<span class="dot" style="background:' + s.color + '"></span><span>' + esc(s.name) + '</span>';
      el.addEventListener("click", function () {
        if (window.DC.available) window.DC.openExternal({ url: s.url }).catch(function () { window.open(s.url, "_blank"); });
        else window.open(s.url, "_blank");
      });
      box.appendChild(el);
    });
  })();
  $("#jump").addEventListener("click", function () {
    var u = firstUrl($("#jump-url").value);
    if (!u) { toast("Paste a link first"); return; }
    $("#url").value = u;
    $("#jump-url").value = "";
    switchTab("download");
  });

  /* ---------------- settings tab ---------------- */
  $("#appver").textContent = APP_VERSION;
  $("#open-github").addEventListener("click", function () {
    var url = "https://github.com/nahid77777771-maker/download-chan";
    if (window.DC.available) window.DC.openExternal({ url: url }).catch(function () { window.open(url, "_blank"); });
    else window.open(url, "_blank");
  });

  /* ---------------- engine status ---------------- */
  function setBadge(text, cls) {
    var b = $("#engine-badge");
    b.textContent = text;
    b.className = "badge" + (cls ? " " + cls : "");
  }

  function checkReady() {
    window.DC.isReady().then(function (r) {
      if (r && r.ready) { setBadge("ready", "ok"); $("#engine-detail").textContent = "yt-dlp (bundled)"; }
    });
  }

  function initEngine() {
    if (!window.DC.available) {
      setBadge("web mode", "bad");
      $("#engine-detail").textContent = "unavailable (web)";
      return;
    }
    setBadge("starting…");
    window.DC.init().then(function () {
      setBadge("ready", "ok");
      $("#engine-detail").textContent = "yt-dlp (bundled)";
    }).catch(function (e) {
      setBadge("error", "bad");
      $("#engine-detail").textContent = (e && e.message) || "error";
    });
  }

  /* ---------------- events + bootstrap ---------------- */
  window.DC.on("progress", function (d) {
    if (activeJob && d.id === activeJob.id) {
      activeJob.progress = d.progress;
      var el = $("#active .bar > i");
      var bar = $("#active .bar");
      if (bar) {
        if (d.progress < 0) { bar.classList.add("indet"); }
        else { bar.classList.remove("indet"); if (el) el.style.width = Math.min(100, d.progress) + "%"; }
      }
      var meta = $("#active .meta span");
      if (meta) meta.textContent = d.progress < 0 ? "Starting…" : d.progress.toFixed(0) + "%";
    }
  });

  function handleShared(text) {
    var url = firstUrl(text);
    if (!url) return;
    $("#url").value = url;
    switchTab("download");
    startDownload(url);
  }

  window.DC.on("sharedUrl", function (d) { if (d && d.url) handleShared(d.url); });

  document.addEventListener("visibilitychange", function () {
    if (!document.hidden) {
      checkReady();
      refreshLibrary();
      window.DC.getSharedUrl().then(function (r) { if (r && r.url) handleShared(r.url); }).catch(function () {});
    }
  });

  function boot() {
    loadSettings();
    initEngine();
    refreshLibrary();

    window.DC.getSharedUrl().then(function (r) {
      if (r && r.url) handleShared(r.url);
    }).catch(function () {});

    setInterval(checkReady, 4000);
  }

  boot();
})();
