// The family Browser and the YouTube / YouTube Music add-ons: on the home page (its own feed emptied), shows
// "Ask for ...", "My channels", "My videos" (or "Your music") -- what the parent allowed on the dashboard -- in
// YouTube's own style, with a Download button under each item when downloads are on.
// __MDM_DATA__ is replaced with {"channels":[{"h":"@x","t":"Name","i":"pic"}],"videos":[{"v":"id","t":"Title","i":"pic"}],
//   "ask":true,"downloads":[{"v":"id","t":"Title","i":"pic"}],"music":false,"dl":true,"nopics":false}.
(function (data) {
  if (window.__mdmShelf) return;
  window.__mdmShelf = true;
  if (data) { data.downloads = data.downloads || []; data.channels = data.channels || []; data.videos = data.videos || []; }
  if (!data || (!data.channels.length && !data.videos.length && !data.ask && !data.downloads.length && !data.dl)) return;
  var music = !!data.music;
  var dlOn = !!data.dl;
  var have = {};
  data.downloads.forEach(function (d) { have[d.v] = 1; });
  // Its own panel over the home page (between the top bar and bottom bar), so nothing of the page's own feed
  // can fade or hide it.
  // The same look for the home panel (#mdm-shelf, fixed) and the list under a video (#mdm-more, in the page).
  function rules(list) {
    return list.map(function (r) { return ['#mdm-shelf', '#mdm-more'].map(function (s) { return s + r[0]; }).join(',') + '{' + r[1] + '}'; }).join('');
  }
  var css = '#mdm-shelf{position:fixed;left:0;right:0;z-index:1;overflow-y:auto;font-family:Roboto,Arial,sans-serif;padding:12px 12px 24px;box-sizing:border-box;color:#0f0f0f;background:#fff;opacity:1}' +
    '#mdm-more{font-family:Roboto,Arial,sans-serif;padding:12px 12px 24px;box-sizing:border-box;color:#0f0f0f;background:#fff;opacity:1;clear:both}' +
    '#mdm-shelf.dark,#mdm-more.dark{color:#f1f1f1;background:#0f0f0f}' +
    rules([
      [' *', 'opacity:1'],
      [' h3', 'font-size:18px;font-weight:700;margin:8px 0 12px'],
      [' .ask', 'display:inline-flex;align-items:center;gap:8px;margin:4px 8px 8px 0;padding:10px 18px;border-radius:20px;background:#f2f2f2;color:#0f0f0f;text-decoration:none;font-size:15px;font-weight:500;border:1px solid #d9d9d9'],
      ['.dark .ask', 'background:#272727;color:#f1f1f1;border-color:#3f3f3f'],
      [' .row', 'display:flex;gap:14px;overflow-x:auto;padding-bottom:8px'],
      [' .ch', 'flex:0 0 auto;width:76px;text-align:center;color:inherit;text-decoration:none;font-size:12px'],
      [' .ch img', 'width:64px;height:64px;border-radius:50%;object-fit:cover;background:#ccc;display:block;margin:0 auto 6px'],
      [' .ch span', 'display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden'],
      [' .vids', 'display:grid;grid-template-columns:repeat(auto-fill,minmax(260px,1fr));gap:16px'],
      [' .vw', 'display:block'],
      [' .v', 'color:inherit;text-decoration:none'],
      [' .v img', 'width:100%;aspect-ratio:16/9;object-fit:cover;border-radius:12px;background:#ccc;display:block'],
      // YouTube Music: square album-art cards, like the real app.
      [' .vids.m', 'grid-template-columns:repeat(auto-fill,minmax(150px,1fr));gap:14px'],
      [' .vids.m .v img', 'aspect-ratio:1/1;border-radius:8px'],
      [' .vids.m .v div', 'font-size:13px;line-height:18px;margin-top:6px'],
      [' .v div', 'font-size:14px;font-weight:500;line-height:20px;margin-top:8px;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden'],
      [' .dl', 'display:inline-block;margin:8px 0 2px;padding:7px 16px;border-radius:18px;background:#4F46E5;color:#fff;text-decoration:none;font-size:14px;font-weight:500'],
      [' .dl.done', 'background:transparent;color:#8a8a8a;border:1px solid #8a8a8a;padding:6px 15px'],
      [' .dl.del', 'background:transparent;color:#d93025;border:1px solid #d93025;padding:6px 15px;margin-left:8px'],
    ]);
  function el(tag, cls, text) { var e = document.createElement(tag); if (cls) e.className = cls; if (text) e.textContent = text; return e; }
  function isHome() { var p = location.pathname; return p === '/' || p === ''; }
  function isDark() {
    var h = document.documentElement;
    return h.hasAttribute('dark') || h.hasAttribute('darker-dark-theme') || (window.matchMedia && matchMedia('(prefers-color-scheme: dark)').matches);
  }
  function place(box) {
    var top = 0, bottom = 0;
    var head = document.querySelector('ytm-mobile-topbar-renderer, #header-bar, #masthead-container, ytmusic-nav-bar');
    if (head) top = Math.max(0, head.getBoundingClientRect().bottom);
    var foot = document.querySelector('ytm-pivot-bar-renderer, ytmusic-pivot-bar-renderer');
    if (foot) bottom = Math.max(0, innerHeight - foot.getBoundingClientRect().top);
    box.style.top = (top || 48) + 'px';
    box.style.bottom = bottom + 'px';
    box.className = isDark() ? 'dark' : '';
  }
  function addCss() {
    if (document.getElementById('mdm-shelf-css')) return;
    var st = el('style'); st.id = 'mdm-shelf-css'; st.textContent = css; (document.head || document.documentElement).appendChild(st);
  }
  function playingId() {
    var m = /[?&]v=([A-Za-z0-9_-]{11})/.exec(location.search);
    return m ? m[1] : '';
  }
  // Where an item is watched / listened to, and the address its download is made from.
  function pageOf(id) { return location.origin + '/watch?v=' + id; }
  function dlAddress(id) { return (music ? 'https://music.youtube.com/watch?v=' : 'https://www.youtube.com/watch?v=') + id; }
  function dlButton(v) {
    if (!dlOn) return null;
    if (have[v.v]) return el('span', 'dl done', '✓  Downloaded');
    var a = el('a', 'dl', '⬇  Download');
    a.href = 'mdmguard://download?u=' + encodeURIComponent(dlAddress(v.v)) + (music ? '&k=music' : '');
    return a;
  }
  // One picture + title that opens the item, and (when downloads are on) its Download button under it.
  function tile(v, target, withDownload) {
    var w = el('div', 'vw');
    var a = el('a', 'v'); a.href = target;
    // "Show song pictures" off (nopics): only the title, no picture and no empty picture area.
    if (!data.nopics) { var img = el('img'); img.alt = ''; img.loading = 'lazy'; img.src = v.i || ('https://i.ytimg.com/vi/' + v.v + '/mqdefault.jpg'); a.appendChild(img); }
    a.appendChild(el('div', null, v.t || (music ? 'Song' : 'Video')));
    w.appendChild(a);
    if (withDownload) { var d = dlButton(v); if (d) w.appendChild(d); }
    return w;
  }
  // A downloaded item can always be deleted from the phone here, whether or not new downloads are allowed.
  function delButton(v) {
    var a = el('a', 'dl del', '🗑  Delete');
    a.href = 'mdmguard://delete?id=' + v.v;
    return a;
  }
  function downloadedTile(v) {
    var w = tile(v, 'mdmguard://play?id=' + v.v, false);
    w.appendChild(delButton(v));
    return w;
  }
  function downloadedGrid() {
    var g = el('div', music ? 'vids m' : 'vids');
    data.downloads.forEach(function (v) { g.appendChild(downloadedTile(v)); });
    return g;
  }
  function channelsRow() {
    var row = el('div', 'row');
    data.channels.forEach(function (c) {
      var a = el('a', 'ch'); a.href = location.origin + '/' + c.h;
      var img = el('img'); img.alt = ''; img.loading = 'lazy'; if (c.i) img.src = c.i; a.appendChild(img);
      a.appendChild(el('span', null, c.t || c.h)); row.appendChild(a);
    });
    return row;
  }
  // Under a video / song: the other items YouTube suggests that are not allowed are gone (youtube_cover.js); here is
  // what is allowed instead, so there is something to watch next.
  function buildMore() {
    var box = document.getElementById('mdm-more');
    var watch = location.pathname === '/watch';
    var now = playingId();
    var vids = data.videos.filter(function (v) { return v.v !== now; });
    var dls = data.downloads.filter(function (v) { return v.v !== now; });
    if (!watch || (!vids.length && !data.channels.length && !dls.length)) { if (box) box.remove(); return; }
    if (box && box.isConnected && box.getAttribute('data-for') === now) { box.className = isDark() ? 'dark' : ''; return; }
    if (box) box.remove();
    addCss();
    box = el('div'); box.id = 'mdm-more'; box.setAttribute('data-for', now); box.className = isDark() ? 'dark' : '';
    if (dls.length) {
      box.appendChild(el('h3', null, 'Downloaded'));
      var dgrid = el('div', music ? 'vids m' : 'vids');
      dls.forEach(function (v) { dgrid.appendChild(downloadedTile(v)); });
      box.appendChild(dgrid);
    }
    if (vids.length) {
      box.appendChild(el('h3', null, 'More from your list'));
      var grid = el('div', music ? 'vids m' : 'vids');
      vids.forEach(function (v) { grid.appendChild(tile(v, pageOf(v.v), true)); });
      box.appendChild(grid);
    }
    if (data.channels.length) {
      box.appendChild(el('h3', null, 'My channels'));
      box.appendChild(channelsRow());
    }
    var host = document.querySelector('ytm-single-column-watch-next-results-renderer, ytm-watch, #secondary-inner, #secondary, #primary-inner') || document.body;
    if (host) host.appendChild(box);
  }
  function build() {
    buildMore();
    var box = document.getElementById('mdm-shelf');
    if (!isHome()) { if (box) box.remove(); return; }
    if (box && box.isConnected) { place(box); return; }
    var host = document.body;
    if (!host) return;
    addCss();
    box = el('div'); box.id = 'mdm-shelf';
    if (data.ask) {
      var ask = el('a', 'ask', music ? '＋  Ask for a song, album or artist' : '＋  Ask for a channel or video'); ask.href = 'mdmguard://ask';
      box.appendChild(ask);
    }
    if (dlOn || data.downloads.length) { // the Downloads screen: what is downloading (progress, Cancel) and what is downloaded (Play, Delete)
      var dlb = el('a', 'ask', '⬇  Downloads'); dlb.href = 'mdmguard://downloads';
      box.appendChild(dlb);
    }
    if (data.downloads.length) {
      box.appendChild(el('h3', null, 'Downloaded'));
      box.appendChild(downloadedGrid());
    }
    if (data.channels.length) {
      box.appendChild(el('h3', null, 'My channels'));
      box.appendChild(channelsRow());
    }
    if (data.videos.length) {
      box.appendChild(el('h3', null, music ? 'Your music' : 'My videos'));
      var grid = el('div', music ? 'vids m' : 'vids');
      data.videos.forEach(function (v) { grid.appendChild(tile(v, pageOf(v.v), true)); });
      box.appendChild(grid);
    }
    if (music && !data.videos.length && !data.downloads.length) {
      box.appendChild(el('h3', null, 'Your music'));
      box.appendChild(el('div', null, 'Nothing is allowed yet. Use “Ask for a song, album or artist” above, or search, and your parent gets it.'));
    }
    host.appendChild(box);
    place(box);
  }
  build();
  setInterval(build, 500);

  // YouTube Music, "Only what I allow": playback stays inside the approved songs. When a song ends, or Next / Previous is tapped, the
  // next approved one plays (the list goes round and round); a song that is not approved is stopped and replaced by an approved one.
  if (music && data.videos.length) {
    var ids = data.videos.map(function (v) { return v.v; });
    var step = function (from, by) { var i = ids.indexOf(from); return ids[((i < 0 ? 0 : i + by) % ids.length + ids.length) % ids.length]; };
    var play = function (id) { location.replace(location.origin + '/watch?v=' + id); };
    setInterval(function () {
      if (location.pathname !== '/watch') return;
      var cur = playingId();
      var vid = document.querySelector('video');
      if (cur && ids.indexOf(cur) < 0) { if (vid && vid.pause) vid.pause(); play(ids[0]); return; }
      if (vid && !vid.__mdmEnd) {
        vid.__mdmEnd = true;
        vid.addEventListener('ended', function (e) {
          e.stopImmediatePropagation();
          if (ids.length === 1) { vid.currentTime = 0; vid.play(); } else play(step(playingId(), 1));
        }, true);
      }
    }, 400);
    document.addEventListener('click', function (e) {
      var b = e.target && e.target.closest ? e.target.closest('.next-button, .previous-button') : null;
      if (!b || location.pathname !== '/watch') return;
      e.preventDefault(); e.stopPropagation();
      var back = b.className.indexOf('previous-button') >= 0;
      if (ids.length === 1) { var v1 = document.querySelector('video'); if (v1) { v1.currentTime = 0; v1.play(); } } else play(step(playingId(), back ? -1 : 1));
    }, true);
  }
})(__MDM_DATA__);
