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
    tidyMenus();
  }

  // Menus. The three-dot menu has no Help, Send feedback, Terms or Premium; the side menu has no Explore or Upgrade; Settings keeps General, Downloads (which opens
  // this app's own Downloads screen) and Language & location, and has no Privacy & data or About. Restricted mode, when the parent forces it, is switched on and cannot
  // be changed there. Only popups, dialogs and the side menu are looked at, so a song that happens to be called "Help" is never touched.
  var ROOTS = 'tp-yt-paper-dialog, ytmusic-dialog, ytmusic-popup-container, ytmusic-menu-popup-renderer, tp-yt-iron-dropdown, ytmusic-settings-page, ytmusic-guide-renderer, tp-yt-app-drawer, ytmusic-pivot-bar-renderer';
  var MENU_GONE = /^(help|send feedback|help (&|and) feedback|terms (&|and) privacy policy|terms of service|privacy policy|get (youtube )?(music )?premium|try (youtube )?(music )?premium|upgrade|explore|open app|open in app|open the app|open youtube music|get the app)$/i;
  var SETTINGS_GONE = /^(privacy (&|and) data|about)$/i;
  function norm(s) { return String(s || '').replace(/\s+/g, ' ').trim(); }
  // The whole row a label sits in: the highest ancestor that holds nothing but that label (its icon and arrow carry no text).
  function rowOf(leaf) {
    var t = norm(leaf.textContent), r = leaf;
    while (r.parentNode && norm(r.parentNode.textContent) === t) r = r.parentNode;
    return r;
  }
  function tagOf(e) { return (e.localName || String(e.tagName || e.tag || '')).toLowerCase(); }
  function tidyMenus() {
    var roots = document.querySelectorAll(ROOTS);
    for (var i = 0; i < roots.length; i++) {
      var inSettings = /dialog|settings/.test(tagOf(roots[i]));
      var leaves = roots[i].querySelectorAll('*');
      for (var j = 0; j < leaves.length; j++) {
        var leaf = leaves[j];
        if (leaf.children && leaf.children.length) continue;
        var t = norm(leaf.textContent);
        if (!t || t.length > 40) continue;
        if (MENU_GONE.test(t) || (inSettings && SETTINGS_GONE.test(t))) { hide(rowOf(leaf)); continue; }
        if (inSettings && /^downloads$/i.test(t)) replaceDownloads(rowOf(leaf));
        else if (inSettings && window.__mdmRestrict && /^restricted mode/i.test(t)) forceRestricted(rowOf(leaf));
      }
    }
  }
  // YouTube Music's own "Downloads" settings row is replaced by a link to this app's Downloads screen (the songs downloaded here, Play and Delete).
  function replaceDownloads(row) {
    if (!row || row.__mdmDl || !row.parentNode) return;
    var before = row.previousSibling;
    if (before && before.id === 'mdm-dl-row') { row.__mdmDl = true; hide(row); return; }
    row.__mdmDl = true;
    var a = document.createElement('a');
    a.id = 'mdm-dl-row';
    a.href = 'mdmguard://downloads';
    a.textContent = 'Downloads';
    a.style.cssText = 'display:flex;justify-content:space-between;align-items:center;padding:16px;font:400 16px Roboto,Arial,sans-serif;color:inherit;text-decoration:none;cursor:pointer';
    var arrow = document.createElement('span');
    arrow.textContent = '\u203A';
    arrow.style.cssText = 'font-size:24px;opacity:.8';
    a.appendChild(arrow);
    row.parentNode.insertBefore(a, row);
    hide(row);
  }
  function forceRestricted(row) {
    if (!row) return;
    for (var holder = row, n = 0; holder && n < 4; holder = holder.parentNode, n++) {
      var sw = holder.querySelector('tp-yt-paper-toggle-button, [role=switch], input');
      if (!sw) continue;
      if (!sw.checked && sw.getAttribute('aria-pressed') !== 'true' && !sw.__mdmSet) { sw.__mdmSet = true; if (sw.click) sw.click(); }
      break;
    }
    if (row.style) row.style.setProperty('pointer-events', 'none', 'important');
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
