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
    // No "Explore" (and no "Upgrade"): Home and Library stay. The downloaded songs are listed under Library (see the shelf).
    var navs = document.querySelectorAll('ytmusic-pivot-bar-item-renderer, ytmusic-guide-entry-renderer, tp-yt-paper-item, ytmusic-nav-bar a');
    for (var n = 0; n < navs.length; n++) {
      var nt = (navs[n].textContent || '').replace(/\s+/g, ' ').trim();
      if (/^(explore|upgrade)$/i.test(nt)) hide(navs[n].closest('ytmusic-pivot-bar-item-renderer,ytmusic-guide-entry-renderer,tp-yt-paper-item') || navs[n]);
    }
    if (window.__mdmNoSignIn) {
      var si = document.querySelectorAll('a[href*="accounts.google.com"], a[href*="ServiceLogin"]');
      for (var s = 0; s < si.length; s++) hide(si[s]);
    }
    tidyMenus();
  }

  // The three-dot menu has no "Help & feedback", "Terms & privacy policy" or "Get YouTube Premium". Settings keeps General and Downloads, and has no
  // "Privacy and data" or "About". Restricted mode, when the parent forces it, is switched on and cannot be switched off from here.
  var MENU_GONE = /^(help (&|and) feedback|terms (&|and) privacy policy|get (youtube )?(music )?premium|try (youtube )?(music )?premium|upgrade)$/i;
  var SETTINGS_GONE = /^(privacy (&|and) data|about)$/i;
  var ROWS = 'ytmusic-menu-navigation-item-renderer,ytmusic-menu-service-item-renderer,ytmusic-menu-popup-renderer tp-yt-paper-item,ytmusic-setting-category-collection-entry-renderer,tp-yt-paper-item,a,button';
  function inSettings() { return /setting/i.test(location.pathname + location.search + location.hash); }
  function tidyMenus() {
    var rows = document.querySelectorAll('ytmusic-menu-navigation-item-renderer,ytmusic-menu-service-item-renderer,ytmusic-setting-category-collection-entry-renderer,tp-yt-paper-item,a');
    var settings = inSettings();
    var general = null;
    for (var i = 0; i < rows.length; i++) {
      var t = (rows[i].textContent || '').replace(/\s+/g, ' ').trim();
      if (!t || t.length > 40) continue;
      if (MENU_GONE.test(t) || (settings && SETTINGS_GONE.test(t))) hide(rows[i].closest(ROWS) || rows[i]);
      else if (settings && /^general$/i.test(t) && !general) general = rows[i];
    }
    if (settings) {
      addDownloadsRow(general);
      if (window.__mdmRestrict) forceRestricted();
    }
  }
  function addDownloadsRow(general) {
    if (!general || document.getElementById('mdm-dl-row')) return;
    var host = general.closest ? (general.closest('ytmusic-setting-category-collection-entry-renderer,tp-yt-paper-item') || general) : general;
    if (!host.parentNode) return;
    var row = document.createElement('a');
    row.id = 'mdm-dl-row';
    row.href = 'mdmguard://downloads';
    row.textContent = 'Downloads';
    row.style.cssText = 'display:block;padding:14px 16px;font:500 16px Roboto,Arial,sans-serif;color:inherit;text-decoration:none';
    host.parentNode.insertBefore(row, host.nextSibling);
  }
  function forceRestricted() {
    var labels = document.querySelectorAll('ytmusic-setting-boolean-renderer, ytmusic-setting-renderer, tp-yt-paper-item');
    for (var i = 0; i < labels.length; i++) {
      var t = (labels[i].textContent || '').replace(/\s+/g, ' ').trim();
      if (!/^restricted mode/i.test(t) || t.length > 160) continue;
      var sw = labels[i].querySelector('tp-yt-paper-toggle-button, [role=switch]');
      if (sw && !sw.checked && sw.getAttribute('aria-pressed') !== 'true' && !sw.__mdmSet) { sw.__mdmSet = true; if (sw.click) sw.click(); }
      if (labels[i].style) labels[i].style.setProperty('pointer-events', 'none', 'important');
    }
  }
  // Song pictures: "allowed" = pictures of items that are not approved (search results, recommendations) are hidden, while the approved songs on our shelf and the song
  // that is playing keep their art; "hide" = no pictures at all, music-video frames included (the sound keeps playing).
  if (window.__mdmPics) {
    var st = document.createElement('style');
    var css = 'img[src*="ytimg.com"],img[src*="ggpht.com"],img[src*="googleusercontent.com"],ytmusic-thumbnail-renderer img{visibility:hidden !important}';
    if (window.__mdmPics === 'hide') css += 'video{visibility:hidden !important}';
    else css += '#mdm-shelf img,#mdm-more img,#song-image img,ytmusic-player #song-image img{visibility:visible !important}';
    st.textContent = css + 'ytmusic-thumbnail-renderer{background:#2a2a2a !important}';
    (document.head || document.documentElement).appendChild(st);
  }
  sweep();
  setInterval(sweep, 800);
})();
