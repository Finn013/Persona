(function () {
  if (window.__persona) return;
  try { Object.defineProperty(window, '__persona', { value: true }); } catch (e) {}
  var C = __CFG__;

  function def(obj, prop, val) {
    if (!obj) return;
    try {
      var getter = function () { return val; };
      try { Object.defineProperty(getter, 'name', { value: 'get ' + prop }); } catch (e) {}
      Object.defineProperty(obj, prop, { get: getter, configurable: true, enumerable: true });
    } catch (e) {}
  }

  var N = window.Navigator && Navigator.prototype;
  if (C.memory) def(N, 'deviceMemory', Math.min(8, Number(C.memory)));
  if (C.cores) def(N, 'hardwareConcurrency', Number(C.cores));
  if (C.touch !== undefined && C.touch !== '') def(N, 'maxTouchPoints', Number(C.touch));
  if (C.lang) {
    var langs = C.lang.split(',').map(function (s) { return s.trim(); }).filter(Boolean);
    if (langs.length) {
      def(N, 'language', langs[0]);
      def(N, 'languages', Object.freeze(langs));
    }
  }

  var S = window.Screen && Screen.prototype;
  if (C.sw) { def(S, 'width', Number(C.sw)); def(S, 'availWidth', Number(C.sw)); }
  if (C.sh) {
    def(S, 'height', Number(C.sh));
    def(S, 'availHeight', Number(C.sh) - (C.desktop ? 40 : 0));
  }
  if (C.dpr && C.desktop) def(window, 'devicePixelRatio', Number(C.dpr));

  if (C.gpuV || C.gpuR) {
    var patch = function (proto) {
      if (!proto) return;
      var orig = proto.getParameter;
      proto.getParameter = function (p) {
        if (p === 37445 && C.gpuV) return C.gpuV;
        if (p === 37446 && C.gpuR) return C.gpuR;
        return orig.apply(this, arguments);
      };
    };
    patch(window.WebGLRenderingContext && WebGLRenderingContext.prototype);
    patch(window.WebGL2RenderingContext && WebGL2RenderingContext.prototype);
  }
})();
