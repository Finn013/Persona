'use strict';
const { app, BrowserWindow, ipcMain, session, shell, Menu, clipboard, net, nativeImage, screen, dialog, webContents } = require('electron');
const path = require('path');
const fs = require('fs');
const os = require('os');
const { PRESETS, FIELDS } = require('./presets');

const CHROME = process.versions.chrome;
const MAJOR = CHROME.split('.')[0];
const WIN_BUILD = Number((os.release().split('.')[2]) || 0);
const REAL_WIN = WIN_BUILD >= 22000 ? '11' : '10';
// Обычный UA Chrome без «Electron/…» и «Persona/…» — иначе Google и др. не пускают
const BASE_UA = `Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/${MAJOR}.0.0.0 Safari/537.36`;
app.userAgentFallback = BASE_UA;
app.setAppUserModelId('com.finn013.persona');

const ICON = path.join(__dirname, 'icon.png');
const SPOOF = fs.readFileSync(path.join(__dirname, 'spoof.js'), 'utf8');

// ---------- профили ----------
const STORE = () => path.join(app.getPath('userData'), 'profiles.json');
let profiles = [];
function loadProfiles() {
  try { profiles = JSON.parse(fs.readFileSync(STORE(), 'utf8')); } catch { profiles = []; }
  if (!profiles.length) {
    profiles.push({ id: newId(), name: 'Профиль 1', color: '#5b8def', home: 'https://www.google.com/', dev: {}, tabs: [] });
    saveProfiles();
  }
}
function saveProfiles() {
  try { fs.writeFileSync(STORE(), JSON.stringify(profiles, null, 2)); } catch (e) { console.error(e); }
}
function newId() { return 'p' + Date.now().toString(36) + Math.random().toString(36).slice(2, 5); }
const byId = id => profiles.find(p => p.id === id);
const partOf = p => 'persist:p-' + p.id;

// ---------- реальные параметры (серые подсказки) ----------
function real() {
  const d = screen.getPrimaryDisplay();
  return {
    os: 'windows', osver: REAL_WIN, model: '',
    sw: String(d.size.width), sh: String(d.size.height), dpr: String(d.scaleFactor),
    cores: String(os.cpus().length), memory: String(Math.min(8, Math.round(os.totalmem() / 2 ** 30))),
    gpuV: 'как есть', gpuR: 'как есть', touch: '0',
    lang: (app.getPreferredSystemLanguages() || [app.getLocale()]).join(', '),
    tz: Intl.DateTimeFormat().resolvedOptions().timeZone, ua: BASE_UA
  };
}

// ---------- итоговая конфигурация «устройства» профиля ----------
function eff(p) {
  const d = Object.assign({}, p.dev || {});
  const osn = d.os || 'windows';
  const mobile = osn === 'android';
  let ua = (d.ua || '').trim();
  if (!ua) {
    if (osn === 'android') ua = `Mozilla/5.0 (Linux; Android ${d.osver || '14'}; ${d.model || 'K'}) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/${MAJOR}.0.0.0 Mobile Safari/537.36`;
    else if (osn === 'mac') ua = `Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/${MAJOR}.0.0.0 Safari/537.36`;
    else if (osn === 'linux') ua = `Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/${MAJOR}.0.0.0 Safari/537.36`;
    else ua = BASE_UA;
  }
  const platform = { windows: 'Win32', mac: 'MacIntel', linux: 'Linux x86_64', android: 'Linux armv81' }[osn];
  const chPlatform = { windows: 'Windows', mac: 'macOS', linux: 'Linux', android: 'Android' }[osn];
  let pv;
  if (osn === 'windows') pv = (d.osver || REAL_WIN) === '10' ? '10.0.0' : '15.0.0';
  else if (osn === 'mac') pv = (d.osver || '14') + '.5.0';
  else if (osn === 'linux') pv = '6.5.0';
  else pv = (d.osver || '14') + '.0.0';
  const brands = [{ brand: 'Google Chrome', version: MAJOR }, { brand: 'Chromium', version: MAJOR }, { brand: 'Not_A Brand', version: '24' }];
  const full = [{ brand: 'Google Chrome', version: CHROME }, { brand: 'Chromium', version: CHROME }, { brand: 'Not_A Brand', version: '24.0.0.0' }];
  const meta = {
    brands, fullVersionList: full, fullVersion: CHROME, platform: chPlatform, platformVersion: pv,
    architecture: mobile ? '' : (osn === 'mac' ? 'arm' : 'x86'), model: mobile ? (d.model || '') : '',
    mobile, bitness: '64', wow64: false
  };
  let acceptLanguage, locale;
  if (d.lang) {
    const langs = d.lang.split(',').map(s => s.trim()).filter(Boolean);
    if (langs.length) {
      locale = langs[0];
      acceptLanguage = langs.map((l, i) => i ? `${l};q=${Math.max(0.1, 1 - i * 0.1).toFixed(1)}` : l).join(',');
    }
  }
  const cfg = {
    memory: d.memory || '', cores: d.cores || '', touch: d.touch ?? (mobile ? '5' : ''), lang: d.lang || '',
    sw: d.sw || '', sh: d.sh || '', dpr: d.dpr || '', gpuV: d.gpuV || '', gpuR: d.gpuR || '', desktop: !mobile
  };
  return { ua, platform, meta, acceptLanguage, locale, tz: d.tz || '', mobile, cfg, d };
}

function prepareSession(p) {
  const ses = session.fromPartition(partOf(p));
  const e = eff(p);
  ses.setUserAgent(e.ua, e.acceptLanguage);
  if (!ses.__persona) {
    ses.__persona = true;
    ses.setPermissionRequestHandler((_wc, perm, cb) => cb(true));
  }
  return ses;
}

// Настройка вкладки: подмена через протокол DevTools до первой загрузки
async function setupContents(wc, p) {
  const e = eff(p);
  const dbg = wc.debugger;
  try { if (!dbg.isAttached()) dbg.attach('1.3'); } catch { return; }
  const s = (m, a) => dbg.sendCommand(m, a).catch(() => {});
  await s('Page.enable');
  await s('Page.addScriptToEvaluateOnNewDocument', { source: SPOOF.replace('__CFG__', JSON.stringify(e.cfg)), runImmediately: true });
  await s('Emulation.setUserAgentOverride', { userAgent: e.ua, acceptLanguage: e.acceptLanguage, platform: e.platform, userAgentMetadata: e.meta });
  if (e.tz) await s('Emulation.setTimezoneOverride', { timezoneId: e.tz });
  if (e.locale) await s('Emulation.setLocaleOverride', { locale: e.locale });
  if (e.cfg.cores) await s('Emulation.setHardwareConcurrencyOverride', { hardwareConcurrency: Number(e.cfg.cores) });
  if (e.mobile) {
    await s('Emulation.setDeviceMetricsOverride', {
      width: 0, height: 0, deviceScaleFactor: Number(e.d.dpr) || 0, mobile: true,
      screenWidth: Number(e.d.sw) || 0, screenHeight: Number(e.d.sh) || 0
    });
    await s('Emulation.setTouchEmulationEnabled', { enabled: true, maxTouchPoints: Number(e.cfg.touch) || 5 });
  }
  attachHelpers(wc, p);
}

// ---------- окна ----------
const wins = new Map(); // id окна -> { win, pid, app, tabs }
let manager = null;

function openManager() {
  if (manager && !manager.isDestroyed()) { manager.show(); manager.focus(); return; }
  manager = new BrowserWindow({
    width: 980, height: 720, minWidth: 720, minHeight: 500, title: 'Persona — профили', icon: ICON,
    backgroundColor: '#17181b', autoHideMenuBar: true,
    webPreferences: { preload: path.join(__dirname, 'preload.js'), contextIsolation: true }
  });
  manager.setMenu(null);
  manager.loadFile(path.join(__dirname, 'manager.html'));
  manager.on('closed', () => { manager = null; });
}

function openProfile(pid, url, appMode) {
  const p = byId(pid);
  if (!p) { openManager(); return; }
  prepareSession(p);
  if (!appMode && url) {
    for (const info of wins.values()) {
      if (info.pid === pid && !info.app && !info.win.isDestroyed()) {
        info.win.webContents.send('open-tab', url);
        if (info.win.isMinimized()) info.win.restore();
        info.win.focus();
        return;
      }
    }
  }
  const e = eff(p);
  let tabs;
  if (url) tabs = [url];
  else if (!appMode && p.tabs && p.tabs.length) tabs = p.tabs;
  else tabs = [p.home || 'https://www.google.com/'];
  const host = (() => { try { return new URL(tabs[0]).hostname; } catch { return ''; } })();
  const win = new BrowserWindow({
    width: e.mobile ? 460 : 1280, height: e.mobile ? 900 : 860, minWidth: 360, minHeight: 300,
    title: appMode ? host : `${p.name} — Persona`, icon: ICON, backgroundColor: '#17181b', autoHideMenuBar: true,
    webPreferences: { preload: path.join(__dirname, 'preload.js'), contextIsolation: true, webviewTag: true }
  });
  win.setMenu(null);
  if (process.platform === 'win32') {
    try {
      const appId = appMode ? `Persona.${p.id}.${host}` : `Persona.${p.id}`;
      const ico = appMode ? siteIconPath(host) : null;
      win.setAppDetails({ appId, appIconPath: ico && fs.existsSync(ico) ? ico : undefined });
      if (ico && fs.existsSync(ico)) win.setIcon(ico);
    } catch {}
  }
  const info = { win, pid, app: !!appMode, tabs: tabs.slice() };
  wins.set(win.id, info);
  win.loadFile(path.join(__dirname, 'browser.html'), {
    query: { pid: p.id, name: p.name, color: p.color || '#5b8def', app: appMode ? '1' : '', tabs: JSON.stringify(tabs), part: partOf(p) }
  });
  win.on('close', () => {
    if (!info.app) {
      const q = byId(pid);
      if (q) { q.tabs = info.tabs.filter(u => /^https?:/i.test(u)).slice(0, 30); saveProfiles(); }
    }
  });
  win.on('closed', () => wins.delete(win.id));
  return win;
}

function closeProfile(pid) {
  for (const info of [...wins.values()]) if (info.pid === pid && !info.win.isDestroyed()) info.win.close();
}

function hostWindowOf(wc) {
  const host = wc.hostWebContents || wc;
  return BrowserWindow.fromWebContents(host);
}

// ---------- вкладки: всплывающие окна, клавиши, контекстное меню ----------
function attachHelpers(wc, p) {
  if (wc.__personaHelpers) return;
  wc.__personaHelpers = true;
  const host = wc.hostWebContents;

  wc.setWindowOpenHandler(({ url, disposition }) => {
    if (disposition === 'new-window') {
      // окна входа (Google, VK и т.п.) — настоящим всплывающим окном с тем же профилем
      return {
        action: 'allow',
        overrideBrowserWindowOptions: { width: 520, height: 720, autoHideMenuBar: true, icon: ICON, backgroundColor: '#ffffff' }
      };
    }
    if (host && !host.isDestroyed()) host.send('open-tab', url, disposition !== 'background-tab');
    else openProfile(p.id, url, false);
    return { action: 'deny' };
  });
  wc.on('did-create-window', child => {
    child.setMenu(null);
    setupContents(child.webContents, p);
  });

  wc.on('before-input-event', (ev, i) => {
    if (i.type !== 'keyDown') return;
    const k = (i.key || '').toLowerCase();
    const c = i.control || i.meta;
    let cmd = null;
    if (c && k === 't') cmd = 'new-tab';
    else if (c && k === 'w') cmd = 'close-tab';
    else if (c && k === 'l') cmd = 'focus-address';
    else if (c && k === 'n') cmd = 'new-window';
    else if (c && k === 'f') cmd = 'find';
    else if (c && k === 'tab') cmd = i.shift ? 'prev-tab' : 'next-tab';
    else if ((c && k === 'r') || k === 'f5') { i.shift ? wc.reloadIgnoringCache() : wc.reload(); ev.preventDefault(); return; }
    else if (i.alt && k === 'arrowleft') { if (wc.canGoBack()) wc.goBack(); ev.preventDefault(); return; }
    else if (i.alt && k === 'arrowright') { if (wc.canGoForward()) wc.goForward(); ev.preventDefault(); return; }
    else if (k === 'f12') { wc.toggleDevTools(); ev.preventDefault(); return; }
    else if (c && (k === '=' || k === '+')) { wc.setZoomLevel(wc.getZoomLevel() + 0.5); ev.preventDefault(); return; }
    else if (c && k === '-') { wc.setZoomLevel(wc.getZoomLevel() - 0.5); ev.preventDefault(); return; }
    else if (c && k === '0') { wc.setZoomLevel(0); ev.preventDefault(); return; }
    else if (k === 'escape') cmd = 'escape';
    if (cmd && host && !host.isDestroyed()) {
      if (cmd !== 'escape') ev.preventDefault();
      host.send('key', cmd);
    }
  });

  wc.on('context-menu', (_e, m) => {
    const t = [];
    const send = (...a) => host && !host.isDestroyed() && host.send(...a);
    if (m.linkURL) {
      t.push({ label: 'Открыть в новой вкладке', click: () => send('open-tab', m.linkURL, false) });
      t.push({ label: 'Открыть в другом браузере', click: () => shell.openExternal(m.linkURL) });
      t.push({ label: 'Копировать ссылку', click: () => clipboard.writeText(m.linkURL) });
      t.push({ type: 'separator' });
    }
    if (m.mediaType === 'image' && m.srcURL) {
      t.push({ label: 'Открыть картинку в новой вкладке', click: () => send('open-tab', m.srcURL, false) });
      t.push({ label: 'Сохранить картинку…', click: () => wc.downloadURL(m.srcURL) });
      t.push({ label: 'Копировать картинку', click: () => wc.copyImageAt(m.x, m.y) });
      t.push({ type: 'separator' });
    }
    if (m.isEditable) {
      if (m.misspelledWord && m.dictionarySuggestions.length) {
        m.dictionarySuggestions.slice(0, 5).forEach(s => t.push({ label: s, click: () => wc.replaceMisspelling(s) }));
        t.push({ type: 'separator' });
      }
      t.push({ label: 'Отменить', role: 'undo' }, { label: 'Повторить', role: 'redo' }, { type: 'separator' },
        { label: 'Вырезать', role: 'cut' }, { label: 'Копировать', role: 'copy' }, { label: 'Вставить', role: 'paste' },
        { label: 'Выделить всё', role: 'selectAll' }, { type: 'separator' });
    } else if (m.selectionText) {
      const q = m.selectionText.trim().slice(0, 200);
      t.push({ label: 'Копировать', role: 'copy' });
      t.push({ label: `Найти в Google «${q.slice(0, 24)}${q.length > 24 ? '…' : ''}»`, click: () => send('open-tab', 'https://www.google.com/search?q=' + encodeURIComponent(q), false) });
      t.push({ type: 'separator' });
    }
    t.push({ label: 'Назад', enabled: wc.canGoBack(), click: () => wc.goBack() });
    t.push({ label: 'Вперёд', enabled: wc.canGoForward(), click: () => wc.goForward() });
    t.push({ label: 'Обновить', click: () => wc.reload() });
    t.push({ type: 'separator' });
    t.push({ label: 'Просмотреть код', click: () => { wc.inspectElement(m.x, m.y); } });
    Menu.buildFromTemplate(t).popup({ window: hostWindowOf(wc) || undefined });
  });
}

// ---------- ярлыки на рабочем столе ----------
function siteIconPath(host) {
  return path.join(app.getPath('userData'), 'icons', host.replace(/[^a-z0-9.-]/gi, '_') + '.ico');
}
function pngToIco(png) {
  const w = png.readUInt32BE(16), h = png.readUInt32BE(20);
  const head = Buffer.alloc(22);
  head.writeUInt16LE(0, 0); head.writeUInt16LE(1, 2); head.writeUInt16LE(1, 4);
  head.writeUInt8(w >= 256 ? 0 : w, 6); head.writeUInt8(h >= 256 ? 0 : h, 7);
  head.writeUInt16LE(1, 10); head.writeUInt16LE(32, 12);
  head.writeUInt32LE(png.length, 14); head.writeUInt32LE(22, 18);
  return Buffer.concat([head, png]);
}
async function fetchSiteIcon(host) {
  const file = siteIconPath(host);
  try {
    fs.mkdirSync(path.dirname(file), { recursive: true });
    const r = await net.fetch(`https://www.google.com/s2/favicons?domain=${encodeURIComponent(host)}&sz=128`);
    if (!r.ok) return null;
    let png = Buffer.from(await r.arrayBuffer());
    if (png.slice(1, 4).toString() !== 'PNG') {
      const img = nativeImage.createFromBuffer(png);
      if (img.isEmpty()) return null;
      png = img.toPNG();
    }
    const img = nativeImage.createFromBuffer(png);
    const sz = img.getSize();
    if (sz.width < 64) png = img.resize({ width: 64, height: 64, quality: 'best' }).toPNG();
    fs.writeFileSync(file, pngToIco(png));
    return file;
  } catch { return null; }
}
async function makeShortcut(pid, url, name) {
  const p = byId(pid);
  if (!p) return { ok: false, error: 'Профиль не найден' };
  let u;
  try { u = new URL(/^https?:\/\//i.test(url) ? url : 'https://' + url); } catch { return { ok: false, error: 'Неверный адрес' }; }
  const host = u.hostname;
  const title = (name || host).trim();
  const icon = await fetchSiteIcon(host);
  const exe = process.env.PORTABLE_EXECUTABLE_FILE || process.execPath;
  const pre = app.isPackaged ? '' : `"${app.getAppPath()}" `;
  const safe = `${title} (${p.name})`.replace(/[\\/:*?"<>|]/g, '_');
  const lnk = path.join(app.getPath('desktop'), safe + '.lnk');
  const ok = shell.writeShortcutLink(lnk, 'create', {
    target: exe,
    args: `${pre}--persona-profile=${p.id} --persona-app --persona-url="${u.href}"`,
    icon: icon || exe, iconIndex: 0,
    appUserModelId: `Persona.${p.id}.${host}`,
    description: `${title} — профиль «${p.name}» в Persona`
  });
  return ok ? { ok: true, file: lnk } : { ok: false, error: 'Windows не дал создать ярлык' };
}

// ---------- IPC ----------
ipcMain.handle('meta', () => ({ presets: PRESETS, fields: FIELDS, real: real(), version: app.getVersion(), chrome: CHROME }));
ipcMain.handle('list', () => profiles.map(p => ({ ...p, open: [...wins.values()].some(w => w.pid === p.id) })));
ipcMain.handle('save', (_e, p) => {
  if (!p.id) { p.id = newId(); p.tabs = []; profiles.push(p); }
  else {
    const i = profiles.findIndex(x => x.id === p.id);
    if (i < 0) profiles.push(p); else profiles[i] = { ...profiles[i], ...p };
  }
  saveProfiles();
  return p.id;
});
ipcMain.handle('delete', async (_e, id) => {
  const p = byId(id);
  if (!p) return;
  const r = await dialog.showMessageBox(manager || undefined, {
    type: 'warning', buttons: ['Удалить', 'Отмена'], defaultId: 1, cancelId: 1,
    title: 'Удалить профиль', message: `Удалить профиль «${p.name}»?`,
    detail: 'Все входы, куки и данные сайтов этого профиля будут стёрты. Ярлыки на рабочем столе перестанут работать.'
  });
  if (r.response !== 0) return false;
  closeProfile(id);
  try { const ses = session.fromPartition(partOf(p)); await ses.clearStorageData(); await ses.clearCache(); } catch {}
  profiles = profiles.filter(x => x.id !== id);
  saveProfiles();
  return true;
});
ipcMain.handle('reorder', (_e, ids) => {
  profiles.sort((a, b) => ids.indexOf(a.id) - ids.indexOf(b.id));
  saveProfiles();
});
ipcMain.handle('open', (_e, id, url) => { openProfile(id, url || null, false); });
ipcMain.handle('shortcut', (_e, id, url, name) => makeShortcut(id, url, name));
ipcMain.handle('setup-tab', async (e, wcId) => {
  const info = wins.get(BrowserWindow.fromWebContents(e.sender)?.id);
  const p = info && byId(info.pid);
  const wc = webContents.fromId(wcId);
  if (p && wc) await setupContents(wc, p);
  return true;
});
ipcMain.on('tabs-state', (e, urls) => {
  const info = wins.get(BrowserWindow.fromWebContents(e.sender)?.id);
  if (info) info.tabs = urls;
});
ipcMain.handle('action', (e, what, a, b) => {
  const win = BrowserWindow.fromWebContents(e.sender);
  const info = win && wins.get(win.id);
  if (what === 'profiles') openManager();
  else if (what === 'close-profile' && info) closeProfile(info.pid);
  else if (what === 'new-window' && info) {
    const w = openProfile(info.pid, null, false);
    w && w.webContents.once('did-finish-load', () => {});
  }
  else if (what === 'open-in-profile' && info) openProfile(info.pid, a, false);
  else if (what === 'shortcut' && info) return makeShortcut(info.pid, a, b);
  else if (what === 'devtools') { const wc = webContents.fromId(a); wc && wc.openDevTools(); }
  else if (what === 'external') shell.openExternal(a);
  else if (what === 'title' && win) win.setTitle(a);
});

// ---------- запуск ----------
function parseArgs(argv) {
  const o = {};
  for (const a of argv) {
    let m;
    if ((m = a.match(/^--persona-profile=(.+)$/))) o.pid = m[1];
    else if ((m = a.match(/^--persona-url=(.+)$/))) o.url = m[1].replace(/^"|"$/g, '');
    else if (a === '--persona-app') o.app = true;
  }
  return o;
}
function handleArgs(argv) {
  const a = parseArgs(argv);
  if (a.pid && byId(a.pid)) openProfile(a.pid, a.url || null, !!a.app);
  else openManager();
}

if (!app.requestSingleInstanceLock()) {
  app.quit();
} else {
  app.on('second-instance', (_e, argv) => handleArgs(argv));
  app.whenReady().then(() => {
    loadProfiles();
    app.on('web-contents-created', (_e, wc) => {
      wc.on('will-attach-webview', (ev, prefs, params) => {
        delete prefs.preload;
        prefs.nodeIntegration = false;
        prefs.contextIsolation = true;
        if (!/^persist:p-/.test(params.partition || '')) ev.preventDefault();
      });
    });
    handleArgs(process.argv);
  });
  app.on('window-all-closed', () => app.quit());
}
