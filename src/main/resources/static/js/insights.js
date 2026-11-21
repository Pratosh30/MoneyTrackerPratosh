'use strict';
/* ===== Insights ===== */
const IN = { view: 'month', month: curMonth(), year: String(new Date().getFullYear()), categoryId: '', from: '', to: '' };

const change = (v, goodWhenDown) => v == null ? '<span class="muted small">no earlier data to compare</span>'
  : `<span class="small ${(v > 0) === !!goodWhenDown ? 'expense' : 'income'}">${v > 0 ? '▲' : v < 0 ? '▼' : '•'} ${Math.abs(v).toFixed(0)}% vs previous</span>`;

Pages.insights = async (params, root) => {
  const custom = IN.from && IN.to;
  const d = await api('GET', '/api/insights' + qs({ view: IN.view, month: IN.month, year: IN.year, categoryId: IN.categoryId, from: IN.from, to: IN.to }));
  cacheTxns(d.largest);
  const s = d.summary;
  root.innerHTML = `<div class="page-head"><div><p class="muted small" style="margin:0">Insights</p><h1>${esc(d.periodLabel)}</h1></div></div>
  <section class="card" style="margin-bottom:1rem"><div class="filters">
    <div><label>View</label><div class="seg" role="group" aria-label="View"><button data-act="in-view" data-v="month" aria-pressed="${IN.view === 'month'}">Month</button><button data-act="in-view" data-v="year" aria-pressed="${IN.view === 'year'}">Year</button></div></div>
    <div><label for="i-month">Month</label><input id="i-month" data-i="month" type="month" value="${esc(IN.month)}" ${IN.view === 'year' ? 'disabled' : ''}></div>
    <div><label for="i-year">Year</label><input id="i-year" data-i="year" type="number" min="1970" max="2100" value="${esc(IN.year)}" ${IN.view === 'month' ? 'disabled' : ''}></div>
    <div><label for="i-cat">Category</label><select id="i-cat" data-i="categoryId">${catOptions('', IN.categoryId, true, 'All categories')}</select></div>
    <div><label for="i-from">From</label><input id="i-from" data-i="from" type="date" value="${esc(IN.from)}"></div>
    <div><label for="i-to">To</label><input id="i-to" data-i="to" type="date" value="${esc(IN.to)}"></div>
    <div><button class="btn" data-act="in-clear" style="width:100%;justify-content:center">Reset</button></div></div>
    ${custom ? '<p class="small muted" style="margin-bottom:0">Custom date range is overriding the month / year view.</p>' : (IN.from || IN.to ? '<p class="small muted" style="margin-bottom:0">Pick both From and To to use a custom range.</p>' : '')}</section>
  <div class="grid g4">
    <div class="card stat"><div class="label">Expenses</div><div class="value expense">${money(s.expense)}</div>${change(d.expenseChange, true)}</div>
    <div class="card stat"><div class="label">Income</div><div class="value income">${money(s.income)}</div>${change(d.incomeChange, false)}</div>
    <div class="card stat"><div class="label">Net</div><div class="value ${Number(s.net) >= 0 ? 'income' : 'expense'}">${money(s.net, { signed: true })}</div></div>
    <div class="card stat"><div class="label">Savings rate</div><div class="value">${s.savingsRate == null ? '—' : pct(s.savingsRate, 1)}</div></div>
    <div class="card stat"><div class="label">Average daily spend</div><div class="value">${money(d.avgDailySpend)}</div></div>
    <div class="card stat"><div class="label">Average monthly spend</div><div class="value">${d.avgMonthlySpend == null ? '—' : money(d.avgMonthlySpend)}</div><div class="hint">months with spending</div></div>
    <div class="card stat"><div class="label">Budget adherence</div><div class="value">${d.budgetCount ? `${d.budgetsWithinLimit}/${d.budgetCount}` : '—'}</div><div class="hint">${d.budgetCount ? `within limit · ${Math.round(d.budgetUsedPercent)}% used` : 'No budgets in this period'}</div></div>
    <div class="card stat"><div class="label">Year-over-year</div><div class="value">${d.yoyExpenseChange == null ? '—' : (d.yoyExpenseChange > 0 ? '+' : '') + d.yoyExpenseChange.toFixed(0) + '%'}</div><div class="hint">${d.yoyExpenseChange == null ? 'Needs the same period last year' : 'expenses vs ' + esc(d.yoyLabel)}</div></div></div><br>
  <section class="card"><h2>What your data shows</h2>
    <ul>${d.observations.map(o => `<li style="margin:.3rem 0">${esc(o)}</li>`).join('')}</ul>
    <p class="muted small" style="margin-bottom:0">Observations are calculated from your recorded transactions. They describe the past and are not financial advice.</p></section><br>
  <div class="grid g2">
    <section class="card"><h2>Income vs expenses</h2>${chartBox('in-trend')}</section>
    <section class="card"><h2>Savings rate trend</h2>${chartBox('in-save')}</section>
    <section class="card"><h2>Expense distribution</h2>${d.categories.length ? chartBox('in-donut') : empty('No expenses in this period')}</section>
    <section class="card"><h2>Top spending categories</h2>${d.categories.length ? `<ul class="list">${d.categories.slice(0, 6).map(c => `<li><div style="display:flex;justify-content:space-between;gap:.5rem"><span><span class="dot" style="background:${esc(c.color)}"></span> ${esc(c.name)}</span><strong>${money(c.total)}</strong></div>
      <div class="bar" style="margin-top:.3rem" role="img" aria-label="${esc(c.name)} ${Math.round(c.share)} percent of spending"><span style="width:${c.share}%;background:${esc(c.color)}"></span></div></li>`).join('')}</ul>` : empty('No expenses in this period')}</section></div><br>
  <section class="card"><h2>Largest expenses</h2>${d.largest.length ? `<ul class="list">${d.largest.map(txnRow).join('')}</ul>` : empty('No expenses in this period')}</section>`;
  $$('[data-i]', root).forEach(el => el.addEventListener('change', () => {
    IN[el.dataset.i] = el.value;
    if (el.dataset.i === 'month' && !el.value) IN.month = curMonth();
    route();
  }));
  const labels = d.trend.map(t => t.label);
  drawChart('in-trend', { type: 'bar', data: { labels, datasets: [
    { label: 'Income', data: d.trend.map(t => Number(t.income)), backgroundColor: COLORS.income },
    { label: 'Expenses', data: d.trend.map(t => Number(t.expense)), backgroundColor: COLORS.expense }] },
    options: { scales: { y: { ticks: { callback: moneyTick } } }, plugins: { tooltip: tooltipMoney } } });
  drawChart('in-save', { type: 'line', data: { labels, datasets: [{ label: 'Savings rate', data: d.trend.map(t => t.savingsRate), borderColor: COLORS.primary, backgroundColor: COLORS.primary, tension: .3, spanGaps: true }] },
    options: { scales: { y: { ticks: { callback: v => v + '%' } } }, plugins: { tooltip: { callbacks: { label: c => c.parsed.y == null ? 'No income' : `Savings rate: ${c.parsed.y}%` } } } } });
  if (d.categories.length) donut('in-donut', d.categories);
};
Actions['in-view'] = el => { IN.view = el.dataset.v; route(); };
Actions['in-clear'] = () => { Object.assign(IN, { view: 'month', month: curMonth(), year: String(new Date().getFullYear()), categoryId: '', from: '', to: '' }); route(); };
