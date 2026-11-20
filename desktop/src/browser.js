'use strict';
const Q = new URLSearchParams(location.search);
const PID = Q.get('pid'), PNAME = Q.get('name'), COLOR = Q.get('color'), APP = !!Q.get('app'), PART = Q.get('part');
const $ = id => document.getElementById(id);
const api = window.persona;

document.documentElement.style.setProperty('--acc', COLOR);
if (APP) document.body.classList.add('app');
$('pname').textContent = PNAME;

const tabs = [];
let cur = null;
const BLANK_ICON = 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 16 16"><circle cx="8" cy="8" r="6.5" fill="none" stroke="%239aa0aa" stroke-width="1.5"/></svg>';

function fixUrl(s) {
  s = s.trim();
  if (!s) return null;
  if (/^[a-z][a-z0-9+.-]*:\/\//i.test(s) || /^(about|data|file):/i.test(s)) return s;
  if (/^localhost(:\d+)?(\/|$)/i.test(s) || /^\d{1,3}(\.\d{1,3}){3}(:\d+)?(\/|$)/.test(s)) return 'http://' + s;
  if (!/\s/.test(s) && /^[^/]+\.[a-zа-яё]{2,}(:\d+)?(\/.*)?$/i.test(s)) return 'https://' + s;
  return 'https://www.google.com/search?q=' + encodeURIComponent(s);
}

function syncState() {
  api.send('tabs-state', tabs.map(t => t.url).filter(u => u && u !== 'about:blank'));
}

function newTab(url, activate = true, after = cur) {
  const wv = document.createElement('webview');
  wv.setAttribute('partition', PART);
  wv.setAttribute('allowpopups', '');
  wv.src = 'about:blank';
  const el = document.createElement('div');
  el.className = 'tab';
  el.innerHTML = '<img><span class="t"></span><span class="x" title="Закрыть (Ctrl+W)">×</span>';
  el.querySelector('img').src = BLANK_ICON;
  const t = { wv, el, url: url || '', title: 'Новая вкладка', ready: false, icon: null };
  el.querySelector('.t').textContent = t.title;
  el.addEventListener('mousedown', e => { if (e.button === 1) { e.preventDefault(); closeTab(t); } else if (!e.target.classList.contains('x')) activateTab(t); });
  el.querySelector('.x').addEventListener('click', e => { e.stopPropagation(); closeTab(t); });

  const idx = after ? tabs.indexOf(after) + 1 : tabs.length;
  tabs.splice(idx, 0, t);
  const strip = $('tabs');
  const ref = tabs[idx + 1] ? tabs[idx + 1].el : $('plus');
  strip.insertBefore(el, ref);
  $('views').appendChild(wv);

  wv.addEventListener('dom-ready', async () => {
    if (t.ready) return;
    t.ready = true;
    await api.invoke('setup-tab', wv.getWebContentsId());
    if (t.url) wv.loadURL(t.url);
    else if (cur === t) setTimeout(() => { $('addr').focus(); }, 0);
  });
  wv.addEventListener('page-title-updated', e => { t.title = e.title || t.title; render(t); });
  wv.addEventListener('page-favicon-updated', e => { t.icon = e.favicons && e.favicons[0]; render(t); });
  const nav = e => {
    if (e.isMainFrame === false) return;
    if (!t.ready || e.url === 'about:blank') return;
    t.url = e.url; render(t); syncState();
  };
  wv.addEventListener('did-navigate', nav);
  wv.addEventListener('did-navigate-in-page', nav);
  wv.addEventListener('did-start-loading', () => { t.loading = true; render(t); });
  wv.addEventListener('did-stop-loading', () => { t.loading = false; render(t); });
  wv.addEventListener('did-fail-load', e => {
    if (e.isMainFrame && e.errorCode !== -3) { t.title = 'Не удалось открыть страницу'; render(t); }
  });
  wv.addEventListener('found-in-page', e => {
    const r = e.result;
    $('findn').textContent = r.matches ? `${r.activeMatchOrdinal}/${r.matches}` : '0/0';
  });
  wv.addEventListener('close', () => closeTab(t));

  if (activate || !cur) activateTab(t);
  syncState();
  return t;
}

function render(t) {
  t.el.querySelector('.t').textContent = t.title;
  t.el.title = t.title + (t.url ? '\n' + t.url : '');
  t.el.querySelector('img').src = t.icon || BLANK_ICON;
  t.el.classList.toggle('loading', !!t.loading);
  if (t === cur) updateBar();
}

function updateBar() {
  const t = cur;
  if (!t) return;
  if (document.activeElement !== $('addr')) $('addr').value = t.url && t.url !== 'about:blank' ? t.url : '';
  let ok = t.ready;
  $('back').disabled = !(ok && t.wv.canGoBack());
  $('fwd').disabled = !(ok && t.wv.canGoForward());
  $('reload').textContent = t.loading ? '✕' : '⟳';
  $('reload').title = t.loading ? 'Остановить' : 'Обновить (F5)';
  $('apptitle').textContent = t.title;
  const title = APP ? t.title : `${t.title} — ${PNAME}`;
  if (document.title !== title) { document.title = title; api.invoke('action', 'title', title); }
}

function activateTab(t) {
  if (cur) { cur.el.classList.remove('active'); cur.wv.classList.remove('active'); }
  cur = t;
  t.el.classList.add('active');
  t.wv.classList.add('active');
  t.el.scrollIntoView({ block: 'nearest', inline: 'nearest' });
  updateBar();
  if (t.ready && t.url) t.wv.focus();
}

function closeTab(t) {
  const i = tabs.indexOf(t);
  if (i < 0) return;
  tabs.splice(i, 1);
  t.el.remove(); t.wv.remove();
  if (!tabs.length) { syncState(); window.close(); return; }
  if (cur === t) { cur = null; activateTab(tabs[Math.min(i, tabs.length - 1)]); }
  syncState();
}

function go(s) {
  const u = fixUrl(s);
  if (!u || !cur) return;
  cur.url = u;
  if (cur.ready) cur.wv.loadURL(u);
  cur.wv.focus();
}

// ----- панель -----
$('plus').onclick = () => newTab('', true, tabs[tabs.length - 1]);
$('back').onclick = () => cur && cur.wv.canGoBack() && cur.wv.goBack();
$('fwd').onclick = () => cur && cur.wv.canGoForward() && cur.wv.goForward();
$('reload').onclick = () => { if (!cur || !cur.ready) return; cur.loading ? cur.wv.stop() : cur.wv.reload(); };
$('addr').addEventListener('keydown', e => {
  if (e.key === 'Enter') go($('addr').value);
  else if (e.key === 'Escape') { updateBarForce(); cur && cur.wv.focus(); }
});
$('addr').addEventListener('focus', () => setTimeout(() => $('addr').select(), 0));
function updateBarForce() { $('addr').blur(); updateBar(); }

// ----- меню -----
const menu = $('menu');
function toast(s) { const el = $('toast'); el.textContent = s; el.style.display = 'block'; clearTimeout(toast.t); toast.t = setTimeout(() => el.style.display = 'none', 3500); }
function buildMenu() {
  const items = APP ? [
    ['Открыть во вкладке профиля', () => cur && api.invoke('action', 'open-in-profile', cur.url)],
    ['Открыть в другом браузере', () => cur && api.invoke('action', 'external', cur.url)],
    ['Копировать адрес', () => cur && navigator.clipboard.writeText(cur.url)],
    '-',
    ['Найти на странице  (Ctrl+F)', openFind],
    ['Профили', () => api.invoke('action', 'profiles')],
  ] : [
    ['Новая вкладка  (Ctrl+T)', () => newTab('', true, tabs[tabs.length - 1])],
    ['Новое окно профиля  (Ctrl+N)', () => api.invoke('action', 'new-window')],
    '-',
    ['Ярлык этого сайта на рабочий стол', makeShortcut],
    ['Найти на странице  (Ctrl+F)', openFind],
    ['Открыть в другом браузере', () => cur && api.invoke('action', 'external', cur.url)],
    '-',
    ['Профили', () => api.invoke('action', 'profiles')],
    ['Закрыть профиль', () => api.invoke('action', 'close-profile')],
    '-',
    ['Инструменты разработчика  (F12)', () => cur && cur.ready && api.invoke('action', 'devtools', cur.wv.getWebContentsId())],
  ];
  menu.innerHTML = '';
  for (const it of items) {
    if (it === '-') { menu.appendChild(document.createElement('hr')); continue; }
    const d = document.createElement('div');
    d.textContent = it[0];
    d.onclick = () => { hideMenu(); it[1](); };
    menu.appendChild(d);
  }
}
function hideMenu() { menu.style.display = 'none'; }
$('more').onclick = e => { e.stopPropagation(); if (menu.style.display === 'block') return hideMenu(); buildMenu(); menu.style.display = 'block'; };
document.addEventListener('click', e => { if (!menu.contains(e.target)) hideMenu(); });
window.addEventListener('blur', hideMenu);

async function makeShortcut() {
  if (!cur || !/^https?:/.test(cur.url)) return toast('Сначала откройте сайт');
  const name = (cur.title || '').split(/ [-—|·] /)[0].slice(0, 40) || new URL(cur.url).hostname;
  const r = await api.invoke('action', 'shortcut', cur.url, name);
  toast(r && r.ok ? `Ярлык «${name}» создан на рабочем столе` : 'Не удалось создать ярлык' + (r && r.error ? ': ' + r.error : ''));
}

// ----- поиск на странице -----
function openFind() { $('find').style.display = 'flex'; $('findq').focus(); $('findq').select(); }
function closeFind() { $('find').style.display = 'none'; $('findn').textContent = ''; if (cur && cur.ready) { cur.wv.stopFindInPage('clearSelection'); cur.wv.focus(); } }
function doFind(forward = true, next = true) {
  const q = $('findq').value;
  if (!cur || !cur.ready) return;
  if (!q) { cur.wv.stopFindInPage('clearSelection'); $('findn').textContent = ''; return; }
  cur.wv.findInPage(q, { forward, findNext: next });
}
$('findq').addEventListener('input', () => doFind(true, false));
$('findq').addEventListener('keydown', e => { if (e.key === 'Enter') doFind(!e.shiftKey, true); else if (e.key === 'Escape') closeFind(); });
$('findnx').onclick = () => doFind(true, true);
$('findp').onclick = () => doFind(false, true);
$('findx').onclick = closeFind;

// ----- клавиши -----
function command(cmd) {
  if (cmd === 'new-tab') APP ? api.invoke('action', 'open-in-profile', '') : newTab('', true, tabs[tabs.length - 1]);
  else if (cmd === 'close-tab') cur && closeTab(cur);
  else if (cmd === 'focus-address') { if (!APP) { $('addr').focus(); $('addr').select(); } }
  else if (cmd === 'new-window') api.invoke('action', 'new-window');
  else if (cmd === 'find') openFind();
  else if (cmd === 'next-tab' || cmd === 'prev-tab') {
    if (tabs.length < 2 || !cur) return;
    const i = tabs.indexOf(cur), d = cmd === 'next-tab' ? 1 : -1;
    activateTab(tabs[(i + d + tabs.length) % tabs.length]);
  }
  else if (cmd === 'escape') { if ($('find').style.display === 'flex') closeFind(); hideMenu(); }
  else if (cmd === 'reload') cur && cur.ready && cur.wv.reload();
}
api.on('key', command);
document.addEventListener('keydown', e => {
  const c = e.ctrlKey || e.metaKey, k = e.key.toLowerCase();
  let cmd = null;
  if (c && k === 't') cmd = 'new-tab';
  else if (c && k === 'w') cmd = 'close-tab';
  else if (c && k === 'l') cmd = 'focus-address';
  else if (c && k === 'n') cmd = 'new-window';
  else if (c && k === 'f') cmd = 'find';
  else if (c && k === 'tab') cmd = e.shiftKey ? 'prev-tab' : 'next-tab';
  else if ((c && k === 'r') || k === 'f5') cmd = 'reload';
  if (cmd) { e.preventDefault(); command(cmd); }
});

api.on('open-tab', (url, activate = true) => {
  if (APP) { api.invoke('action', 'open-in-profile', url); return; }
  newTab(fixUrl(url) || url, activate !== false);
});

// ----- старт -----
let initial = [];
try { initial = JSON.parse(Q.get('tabs') || '[]'); } catch {}
if (!initial.length) initial = ['https://www.google.com/'];
initial.forEach((u, i) => newTab(u, i === 0, tabs[tabs.length - 1]));
setInterval(() => cur && updateBar(), 1000);
