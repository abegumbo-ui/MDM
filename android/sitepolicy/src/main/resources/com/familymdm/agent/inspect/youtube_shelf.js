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
  var css = '#mdm-shelf{position:fixed;left:0;right:0;z-index:1;overflow-y:auto;font-family:Roboto,Arial,sans-serif;padding:12px 12px 24px;box-sizing:border-box;color:#0f0f0f;background:#fff;opacity:1}' +
    '#mdm-shelf.dark{color:#f1f1f1;background:#0f0f0f}' +
    '#mdm-shelf *{opacity:1}' +
    '#mdm-shelf h3{font-size:18px;font-weight:700;margin:8px 0 12px}' +
    '#mdm-shelf .ask{display:inline-flex;align-items:center;gap:8px;margin:4px 0 8px;padding:10px 18px;border-radius:20px;background:#f2f2f2;color:#0f0f0f;text-decoration:none;font-size:15px;font-weight:500;border:1px solid #d9d9d9}' +
    '#mdm-shelf.dark .ask{background:#272727;color:#f1f1f1;border-color:#3f3f3f}' +
    '#mdm-shelf .row{display:flex;gap:14px;overflow-x:auto;padding-bottom:8px}' +
    '#mdm-shelf .ch{flex:0 0 auto;width:76px;text-align:center;color:inherit;text-decoration:none;font-size:12px}' +
    '#mdm-shelf .ch img{width:64px;height:64px;border-radius:50%;object-fit:cover;background:#ccc;display:block;margin:0 auto 6px}' +
    '#mdm-shelf .ch span{display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden}' +
    '#mdm-shelf .vids{display:grid;grid-template-columns:repeat(auto-fill,minmax(260px,1fr));gap:16px}' +
    '#mdm-shelf .v{color:inherit;text-decoration:none}' +
    '#mdm-shelf .v img{width:100%;aspect-ratio:16/9;object-fit:cover;border-radius:12px;background:#ccc;display:block}' +
    '#mdm-shelf .v div{font-size:14px;font-weight:500;line-height:20px;margin-top:8px;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden}';
  function el(tag, cls, text) { var e = document.createElement(tag); if (cls) e.className = cls; if (text) e.textContent = text; return e; }
  function isHome() { var p = location.pathname; return p === '/' || p === ''; }
  function isDark() {
    var h = document.documentElement;
    return h.hasAttribute('dark') || h.hasAttribute('darker-dark-theme') || (window.matchMedia && matchMedia('(prefers-color-scheme: dark)').matches);
  }
  function place(box) {
    var top = 0, bottom = 0;
    var head = document.querySelector('ytm-mobile-topbar-renderer, #header-bar, #masthead-container');
    if (head) top = Math.max(0, head.getBoundingClientRect().bottom);
    var foot = document.querySelector('ytm-pivot-bar-renderer');
    if (foot) bottom = Math.max(0, innerHeight - foot.getBoundingClientRect().top);
    box.style.top = (top || 48) + 'px';
    box.style.bottom = bottom + 'px';
    box.className = isDark() ? 'dark' : '';
  }
  function build() {
    var box = document.getElementById('mdm-shelf');
    if (!isHome()) { if (box) box.remove(); return; }
    if (box && box.isConnected) { place(box); return; }
    var host = document.body;
    if (!host) return;
    if (!document.getElementById('mdm-shelf-css')) { var st = el('style'); st.id = 'mdm-shelf-css'; st.textContent = css; (document.head || document.documentElement).appendChild(st); }
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
