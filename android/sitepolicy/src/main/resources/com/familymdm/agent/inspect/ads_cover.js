// "Ads load but are covered" (a site that will not open with ads blocked): the ads are allowed to
// load, so the site works, but every ad is painted solid black and cannot be tapped. On YouTube an
// ad video plays muted and black. Added by the family Browser for such sites.
(function () {
  if (window.__mdmAdsCovered) return;
  window.__mdmAdsCovered = true;
  var sel = [
    'iframe[src*="doubleclick.net"]', 'iframe[src*="googlesyndication"]', 'iframe[id^="google_ads_iframe"]', 'iframe[name^="google_ads"]',
    'iframe[src*="amazon-adsystem"]', 'iframe[src*="adnxs"]', 'iframe[src*="taboola"]', 'iframe[src*="outbrain"]', 'iframe[src*="criteo"]',
    'iframe[src*="adsafeprotected"]', 'iframe[src*="moatads"]', 'iframe[src*="adservice"]',
    'ins.adsbygoogle', '[id^="div-gpt-ad"]', '[id^="google_ads"]', '[data-google-query-id]', '[class*="taboola"]', '[id*="taboola"]',
    '.OUTBRAIN', '[class*="outbrain"]', '[aria-label="Advertisement"]', '[aria-label="Advertisements"]', '[data-ad-slot]', '[data-ad-unit]',
    '[data-adunit]', '.ad-slot', '.ad-container', '.advertisement', '.adunit', '.ad-banner', '.adsbox',
    'ytm-promoted-sparkles-web-renderer', 'ytm-companion-ad-renderer', 'ad-slot-renderer', 'ytd-ad-slot-renderer', 'ytd-in-feed-ad-layout-renderer',
    'ytd-banner-promo-renderer', '#masthead-ad', '#player-ads', '.ytp-ad-overlay-container'].join(',');
  function addCss() {
    if (document.getElementById('mdm-ads-covered')) return;
    var st = document.createElement('style'); st.id = 'mdm-ads-covered';
    st.textContent = sel + '{filter:brightness(0) !important;pointer-events:none !important}';
    (document.head || document.documentElement).appendChild(st);
  }
  setInterval(function () {
    try {
      addCss();
      var ad = document.querySelector('.ad-showing video, .ad-interrupting video');
      document.querySelectorAll('video').forEach(function (v) {
        if (v === ad) { v.muted = true; v.style.filter = 'brightness(0)'; v.__mdmAd = true; }
        else if (v.__mdmAd) { v.style.filter = ''; v.muted = false; v.__mdmAd = false; }
      });
    } catch (e) {}
  }, 250);
})();
