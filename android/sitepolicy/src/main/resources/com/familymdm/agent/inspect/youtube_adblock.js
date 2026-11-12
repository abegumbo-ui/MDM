// "Block ads" on YouTube (the family Browser and the YouTube add-on). Runs before YouTube's own
// scripts where the phone allows it. Best effort: YouTube changes its pages from time to time.
//  1. The ad slots are taken out of the data YouTube gives its player and its feeds
//     (adPlacements, playerAds, adSlots and ad items), the way well-known ad blockers do it.
//  2. Ad banners and promoted items are hidden.
//  3. If a video ad still starts, it is muted and skipped to its end.
(function () {
  if (window.__mdmNoAds) return;
  window.__mdmNoAds = true;
  var AD_KEYS = ['adPlacements', 'playerAds', 'adSlots', 'adBreakHeartbeatParams', 'adBreakParams'];
  var AD_ITEMS = ['adSlotRenderer', 'promotedSparklesWebRenderer', 'promotedVideoRenderer', 'compactPromotedVideoRenderer',
    'displayAdRenderer', 'inFeedAdLayoutRenderer', 'adSlotAndLayoutRenderer', 'promotedSparklesTextSearchRenderer', 'searchPyvRenderer'];
  function isAdItem(x) {
    if (!x || typeof x !== 'object') return false;
    for (var i = 0; i < AD_ITEMS.length; i++) if (x[AD_ITEMS[i]]) return true;
    var c = x.richItemRenderer && x.richItemRenderer.content;
    if (c) for (var j = 0; j < AD_ITEMS.length; j++) if (c[AD_ITEMS[j]]) return true;
    return false;
  }
  function prune(o, depth) {
    if (!o || typeof o !== 'object' || depth > 30) return o;
    if (Array.isArray(o)) {
      for (var i = o.length - 1; i >= 0; i--) { if (isAdItem(o[i])) o.splice(i, 1); else prune(o[i], depth + 1); }
      return o;
    }
    for (var k = 0; k < AD_KEYS.length; k++) if (AD_KEYS[k] in o) delete o[AD_KEYS[k]];
    for (var key in o) if (Object.prototype.hasOwnProperty.call(o, key)) prune(o[key], depth + 1);
    return o;
  }
  function looksLikeYoutube(r) {
    return r && typeof r === 'object' && (r.responseContext || r.playerResponse || r.adPlacements || r.playerAds || r.contents || r.onResponseReceivedActions);
  }
  try {
    var parse = JSON.parse;
    JSON.parse = function () { var r = parse.apply(this, arguments); try { if (looksLikeYoutube(r)) prune(r, 0); } catch (e) {} return r; };
  } catch (e) {}
  try {
    var json = Response.prototype.json;
    Response.prototype.json = function () { return json.call(this).then(function (r) { try { if (looksLikeYoutube(r)) prune(r, 0); } catch (e) {} return r; }); };
  } catch (e) {}
  // The first player data and feed come written into the page itself.
  ['ytInitialPlayerResponse', 'ytInitialData'].forEach(function (name) {
    try {
      var value = window[name];
      Object.defineProperty(window, name, {
        configurable: true,
        get: function () { return value; },
        set: function (v) { try { prune(v, 0); } catch (e) {} value = v; }
      });
      if (value) prune(value, 0);
    } catch (e) {}
  });
  var css = 'ytm-promoted-sparkles-web-renderer,ytm-promoted-video-renderer,ytm-companion-ad-renderer,ad-slot-renderer,ytm-ad-slot-renderer,' +
    'ytd-ad-slot-renderer,ytd-in-feed-ad-layout-renderer,ytd-banner-promo-renderer,ytd-promoted-sparkles-web-renderer,ytd-promoted-video-renderer,' +
    'ytd-display-ad-renderer,ytd-companion-slot-renderer,ytd-action-companion-ad-renderer,#masthead-ad,#player-ads,' +
    '.ytp-ad-overlay-container,.ytp-ad-image-overlay,ytm-promoted-sparkles-text-search-renderer{display:none !important}';
  function addCss() {
    if (document.getElementById('mdm-noads')) return;
    var st = document.createElement('style'); st.id = 'mdm-noads'; st.textContent = css;
    (document.head || document.documentElement).appendChild(st);
  }
  setInterval(function () {
    try {
      addCss();
      var ad = document.querySelector('.ad-showing video, .ad-interrupting video');
      if (ad) { ad.muted = true; if (isFinite(ad.duration) && ad.duration > 0) ad.currentTime = ad.duration; }
      var skip = document.querySelector('.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button, .ytm-skip-ad-button');
      if (skip) skip.click();
    } catch (e) {}
  }, 300);
})();
