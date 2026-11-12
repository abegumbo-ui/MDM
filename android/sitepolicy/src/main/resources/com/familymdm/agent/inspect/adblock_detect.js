// Notices a page that will not open with ads blocked ("Ad blocker detected", "please turn off your
// ad blocker"...). The family Browser then lets that site's ads load but covers them (ads_cover.js)
// and reloads it, so nobody has to do anything.
(function () {
  if (window.__mdmAdDetect) return;
  window.__mdmAdDetect = true;
  var re = /ad[\s-]?block(er)?s?\b[^.!?]{0,60}(detected|enabled|is on|active|disable|turn off|pause|whitelist|allowlist)|(disable|turn off|pause|deactivate|switch off)\s+(your\s+)?ad[\s-]?block|(whitelist|allowlist)\s+(this site|us|our site)|ad blockers? (are not allowed|violate)|(you('re| are)|seems you('re| are)|looks like you('re| are)) using an ad[\s-]?block/i;
  function check() {
    try {
      var t = (document.body && document.body.innerText || '').slice(0, 30000);
      if (re.test(t) && window.MdmAds) { window.MdmAds.blocked(location.hostname); return true; }
    } catch (e) {}
    return false;
  }
  var n = 0;
  var timer = setInterval(function () { if (check() || ++n > 10) clearInterval(timer); }, 1500);
})();
