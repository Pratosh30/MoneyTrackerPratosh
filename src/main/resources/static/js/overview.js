'use strict';
/* ===== Overview ===== */
function donut(id, cats) {
  drawChart(id, { type: 'doughnut', data: { labels: cats.map(c => c.name), datasets: [{ data: cats.map(c => Number(c.total)), backgroundColor: cats.map(c => c.color), borderWidth: 0 }] },
    options: { cutout: '62%', plugins: { legend: { display: false }, tooltip: { callbacks: { label: c => `${c.label}: ${money(c.parsed)}` } } } } });
}
function budgetRows(items, limit) {
  return `<ul class="list">${items.slice(0, limit || 99).map(p => `<li>
    <div style="display:flex;justify-content:space-between;gap:.5rem;flex-wrap:wrap;margin-bottom:.35rem">
      <span><span class="dot" style="background:${esc(p.categoryColor)}"></span> <strong>${esc(p.categoryName)}</strong></span>
      <span>${money(p.spent)} / ${money(p.amount)} ${statusBadge(p.status, p.statusLabel)}</span></div>
    <div class="bar ${p.status}" role="progressbar" aria-valuemin="0" aria-valuemax="100" aria-valuenow="${Math.min(100, Math.round(p.percent))}" aria-label="${esc(p.categoryName)} ${Math.round(p.percent)}% used"><span style="width:${Math.min(100, p.percent)}%"></span></div></li>`).join('')}</ul>`;
}

Pages.overview = async (params, root) => {
  const o = await api('GET', '/api/overview' + qs({ month: params.month }));
  cacheTxns(o.recent);
  const s = o.summary;
  root.innerHTML = `
  <div class="page-head"><div><p class="muted small" style="margin:0">Overview</p><h1>${esc(o.monthLabel)}</h1></div>
    <div class="actions">
      <select id="monthSel" aria-label="Choose month" style="width:auto">${o.months.map(m => `<option value="${m}" ${m === o.month ? 'selected' : ''}>${esc(monthLabel(m))}</option>`).join('')}</select>
      <button class="btn" data-act="add-income">＋ Income</button><button class="btn primary" data-act="add-expense">− Expense</button>
      <button class="btn" data-act="add-budget">🎯 Budget</button></div></div>
  ${o.otherCurrency ? '<p class="note small" role="note">Transactions recorded in another currency are left unchanged and excluded from these totals.</p><br>' : ''}
  <div class="grid g4">
    <div class="card stat"><div class="label">Income</div><div class="value income">${money(s.income)}</div></div>
    <div class="card stat"><div class="label">Expenses</div><div class="value expense">${money(s.expense)}</div></div>
    <div class="card stat"><div class="label">Net cash flow</div><div class="value ${Number(s.net) >= 0 ? 'income' : 'expense'}">${money(s.net, { signed: true })}</div><div class="hint">${Number(s.net) >= 0 ? 'Surplus this month' : 'Spending exceeds income'}</div></div>
    <div class="card stat"><div class="label">Savings rate</div><div class="value">${s.savingsRate == null ? '—' : pct(s.savingsRate, 1)}</div><div class="hint">${s.savingsRate == null ? 'Add income to calculate' : 'of income kept'}</div></div></div><br>
  <div class="grid g3-2">
    <section class="card"><div class="card-head"><h2>Budget usage</h2>${Number(o.budget.total) > 0 ? `<span class="muted small">${money(o.budget.spent)} of ${money(o.budget.total)}</span>` : ''}</div>
      ${o.budget.items.length ? budgetRows(o.budget.items, 6) : empty('No budgets for this month', 'Set a spending limit per category to track it here.') + '<div style="text-align:center"><button class="btn primary sm" data-act="add-budget">Create budget</button></div>'}</section>
    <section class="card"><h2>Spending by category</h2>
      ${o.categories.length ? chartBox('overview-donut', 200) + `<ul class="list small">${o.categories.slice(0, 5).map(c => `<li style="padding:.35rem 0;display:flex;justify-content:space-between;gap:.5rem"><span><span class="dot" style="background:${esc(c.color)}"></span> ${esc(c.name)}</span><span>${money(c.total)} · ${Math.round(c.share)}%</span></li>`).join('')}</ul>` : empty('No expenses this month')}</section></div><br>
  <div class="grid g3-2">
    <section class="card"><div class="card-head"><h2>Transactions in ${esc(monthLabel(o.month))}</h2><a href="#/transactions?month=${o.month}">View all</a></div>
      ${o.recent.length ? `<ul class="list">${o.recent.map(txnRow).join('')}</ul>` : empty('No transactions this month', 'Add your first income or expense.')}</section>
    <div class="grid" style="align-content:start">
      <section class="card"><h2>Upcoming recurring</h2>${o.upcoming.length ? `<ul class="list small">${o.upcoming.map(r => `<li style="display:flex;justify-content:space-between;gap:.5rem"><span><strong>${esc(r.description || r.categoryName || r.type)}</strong><br><span class="muted">${esc(fmtDate(r.nextRun + 'T12:00'))} · ${esc(r.frequency)}</span></span><span class="${r.type}">${r.type === 'income' ? '+' : '−'}${money(r.amount)}</span></li>`).join('')}</ul>` : empty('Nothing scheduled', 'Add recurring bills or salary in Settings.')}</section>
      <section class="card"><h2>Accounts</h2>${o.accounts.filter(a => !a.archived).length ? `<ul class="list small">${o.accounts.filter(a => !a.archived).map(a => `<li style="display:flex;justify-content:space-between"><span>${esc(a.name)} <span class="muted">· ${esc(a.type)}</span></span><strong>${money(a.balance)}</strong></li>`).join('')}</ul>` : empty('No accounts')}</section></div></div>`;
  $('#monthSel').addEventListener('change', e => go('overview', { month: e.target.value }));
  if (o.categories.length) donut('overview-donut', o.categories);
  window._ovMonth = o.month;
};
Actions['add-income'] = () => openTxnDialog({ type: 'income' });
Actions['add-expense'] = () => openTxnDialog({ type: 'expense' });
Actions['add-budget'] = () => openBudgetDialog({ period: 'month', key: window._ovMonth || curMonth() });
