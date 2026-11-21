'use strict';
/* ===== Budgets ===== */
const BG = { period: 'month', key: curMonth() };

function openBudgetDialog({ period, key, budget }) {
  const b = budget;
  openDialog({
    title: b ? 'Edit budget' : 'Add budget', submitText: b ? 'Save changes' : 'Create budget',
    body: `<div class="field"><label for="b-cat">Expense category</label><select id="b-cat" name="categoryId" required>${catOptions('expense', b && b.categoryId, false)}</select></div>
      <div class="row"><div class="field"><label for="b-per">Period</label><select id="b-per" name="period"><option value="month" ${period === 'month' ? 'selected' : ''}>Monthly</option><option value="year" ${period === 'year' ? 'selected' : ''}>Yearly</option></select></div>
      <div class="field"><label for="b-key">${period === 'year' ? 'Year' : 'Month'}</label>${period === 'year' ? `<input id="b-key" name="periodKey" type="number" min="1970" max="2100" value="${esc(key)}" required>` : `<input id="b-key" name="periodKey" type="month" value="${esc(key)}" required>`}</div></div>
      <div class="field"><label for="b-amt">Budget amount (${esc(App.me.currency)})</label><input id="b-amt" name="amount" type="number" step="0.01" min="0.01" required value="${b ? b.amount : ''}"></div>`,
    onOpen: d => $('#b-per', d).addEventListener('change', e => {   // swap month/year input
      const per = e.target.value, k = $('#b-key', d);
      const y = (k.value || curMonth()).slice(0, 4);
      k.type = per === 'year' ? 'number' : 'month'; k.value = per === 'year' ? y : y + '-' + curMonth().slice(5);
      $('label[for=b-key]', d).textContent = per === 'year' ? 'Year' : 'Month';
    }),
    onSubmit: async data => {
      const body = { categoryId: Number(data.categoryId), period: data.period, periodKey: data.periodKey, amount: Number(data.amount) };
      await (b ? api('PUT', '/api/budgets/' + b.id, body) : api('POST', '/api/budgets', body));
      BG.period = body.period; BG.key = body.periodKey;
      toast(b ? 'Budget updated' : 'Budget created'); refresh();
    }
  });
}

Pages.budgets = async (params, root) => {
  if (params.period) BG.period = params.period;
  if (params.key) BG.key = params.key;
  if (BG.period === 'year' && !/^\d{4}$/.test(BG.key)) BG.key = String(new Date().getFullYear());
  if (BG.period === 'month' && !/^\d{4}-\d{2}$/.test(BG.key)) BG.key = curMonth();
  const endMonth = BG.period === 'year' ? BG.key + '-12' : BG.key;
  const [v, perf] = await Promise.all([api('GET', '/api/budgets' + qs({ period: BG.period, key: BG.key })), api('GET', '/api/budgets/performance' + qs({ endMonth, months: 6 }))]);
  window._budgets = Object.fromEntries(v.items.map(i => [i.id, i]));
  const unitPrev = BG.period === 'year' ? 'last year' : 'last month';
  root.innerHTML = `<div class="page-head"><div><p class="muted small" style="margin:0">Budgets</p><h1>${esc(v.label)}</h1></div>
    <div class="actions"><div class="seg" role="group" aria-label="Budget period">
      <button data-act="bg-period" data-p="month" aria-pressed="${BG.period === 'month'}">Monthly</button><button data-act="bg-period" data-p="year" aria-pressed="${BG.period === 'year'}">Yearly</button></div>
      ${BG.period === 'year' ? `<input id="bgKey" type="number" min="1970" max="2100" value="${esc(BG.key)}" style="width:100px" aria-label="Year">` : `<input id="bgKey" type="month" value="${esc(BG.key)}" style="width:auto" aria-label="Month">`}
      <button class="btn primary" data-act="bg-add">＋ Add budget</button></div></div>
    <div class="grid g4">
      <div class="card stat"><div class="label">Total budget</div><div class="value">${money(v.totalBudget)}</div></div>
      <div class="card stat"><div class="label">Spent</div><div class="value expense">${money(v.totalSpent)}</div></div>
      <div class="card stat"><div class="label">${Number(v.totalRemaining) < 0 ? 'Over budget by' : 'Remaining'}</div><div class="value ${Number(v.totalRemaining) < 0 ? 'expense' : 'income'}">${money(Math.abs(v.totalRemaining))}</div></div>
      <div class="card stat"><div class="label">Used</div><div class="value">${v.items.length ? pct(v.percent, 0) : '—'}</div><div class="hint">${v.items.length ? statusBadge(v.status, v.statusLabel) : 'No budgets yet'}</div></div></div><br>
    <section class="card"><h2>Category budgets</h2>
      ${v.items.length ? `<ul class="list">${v.items.map(p => `<li>
        <div style="display:flex;justify-content:space-between;gap:.5rem;flex-wrap:wrap;align-items:center">
          <span><span class="dot" style="background:${esc(p.categoryColor)}"></span> <strong>${esc(p.categoryName)}</strong> ${statusBadge(p.status, p.statusLabel)}</span>
          <span><button class="icon-btn" data-act="bg-edit" data-id="${p.id}" aria-label="Edit ${esc(p.categoryName)} budget">✏️</button><button class="icon-btn" data-act="bg-del" data-id="${p.id}" aria-label="Delete ${esc(p.categoryName)} budget">🗑️</button></span></div>
        <div class="bar ${p.status}" style="margin:.45rem 0" role="progressbar" aria-valuemin="0" aria-valuemax="100" aria-valuenow="${Math.min(100, Math.round(p.percent))}" aria-label="${esc(p.categoryName)} ${Math.round(p.percent)}% used"><span style="width:${Math.min(100, p.percent)}%"></span></div>
        <div class="small muted" style="display:flex;justify-content:space-between;flex-wrap:wrap;gap:.3rem"><span>${money(p.spent)} of ${money(p.amount)} (${Math.round(p.percent)}%)</span>
          <span>${Number(p.remaining) < 0 ? `<strong class="expense">Overspent by ${money(Math.abs(p.remaining))}</strong>` : `${money(p.remaining)} left`} · ${money(p.previousSpent)} spent ${unitPrev}</span></div></li>`).join('')}</ul>` : empty('No budgets for this period', 'Create a limit for a category such as Food or Transport.')}</section><br>
    <div class="grid g2">
      <section class="card"><h2>Budget vs actual — last 6 months</h2>${chartBox('bg-months')}<p class="muted small">Actual counts spending in budgeted categories only.</p></section>
      <section class="card"><h2>This period vs ${esc(unitPrev)} by category</h2>${v.items.length ? chartBox('bg-cats') : empty('Nothing to compare yet')}</section></div>
    ${perf.years.length ? `<br><section class="card"><h2>Yearly budget performance</h2>${chartBox('bg-years', 220)}</section>` : ''}`;
  $('#bgKey').addEventListener('change', e => { if (e.target.value) { BG.key = e.target.value; route(); } });
  drawChart('bg-months', { type: 'bar', data: { labels: perf.months.map(m => m.label), datasets: [
    { label: 'Budgeted', data: perf.months.map(m => Number(m.budgeted)), backgroundColor: COLORS.budget },
    { label: 'Spent', data: perf.months.map(m => Number(m.spent)), backgroundColor: COLORS.primary }] },
    options: { scales: { y: { ticks: { callback: moneyTick } } }, plugins: { tooltip: tooltipMoney } } });
  if (v.items.length) drawChart('bg-cats', { type: 'bar', data: { labels: v.items.map(i => i.categoryName), datasets: [
    { label: cap(unitPrev), data: v.items.map(i => Number(i.previousSpent)), backgroundColor: COLORS.budget },
    { label: 'This period', data: v.items.map(i => Number(i.spent)), backgroundColor: COLORS.primary }] },
    options: { scales: { y: { ticks: { callback: moneyTick } } }, plugins: { tooltip: tooltipMoney } } });
  if (perf.years.length) drawChart('bg-years', { type: 'bar', data: { labels: perf.years.map(y => String(y.year)), datasets: [
    { label: 'Budgeted', data: perf.years.map(y => Number(y.budgeted)), backgroundColor: COLORS.budget },
    { label: 'Spent', data: perf.years.map(y => Number(y.spent)), backgroundColor: COLORS.primary }] },
    options: { scales: { y: { ticks: { callback: moneyTick } } }, plugins: { tooltip: tooltipMoney } } });
};
Actions['bg-period'] = el => { BG.period = el.dataset.p; BG.key = BG.period === 'year' ? BG.key.slice(0, 4) : (BG.key.length === 4 ? curMonth() : BG.key); route(); };
Actions['bg-add'] = () => openBudgetDialog({ period: BG.period, key: BG.key });
Actions['bg-edit'] = el => { const b = window._budgets[el.dataset.id]; openBudgetDialog({ period: b.period, key: b.periodKey, budget: b }); };
Actions['bg-del'] = async el => {
  if (!(await confirmBox('Delete this budget?', 'The category budget for this period will be removed. Your transactions are not affected.'))) return;
  try { await api('DELETE', '/api/budgets/' + el.dataset.id); toast('Budget deleted'); refresh(); } catch (e) { fail(e); }
};
