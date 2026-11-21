'use strict';
/* ===== core helpers shared by every page ===== */
const App = { me: null, currencies: [], cats: [], accts: [] };
const Pages = {};           // page renderers; initialize before page modules load
const Actions = {};          // click handlers keyed by data-act
const $ = (s, r = document) => r.querySelector(s);
const $$ = (s, r = document) => Array.from(r.querySelectorAll(s));
const PAYMENT_METHODS = ['cash', 'upi', 'debit card', 'credit card', 'bank transfer', 'wallet', 'cheque', 'other'];
const ACCOUNT_TYPES = ['cash', 'bank', 'card', 'upi', 'wallet', 'other'];

function esc(s) {
  return String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}
function cap(s) { s = String(s || ''); return s.charAt(0).toUpperCase() + s.slice(1); }

/* ---------- theme ---------- */
function applyTheme(pref) {
  const dark = pref === 'dark' || (pref !== 'light' && window.matchMedia('(prefers-color-scheme: dark)').matches);
  document.documentElement.dataset.theme = dark ? 'dark' : 'light';
}
applyTheme(localStorage.getItem('mm-theme') || 'system');

/* ---------- API ---------- */
async function api(method, url, body) {
  const opts = { method, credentials: 'same-origin', headers: {} };
  if (body instanceof FormData) opts.body = body;
  else if (body !== undefined) { opts.headers['Content-Type'] = 'application/json'; opts.body = JSON.stringify(body); }
  let res;
  try { res = await fetch(url, opts); }
  catch (e) { throw new Error('Cannot reach the server. Please check that the app is running.'); }
  if (res.status === 204) return null;
  let data = null;
  try { data = await res.json(); } catch (e) { /* no body */ }
  if (!res.ok) {
    if (res.status === 401 && !url.startsWith('/api/auth/')) { App.me = null; if (typeof showAuth === 'function') showAuth(); }
    throw new Error((data && data.message) || 'Something went wrong. Please try again.');
  }
  return data;
}
const qs = obj => {
  const p = new URLSearchParams();
  Object.entries(obj).forEach(([k, v]) => { if (v !== undefined && v !== null && v !== '') p.set(k, v); });
  const s = p.toString();
  return s ? '?' + s : '';
};

/* ---------- money & dates ---------- */
function currencyMeta(code) { return App.currencies.find(c => c.code === (code || (App.me && App.me.currency))) || { code: 'INR', symbol: '₹', locale: 'en-IN', decimals: 2 }; }
function money(v, o = {}) {
  const meta = currencyMeta(o.currency);
  const n = Number(v) || 0;
  let s;
  try {
    s = new Intl.NumberFormat(meta.locale, {
      style: 'currency', currency: meta.code, minimumFractionDigits: meta.decimals, maximumFractionDigits: meta.decimals
    }).format(Math.abs(n));
  } catch (e) { s = meta.symbol + Math.abs(n).toFixed(meta.decimals); }
  if (o.signed && n !== 0) return (n > 0 ? '+' : '−') + s;
  return n < 0 ? '−' + s : s;
}
const pct = (v, d = 0) => (v == null ? '—' : Number(v).toFixed(d) + '%');
const pad2 = n => String(n).padStart(2, '0');
function toLocalInput(d) { return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}T${pad2(d.getHours())}:${pad2(d.getMinutes())}`; }
function todayStr() { const d = new Date(); return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`; }
function curMonth() { const d = new Date(); return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}`; }
function fmtDate(iso) { return new Date(iso).toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' }); }
function fmtTime(iso) { return new Date(iso).toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' }); }
function monthLabel(key) { const [y, m] = key.split('-').map(Number); return new Date(y, m - 1, 1).toLocaleDateString(undefined, { month: 'long', year: 'numeric' }); }

/* ---------- toasts ---------- */
function toast(msg, kind) {
  const el = document.createElement('div');
  el.className = 'toast' + (kind === 'error' ? ' error' : '');
  el.textContent = msg;
  $('#toasts').appendChild(el);
  setTimeout(() => el.remove(), kind === 'error' ? 6000 : 3200);
}
const fail = e => toast(e && e.message ? e.message : String(e), 'error');

/* ---------- dialogs ---------- */
function openDialog({ title, body, submitText = 'Save', danger = false, hideSubmit = false, onSubmit, onOpen, extraFoot = '' }) {
  const d = document.createElement('dialog');
  d.className = 'modal';
  d.innerHTML = `<form novalidate><h2 style="margin-bottom:.9rem">${esc(title)}</h2>
    <div class="dlg-body">${body}</div><p class="err" role="alert" hidden></p>
    <div class="foot">${extraFoot}<button type="button" class="btn ghost" data-close>${hideSubmit ? 'Close' : 'Cancel'}</button>
    ${hideSubmit ? '' : `<button type="submit" class="btn ${danger ? 'danger' : 'primary'}">${esc(submitText)}</button>`}</div></form>`;
  document.body.appendChild(d);
  const form = $('form', d), err = $('.err', d);
  d.addEventListener('close', () => d.remove());
  $('[data-close]', d).addEventListener('click', () => d.close());
  form.addEventListener('submit', async ev => {
    ev.preventDefault();
    err.hidden = true;
    if (!form.checkValidity()) { form.reportValidity(); return; }
    const btn = $('button[type=submit]', d);
    if (btn) btn.disabled = true;
    try {
      const data = Object.fromEntries(new FormData(form).entries());
      const keep = await onSubmit(data, d);
      if (keep !== false) d.close();
    } catch (e) { err.textContent = e.message; err.hidden = false; }
    finally { if (btn) btn.disabled = false; }
  });
  d.showModal();
  if (onOpen) onOpen(d);
  const first = $('input:not([type=hidden]),select,textarea', d);
  if (first) first.focus();
  return d;
}
function confirmBox(title, text, okText = 'Delete') {
  return new Promise(resolve => {
    let done = false;
    const d = openDialog({
      title, body: `<p>${esc(text)}</p>`, submitText: okText, danger: true,
      onSubmit: async () => { done = true; resolve(true); }
    });
    d.addEventListener('close', () => { if (!done) resolve(false); });
  });
}

/* ---------- small UI builders ---------- */
const statusBadge = (status, label) => `<span class="badge ${esc(status)}">${esc(label)}</span>`;
const empty = (title, text = '') => `<div class="empty"><strong>${esc(title)}</strong>${esc(text)}</div>`;
const skeleton = (n = 3) => `<div class="grid">${'<div class="skeleton"></div>'.repeat(n)}</div>`;
const catOptions = (kind, selected, withBlank = true, blankText = 'Uncategorised') =>
  (withBlank ? `<option value="">${esc(blankText)}</option>` : '') +
  App.cats.filter(c => !kind || c.kind === kind).map(c => `<option value="${c.id}" ${String(selected) === String(c.id) ? 'selected' : ''}>${esc(c.name)}</option>`).join('');
const acctOptions = (selected, blankText = 'No account') =>
  `<option value="">${esc(blankText)}</option>` + App.accts.filter(a => !a.archived || String(selected) === String(a.id))
    .map(a => `<option value="${a.id}" ${String(selected) === String(a.id) ? 'selected' : ''}>${esc(a.name)}</option>`).join('');
const methodOptions = selected => PAYMENT_METHODS.map(m => `<option value="${m}" ${m === selected ? 'selected' : ''}>${esc(cap(m))}</option>`).join('');

const TXN_CACHE = {};
const cacheTxns = list => (list || []).forEach(t => { TXN_CACHE[t.id] = t; });

function txnRow(t) {
  const sign = t.type === 'income' ? 1 : -1;
  return `<li class="txn" data-act="edit-txn" data-id="${t.id}" tabindex="0" role="button" aria-label="${esc(t.description || t.categoryName || t.type)}, ${esc(money(t.amount))}">
    <div class="meta"><div class="title">${esc(t.description || t.categoryName || cap(t.type))}</div>
      <div class="chip"><span class="dot" style="background:${esc(t.categoryColor || '#64748b')}"></span>${esc(t.categoryName || 'Uncategorised')}
      · ${esc(fmtDate(t.occurredAt))} ${esc(fmtTime(t.occurredAt))} · ${esc(cap(t.paymentMethod))}${t.accountName ? ' · ' + esc(t.accountName) : ''}${t.demo ? ' · <em>demo</em>' : ''}</div></div>
    <div class="amt ${t.type}">${t.type === 'income' ? '+' : '−'}${esc(money(t.amount, { currency: t.currency }))}</div></li>`;
}

/* ---------- charts (Chart.js, loaded from CDN) ---------- */
const _charts = {};
const chartBox = (id, h) => `<div class="chart" ${h ? `style="height:${h}px"` : ''}><canvas id="${id}" role="img" aria-label="${esc(id.replace(/-/g, ' '))} chart"></canvas></div>`;
function drawChart(id, config) {
  const el = document.getElementById(id);
  if (!el) return;
  if (!window.Chart) { el.parentElement.innerHTML = '<p class="muted small">Charts need an internet connection to load Chart.js.</p>'; return; }
  if (_charts[id]) _charts[id].destroy();
  const css = getComputedStyle(document.documentElement);
  Chart.defaults.color = css.getPropertyValue('--muted').trim();
  Chart.defaults.borderColor = css.getPropertyValue('--border').trim();
  config.options = Object.assign({ responsive: true, maintainAspectRatio: false }, config.options || {});
  _charts[id] = new Chart(el, config);
}
function killCharts() { Object.keys(_charts).forEach(k => { _charts[k].destroy(); delete _charts[k]; }); }
const moneyTick = v => money(v, {}).replace(/\.00$/, '');
const tooltipMoney = { callbacks: { label: c => `${c.dataset.label || c.label}: ${money(c.parsed.y ?? c.parsed ?? c.raw)}` } };
const COLORS = { income: '#16a34a', expense: '#ef4444', primary: '#6366f1', budget: '#94a3b8' };

/* ---------- master data cache ---------- */
async function loadMasters() {
  const [cats, accts] = await Promise.all([api('GET', '/api/categories'), api('GET', '/api/accounts')]);
  App.cats = cats; App.accts = accts;
}

/* ---------- event delegation ---------- */
document.addEventListener('click', e => {
  const el = e.target.closest('[data-act]');
  if (!el) return;
  const fn = Actions[el.dataset.act];
  if (fn) { e.preventDefault(); fn(el, e); }
});
document.addEventListener('keydown', e => {
  if ((e.key === 'Enter' || e.key === ' ') && e.target.matches && e.target.matches('li[data-act]')) { e.preventDefault(); e.target.click(); }
});
