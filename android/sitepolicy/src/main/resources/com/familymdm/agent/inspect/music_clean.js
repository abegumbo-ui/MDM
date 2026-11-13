// YouTube Music icon: only the music. Takes away the "Open app" / "Get the app" prompts and, when the parent has not
// allowed signing in, the "Sign in" button. window.__mdmNoSignIn is set by the app before this runs.
(function () {
  if (window.__mdmMusicClean) return;
  window.__mdmMusicClean = true;
  var BAD = /^(open app|open in app|open the app|get the app|get app|install app|use the app|open youtube music|switch to app|download the app|get youtube music|try the app)$/i;
  function hide(e) {
    if (e && e.style && e.style.display !== 'none') e.style.setProperty('display', 'none', 'important');
  }
  function sweep() {
    var sel = ['ytmusic-mealbar-promo-renderer', 'ytmusic-app-promo-renderer', 'ytmusic-upsell-dialog-renderer', '.mealbar-promo-renderer',
      '[class*="open-app"]', '[id*="open-app"]', '[class*="OpenApp"]', 'a[href*="play.google.com"]', 'a[href^="intent:"]', 'a[href*="music.youtube.com/app"]'];
    for (var i = 0; i < sel.length; i++) {
      var found = document.querySelectorAll(sel[i]);
      for (var k = 0; k < found.length; k++) hide(found[k]);
    }
    var els = document.querySelectorAll('a,button,yt-button-renderer,ytmusic-button-renderer,tp-yt-paper-button,div[role=button],span');
    for (var j = 0; j < els.length; j++) {
      var t = (els[j].textContent || '').replace(/\s+/g, ' ').trim();
      if (!t || t.length > 30) continue;
      if (BAD.test(t)) {
        hide(els[j].closest('ytmusic-mealbar-promo-renderer,ytmusic-button-renderer,yt-button-renderer,a,button') || els[j]);
      } else if (window.__mdmNoSignIn && /^sign in$/i.test(t)) {
        hide(els[j].closest('ytmusic-button-renderer,yt-button-renderer,a,button') || els[j]);
      }
    }
    if (window.__mdmNoSignIn) {
      var si = document.querySelectorAll('a[href*="accounts.google.com"], a[href*="ServiceLogin"]');
      for (var s = 0; s < si.length; s++) hide(si[s]);
    }
  }
  sweep();
  setInterval(sweep, 800);
})();
