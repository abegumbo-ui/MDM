// The family Browser and the YouTube add-on: on YouTube's home page (its own feed emptied), shows
// "Ask for a channel or video", "Your channels" and "Your videos" -- what the parent allowed on the
// dashboard -- in YouTube's own style.
// __MDM_DATA__ is replaced with {"channels":[{"h":"@x","t":"Name","i":"pic"}],"videos":[{"v":"id","t":"Title","i":"pic"}],"ask":true}.
(function (data) {
  if (window.__mdmShelf) return;
  window.__mdmShelf = true;
  if (!data || (!data.channels.length && !data.videos.length && !data.ask)) return;
  // Its own panel over YouTube's home page (between YouTube's top bar and bottom bar), so nothing of
  // YouTube's own page can fade or hide it.
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
      [' .ask', 'display:inline-flex;align-items:center;gap:8px;margin:4px 0 8px;padding:10px 18px;border-radius:20px;background:#f2f2f2;color:#0f0f0f;text-decoration:none;font-size:15px;font-weight:500;border:1px solid #d9d9d9'],
      ['.dark .ask', 'background:#272727;color:#f1f1f1;border-color:#3f3f3f'],
      [' .row', 'display:flex;gap:14px;overflow-x:auto;padding-bottom:8px'],
      [' .ch', 'flex:0 0 auto;width:76px;text-align:center;color:inherit;text-decoration:none;font-size:12px'],
      [' .ch img', 'width:64px;height:64px;border-radius:50%;object-fit:cover;background:#ccc;display:block;margin:0 auto 6px'],
      [' .ch span', 'display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden'],
      [' .vids', 'display:grid;grid-template-columns:repeat(auto-fill,minmax(260px,1fr));gap:16px'],
      [' .v', 'color:inherit;text-decoration:none'],
      [' .v img', 'width:100%;aspect-ratio:16/9;object-fit:cover;border-radius:12px;background:#ccc;display:block'],
      [' .v div', 'font-size:14px;font-weight:500;line-height:20px;margin-top:8px;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden'],
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
  // Under a video: the other videos YouTube suggests that are not allowed are gone (youtube_cover.js); here is
  // what is allowed instead, so there is something to watch next.
  function buildMore() {
    var box = document.getElementById('mdm-more');
    var watch = location.pathname === '/watch';
    var now = playingId();
    var vids = data.videos.filter(function (v) { return v.v !== now; });
    if (!watch || (!vids.length && !data.channels.length)) { if (box) box.remove(); return; }
    if (box && box.isConnected && box.getAttribute('data-for') === now) { box.className = isDark() ? 'dark' : ''; return; }
    if (box) box.remove();
    addCss();
    box = el('div'); box.id = 'mdm-more'; box.setAttribute('data-for', now); box.className = isDark() ? 'dark' : '';
    if (vids.length) {
      box.appendChild(el('h3', null, 'More from your list'));
      var grid = el('div', 'vids');
      vids.forEach(function (v) {
        var a = el('a', 'v'); a.href = location.origin + '/watch?v=' + v.v;
        var img = el('img'); img.alt = ''; img.loading = 'lazy'; img.src = v.i || ('https://i.ytimg.com/vi/' + v.v + '/mqdefault.jpg'); a.appendChild(img);
        a.appendChild(el('div', null, v.t || 'Video')); grid.appendChild(a);
      });
      box.appendChild(grid);
    }
    if (data.channels.length) {
      box.appendChild(el('h3', null, 'My channels'));
      var row = el('div', 'row');
      data.channels.forEach(function (c) {
        var a = el('a', 'ch'); a.href = location.origin + '/' + c.h;
        var img = el('img'); img.alt = ''; img.loading = 'lazy'; if (c.i) img.src = c.i; a.appendChild(img);
        a.appendChild(el('span', null, c.t || c.h)); row.appendChild(a);
      });
      box.appendChild(row);
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
      var ask = el('a', 'ask', '＋  Ask for a channel or video'); ask.href = 'mdmguard://ask';
      box.appendChild(ask);
    }
    if (data.channels.length) {
      box.appendChild(el('h3', null, 'My channels'));
      var row = el('div', 'row');
      data.channels.forEach(function (c) {
        var a = el('a', 'ch'); a.href = location.origin + '/' + c.h;
        var img = el('img'); img.alt = ''; img.loading = 'lazy'; if (c.i) img.src = c.i; a.appendChild(img);
        a.appendChild(el('span', null, c.t || c.h)); row.appendChild(a);
      });
      box.appendChild(row);
    }
    if (data.videos.length) {
      box.appendChild(el('h3', null, 'My videos'));
      var grid = el('div', 'vids');
      data.videos.forEach(function (v) {
        var a = el('a', 'v'); a.href = location.origin + '/watch?v=' + v.v;
        var img = el('img'); img.alt = ''; img.loading = 'lazy'; img.src = v.i || ('https://i.ytimg.com/vi/' + v.v + '/mqdefault.jpg'); a.appendChild(img);
        a.appendChild(el('div', null, v.t || 'Video')); grid.appendChild(a);
      });
      box.appendChild(grid);
    }
    host.appendChild(box);
    place(box);
  }
  build();
  setInterval(build, 500);
})(__MDM_DATA__);
