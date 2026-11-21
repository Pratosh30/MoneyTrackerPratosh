'use strict';
/* ===== app shell, router, quick-add ===== */
const NAV = [['overview', '📊', 'Overview'], ['transactions', '🧾', 'Transactions'], ['budgets', '🎯', 'Budgets'], ['insights', '📈', 'Insights'], ['settings', '⚙️', 'Settings']];

function parseHash() {
  const raw = location.hash.replace(/^#\/?/, '');
  const [path, query = ''] = raw.split('?');
  return { page: Pages[path] ? path : 'overview', params: Object.fromEntries(new URLSearchParams(query)) };
}
function go(page, params) { location.hash = '#/' + page + qs(params || {}); }

async function startApp() {
  try { await loadMasters(); } catch (e) { return fail(e); }
  localStorage.setItem('mm-theme', App.me.theme);
  applyTheme(App.me.theme);
  $('#app').innerHTML = `<div class="shell">
    <aside class="sidebar"><div class="brand">💰 My Money Map</div>
      <nav class="nav" aria-label="Main">${NAV.map(([k, i, l]) => `<a href="#/${k}" data-nav="${k}"><span aria-hidden="true">${i}</span>${l}</a>`).join('')}</nav>
      <div class="who">Signed in as<br><strong>${esc(App.me.userId)}</strong></div></aside>
    <div class="main"><div class="topbar">
      <form class="search" id="globalSearch" role="search"><input type="search" id="gs" placeholder="Search transactions…  ( / )" aria-label="Search transactions"></form>
      <button class="btn primary" data-act="quick-add" title="Add transaction (n)">＋ Add</button>
      <button class="icon-btn" data-act="toggle-theme" aria-label="Toggle dark mode" title="Toggle dark mode">🌓</button></div>
      <div id="page"></div></div></div>`;
  $('#globalSearch').addEventListener('submit', e => { e.preventDefault(); const v = $('#gs').value.trim(); const same = location.hash === '#/transactions'; TX.reset(); TX.f.q = v; go('transactions'); if (same) route(); });
  if (!location.hash) location.hash = '#/overview';
  route();
}
async function route() {
  if (!App.me) return;
  killCharts();
  const { page, params } = parseHash();
  $$('[data-nav]').forEach(a => a.classList.toggle('active', a.dataset.nav === page));
  const root = $('#page');
  if (!root) return;
  root.innerHTML = skeleton(4);
  try { await Pages[page](params, root); } catch (e) { root.innerHTML = `<div class="card"><p class="err">${esc(e.message)}</p><button class="btn" data-act="reload">Try again</button></div>`; }
}
async function refresh() { try { await loadMasters(); } catch (e) { /* ignore */ } await route(); }
Actions.reload = () => route();
window.addEventListener('hashchange', route);

Actions['toggle-theme'] = async () => {
  const dark = document.documentElement.dataset.theme === 'dark';
  const next = dark ? 'light' : 'dark';
  applyTheme(next); localStorage.setItem('mm-theme', next);
  try { App.me = await api('PUT', '/api/me', { theme: next }); } catch (e) { /* theme still applied locally */ }
  route();
};
Actions['quick-add'] = () => openTxnDialog({ type: 'expense' });
Actions['edit-txn'] = el => { const t = TXN_CACHE[el.dataset.id]; if (t) openTxnDialog({ txn: t }); };
document.addEventListener('keydown', e => {
  if (!App.me || e.ctrlKey || e.metaKey || e.altKey) return;
  if (e.target.matches('input,select,textarea') || document.querySelector('dialog[open]')) return;
  if (e.key === '/') { e.preventDefault(); const g = $('#gs'); if (g) g.focus(); }
  else if (e.key === 'n') { e.preventDefault(); openTxnDialog({ type: 'expense' }); }
  else if (e.key === 'i') { e.preventDefault(); openTxnDialog({ type: 'income' }); }
});

/* ---------- add / edit transaction dialog (used everywhere) ---------- */
function openTxnDialog({ type = 'expense', txn = null } = {}) {
  const t = txn;
  const kind = t ? t.type : type;
  const d = openDialog({
    title: t ? 'Edit transaction' : 'Add transaction',
    submitText: t ? 'Save changes' : 'Add',
    extraFoot: t ? '<button type="button" class="btn danger" data-del style="margin-right:auto">Delete</button>' : '',
    body: `<div class="seg" role="group" aria-label="Type" style="margin-bottom:.8rem">
        <button type="button" data-kind="expense" aria-pressed="${kind === 'expense'}">Expense</button>
        <button type="button" data-kind="income" aria-pressed="${kind === 'income'}">Income</button></div>
      <input type="hidden" name="type" value="${kind}">
      <div class="row"><div class="field"><label for="t-amt">Amount (${esc(App.me.currency)})</label><input id="t-amt" name="amount" type="number" step="0.01" min="0.01" required inputmode="decimal" value="${t ? t.amount : ''}"></div>
      <div class="field"><label for="t-when">Date &amp; time</label><input id="t-when" name="occurredAt" type="datetime-local" required value="${t ? t.occurredAt.slice(0, 16) : toLocalInput(new Date())}"></div></div>
      <div class="row"><div class="field"><label for="t-cat">Category</label><select id="t-cat" name="categoryId">${catOptions(kind, t && t.categoryId)}</select></div>
      <div class="field"><label for="t-acc">Account</label><select id="t-acc" name="accountId">${acctOptions(t && t.accountId)}</select></div></div>
      <div class="row"><div class="field"><label for="t-pm">Payment method</label><select id="t-pm" name="paymentMethod">${methodOptions(t ? t.paymentMethod : 'cash')}</select></div>
      <div class="field"><label for="t-desc">Description</label><input id="t-desc" name="description" maxlength="200" value="${esc(t && t.description)}"></div></div>
      <div class="field"><label for="t-notes">Notes / tags</label><textarea id="t-notes" name="notes" maxlength="1000">${esc(t && t.notes)}</textarea></div>
      ${t && t.currency !== App.me.currency ? `<p class="note small">Recorded in ${esc(t.currency)}; it is not converted.</p>` : ''}`,
    onSubmit: async data => {
      const body = {
        type: data.type, amount: Number(data.amount), occurredAt: data.occurredAt.length === 16 ? data.occurredAt + ':00' : data.occurredAt,
        categoryId: data.categoryId ? Number(data.categoryId) : null, accountId: data.accountId ? Number(data.accountId) : null,
        paymentMethod: data.paymentMethod, description: data.description, notes: data.notes
      };
      await (t ? api('PUT', '/api/transactions/' + t.id, body) : api('POST', '/api/transactions', body));
      toast(t ? 'Transaction updated' : (body.type === 'income' ? 'Income added' : 'Expense added'));
      refresh();
    }
  });
  $$('[data-kind]', d).forEach(b => b.addEventListener('click', () => {
    $$('[data-kind]', d).forEach(x => x.setAttribute('aria-pressed', x === b));
    $('[name=type]', d).value = b.dataset.kind;
    $('#t-cat', d).innerHTML = catOptions(b.dataset.kind, '');
  }));
  const del = $('[data-del]', d);
  if (del) del.addEventListener('click', async () => {
    if (!(await confirmBox('Delete this transaction?', 'This cannot be undone.'))) return;
    try { await api('DELETE', '/api/transactions/' + t.id); d.close(); toast('Transaction deleted'); refresh(); } catch (e) { fail(e); }
  });
}
Actions['logout'] = async () => {
  try { await api('POST', '/api/auth/logout'); } catch (e) { /* ignore */ }
  App.me = null; killCharts(); toast('Signed out'); showAuth();
};

/* ---------- boot ---------- */
(async function boot() {
  try {
    App.currencies = await api('GET', '/api/auth/currencies');
    const s = await api('GET', '/api/auth/session');
    if (s.authenticated) { App.me = s.profile; startApp(); } else showAuth();
  } catch (e) { $('#app').innerHTML = `<p class="boot">${esc(e.message)}</p>`; }
})();
