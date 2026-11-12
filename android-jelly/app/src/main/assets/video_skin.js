// "Cover skin" for videos in the family Browser (added to each page by Guard.videoScript).
// A see-through layer is laid exactly over each video's picture; on every frame the phone's
// graphics chip paints grey blocks wherever it sees skin (the same rules as SkinMask.java for
// pictures). A video that cannot be read (protected or from a site that forbids it) is covered
// completely. Nothing leaves the phone.
(function () {
  if (window.__mdmVideoSkin) return;
  window.__mdmVideoSkin = true;

  var CELL = 10; // block size in CSS pixels
  var VS = 'attribute vec2 p;void main(){gl_Position=vec4(p,0.0,1.0);}';
  var FS = [
    'precision mediump float;',
    'uniform sampler2D v;uniform vec2 size;uniform float cell;',
    'bool skin(vec3 c){c*=255.0;float r=c.r,g=c.g,b=c.b;',
    ' float mx=max(r,max(g,b)),mn=min(r,min(g,b));',
    ' if(!(r>95.0&&g>40.0&&b>20.0&&mx-mn>15.0&&abs(r-g)>15.0&&r>g&&r>b))return false;',
    ' float cb=128.0-0.168736*r-0.331264*g+0.5*b;float cr=128.0+0.5*r-0.418688*g-0.081312*b;',
    ' return cb>=77.0&&cb<=127.0&&cr>=133.0&&cr<=173.0;}',
    'void main(){vec2 base=floor(gl_FragCoord.xy/cell)*cell;float n=0.0;',
    ' for(int i=0;i<3;i++)for(int j=0;j<3;j++){vec2 q=(base+(vec2(float(i),float(j))+0.5)*cell/3.0)/size;',
    '  if(skin(texture2D(v,q).rgb))n+=1.0;}',
    ' gl_FragColor=n>=3.0?vec4(0.62,0.62,0.62,1.0):vec4(0.0);}'
  ].join('\n');

  // A video shown full screen by itself could not carry the layer: full screen its box instead.
  try {
    var fs = function () { var p = this.parentElement; return p && p.requestFullscreen ? p.requestFullscreen() : Promise.reject(); };
    HTMLVideoElement.prototype.requestFullscreen = fs;
    HTMLVideoElement.prototype.webkitRequestFullscreen = fs;
    HTMLVideoElement.prototype.webkitEnterFullscreen = fs;
  } catch (e) {}

  function cover(video) {
    if (video.__mdm) return;
    var canvas = document.createElement('canvas');
    canvas.style.cssText = 'position:fixed;pointer-events:none;z-index:2147483647;left:0;top:0;width:0;height:0;';
    var gl = canvas.getContext('webgl', { premultipliedAlpha: true, alpha: true, preserveDrawingBuffer: !!window.__mdmVideoSkinTest });
    var st = { canvas: canvas, gl: gl, blocked: false };
    video.__mdm = st;
    try { video.setAttribute('controlslist', 'nofullscreen'); } catch (e) {}
    if (gl) {
      var sh = function (type, src) { var s = gl.createShader(type); gl.shaderSource(s, src); gl.compileShader(s); return s; };
      var prog = gl.createProgram();
      gl.attachShader(prog, sh(gl.VERTEX_SHADER, VS));
      gl.attachShader(prog, sh(gl.FRAGMENT_SHADER, FS));
      gl.linkProgram(prog);
      gl.useProgram(prog);
      var buf = gl.createBuffer();
      gl.bindBuffer(gl.ARRAY_BUFFER, buf);
      gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 1, -1, -1, 1, 1, 1]), gl.STATIC_DRAW);
      var loc = gl.getAttribLocation(prog, 'p');
      gl.enableVertexAttribArray(loc);
      gl.vertexAttribPointer(loc, 2, gl.FLOAT, false, 0, 0);
      var tex = gl.createTexture();
      gl.bindTexture(gl.TEXTURE_2D, tex);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
      gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL, true);
      st.uSize = gl.getUniformLocation(prog, 'size');
      st.uCell = gl.getUniformLocation(prog, 'cell');
    }
    var draw = function () {
      if (!video.isConnected) { canvas.remove(); return; }
      var host = document.fullscreenElement || document.webkitFullscreenElement || document.documentElement;
      if (canvas.parentNode !== host) host.appendChild(canvas);
      // Where the picture really is inside the video's box (videos keep their shape: "contain").
      var r = video.getBoundingClientRect(), vw = video.videoWidth, vh = video.videoHeight;
      if (!vw || !vh || r.width < 2 || r.height < 2 || getComputedStyle(video).visibility === 'hidden') { canvas.style.width = '0'; return; }
      var fit = getComputedStyle(video).objectFit, s = fit === 'cover' ? Math.max(r.width / vw, r.height / vh) : fit === 'fill' ? 0 : Math.min(r.width / vw, r.height / vh);
      var dw = s ? vw * s : r.width, dh = s ? vh * s : r.height;
      var left = r.left + (r.width - dw) / 2, top = r.top + (r.height - dh) / 2;
      canvas.style.left = left + 'px'; canvas.style.top = top + 'px';
      canvas.style.width = dw + 'px'; canvas.style.height = dh + 'px';
      var w = Math.max(1, Math.round(dw)), h = Math.max(1, Math.round(dh));
      if (canvas.width !== w || canvas.height !== h) { canvas.width = w; canvas.height = h; }
      if (!gl || st.blocked) { coverAll(); return; }
      try {
        gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGB, gl.RGB, gl.UNSIGNED_BYTE, video);
      } catch (e) {
        st.blocked = true; // the site does not let its video be read: cover it completely
        coverAll();
        return;
      }
      gl.viewport(0, 0, w, h);
      gl.uniform2f(st.uSize, w, h);
      gl.uniform1f(st.uCell, CELL);
      gl.clearColor(0, 0, 0, 0);
      gl.clear(gl.COLOR_BUFFER_BIT);
      gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
    };
    function coverAll() {
      if (gl) { gl.viewport(0, 0, canvas.width, canvas.height); gl.clearColor(0.62, 0.62, 0.62, 1); gl.clear(gl.COLOR_BUFFER_BIT); }
      else canvas.style.background = '#9e9e9e';
    }
    var loop = function () {
      draw();
      if (!video.isConnected) return;
      if (video.requestVideoFrameCallback && !video.paused) video.requestVideoFrameCallback(loop);
      else requestAnimationFrame(loop);
    };
    loop();
  }

  function scan() { var vs = document.getElementsByTagName('video'); for (var i = 0; i < vs.length; i++) cover(vs[i]); }
  scan();
  new MutationObserver(scan).observe(document.documentElement, { childList: true, subtree: true });
})();
