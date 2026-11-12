// Videos that may not be watched are covered with the family logo and cannot be tapped.
//  "channels" (Only what I allow): every video that is not on the list and not from an allowed
//    channel, on every YouTube page: beside a video, in search, on channel pages, in Shorts,
//    playlists; channel tiles of channels that are not allowed too.
//  "restricted": on a video's own page, every suggested video (only the one playing stays).
// Works from each video's own address, not from YouTube's page layout, so it keeps working when
// YouTube renames its page parts. The family's own panel ("My channels", "My videos") is left alone.
(function () {
  window.__mdmCoverData = __MDM_DATA__;
  if (window.__mdmCover) return;
  window.__mdmCover = true;

  var SHIELD = 'M12,1L3,5v6c0,5.55 3.84,10.74 9,12 5.16,-1.26 9,-6.45 9,-12L21,5l-9,-4z';
  function logo(color) {
    return 'url("data:image/svg+xml,' + encodeURIComponent("<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24'><path fill='" + color + "' d='" + SHIELD + "'/></svg>") + '")';
  }
  var css = '.mdm-cov{position:relative !important;pointer-events:none !important;overflow:hidden !important;background:transparent !important;min-height:40px}' +
    '.mdm-cov *{visibility:hidden !important}' +
    '.mdm-cov::after{content:"Not allowed";visibility:visible !important;position:absolute;left:0;top:0;right:0;bottom:0;z-index:9;display:flex;align-items:center;justify-content:center;' +
    'padding-left:34px;box-sizing:border-box;border-radius:12px;background:' + logo('#4F46E5') + ' calc(50% - 42px) 50%/26px 26px no-repeat,#ececf4;color:#5f6368;font:500 13px Roboto,Arial,sans-serif;opacity:1 !important}' +
    'html[dark] .mdm-cov::after,html[darker-dark-theme] .mdm-cov::after{background:' + logo('#6366F1') + ' calc(50% - 42px) 50%/26px 26px no-repeat,#1f1f2a;color:#aaa}' +
    // Suggestions over and after the video, and "Up next".
    '.ytp-endscreen-content,.ytp-ce-element,.ytp-autonav-endscreen,.ytp-autonav-endscreen-countdown-container,.ytp-suggestion-set,' +
    '.ytp-pause-overlay,.ytp-videowall-still,.ytp-upnext,.ytm-autonav-bar,ytm-autonav-renderer{display:none !important}';

  var TILE = ['ytm-video-with-context-renderer', 'ytm-compact-video-renderer', 'ytm-video-card-renderer', 'ytm-media-item',
    'ytm-shorts-lockup-view-model', 'ytm-shorts-lockup-view-model-v2', 'ytm-reel-item-renderer', 'ytm-playlist-video-renderer',
    'ytm-playlist-panel-video-renderer', 'ytm-compact-playlist-renderer', 'ytm-compact-radio-renderer', 'ytm-radio-renderer',
    'ytm-playlist-renderer', 'ytm-compact-autoplay-renderer', 'ytm-endscreen-element-renderer', 'ytm-rich-item-renderer',
    'yt-lockup-view-model', 'ytd-rich-item-renderer', 'ytd-video-renderer', 'ytd-compact-video-renderer', 'ytd-grid-video-renderer',
    'ytd-reel-item-renderer', 'ytd-playlist-video-renderer', 'ytd-playlist-panel-video-renderer', 'ytd-compact-radio-renderer',
    'ytd-compact-playlist-renderer', 'ytd-radio-renderer', 'ytd-playlist-renderer', 'ytd-grid-playlist-renderer',
    'ytd-end-screen-video-renderer'].join(',');
  var CHANNEL_TILE = 'ytm-compact-channel-renderer,ytm-channel-renderer,ytm-grid-channel-renderer,ytd-channel-renderer,ytd-grid-channel-renderer';
  var VIDEO_LINK = 'a[href*="watch?"],a[href*="/shorts/"],a[href*="playlist?list="],a[href*="youtu.be/"]';
  // The video that is playing: its own title and player (and links to its own moments) stay.
  var PLAYING = '#player,ytm-player,#movie_player,.player-container,ytd-player,#player-container-id';

  function idOf(href) {
    var m = /[?&]v=([A-Za-z0-9_-]{11})/.exec(href) || /\/shorts\/([A-Za-z0-9_-]{11})/.exec(href) || /youtu\.be\/([A-Za-z0-9_-]{11})/.exec(href);
    return m ? m[1] : null;
  }
  function handleOf(href) {
    var m = /(?:^|youtube\.com)\/(@[A-Za-z0-9._-]{3,30})(?:[\/?#]|$)/.exec(href);
    return m ? m[1].toLowerCase() : null;
  }
  function otherChannelLink(href) {
    return /(?:^|youtube\.com)\/(channel|c|user)\//.test(href);
  }
  function lists() {
    var d = window.__mdmCoverData || {}, v = {}, c = {};
    (d.videos || []).forEach(function (x) { if (x && x.v) v[x.v] = 1; });
    (d.channels || []).forEach(function (x) { if (x && x.h) c[String(x.h).toLowerCase()] = 1; });
    return { mode: d.cover, v: v, c: c };
  }

  function addCss() {
    if (document.getElementById('mdm-cover-css')) return;
    var st = document.createElement('style');
    st.id = 'mdm-cover-css';
    st.textContent = css;
    (document.head || document.documentElement).appendChild(st);
  }

  /** May this tile (one video) be shown? */
  function tileAllowed(tile, id, L, here) {
    if (id && id === here.playing) return true;
    if (L.mode === 'restricted') return !here.watchPage;
    if (id && L.v[id]) return true;
    var links = tile.tagName === 'A' ? [tile] : tile.querySelectorAll('a[href]');
    var sawChannel = false;
    for (var i = 0; i < links.length; i++) {
      var href = links[i].getAttribute('href') || '';
      var h = handleOf(href);
      if (h) { sawChannel = true; if (L.c[h]) return true; } else if (otherChannelLink(href)) sawChannel = true;
    }
    // On an allowed channel's own pages, its videos do not repeat the channel's link.
    return !sawChannel && here.allowedChannelPage;
  }

  function scan() {
    var L = lists();
    if (L.mode !== 'channels' && L.mode !== 'restricted') return;
    addCss();
    var path = location.pathname;
    var page = /^\/(@[A-Za-z0-9._-]{3,30})/.exec(path);
    var here = {
      playing: idOf(location.href),
      watchPage: path === '/watch' || path.indexOf('/shorts/') === 0,
      allowedChannelPage: !!(page && L.c[page[1].toLowerCase()])
    };
    var decided = new Map();
    var links = document.querySelectorAll(VIDEO_LINK);
    for (var i = 0; i < links.length; i++) {
      var a = links[i];
      if (a.closest('#mdm-shelf')) continue;
      var tile = a.closest(TILE);
      if (!tile && a.closest(PLAYING)) continue;
      tile = tile || a;
      var ok = tileAllowed(tile, idOf(a.getAttribute('href') || ''), L, here);
      decided.set(tile, decided.has(tile) ? decided.get(tile) && ok : ok);
    }
    if (L.mode === 'channels') {
      var chs = document.querySelectorAll(CHANNEL_TILE);
      for (var j = 0; j < chs.length; j++) {
        var link = chs[j].querySelector('a[href]');
        var h = link ? handleOf(link.getAttribute('href') || '') : null;
        decided.set(chs[j], !!(h && L.c[h]));
      }
    }
    decided.forEach(function (ok, t) { if (t.classList.contains('mdm-cov') === ok) t.classList.toggle('mdm-cov', !ok); });
    // YouTube reuses its tiles for other videos: one no longer showing a video is uncovered.
    var old = document.querySelectorAll('.mdm-cov');
    for (var k = 0; k < old.length; k++) if (!decided.has(old[k])) old[k].classList.remove('mdm-cov');
  }

  // A tap that still reaches a covered tile does nothing.
  document.addEventListener('click', function (e) {
    if (e.target && e.target.closest && e.target.closest('.mdm-cov')) { e.preventDefault(); e.stopPropagation(); }
  }, true);

  var queued = false;
  function soon() {
    if (queued) return;
    queued = true;
    setTimeout(function () { queued = false; try { scan(); } catch (e) {} }, 150);
  }
  try { new MutationObserver(soon).observe(document.documentElement, { childList: true, subtree: true, attributes: true, attributeFilter: ['href'] }); } catch (e) {}
  setInterval(function () { try { scan(); } catch (e) {} }, 1000);
  soon();
})();
