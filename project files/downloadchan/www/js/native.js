/* Thin wrapper over the native Capacitor "Downloader" plugin.
   On the web build (no Capacitor) most methods are unavailable. */
(function () {
  "use strict";
  var Plugins = (window.Capacitor && window.Capacitor.Plugins) || {};
  var D = Plugins.Downloader || null;

  function missing() {
    return Promise.reject(new Error("Native engine not available in this build"));
  }

  window.DC = {
    available: !!D,
    isReady: function () {
      return D ? D.isReady() : Promise.resolve({ ready: false });
    },
    init: function () {
      return D ? D.init() : missing();
    },
    getSharedUrl: function () {
      return D ? D.getSharedUrl() : Promise.resolve({ url: "" });
    },
    getInfo: function (o) { return D ? D.getInfo(o) : missing(); },
    download: function (o) { return D ? D.download(o) : missing(); },
    downloadRemote: function (o) { return D ? D.downloadRemote(o) : missing(); },
    updateEngine: function () { return D ? D.updateEngine() : missing(); },
    cancel: function (o) { return D ? D.cancel(o) : missing(); },
    listFiles: function () { return D ? D.listFiles() : Promise.resolve({ files: [], dir: "" }); },
    deleteFile: function (o) { return D ? D.deleteFile(o) : missing(); },
    openFile: function (o) { return D ? D.openFile(o) : missing(); },
    shareFile: function (o) { return D ? D.shareFile(o) : missing(); },
    openExternal: function (o) { return D ? D.openExternal(o) : missing(); },
    on: function (event, cb) {
      if (!D) return { remove: function () {} };
      var handle = D.addListener(event, cb);
      return { remove: function () { handle.then(function (h) { h.remove(); }); } };
    }
  };
})();
