'use strict';
/* ===== Settings ===== */
let ST = { goals: [], recurring: [] };
const sect = (title, desc, inner, action = '') => `<section class="card" style="margin-bottom:1rem"><div class="card-head"><div><h2>${title}</h2><p class="muted small" style="margin:0">${desc}</p></div>${action}</div>${inner}</section>`;
const iconBtns = (editAct, delAct, id, label) => `<span class="nowrap"><button class="icon-btn" data-act="${editAct}" data-id="${id}" aria-label="Edit ${esc(label)}">✏️</button><button class="icon-btn" data-act="${delAct}" data-id="${id}" aria-label="Delete ${esc(label)}">🗑️</button></span>`;

Pages.settings = async (params, root) => {
  const [accts, goals, recurring] = await Promise.all([api('GET', '/api/accounts'), api('GET', '/api/goals'), api('GET', '/api/recurring')]);
  App.accts = accts; ST = { goals, recurring };
  const me = App.me, curOpts = App.currencies.map(c => `<option value="${c.code}" ${c.code === me.currency ? 'selected' : ''}>${esc(c.code)} — ${esc(c.name)} (${esc(c.symbol)})</option>`).join('');
  const catRow = c => `<li style="display:flex;justify-content:space-between;align-items:center"><span><span class="dot" style="background:${esc(c.color)}"></span> ${esc(c.name)}</span>${iconBtns('cat-edit', 'cat-del', c.id, c.name)}</li>`;
  root.innerHTML = `<div class="page-head"><h1>Settings</h1></div>
  ${sect('Profile', 'Your account details.', `<div class="row"><div class="field"><label>User ID</label><input value="${esc(me.userId)}" disabled></div>
     <div class="field"><label for="s-name">Display name</label><div style="display:flex;gap:.5rem"><input id="s-name" maxlength="60" value="${esc(me.displayName)}"><button class="btn" data-act="save-name">Save</button></div></div></div>`)}
  ${sect('Appearance', 'Light, dark, or follow your device.', `<div class="seg" role="group" aria-label="Theme">${['system', 'light', 'dark'].map(t => `<button data-act="set-theme" data-t="${t}" aria-pressed="${me.theme === t}">${cap(t)}</button>`).join('')}</div>`)}
  ${sect('Currency', 'Used for new transactions and every total you see.', `<div style="display:flex;gap:.5rem;flex-wrap:wrap"><select id="s-cur" style="max-width:340px">${curOpts}</select><button class="btn" data-act="save-cur">Save currency</button></div>
     <p class="note small" style="margin-bottom:0">Changing currency does <strong>not</strong> convert old amounts. Existing transactions keep the currency they were recorded in and are left out of totals while a different currency is selected.</p>`)}
  ${sect('Security', 'Change the PIN you sign in with.', `<button class="btn" data-act="change-pin">Change PIN</button>`)}
  ${sect('Accounts & wallets', 'Where your money lives. Balances include all linked transactions.', accts.length ? `<ul class="list">${accts.map(a => `<li style="display:flex;justify-content:space-between;align-items:center;gap:.5rem"><span>${esc(a.name)} <span class="muted small">· ${esc(a.type)}${a.archived ? ' · archived' : ''}</span></span><span><strong>${money(a.balance)}</strong> ${iconBtns('acc-edit', 'acc-del', a.id, a.name)}</span></li>`).join('')}</ul>` : empty('No accounts'), '<button class="btn primary sm" data-act="acc-add">＋ Add</button>')}
  ${sect('Categories', 'Used for transactions and budgets. Deleting one keeps its transactions as “Uncategorised”.', `<div class="grid g2"><div><h3>Expense</h3><ul class="list">${App.cats.filter(c => c.kind === 'expense').map(catRow).join('')}</ul></div><div><h3>Income</h3><ul class="list">${App.cats.filter(c => c.kind === 'income').map(catRow).join('')}</ul></div></div>`, '<button class="btn primary sm" data-act="cat-add">＋ Add</button>')}
  ${sect('Savings goals', 'Track a target and what you have saved so far.', goals.length ? `<div class="grid g2">${goals.map(g => `<div class="card"><div style="display:flex;justify-content:space-between"><div><strong>${esc(g.name)}</strong><br><span class="muted small">Target date: ${g.targetDate ? esc(fmtDate(g.targetDate + 'T12:00')) : 'not set'}</span></div>${iconBtns('goal-edit', 'goal-del', g.id, g.name)}</div>
      <div style="display:flex;justify-content:space-between;margin:.6rem 0 .3rem"><strong>${money(g.savedAmount)}</strong><span class="muted">of ${money(g.targetAmount)}</span></div>
      <div class="bar" role="progressbar" aria-valuemin="0" aria-valuemax="100" aria-valuenow="${Math.round(g.percent)}" aria-label="${esc(g.name)} ${Math.round(g.percent)}% saved"><span style="width:${g.percent}%"></span></div></div>`).join('')}</div>` : empty('No savings goals', 'Add a target such as an emergency fund or a trip.'), '<button class="btn primary sm" data-act="goal-add">＋ Add</button>')}
  ${sect('Recurring transactions', 'Salary, rent and bills that repeat. Due items are added automatically.', recurring.length ? `<ul class="list">${recurring.map(r => `<li style="display:flex;justify-content:space-between;align-items:center;gap:.5rem"><span><strong>${esc(r.description || r.categoryName || r.type)}</strong> ${r.active ? '' : '<span class="badge unused">Paused</span>'}<br><span class="muted small">${esc(cap(r.frequency))} · next ${esc(fmtDate(r.nextRun + 'T12:00'))}</span></span><span><strong class="${r.type}">${r.type === 'income' ? '+' : '−'}${money(r.amount)}</strong> ${iconBtns('rec-edit', 'rec-del', r.id, r.description || 'rule')}</span></li>`).join('')}</ul>` : empty('No recurring transactions'), '<button class="btn primary sm" data-act="rec-add">＋ Add</button>')}
  ${sect('Your data', 'Take your data with you, or bring some in.', `<div class="actions"><a class="btn" href="/api/transactions/export" download>⬇ Transactions (CSV)</a><a class="btn" href="/api/export/json" download>⬇ Everything (JSON)</a><a class="btn" href="#/transactions">⬆ Import CSV…</a></div>`)}
  ${sect('Demo data', 'Optional sample transactions, budgets and goals, clearly labelled “demo”. Only your own account is affected.', me.demoLoaded
    ? `<p>Demo data is loaded.</p><button class="btn danger" data-act="demo-clear">Clear demo data</button>` : `<button class="btn" data-act="demo-load">Load demo data</button>`)}
  <button class="btn" data-act="logout">Sign out</button>`;
};

/* profile / appearance / currency / pin */
Actions['save-name'] = async () => { try { App.me = await api('PUT', '/api/me', { displayName: $('#s-name').value }); toast('Name saved'); } catch (e) { fail(e); } };
Actions['set-theme'] = async el => { try { App.me = await api('PUT', '/api/me', { theme: el.dataset.t }); localStorage.setItem('mm-theme', el.dataset.t); applyTheme(el.dataset.t); route(); } catch (e) { fail(e); } };
Actions['save-cur'] = async () => { try { App.me = await api('PUT', '/api/me', { currency: $('#s-cur').value }); toast('Currency updated to ' + App.me.currency); route(); } catch (e) { fail(e); } };
Actions['change-pin'] = () => openDialog({
  title: 'Change PIN', submitText: 'Change PIN',
  body: `<div class="field"><label for="p0">Current PIN</label><input id="p0" name="currentPin" type="password" inputmode="numeric" maxlength="6" required></div>
    <div class="row"><div class="field"><label for="p1">New PIN (4–6 digits)</label><input id="p1" name="newPin" type="password" inputmode="numeric" maxlength="6" pattern="\\d{4,6}" required></div>
    <div class="field"><label for="p2">Repeat new PIN</label><input id="p2" name="newPin2" type="password" inputmode="numeric" maxlength="6" required></div></div>`,
  onSubmit: async d => { if (d.newPin !== d.newPin2) throw new Error('The two new PINs do not match.'); await api('PUT', '/api/me/pin', { currentPin: d.currentPin, newPin: d.newPin }); toast('PIN changed'); }
});

/* generic delete helper */
async function delRow(url, title, text, done) { if (!(await confirmBox(title, text))) return; try { await api('DELETE', url); toast('Deleted'); (done || refresh)(); } catch (e) { fail(e); } }

/* accounts */
function accDialog(a) {
  openDialog({ title: a ? 'Edit account' : 'Add account', body: `<div class="field"><label for="a-n">Name</label><input id="a-n" name="name" maxlength="40" required value="${esc(a && a.name)}"></div>
    <div class="row"><div class="field"><label for="a-t">Type</label><select id="a-t" name="type">${ACCOUNT_TYPES.map(t => `<option ${a && a.type === t ? 'selected' : ''}>${t}</option>`).join('')}</select></div>
    <div class="field"><label for="a-b">Opening balance</label><input id="a-b" name="openingBalance" type="number" step="0.01" value="${a ? a.openingBalance : 0}"></div></div>
    <label style="font-weight:400"><input type="checkbox" name="archived" value="1" style="width:auto" ${a && a.archived ? 'checked' : ''}> Archived (hide from pickers)</label>`,
    onSubmit: async d => { const body = { name: d.name, type: d.type, openingBalance: Number(d.openingBalance || 0), archived: d.archived === '1' }; await (a ? api('PUT', '/api/accounts/' + a.id, body) : api('POST', '/api/accounts', body)); toast('Account saved'); refresh(); } });
}
Actions['acc-add'] = () => accDialog();
Actions['acc-edit'] = el => accDialog(App.accts.find(a => String(a.id) === el.dataset.id));
Actions['acc-del'] = el => delRow('/api/accounts/' + el.dataset.id, 'Delete this account?', 'Its transactions are kept but will no longer be linked to an account.');

/* categories */
function catDialog(c) {
  openDialog({ title: c ? 'Edit category' : 'Add category', body: `<div class="field"><label for="c-n">Name</label><input id="c-n" name="name" maxlength="40" required value="${esc(c && c.name)}"></div>
    <div class="row"><div class="field"><label for="c-k">Kind</label><select id="c-k" name="kind"><option value="expense" ${c && c.kind === 'expense' ? 'selected' : ''}>Expense</option><option value="income" ${c && c.kind === 'income' ? 'selected' : ''}>Income</option></select></div>
    <div class="field"><label for="c-c">Colour</label><input id="c-c" class="swatch" name="color" type="color" value="${esc(c ? c.color : '#6366f1')}"></div></div>`,
    onSubmit: async d => { await (c ? api('PUT', '/api/categories/' + c.id, d) : api('POST', '/api/categories', d)); toast('Category saved'); refresh(); } });
}
Actions['cat-add'] = () => catDialog();
Actions['cat-edit'] = el => catDialog(App.cats.find(c => String(c.id) === el.dataset.id));
Actions['cat-del'] = el => delRow('/api/categories/' + el.dataset.id, 'Delete this category?', 'Transactions are kept as “Uncategorised”. Budgets for this category are removed.');

/* goals */
function goalDialog(g) {
  openDialog({ title: g ? 'Edit goal' : 'Add savings goal', body: `<div class="field"><label for="g-n">Goal name</label><input id="g-n" name="name" maxlength="80" required value="${esc(g && g.name)}"></div>
    <div class="row"><div class="field"><label for="g-t">Target amount</label><input id="g-t" name="targetAmount" type="number" step="0.01" min="0.01" required value="${g ? g.targetAmount : ''}"></div>
    <div class="field"><label for="g-s">Already saved</label><input id="g-s" name="savedAmount" type="number" step="0.01" min="0" value="${g ? g.savedAmount : 0}"></div></div>
    <div class="field"><label for="g-d">Target date</label><input id="g-d" name="targetDate" type="date" value="${esc(g && g.targetDate)}"></div>`,
    onSubmit: async d => { const body = { name: d.name, targetAmount: Number(d.targetAmount), savedAmount: Number(d.savedAmount || 0), targetDate: d.targetDate || null }; await (g ? api('PUT', '/api/goals/' + g.id, body) : api('POST', '/api/goals', body)); toast('Goal saved'); refresh(); } });
}
Actions['goal-add'] = () => goalDialog();
Actions['goal-edit'] = el => goalDialog(ST.goals.find(g => String(g.id) === el.dataset.id));
Actions['goal-del'] = el => delRow('/api/goals/' + el.dataset.id, 'Delete this goal?', 'The goal will be permanently removed.');

/* recurring */
function recDialog(r) {
  const kind = r ? r.type : 'expense';
  const d = openDialog({ title: r ? 'Edit recurring transaction' : 'Add recurring transaction', body: `<div class="row"><div class="field"><label for="r-t">Type</label><select id="r-t" name="type"><option value="expense" ${kind === 'expense' ? 'selected' : ''}>Expense</option><option value="income" ${kind === 'income' ? 'selected' : ''}>Income</option></select></div>
    <div class="field"><label for="r-a">Amount</label><input id="r-a" name="amount" type="number" step="0.01" min="0.01" required value="${r ? r.amount : ''}"></div></div>
    <div class="field"><label for="r-d">Description</label><input id="r-d" name="description" maxlength="200" value="${esc(r && r.description)}"></div>
    <div class="row"><div class="field"><label for="r-c">Category</label><select id="r-c" name="categoryId">${catOptions(kind, r && r.categoryId)}</select></div>
    <div class="field"><label for="r-ac">Account</label><select id="r-ac" name="accountId">${acctOptions(r && r.accountId)}</select></div></div>
    <div class="row"><div class="field"><label for="r-f">Repeats</label><select id="r-f" name="frequency">${['weekly', 'monthly', 'yearly'].map(f => `<option value="${f}" ${r && r.frequency === f ? 'selected' : (!r && f === 'monthly' ? 'selected' : '')}>${cap(f)}</option>`).join('')}</select></div>
    <div class="field"><label for="r-n">Next date</label><input id="r-n" name="nextRun" type="date" required value="${r ? r.nextRun : todayStr()}"></div></div>
    <div class="row"><div class="field"><label for="r-p">Payment method</label><select id="r-p" name="paymentMethod">${methodOptions(r ? r.paymentMethod : 'cash')}</select></div>
    <div class="field" style="align-self:end"><label style="font-weight:400"><input type="checkbox" name="active" value="1" style="width:auto" ${!r || r.active ? 'checked' : ''}> Active</label></div></div>`,
    onSubmit: async x => {
      const body = { type: x.type, amount: Number(x.amount), description: x.description, categoryId: x.categoryId ? Number(x.categoryId) : null, accountId: x.accountId ? Number(x.accountId) : null, frequency: x.frequency, nextRun: x.nextRun, paymentMethod: x.paymentMethod, active: x.active === '1' };
      await (r ? api('PUT', '/api/recurring/' + r.id, body) : api('POST', '/api/recurring', body));
      try { await api('POST', '/api/recurring/run'); } catch (e) { /* ignore */ }
      toast('Recurring transaction saved'); refresh();
    } });
  $('#r-t', d).addEventListener('change', e => { $('#r-c', d).innerHTML = catOptions(e.target.value, ''); });
}
Actions['rec-add'] = () => recDialog();
Actions['rec-edit'] = el => recDialog(ST.recurring.find(r => String(r.id) === el.dataset.id));
Actions['rec-del'] = el => delRow('/api/recurring/' + el.dataset.id, 'Delete this recurring rule?', 'Transactions already created stay; no new ones will be added.');

/* demo data */
Actions['demo-load'] = async () => { try { App.me = await api('POST', '/api/demo'); toast('Demo data loaded (labelled “demo”)'); refresh(); } catch (e) { fail(e); } };
Actions['demo-clear'] = async () => {
  if (!(await confirmBox('Clear demo data?', 'Only the sample rows marked “demo” are removed. Your own entries stay.', 'Clear demo data'))) return;
  try { App.me = await api('DELETE', '/api/demo'); toast('Demo data cleared'); refresh(); } catch (e) { fail(e); }
};
