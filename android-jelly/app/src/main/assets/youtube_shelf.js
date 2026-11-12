// The family Browser: on YouTube's home page (its own feed emptied), shows "Your channels" and
// "Your videos" -- what the parent allowed on the dashboard -- in YouTube's own style.
// __MDM_DATA__ is replaced by Guard with {"channels":[{"h":"@x","t":"Name","i":"pic"}],"videos":[{"v":"id","t":"Title","i":"pic"}]}.
(function (data) {
  if (window.__mdmShelf) return;
  window.__mdmShelf = true;
  if (!data || (!data.channels.length && !data.videos.length)) return;
  var css = '#mdm-shelf{font-family:Roboto,Arial,sans-serif;padding:12px 12px 4px;color:#0f0f0f}' +
    'html[dark] #mdm-shelf,html[darker-dark-theme] #mdm-shelf{color:#f1f1f1}' +
    '#mdm-shelf h3{font-size:18px;font-weight:700;margin:8px 0 12px}' +
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
  function build() {
    var box = document.getElementById('mdm-shelf');
    if (!isHome()) { if (box) box.remove(); return; }
    if (box && box.isConnected) return;
    var host = document.querySelector('ytm-browse') || document.querySelector('ytd-browse[page-subtype="home"] #primary') || document.querySelector('ytd-browse #primary') || document.querySelector('#page-manager');
    if (!host) return;
    if (!document.getElementById('mdm-shelf-css')) { var st = el('style'); st.id = 'mdm-shelf-css'; st.textContent = css; (document.head || document.documentElement).appendChild(st); }
    box = el('div'); box.id = 'mdm-shelf';
    if (data.channels.length) {
      box.appendChild(el('h3', null, 'Your channels'));
      var row = el('div', 'row');
      data.channels.forEach(function (c) {
        var a = el('a', 'ch'); a.href = location.origin + '/' + c.h;
        var img = el('img'); img.alt = ''; img.loading = 'lazy'; if (c.i) img.src = c.i; a.appendChild(img);
        a.appendChild(el('span', null, c.t || c.h)); row.appendChild(a);
      });
      box.appendChild(row);
    }
    if (data.videos.length) {
      box.appendChild(el('h3', null, 'Your videos'));
      var grid = el('div', 'vids');
      data.videos.forEach(function (v) {
        var a = el('a', 'v'); a.href = location.origin + '/watch?v=' + v.v;
        var img = el('img'); img.alt = ''; img.loading = 'lazy'; img.src = v.i || ('https://i.ytimg.com/vi/' + v.v + '/mqdefault.jpg'); a.appendChild(img);
        a.appendChild(el('div', null, v.t || 'Video')); grid.appendChild(a);
      });
      box.appendChild(grid);
    }
    host.insertBefore(box, host.firstChild);
  }
  build();
  setInterval(build, 500);
})(__MDM_DATA__);
