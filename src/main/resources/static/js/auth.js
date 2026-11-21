'use strict';
/* ===== sign in / create account ===== */
async function showAuth(tab = 'login') {
  killCharts();
  if (!App.currencies.length) { try { App.currencies = await api('GET', '/api/auth/currencies'); } catch (e) { /* shown below */ } }
  const curOpts = App.currencies.map(c => `<option value="${c.code}">${esc(c.code)} — ${esc(c.name)} (${esc(c.symbol)})</option>`).join('');
  $('#app').innerHTML = `<main class="auth"><div class="card">
    <div class="logo" aria-hidden="true">💰</div><h1>My Money Map</h1>
    <p class="tag">Know where your money goes.</p>
    <div class="seg" role="group" aria-label="Sign in or create account">
      <button type="button" data-act="auth-tab" data-tab="login" aria-pressed="${tab === 'login'}">Sign in</button>
      <button type="button" data-act="auth-tab" data-tab="register" aria-pressed="${tab === 'register'}">Create account</button></div>
    ${tab === 'login' ? `
    <form id="loginForm" novalidate>
      <div class="field"><label for="l-id">User ID</label><input id="l-id" name="userId" autocomplete="username" required></div>
      <div class="field"><label for="l-pin">PIN (4–6 digits)</label><input id="l-pin" name="pin" type="password" inputmode="numeric" maxlength="6" autocomplete="current-password" required></div>
      <p class="err" role="alert" hidden></p>
      <button class="btn primary" style="width:100%;justify-content:center">Sign in</button></form>` : `
    <form id="regForm" novalidate>
      <div class="field"><label for="r-id">Choose a User ID</label><input id="r-id" name="userId" autocomplete="username" maxlength="24" required>
        <span class="small muted" id="idHint">3–24 letters, numbers, dots, _ or -</span></div>
      <div class="field"><label for="r-name">Your name (optional)</label><input id="r-name" name="displayName" maxlength="60"></div>
      <div class="row"><div class="field"><label for="r-pin">PIN (4–6 digits)</label><input id="r-pin" name="pin" type="password" inputmode="numeric" maxlength="6" autocomplete="new-password" required></div>
      <div class="field"><label for="r-pin2">Repeat PIN</label><input id="r-pin2" name="pin2" type="password" inputmode="numeric" maxlength="6" autocomplete="new-password" required></div></div>
      <div class="field"><label for="r-cur">Currency</label><select id="r-cur" name="currency">${curOpts}</select>
        <span class="small muted">You can change this later in Settings.</span></div>
      <p class="err" role="alert" hidden></p>
      <button class="btn primary" style="width:100%;justify-content:center">Create account</button></form>`}
  </div></main>`;
  const err = $('.err', $('#app'));
  const show = m => { err.textContent = m; err.hidden = false; };
  const lf = $('#loginForm'), rf = $('#regForm');
  if (lf) lf.addEventListener('submit', async e => {
    e.preventDefault(); err.hidden = true;
    const d = Object.fromEntries(new FormData(lf));
    if (!d.userId.trim() || !/^\d{4,6}$/.test(d.pin)) return show('Enter your User ID and a 4–6 digit PIN.');
    try { App.me = await api('POST', '/api/auth/login', d); startApp(); } catch (x) { show(x.message); }
  });
  if (rf) {
    $('#r-id').addEventListener('blur', async ev => {
      const v = ev.target.value.trim(), hint = $('#idHint');
      if (v.length < 3) return;
      try { const r = await api('GET', '/api/auth/available' + qs({ userId: v })); hint.textContent = r.available ? '✓ Available' : '✗ Already taken'; }
      catch (x) { hint.textContent = x.message; }
    });
    rf.addEventListener('submit', async e => {
      e.preventDefault(); err.hidden = true;
      const d = Object.fromEntries(new FormData(rf));
      if (!/^\d{4,6}$/.test(d.pin)) return show('PIN must be 4 to 6 digits.');
      if (d.pin !== d.pin2) return show('The two PINs do not match.');
      delete d.pin2;
      try { App.me = await api('POST', '/api/auth/register', d); toast('Welcome! Your account is ready.'); startApp(); } catch (x) { show(x.message); }
    });
  }
}
Actions['auth-tab'] = el => showAuth(el.dataset.tab);
