// "Filter nudity only" for videos (added to the pages of a site with that choice by YoutubeInspector).
// Each video starts covered with a plain grey layer. About twice a second a small picture of the video's current frame is sent to the
// phone's own filter (https://ask.mdm.invalid/nudity, answered on the phone, nothing leaves it) which checks it with the same
// checker as pictures. A frame that may show nudity keeps the video covered; the video shows once two checks in a row are clean.
// A video that cannot be read (protected, or from a site that forbids it) stays covered, and so does one the filter cannot be reached for.
(function () {
  if (window.__mdmVideoNude) return;
  window.__mdmVideoNude = true;

  var ENDPOINT = 'https://ask.mdm.invalid/nudity';
  var EVERY = 500;   // milliseconds between two checks of one video
  var SIDE = 320;    // the longest side of the picture that is sent
  var CLEAN = 2;     // clean checks in a row before a covered video shows again (the first check of a video needs only one)

  // A video shown full screen by itself could not carry the layer: full screen its box instead.
  try {
    var fs = function () { var p = this.parentElement; return p && p.requestFullscreen ? p.requestFullscreen() : Promise.reject(); };
    HTMLVideoElement.prototype.requestFullscreen = fs;
    HTMLVideoElement.prototype.webkitRequestFullscreen = fs;
    HTMLVideoElement.prototype.webkitEnterFullscreen = fs;
  } catch (e) {}

  function guard(video) {
    if (video.__mdmNude) return;
    var layer = document.createElement('div');
    layer.style.cssText = 'position:fixed;pointer-events:none;z-index:2147483647;left:0;top:0;width:0;height:0;background:#9e9e9e;';
    var work = document.createElement('canvas');
    var st = { layer: layer, covered: true, clean: 0, checked: false, busy: false, blocked: false, lastTime: -1 };
    video.__mdmNude = st;
    try { video.setAttribute('controlslist', 'nofullscreen'); } catch (e) {}

    // Keep the layer exactly over the picture (videos keep their shape: "contain").
    function place() {
      if (!video.isConnected) { layer.remove(); return false; }
      var host = document.fullscreenElement || document.webkitFullscreenElement || document.documentElement;
      if (layer.parentNode !== host) host.appendChild(layer);
      var r = video.getBoundingClientRect(), vw = video.videoWidth, vh = video.videoHeight;
      if (!st.covered || r.width < 2 || r.height < 2 || getComputedStyle(video).visibility === 'hidden') { layer.style.width = '0'; return true; }
      var fit = getComputedStyle(video).objectFit;
      var s = !vw || !vh ? 0 : fit === 'cover' ? Math.max(r.width / vw, r.height / vh) : fit === 'fill' ? 0 : Math.min(r.width / vw, r.height / vh);
      var dw = s ? vw * s : r.width, dh = s ? vh * s : r.height;
      layer.style.left = (r.left + (r.width - dw) / 2) + 'px';
      layer.style.top = (r.top + (r.height - dh) / 2) + 'px';
      layer.style.width = dw + 'px';
      layer.style.height = dh + 'px';
      return true;
    }
    function frame() {
      if (place()) requestAnimationFrame(frame);
    }
    frame();

    function verdict(nude, checked) {
      if (!checked) { st.covered = false; st.checked = true; } // this phone has no checker: nothing to go by
      else if (nude) { st.covered = true; st.clean = 0; st.checked = true; }
      else {
        st.clean++;
        if (!st.checked || st.clean >= CLEAN) st.covered = false;
        st.checked = true;
      }
      place(); // the layer follows at once, not only on the next animation frame
    }

    function check() {
      if (!video.isConnected) return;
      setTimeout(check, EVERY);
      if (st.busy || video.readyState < 2 || !video.videoWidth || !video.videoHeight) return;
      if (st.checked && !st.covered && video.paused && video.currentTime === st.lastTime) return; // the same still picture, already checked
      st.lastTime = video.currentTime;
      var k = SIDE / Math.max(video.videoWidth, video.videoHeight);
      work.width = Math.max(16, Math.round(video.videoWidth * Math.min(1, k)));
      work.height = Math.max(16, Math.round(video.videoHeight * Math.min(1, k)));
      var data;
      try {
        work.getContext('2d').drawImage(video, 0, 0, work.width, work.height);
        data = work.toDataURL('image/jpeg', 0.7);
      } catch (e) {
        st.blocked = true; // the site does not let its video be read: it stays covered
        st.covered = true;
        return;
      }
      st.busy = true;
      var done = function () { st.busy = false; };
      try {
        fetch(ENDPOINT, { method: 'POST', body: data, headers: { 'Content-Type': 'text/plain' }, cache: 'no-store' })
          .then(function (r) { return r.json(); })
          .then(function (j) { verdict(!!j.nude, j.checked !== false); done(); })
          .catch(done);
      } catch (e) { done(); }
    }
    check();
  }

  function scan() { var vs = document.getElementsByTagName('video'); for (var i = 0; i < vs.length; i++) guard(vs[i]); }
  scan();
  new MutationObserver(scan).observe(document.documentElement, { childList: true, subtree: true });
})();
