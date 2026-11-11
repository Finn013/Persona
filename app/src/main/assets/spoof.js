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
  if (C.ua) {
    def(N, 'userAgent', C.ua);
    def(N, 'appVersion', C.ua.replace(/^Mozilla\//, ''));
  }
  if (C.platform) def(N, 'platform', C.platform);
  if (C.cores) def(N, 'hardwareConcurrency', Number(C.cores));
  if (C.memory) def(N, 'deviceMemory', Number(C.memory));
  if (C.touch) def(N, 'maxTouchPoints', Number(C.touch));
  if (C.lang) {
    var langs = C.lang.split(',').map(function (s) { return s.trim(); }).filter(Boolean);
    if (langs.length) {
      def(N, 'language', langs[0]);
      def(N, 'languages', Object.freeze(langs));
    }
  }

  var S = window.Screen && Screen.prototype;
  if (C.sw) { def(S, 'width', Number(C.sw)); def(S, 'availWidth', Number(C.sw)); }
  if (C.sh) { def(S, 'height', Number(C.sh)); def(S, 'availHeight', Number(C.sh)); }
  if (C.dpr) def(window, 'devicePixelRatio', Number(C.dpr));

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

  if (C.tz) {
    try {
      var ODTF = Intl.DateTimeFormat;
      var withTz = function (o) { o = Object.assign({}, o || {}); if (!o.timeZone) o.timeZone = C.tz; return o; };
      var DTF = function (loc, o) { return new ODTF(loc, withTz(o)); };
      DTF.prototype = ODTF.prototype;
      DTF.supportedLocalesOf = ODTF.supportedLocalesOf;
      Intl.DateTimeFormat = DTF;
      var D = Date.prototype;
      ['toLocaleString', 'toLocaleDateString', 'toLocaleTimeString'].forEach(function (m) {
        var o = D[m];
        D[m] = function (loc, opt) { return o.call(this, loc, withTz(opt)); };
      });
      var fmt = new ODTF('en-US', { timeZone: C.tz, hourCycle: 'h23', year: 'numeric', month: 'numeric',
        day: 'numeric', hour: 'numeric', minute: 'numeric', second: 'numeric' });
      D.getTimezoneOffset = function () {
        var t = this.getTime();
        if (isNaN(t)) return NaN;
        var p = {};
        fmt.formatToParts(this).forEach(function (x) { p[x.type] = x.value; });
        var asUtc = Date.UTC(+p.year, +p.month - 1, +p.day, (+p.hour) % 24, +p.minute, +p.second);
        return Math.round((Math.floor(t / 1000) * 1000 - asUtc) / 60000);
      };
    } catch (e) {}
  }

  if (C.uad) {
    var U = C.uad;
    var low = { brands: U.brands, mobile: U.mobile, platform: 'Android' };
    var uad = {
      brands: U.brands,
      mobile: U.mobile,
      platform: 'Android',
      getHighEntropyValues: function () {
        return Promise.resolve({
          brands: U.brands, mobile: U.mobile, platform: 'Android',
          platformVersion: U.platformVersion, model: U.model,
          architecture: U.arch, bitness: U.bitness, wow64: false,
          uaFullVersion: U.fullVersion, fullVersionList: U.fullVersionList,
          formFactors: [U.mobile ? 'Mobile' : 'Tablet']
        });
      },
      toJSON: function () { return low; }
    };
    def(N, 'userAgentData', uad);
  }
})();
