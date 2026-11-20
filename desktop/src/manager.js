'use strict';
const api = window.persona;
const $ = id => document.getElementById(id);
const COLORS = ['#5b8def', '#e5534b', '#57ab5a', '#c69026', '#986ee2', '#e275ad', '#39c5cf', '#f0883e', '#768390'];
const OS = { '': 'как есть (Windows)', windows: 'Windows', mac: 'macOS', linux: 'Linux', android: 'Android (телефон)' };
let META, editing = null, color = COLORS[0];

function toast(s) { const el = $('toast'); el.textContent = s; el.style.display = 'block'; clearTimeout(toast.t); toast.t = setTimeout(() => el.style.display = 'none', 4000); }
function esc(s) { return String(s).replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c])); }

function devSummary(d) {
  d = d || {};
  if (!Object.values(d).some(v => v)) return 'Устройство: как есть';
  const parts = [];
  if (d.os) parts.push(OS[d.os] + (d.osver ? ' ' + d.osver : ''));
  if (d.model) parts.push(d.model);
  if (d.sw && d.sh) parts.push(`${d.sw}×${d.sh}`);
  if (d.tz) parts.push(d.tz);
  if (d.lang) parts.push(d.lang.split(',')[0]);
  return 'Подмена: ' + (parts.join(', ') || 'частично');
}

async function refresh() {
  const list = await api.invoke('list');
  const root = $('list');
  root.innerHTML = '';
  if (!list.length) { root.innerHTML = '<div class="empty">Профилей пока нет</div>'; return; }
  for (const p of list) {
    const c = document.createElement('div');
    c.className = 'card';
    c.innerHTML = `<div class="top"><div class="dot" style="background:${esc(p.color || COLORS[0])}">${esc((p.name || '?').trim()[0] || '?').toUpperCase()}</div>
      <div class="nm">${esc(p.name)}</div>${p.open ? '<span class="on">● открыт</span>' : ''}</div>
      <div class="dev">${esc(devSummary(p.dev))}</div>
      <div class="btns"><button class="pri" data-a="open">Открыть</button><button data-a="sc">Ярлык сайта</button>
      <button data-a="edit">Изменить</button><button class="del" data-a="del">Удалить</button></div>`;
    c.querySelector('[data-a=open]').onclick = () => api.invoke('open', p.id).then(() => setTimeout(refresh, 500));
    c.querySelector('[data-a=sc]').onclick = () => openShortcut(p);
    c.querySelector('[data-a=edit]').onclick = () => openEditor(p);
    c.querySelector('[data-a=del]').onclick = async () => { if (await api.invoke('delete', p.id)) refresh(); };
    c.querySelector('.top').ondblclick = () => api.invoke('open', p.id);
    root.appendChild(c);
  }
}

function buildColors() {
  const el = $('colors');
  el.innerHTML = '';
  for (const c of COLORS) {
    const s = document.createElement('span');
    s.style.background = c;
    if (c === color) s.className = 'sel';
    s.onclick = () => { color = c; buildColors(); };
    el.appendChild(s);
  }
}

function buildFields(dev) {
  const root = $('fields');
  root.innerHTML = '';
  for (const [k, label] of META.fields) {
    const w = document.createElement('div');
    if (k === 'ua' || k === 'gpuR') w.className = 'wide';
    if (k === 'os') {
      w.innerHTML = `<label>${esc(label)}</label><select id="d_os">${Object.entries(OS).map(([v, n]) => `<option value="${v}">${esc(n)}</option>`).join('')}</select>`;
      root.appendChild(w);
      w.querySelector('select').value = dev.os || '';
      continue;
    }
    const ph = META.real[k] || '';
    w.innerHTML = `<label>${esc(label)}</label><input id="d_${k}" spellcheck="false" placeholder="${esc(ph)}">`;
    root.appendChild(w);
    w.querySelector('input').value = dev[k] || '';
  }
}

function readDev() {
  const d = {};
  for (const [k] of META.fields) {
    const el = $('d_' + k);
    const v = el && el.value.trim();
    if (v) d[k] = v;
  }
  return d;
}

function openEditor(p) {
  editing = p ? JSON.parse(JSON.stringify(p)) : { name: '', color: COLORS[Math.floor(Math.random() * COLORS.length)], home: '', dev: {} };
  $('edTitle').textContent = p ? 'Изменить профиль' : 'Новый профиль';
  $('f_name').value = editing.name || '';
  $('f_home').value = editing.home || '';
  color = editing.color || COLORS[0];
  buildColors();
  const sel = $('preset');
  sel.innerHTML = '<option value="">— выбрать —</option>' + META.presets.map((x, i) => `<option value="${i}">${esc(x.name)}</option>`).join('');
  sel.value = '';
  buildFields(editing.dev || {});
  $('ed').showModal();
  $('f_name').focus();
}

$('preset').onchange = () => {
  const i = $('preset').value;
  if (i === '') return;
  const keep = readDev();
  const pr = META.presets[+i].values;
  const d = { lang: keep.lang, tz: keep.tz, ...pr };
  buildFields(d);
};
$('clearDev').onclick = () => { $('preset').value = ''; buildFields({}); };

$('ed').addEventListener('close', async () => {
  if ($('ed').returnValue !== 'ok' || !editing) return;
  const name = $('f_name').value.trim();
  if (!name) return;
  editing.name = name;
  editing.color = color;
  let home = $('f_home').value.trim();
  if (home && !/^https?:\/\//i.test(home)) home = 'https://' + home;
  editing.home = home;
  editing.dev = readDev();
  delete editing.open;
  delete editing.tabs;
  await api.invoke('save', editing);
  editing = null;
  refresh();
  toast('Сохранено. Открытые окна профиля применят изменения после переоткрытия.');
});

let scProfile = null;
function openShortcut(p) {
  scProfile = p;
  $('scHint').textContent = `Сайт будет открываться отдельным окном в профиле «${p.name}».`;
  $('sc_url').value = '';
  $('sc_name').value = '';
  $('sc').showModal();
}
$('sc').addEventListener('close', async () => {
  if ($('sc').returnValue !== 'ok' || !scProfile) return;
  const r = await api.invoke('shortcut', scProfile.id, $('sc_url').value.trim(), $('sc_name').value.trim());
  toast(r.ok ? 'Ярлык создан на рабочем столе' : 'Не получилось: ' + r.error);
});

$('add').onclick = () => openEditor(null);
window.addEventListener('focus', refresh);

(async () => {
  META = await api.invoke('meta');
  $('ver').textContent = `версия ${META.version} · движок Chromium ${META.chrome.split('.')[0]}`;
  refresh();
})();
