// A "Download" button on a YouTube video page (the YouTube icon and the Browser, inside the MDM app).
// It opens mdmguard://download?u=<this page>, which the app answers by starting the download.
(function () {
  if (window.__mdmDownload) return;
  window.__mdmDownload = true;
  function build() {
    var b = document.getElementById('mdm-dl');
    var watch = location.pathname === '/watch' && /[?&]v=[A-Za-z0-9_-]{11}/.test(location.search);
    if (!watch) { if (b) b.remove(); return; }
    if (!b) {
      b = document.createElement('a');
      b.id = 'mdm-dl';
      b.textContent = '\u2B07  Download';
      b.style.cssText = 'position:fixed;right:14px;bottom:76px;z-index:2147483000;padding:12px 18px;border-radius:24px;background:#4F46E5;color:#fff;font:500 15px Roboto,Arial,sans-serif;text-decoration:none;box-shadow:0 2px 8px rgba(0,0,0,.3)';
      (document.body || document.documentElement).appendChild(b);
    }
    b.href = 'mdmguard://download?u=' + encodeURIComponent(location.href);
  }
  build();
  setInterval(build, 700);
})();
