'use strict';
/* ===== Transactions ===== */
const TX = {
  f: {}, 
  reset() { this.f = { q: '', type: '', categoryId: '', accountId: '', paymentMethod: '', month: '', year: '', from: '', to: '', sort: 'date', dir: 'desc', page: 0, size: 20 }; }
};
TX.reset();

async function loadTxList() {
  const box = $('#txList');
  if (!box) return;
  try {
    const r = await api('GET', '/api/transactions' + qs(TX.f));
    cacheTxns(r.items);
    box.innerHTML = r.items.length ? `<ul class="list">${r.items.map(txnRow).join('')}</ul>` :
      empty('No transactions found', Object.entries(TX.f).some(([k, v]) => v && !['sort', 'dir', 'page', 'size'].includes(k)) ? 'Try clearing some filters.' : 'Add your first income or expense with the Add button.');
    $('#txTotals').innerHTML = `${r.total} result${r.total === 1 ? '' : 's'} · <span class="income">+${money(r.income)}</span> · <span class="expense">−${money(r.expense)}</span>`;
    $('#txPager').innerHTML = r.pages > 1 ? `<button class="btn sm" data-act="tx-page" data-d="-1" ${r.page <= 0 ? 'disabled' : ''}>← Previous</button>
      <span class="muted small">Page ${r.page + 1} of ${r.pages}</span><button class="btn sm" data-act="tx-page" data-d="1" ${r.page + 1 >= r.pages ? 'disabled' : ''}>Next →</button>` : '';
  } catch (e) { box.innerHTML = `<p class="err">${esc(e.message)}</p>`; }
}

Pages.transactions = async (params, root) => {
  if (params.month) { TX.reset(); TX.f.month = params.month; }
  const f = TX.f;
  const years = []; for (let y = new Date().getFullYear(); y >= new Date().getFullYear() - 8; y--) years.push(y);
  root.innerHTML = `<div class="page-head"><h1>Transactions</h1><div class="actions">
      <button class="btn" data-act="add-income">＋ Income</button><button class="btn primary" data-act="add-expense">− Expense</button>
      <a class="btn" href="/api/transactions/export" download>⬇ Export CSV</a>
      <button class="btn" data-act="tx-import">⬆ Import CSV</button><input type="file" id="csvFile" accept=".csv,text/csv" hidden></div></div>
    <section class="card" style="margin-bottom:1rem"><div class="filters" role="search">
      <div><label for="f-q">Search</label><input id="f-q" data-f="q" type="search" placeholder="Description or notes" value="${esc(f.q)}"></div>
      <div><label for="f-type">Type</label><select id="f-type" data-f="type"><option value="">All</option><option value="income" ${f.type === 'income' ? 'selected' : ''}>Income</option><option value="expense" ${f.type === 'expense' ? 'selected' : ''}>Expense</option></select></div>
      <div><label for="f-cat">Category</label><select id="f-cat" data-f="categoryId">${catOptions('', f.categoryId, true, 'All')}</select></div>
      <div><label for="f-acc">Account</label><select id="f-acc" data-f="accountId">${acctOptions(f.accountId, 'All')}</select></div>
      <div><label for="f-pm">Payment</label><select id="f-pm" data-f="paymentMethod"><option value="">All</option>${methodOptions(f.paymentMethod)}</select></div>
      <div><label for="f-month">Month</label><input id="f-month" data-f="month" type="month" value="${esc(f.month)}"></div>
      <div><label for="f-year">Year</label><select id="f-year" data-f="year"><option value="">All</option>${years.map(y => `<option ${String(f.year) === String(y) ? 'selected' : ''}>${y}</option>`).join('')}</select></div>
      <div><label for="f-from">From</label><input id="f-from" data-f="from" type="date" value="${esc(f.from)}"></div>
      <div><label for="f-to">To</label><input id="f-to" data-f="to" type="date" value="${esc(f.to)}"></div>
      <div><label for="f-sort">Sort</label><select id="f-sort" data-f="sortcombo">
        ${[['date|desc', 'Newest first'], ['date|asc', 'Oldest first'], ['amount|desc', 'Amount: high → low'], ['amount|asc', 'Amount: low → high']].map(([v, l]) => `<option value="${v}" ${v === f.sort + '|' + f.dir ? 'selected' : ''}>${l}</option>`).join('')}</select></div>
      <div><label for="f-size">Per page</label><select id="f-size" data-f="size">${[10, 20, 50, 100].map(n => `<option ${n === f.size ? 'selected' : ''}>${n}</option>`).join('')}</select></div>
      <div><button class="btn" data-act="tx-clear" style="width:100%;justify-content:center">Clear filters</button></div></div></section>
    <section class="card"><p class="muted small" id="txTotals" style="margin-top:0"></p><div id="txList">${skeleton(3)}</div><div class="pager" id="txPager"></div></section>`;
  let timer;
  $$('[data-f]', root).forEach(el => {
    const handler = () => {
      const k = el.dataset.f;
      if (k === 'sortcombo') { const [s, d] = el.value.split('|'); f.sort = s; f.dir = d; }
      else f[k] = k === 'size' ? Number(el.value) : el.value;
      if (k === 'month' && el.value) f.year = '';
      f.page = 0; loadTxList();
    };
    el.addEventListener(el.tagName === 'INPUT' && el.type === 'search' ? 'input' : 'change', () => { clearTimeout(timer); timer = setTimeout(handler, el.type === 'search' ? 250 : 0); });
  });
  $('#csvFile').addEventListener('change', async e => {
    const file = e.target.files[0]; if (!file) return;
    const fd = new FormData(); fd.append('file', file);
    try {
      const r = await api('POST', '/api/transactions/import', fd);
      openDialog({ title: 'Import finished', hideSubmit: true, body: `<p><strong>${r.imported}</strong> transaction(s) imported, <strong>${r.skipped}</strong> skipped.</p>${r.errors.length ? `<ul class="small err">${r.errors.map(x => `<li>${esc(x)}</li>`).join('')}</ul>` : ''}` });
      refresh();
    } catch (x) { fail(x); }
    e.target.value = '';
  });
  await loadTxList();
};
Actions['tx-page'] = el => { TX.f.page = Math.max(0, TX.f.page + Number(el.dataset.d)); loadTxList(); window.scrollTo({ top: 0 }); };
Actions['tx-clear'] = () => { TX.reset(); route(); };
Actions['tx-import'] = () => $('#csvFile').click();
