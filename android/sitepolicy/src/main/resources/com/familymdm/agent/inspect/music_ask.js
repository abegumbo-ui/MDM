// YouTube Music icon, "Only what I allow": every song, album, artist and playlist in the search results and on artist / album pages gets an
// "Ask" button under it (titles stay; pictures of what is not approved are hidden by music_clean.js). It sends the request to the parent
// without leaving the page. window.__mdmApprovedIds (set by the shelf) lists the songs that are already approved: they need no button.
(function () {
  if (window.__mdmMusicAsk) return;
  window.__mdmMusicAsk = true;
  var css = '.mdm-ask{display:inline-block;margin:6px 8px 6px 12px;padding:7px 16px;border-radius:18px;background:#4F46E5;color:#fff;font:500 13px Roboto,Arial,sans-serif;border:0;cursor:pointer}' +
    '.mdm-ask.sent{background:#3a3a3a;color:#aaa}.mdm-askrow{display:block}';
  var ITEMS = 'ytmusic-responsive-list-item-renderer, ytmusic-two-row-item-renderer, ytmusic-card-shelf-renderer';
  var LINKS = 'a[href*="watch?v="], a[href*="/browse/"], a[href*="/channel/"], a[href*="playlist?list="]';
  function el(tag, cls, text) { var e = document.createElement(tag); if (cls) e.className = cls; if (text) e.textContent = text; return e; }
  // The address that identifies the item, with the page's tracking left out, and what kind of item it is.
  function identify(href) {
    var m;
    if ((m = /[?&]v=([A-Za-z0-9_-]{11})/.exec(href))) return { url: 'https://music.youtube.com/watch?v=' + m[1], kind: 'song', id: m[1] };
    if ((m = /\/channel\/(UC[A-Za-z0-9_-]{22})/.exec(href))) return { url: 'https://music.youtube.com/channel/' + m[1], kind: 'artist' };
    if ((m = /\/browse\/(MPRE[A-Za-z0-9_-]+)/.exec(href))) return { url: 'https://music.youtube.com/browse/' + m[1], kind: 'album' };
    if ((m = /[?&]list=([A-Za-z0-9_-]{10,})/.exec(href)) && /playlist/.test(href)) return { url: 'https://music.youtube.com/playlist?list=' + m[1], kind: 'playlist' };
    return null;
  }
  function titleOf(item, a) {
    var t = item.querySelector('yt-formatted-string.title, .title, yt-formatted-string');
    var s = (t && t.textContent) || (a && a.getAttribute && a.getAttribute('title')) || (item.getAttribute && item.getAttribute('aria-label')) || '';
    return String(s).replace(/\s+/g, ' ').trim().slice(0, 120);
  }
  function approved(id) {
    var ids = window.__mdmApprovedIds || [];
    for (var i = 0; i < ids.length; i++) if (ids[i] === id) return true;
    return false;
  }
  function sweep() {
    var items = document.querySelectorAll(ITEMS);
    for (var i = 0; i < items.length; i++) {
      var item = items[i];
      if (item.getAttribute('data-mdm-ask')) continue;
      // The top result card holds the artist (or album) and a few of its songs: its own button is for the artist / album, the songs have theirs.
      var isCard = String(item.tagName).toLowerCase() === 'ytmusic-card-shelf-renderer';
      var a = isCard ? item.querySelector('a[href*="/channel/"], a[href*="/browse/"]') : item.querySelector(LINKS);
      if (!a) continue;
      item.setAttribute('data-mdm-ask', '1');
      var what = identify(a.href || '');
      if (!what || (what.kind === 'song' && approved(what.id))) continue;
      var title = titleOf(item, a);
      var label = what.kind === 'song' ? 'Ask for this song' : what.kind === 'artist' ? 'Ask for this artist' : what.kind === 'album' ? 'Ask for this album' : 'Ask for this playlist';
      var btn = el('button', 'mdm-ask', label);
      btn.onclick = function (what, title, btn) {
        return function (e) {
          if (e && e.preventDefault) { e.preventDefault(); e.stopPropagation(); }
          if (btn.className.indexOf('sent') >= 0) return;
          btn.className += ' sent';
          btn.textContent = 'Asked ✓';
          location.href = 'mdmguard://request?u=' + encodeURIComponent(what.url) + '&note=' + encodeURIComponent('Asked from YouTube Music: ' + title) + '&stay=1';
        };
      }(what, title, btn);
      if (String(item.tagName).toLowerCase() === 'ytmusic-responsive-list-item-renderer' && item.parentNode) {
        var row = el('div', 'mdm-askrow'); row.appendChild(btn);
        item.parentNode.insertBefore(row, item.nextSibling);
      } else item.appendChild(btn);
    }
  }
  var st = el('style'); st.textContent = css; (document.head || document.documentElement).appendChild(st);
  sweep();
  setInterval(sweep, 800);
})();
