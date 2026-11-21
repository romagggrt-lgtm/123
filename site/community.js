(() => {
  'use strict';
  const $ = selector => document.querySelector(selector);
  const config = window.ZALUPA_COMMUNITY || {};
  const apiUrl = String(config.url || '').replace(/\/+$/, '');
  const apiKey = String(config.key || '');
  // The browser may receive only a public publishable or legacy anon key, never an administrator key.
  let keyIsSecret = apiKey.startsWith('sb_secret_');
  try { if (apiKey.split('.').length === 3 && JSON.parse(atob(apiKey.split('.')[1].replace(/-/g, '+').replace(/_/g, '/'))).role === 'service_role') keyIsSecret = true; } catch (_) {}
  const configured = /^https:\/\/[a-z0-9-]+\.supabase\.co$/i.test(apiUrl) && !!apiKey && !keyIsSecret;
  const profileButton = $('.profile-button'), accountDialog = $('#account-dialog'), roleDialog = $('#role-dialog');
  const chatToggle = $('.chat-toggle'), chatPanel = $('#chat-panel'), messages = $('.chat-messages');
  const authForm = $('#auth-form'), roleForm = $('#role-form'), chatForm = $('.chat-form');
  let user = null, token = null, mode = 'login', roleTarget = '', timer = null, loadingMessages = false, renderedMessages = '';
  try { token = localStorage.getItem('zalupa-community-session'); } catch (_) {}

  const errors = {
    nickname:'Ник должен содержать от 3 до 16 латинских букв, цифр или символов подчёркивания.',
    password:'Пароль должен содержать не менее 8 символов и не более 72 байт.',
    taken:'Этот ник уже занят.', reserved:'Этот ник зарезервирован для владельца.',
    credentials:'Неверный ник или пароль.', signup_rate:'Слишком много регистраций. Попробуй через несколько минут.', rate:'Слишком много попыток. Попробуй через 15 минут.',
    session:'Сессия истекла. Войди в аккаунт ещё раз.', forbidden:'Выдавать роли может только владелец.',
    not_found:'Игрок не найден или его роль нельзя изменить.', role:'Проверь название и цвет роли.',
    message:'Сообщение должно содержать от 1 до 500 символов.', slow:'Пожалуйста, подожди немного перед следующим сообщением.',
    offline:'Сервис регистрации и чата пока не подключён.', network:'Не удалось связаться с сервером. Попробуй ещё раз.'
  };
  const errorText = code => errors[code] || errors.network;
  function saveToken(value) {
    token = value;
    try { if (value) localStorage.setItem('zalupa-community-session', value); else localStorage.removeItem('zalupa-community-session'); } catch (_) {}
  }
  async function rpc(name, payload = {}) {
    if (!configured) throw new Error('offline');
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 12000);
    try {
      const headers = {'Content-Type':'application/json', apikey:apiKey};
      // Publishable keys are API keys, not JWTs: sending them as Bearer tokens can be rejected.
      if (!apiKey.startsWith('sb_publishable_')) headers.Authorization = `Bearer ${apiKey}`;
      const response = await fetch(`${apiUrl}/rest/v1/rpc/zs_${name}`, {
        method:'POST', headers,
        body:JSON.stringify(payload), signal:controller.signal, cache:'no-store'
      });
      if (!response.ok) throw new Error('network');
      const result = await response.json();
      if (result && result.error) throw new Error(result.error);
      return result;
    } catch (e) { throw new Error(errors[e.message] ? e.message : 'network'); }
    finally { clearTimeout(timeout); }
  }
  function paintUser() {
    $('#profile-name').textContent = user?.nickname || 'Войти';
    profileButton.setAttribute('aria-label', user ? `Профиль ${user.nickname}` : 'Войти в профиль');
    const role = $('#profile-role'); role.textContent = user?.role || ''; role.hidden = !user?.role;
    role.style.color = /^#[0-9a-f]{6}$/i.test(user?.color || '') ? user.color : '';
    $('#auth-forms').hidden = !!user; $('#account-details').hidden = !user;
    $('#account-nickname').textContent = user?.nickname || '';
    const accountRole = $('#account-role'); accountRole.textContent = user?.role || ''; accountRole.hidden = !user?.role;
    accountRole.style.color = role.style.color;
  }
  function setMode(next) {
    mode = next; const registering = mode === 'register';
    $('#login-tab').setAttribute('aria-pressed', String(!registering));
    $('#register-tab').setAttribute('aria-pressed', String(registering));
    $('#confirm-label').hidden = !registering; authForm.elements.confirmation.required = registering;
    authForm.elements.password.autocomplete = registering ? 'new-password' : 'current-password';
    $('#register-hint').hidden = !registering;
    $('#auth-submit').textContent = registering ? 'Зарегистрироваться' : 'Войти';
    authForm.querySelector('.form-error').textContent = '';
  }
  // Keep the surface present until its exit finishes; cancellation lets a quick reopen win.
  const exits = new WeakMap();
  function cancelExit(element) {
    const pending = exits.get(element);
    if (!pending) return;
    clearTimeout(pending.timeout);
    element.removeEventListener('animationend', pending.onEnd);
    exits.delete(element);
    element.classList.remove('is-closing');
  }
  function animateExit(element, finish) {
    if (exits.has(element)) return;
    if (matchMedia('(prefers-reduced-motion: reduce)').matches) { finish(); return; }
    const done = () => { cancelExit(element); finish(); };
    const onEnd = event => { if (event.target === element && event.animationName === 'community-out') done(); };
    const timeout = setTimeout(done, 400);
    exits.set(element, { timeout, onEnd });
    element.addEventListener('animationend', onEnd);
    element.classList.add('is-closing');
  }
  function closeDialog(dialog) {
    if (dialog.open) animateExit(dialog, () => dialog.close());
  }
  function showDialog(dialog) {
    cancelExit(dialog);
    if (!dialog.open) dialog.showModal();
  }
  profileButton.addEventListener('click', () => { paintUser(); showDialog(accountDialog); });
  $('#login-tab').addEventListener('click', () => setMode('login'));
  $('#register-tab').addEventListener('click', () => setMode('register'));
  document.querySelectorAll('.dialog-close').forEach(button => button.addEventListener('click', () => closeDialog(button.closest('dialog'))));
  [accountDialog, roleDialog].forEach(dialog => {
    dialog.addEventListener('click', e => { if (e.target === dialog) closeDialog(dialog); });
    dialog.addEventListener('cancel', e => { e.preventDefault(); closeDialog(dialog); });
  });
  authForm.addEventListener('submit', async e => {
    e.preventDefault(); const formError = authForm.querySelector('.form-error'); formError.textContent = '';
    const nickname = authForm.elements.nickname.value.trim(), password = authForm.elements.password.value;
    if (mode === 'register' && password !== authForm.elements.confirmation.value) { formError.textContent = 'Пароли не совпадают.'; return; }
    if (new TextEncoder().encode(password).length > 72) { formError.textContent = errorText('password'); return; }
    const submit = $('#auth-submit'); submit.disabled = true;
    try {
      const result = await rpc(mode, {p_nickname:nickname, p_password:password});
      saveToken(result.token); user = result.user; paintUser(); authForm.reset(); closeDialog(accountDialog);
      $('.chat-feedback').textContent = ''; if (!chatPanel.hidden) await refreshMessages();
    } catch (error) { formError.textContent = errorText(error.message); }
    finally { submit.disabled = false; }
  });
  $('#logout-button').addEventListener('click', async () => {
    const oldToken = token; saveToken(null); user = null; paintUser(); closeDialog(accountDialog); renderedMessages = '';
    if (oldToken) rpc('logout', {p_token:oldToken}).catch(() => {});
    if (!chatPanel.hidden) refreshMessages();
  });
  async function loadUser() {
    if (!token || !configured) { paintUser(); return; }
    try { user = await rpc('me', {p_token:token}); }
    catch (e) { if (e.message === 'session') { saveToken(null); user = null; } }
    paintUser();
  }
  function closeChat() {
    chatToggle.setAttribute('aria-expanded','false');
    clearInterval(timer); timer = null;
    chatPanel.inert = true;
    animateExit(chatPanel, () => { chatPanel.hidden = true; chatPanel.inert = false; });
  }
  chatToggle.addEventListener('click', () => {
    if (!chatPanel.hidden && !exits.has(chatPanel)) { closeChat(); return; }
    cancelExit(chatPanel);
    chatPanel.inert = false;
    chatPanel.hidden = false; chatToggle.setAttribute('aria-expanded','true'); refreshMessages();
    timer = setInterval(() => { if (!document.hidden) { refreshMessages(); if (token) loadUser(); } }, 5000);
  });
  $('.chat-close').addEventListener('click', () => { closeChat(); chatToggle.focus(); });
  document.addEventListener('keydown', e => { if (e.key === 'Escape' && !chatPanel.hidden && !accountDialog.open && !roleDialog.open) { closeChat(); chatToggle.focus(); } });
  document.addEventListener('click', e => { if (!chatPanel.hidden && !e.target.closest('.chat-wrap') && !e.target.closest('dialog')) closeChat(); });
  function createMessage(item) {
    const article = document.createElement('article'); article.className = 'chat-message';
    const meta = document.createElement('div'); meta.className = 'message-meta';
    const canEdit = user?.owner && item.nickname.toLowerCase() !== user.nickname.toLowerCase();
    const name = document.createElement(canEdit ? 'button' : 'span'); name.className = 'message-name' + (canEdit ? ' owner-action' : '');
    name.textContent = item.nickname;
    if (canEdit) { name.type = 'button'; name.title = 'Изменить роль игрока'; name.addEventListener('click', () => openRole(item)); }
    meta.append(name);
    if (item.role) { const role = document.createElement('span'); role.className = 'message-role'; role.textContent = item.role; if (/^#[0-9a-f]{6}$/i.test(item.color || '')) { role.style.color = item.color; role.style.borderColor = item.color; } meta.append(role); }
    const time = document.createElement('time'); time.className = 'message-time'; time.dateTime = item.created_at;
    time.textContent = new Intl.DateTimeFormat('ru-RU',{hour:'2-digit',minute:'2-digit',timeZone:'Europe/Moscow'}).format(new Date(item.created_at)); meta.append(time);
    const body = document.createElement('p'); body.className = 'message-body'; body.textContent = item.body;
    article.append(meta, body); return article;
  }
  async function refreshMessages() {
    if (loadingMessages) return; loadingMessages = true;
    try {
      const list = await rpc('messages');
      if (!Array.isArray(list)) throw new Error('network');
      const fingerprint = JSON.stringify([list,user?.nickname,user?.owner]);
      if (fingerprint === renderedMessages) return;
      const stick = messages.scrollHeight - messages.scrollTop - messages.clientHeight < 80 || !renderedMessages;
      messages.replaceChildren();
      if (!list.length) { const empty = document.createElement('p'); empty.className = 'chat-empty'; empty.textContent = 'Здесь пока тихо. Начни разговор первым!'; messages.append(empty); }
      else list.forEach(item => messages.append(createMessage(item)));
      renderedMessages = fingerprint; if (stick) messages.scrollTop = messages.scrollHeight;
    } catch (e) {
      if (!renderedMessages) { messages.replaceChildren(); const empty = document.createElement('p'); empty.className = 'chat-empty'; empty.textContent = errorText(e.message); messages.append(empty); }
      else $('.chat-feedback').textContent = errorText(e.message);
    } finally { loadingMessages = false; }
  }
  chatForm.addEventListener('submit', async e => {
    e.preventDefault(); const feedback = $('.chat-feedback'); feedback.textContent = '';
    if (!user || !token) { feedback.textContent = 'Войди в аккаунт, чтобы писать в чате.'; return; }
    const input = $('#chat-input'), text = input.value.trim(); if (!text) { feedback.textContent = 'Напиши сообщение.'; return; }
    const button = chatForm.querySelector('button'); button.disabled = true;
    try { await rpc('send',{p_token:token,p_body:text}); input.value = ''; await refreshMessages(); messages.scrollTop = messages.scrollHeight; }
    catch (error) { feedback.textContent = errorText(error.message); if (error.message === 'session') { saveToken(null); user = null; paintUser(); } }
    finally { button.disabled = false; }
  });
  const hue = $('#role-hue'), saturation = $('#role-saturation'), brightness = $('#role-brightness'), color = $('#role-color'), wheel = $('.color-wheel'), marker = $('.color-marker');
  function hsvToHex(h,s,v) { s/=100;v/=100; const f=n=>{const k=(n+h/60)%6;return Math.round((v-v*s*Math.max(Math.min(k,4-k,1),0))*255).toString(16).padStart(2,'0')}; return '#'+f(5)+f(3)+f(1); }
  function syncColor() {
    color.value = hsvToHex(+hue.value,+saturation.value,+brightness.value);
    const angle = (+hue.value-90)*Math.PI/180, distance = +saturation.value/2;
    marker.style.left = `${50+Math.cos(angle)*distance}%`; marker.style.top = `${50+Math.sin(angle)*distance}%`;
  }
  function fromHex(hex) {
    const r=parseInt(hex.slice(1,3),16)/255,g=parseInt(hex.slice(3,5),16)/255,b=parseInt(hex.slice(5,7),16)/255;
    const max=Math.max(r,g,b),min=Math.min(r,g,b),d=max-min; let h=0;
    if(d) { if(max===r) h=((g-b)/d+6)%6; else if(max===g) h=(b-r)/d+2; else h=(r-g)/d+4; h*=60; }
    hue.value=Math.round(h)%360; saturation.value=Math.round(max?d/max*100:0); brightness.value=Math.max(20,Math.round(max*100)); syncColor();
  }
  [hue,saturation,brightness].forEach(input => input.addEventListener('input',syncColor));
  color.addEventListener('input',()=>fromHex(color.value));
  wheel.addEventListener('pointerdown', e => { wheel.setPointerCapture(e.pointerId); pick(e); });
  wheel.addEventListener('pointermove', e => { if (wheel.hasPointerCapture(e.pointerId)) pick(e); });
  function pick(e) { const r=wheel.getBoundingClientRect(),x=(e.clientX-r.left-r.width/2)/(r.width/2),y=(e.clientY-r.top-r.height/2)/(r.height/2); hue.value=Math.round((Math.atan2(y,x)*180/Math.PI+90+360)%360); saturation.value=Math.min(100,Math.round(Math.hypot(x,y)*100)); syncColor(); }
  function openRole(item) {
    roleTarget = item.nickname; $('#role-target').textContent = roleTarget; roleForm.elements.role.value = item.role || '';
    roleForm.querySelector('.form-error').textContent = ''; fromHex(/^#[0-9a-f]{6}$/i.test(item.color || '') ? item.color : '#a875ed'); showDialog(roleDialog);
  }
  roleForm.addEventListener('submit',async e => {
    e.preventDefault(); const button=roleForm.querySelector('[type=submit]'), err=roleForm.querySelector('.form-error'); err.textContent=''; button.disabled=true;
    try { await rpc('set_role',{p_token:token,p_nickname:roleTarget,p_role:roleForm.elements.role.value,p_color:color.value}); closeDialog(roleDialog); renderedMessages=''; await refreshMessages(); }
    catch(error){ err.textContent=errorText(error.message); } finally { button.disabled=false; }
  });
  syncColor(); paintUser(); loadUser();
})();
