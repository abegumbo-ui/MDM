// Videos that may not be watched are taken out of the page (nothing is shown in their place).
//  "channels" (Only what I allow): every video that is not on the list and not from an allowed
//    channel, on every YouTube page: beside a video, in search, on channel pages, in Shorts,
//    playlists; channel tiles of channels that are not allowed too.
//  "restricted": on a video's own page, every suggested video (only the one playing stays).
// Works from each video's own address, not from YouTube's page layout, so it keeps working when
// YouTube renames its page parts. The family's own panel ("My channels", "My videos") is left alone.
// With guard (Chrome and the other browsers, through Inspection): YouTube changes videos without
// loading a new page, so a video, channel or page that is not allowed is also stopped here: the
// video is paused and the family's "not allowed" screen covers the page. Who made a video is read
// from YouTube's own data (its player answer), never looked up elsewhere.
(function () {
  window.__mdmCoverData = __MDM_DATA__;
  if (window.__mdmCover) return;
  window.__mdmCover = true;

  var SHIELD = 'M12,1L3,5v6c0,5.55 3.84,10.74 9,12 5.16,-1.26 9,-6.45 9,-12L21,5l-9,-4z';
  function logo(color) {
    return 'url("data:image/svg+xml,' + encodeURIComponent("<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24'><path fill='" + color + "' d='" + SHIELD + "'/></svg>") + '")';
  }
  var css = '.mdm-cov{display:none !important}' +
    // Suggestions over and after the video, and "Up next".
    '.ytp-endscreen-content,.ytp-ce-element,.ytp-autonav-endscreen,.ytp-autonav-endscreen-countdown-container,.ytp-suggestion-set,' +
    '.ytp-pause-overlay,.ytp-videowall-still,.ytp-upnext,.ytm-autonav-bar,ytm-autonav-renderer{display:none !important}' +
    '#mdm-block{position:fixed;left:0;top:0;right:0;bottom:0;z-index:2147483647;background:#f4f4fb;display:flex;align-items:center;justify-content:center;padding:24px;box-sizing:border-box;font-family:Roboto,Arial,sans-serif;color:#1d1b20}' +
    '#mdm-block .c{background:#fff;border-radius:20px;padding:28px 22px;max-width:440px;width:100%;box-sizing:border-box;text-align:center;box-shadow:0 6px 24px rgba(0,0,0,.08)}' +
    '#mdm-block .bd{width:64px;height:64px;border-radius:18px;background:#4F46E5;display:flex;align-items:center;justify-content:center;margin:0 auto 16px}' +
    '#mdm-block h1{font-size:22px;margin:0 0 10px}#mdm-block p{color:#5f6368;font-size:15px;line-height:1.5;margin:6px 0}' +
    '#mdm-block a{display:block;margin-top:12px;padding:14px;border-radius:24px;background:#4F46E5;color:#fff;text-decoration:none;font-size:16px;cursor:pointer}' +
    '#mdm-block a.o{background:transparent;color:#4F46E5;border:1px solid #4F46E5}' +
    // Search results that may be asked for: the title stays, the picture does not, and an "Ask for access" button.
    // (with "skin" the pictures stay: the Browser covers the skin in them)
    '.mdm-ask:not(.mdm-skin) img,.mdm-ask:not(.mdm-skin) yt-image,.mdm-ask video,.mdm-ask:not(.mdm-skin) ytm-thumbnail-overlay-time-status-renderer,.mdm-ask:not(.mdm-skin) .video-thumbnail-img{visibility:hidden !important}' +
    '.mdm-ask:not(.mdm-skin) .media-item-thumbnail-container,.mdm-ask:not(.mdm-skin) ytm-thumbnail-cover,.mdm-ask:not(.mdm-skin) a#thumbnail,.mdm-ask:not(.mdm-skin) .compact-media-item-image{background:' + logo('#4F46E5') + ' center/34px 34px no-repeat,#ececf4 !important}' +
    '.mdm-ask-btn{display:inline-block;margin:6px 12px 12px;padding:10px 18px;border-radius:20px;border:0;background:#4F46E5;color:#fff;font:500 14px Roboto,Arial,sans-serif;cursor:pointer}' +
    '@media(prefers-color-scheme:dark){#mdm-block{background:#111318;color:#e6e1e5}#mdm-block .c{background:#1b1d24}#mdm-block p{color:#a8a8b3}}';

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
    return { mode: d.cover, guard: !!d.guard, askSearch: !!d.askSearch, askSkin: d.askPictures === 'skin', v: v, c: c };
  }

  // ---- guard: who made each video, and which @handle each channel id has (from YouTube's own data) ----
  var owners = {}, channelHandles = {}, firstSeen = {};
  function note(r) {
    try {
      if (!r || typeof r !== 'object') return;
      if (r.playerResponse) note(r.playerResponse);
      var vd = r.videoDetails, mf = r.microformat && r.microformat.playerMicroformatRenderer;
      if (vd && vd.videoId) {
        var h = mf && mf.ownerProfileUrl ? handleOf(String(mf.ownerProfileUrl)) : null;
        if (h || owners[vd.videoId] === undefined) owners[vd.videoId] = h || '';
      }
      var cm = r.metadata && r.metadata.channelMetadataRenderer;
      if (cm && cm.externalId) channelHandles[cm.externalId] = cm.vanityChannelUrl ? handleOf(String(cm.vanityChannelUrl)) || '' : '';
    } catch (e) {}
  }
  if ((window.__mdmCoverData || {}).guard) {
    try {
      var parse = JSON.parse;
      JSON.parse = function () { var r = parse.apply(this, arguments); note(r); return r; };
    } catch (e) {}
    try {
      var rjson = Response.prototype.json;
      Response.prototype.json = function () { return rjson.call(this).then(function (r) { note(r); return r; }); };
    } catch (e) {}
  }
  var OWNER = 'ytm-slim-owner-renderer a[href],ytd-video-owner-renderer a[href],#owner a[href],ytm-reel-player-header-renderer a[href],ytm-slim-video-metadata-section-renderer a[href*="/@"]';
  var SHIELD_SVG = "<svg width='34' height='34' viewBox='0 0 24 24'><path fill='#fff' d='" + SHIELD + "'/></svg>";

  /** Null when this page may be shown, otherwise what to say. */
  function pageBlocked(L, here) {
    var p = location.pathname;
    if (p === '/' || p === '') return null;
    if (here.playing) {
      if (L.v[here.playing]) return null;
      note(window.ytInitialPlayerResponse);
      var o = owners[here.playing];
      if (!o) {
        var el = document.querySelector(OWNER);
        var dom = el ? handleOf(el.getAttribute('href') || '') : null;
        if (dom) o = dom;
      }
      if (o && L.c[o]) return null;
      if (o === undefined || o === null || o === '') {
        if (!firstSeen[here.playing]) firstSeen[here.playing] = Date.now();
        if (Date.now() - firstSeen[here.playing] < 4000) return 'Checking this video…';
      }
      return 'This video is not allowed';
    }
    if (here.allowedChannelPage) return null;
    if (/^\/@/.test(p)) return 'This channel is not allowed';
    var cid = /^\/channel\/([A-Za-z0-9_-]+)/.exec(p);
    if (cid) {
      note(window.ytInitialData);
      var ch = channelHandles[cid[1]];
      return ch && L.c[ch] ? null : 'This channel is not allowed';
    }
    return 'This part of YouTube is not available';
  }

  function showBlock(msg) {
    var vids = document.querySelectorAll('video');
    for (var i = 0; i < vids.length; i++) { try { vids[i].pause(); vids[i].muted = true; } catch (e) {} }
    var b = document.getElementById('mdm-block');
    if (!b) {
      b = document.createElement('div');
      b.id = 'mdm-block';
      b.innerHTML = '<div class="c"><div class="bd">' + SHIELD_SVG + '</div><h1></h1><p>Only the YouTube channels and videos that were allowed open here.</p>' +
        '<p>Contact your administrator if you have any questions.</p><a id="mdm-back">Go back</a><a class="o" href="/">Go to YouTube\'s home page</a></div>';
      (document.body || document.documentElement).appendChild(b);
      b.querySelector('#mdm-back').addEventListener('click', function (e) { e.preventDefault(); history.back(); });
    }
    var h1 = b.querySelector('h1');
    if (h1.textContent !== msg) h1.textContent = msg;
  }
  function hideBlock() {
    var b = document.getElementById('mdm-block');
    if (b) b.remove();
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
    if (L.guard && L.mode === 'channels') {
      var msg = pageBlocked(L, here);
      if (msg) showBlock(msg); else hideBlock();
    }
    var decided = new Map(), targets = new Map();
    var links = document.querySelectorAll(VIDEO_LINK);
    for (var i = 0; i < links.length; i++) {
      var a = links[i];
      if (a.closest('#mdm-shelf') || a.closest('#mdm-block')) continue;
      var tile = a.closest(TILE);
      if (!tile && a.closest(PLAYING)) continue;
      tile = tile || a;
      var vid = idOf(a.getAttribute('href') || '');
      var ok = tileAllowed(tile, vid, L, here);
      decided.set(tile, decided.has(tile) ? decided.get(tile) && ok : ok);
      if (vid && !targets.has(tile)) targets.set(tile, 'https://www.youtube.com/watch?v=' + vid);
    }
    if (L.mode === 'channels') {
      var chs = document.querySelectorAll(CHANNEL_TILE);
      for (var j = 0; j < chs.length; j++) {
        var link = chs[j].querySelector('a[href]');
        var h = link ? handleOf(link.getAttribute('href') || '') : null;
        decided.set(chs[j], !!(h && L.c[h]));
        if (h) targets.set(chs[j], 'https://www.youtube.com/' + h);
      }
    }
    // On search results (when asking is allowed) a video that is not allowed can be asked for.
    var askHere = L.askSearch && L.mode === 'channels' && path === '/results';
    decided.forEach(function (ok, t) {
      var u = targets.get(t);
      var ask = askHere && !ok && !!u;
      var cov = !ok && !ask;
      if (t.classList.contains('mdm-cov') !== cov) t.classList.toggle('mdm-cov', cov);
      if (t.classList.contains('mdm-ask') !== ask) t.classList.toggle('mdm-ask', ask);
      var skin = ask && L.askSkin;
      if (t.classList.contains('mdm-skin') !== skin) t.classList.toggle('mdm-skin', skin);
      var b = t.querySelector(':scope > .mdm-ask-btn');
      if (ask) {
        if (!b) {
          b = document.createElement('button');
          b.type = 'button';
          b.className = 'mdm-ask-btn';
          b.textContent = 'Ask for access';
          t.appendChild(b);
        }
        var te = t.querySelector('h3,h4,.media-item-headline,#video-title,[role="heading"]');
        b.setAttribute('data-u', u);
        b.setAttribute('data-t', te ? te.textContent.replace(/\s+/g, ' ').trim().slice(0, 120) : '');
      } else if (b) b.remove();
    });
    // YouTube reuses its tiles for other videos: one no longer showing a video is uncovered.
    var old = document.querySelectorAll('.mdm-cov,.mdm-ask');
    for (var k = 0; k < old.length; k++) if (!decided.has(old[k])) {
      old[k].classList.remove('mdm-cov', 'mdm-ask', 'mdm-skin');
      var ob = old[k].querySelector(':scope > .mdm-ask-btn');
      if (ob) ob.remove();
    }
  }

  // A tap that still reaches a covered tile does nothing.
  // "Ask for access" sends the request (the Browser or the YouTube icon shows "Sent").
  document.addEventListener('click', function (e) {
    var t = e.target && e.target.closest ? e.target : null;
    if (!t) return;
    var btn = t.closest('.mdm-ask-btn');
    if (btn) {
      e.preventDefault();
      e.stopPropagation();
      var title = btn.getAttribute('data-t');
      window.__mdmAsked = btn.getAttribute('data-u');
      location.href = 'mdmguard://request?u=' + encodeURIComponent(btn.getAttribute('data-u')) + '&note=' + encodeURIComponent('Asked from YouTube search' + (title ? ': ' + title : ''));
      return;
    }
    if (t.closest('.mdm-cov') || t.closest('.mdm-ask')) { e.preventDefault(); e.stopPropagation(); }
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
