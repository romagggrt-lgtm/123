(() => {
  const root = document.documentElement;
  const toggle = document.querySelector('.theme-toggle');
  const themeColor = document.querySelector('#browser-theme');
  const links = [...document.querySelectorAll('.nav-links a')];
  const sections = [...document.querySelectorAll('main section[id]')];

  function syncTheme() {
    const dark = root.dataset.theme === 'dark';
    const label = dark ? 'Включить светлую тему' : 'Включить тёмную тему';
    toggle.setAttribute('aria-label', label);
    toggle.setAttribute('title', label);
    toggle.setAttribute('aria-pressed', String(dark));
    themeColor.setAttribute('content', dark ? '#100e17' : '#f8f6fe');
  }
  syncTheme();
  toggle.addEventListener('click', () => {
    root.dataset.theme = root.dataset.theme === 'dark' ? 'light' : 'dark';
    try { localStorage.setItem('evrion-theme', root.dataset.theme); } catch (_) {}
    syncTheme();
  });

  let scrollFrame = 0;
  function updateActiveLink() {
    scrollFrame = 0;
    const marker = Math.min(window.innerHeight * .4, 300);
    let current = null;
    for (const section of sections) {
      const rect = section.getBoundingClientRect();
      if (rect.top <= marker && rect.bottom > marker) current = section.id;
    }
    for (const link of links) {
      if (current && link.hash === '#' + current) link.setAttribute('aria-current', 'location');
      else link.removeAttribute('aria-current');
    }
  }
  function scheduleActiveLink() {
    if (!scrollFrame) scrollFrame = requestAnimationFrame(updateActiveLink);
  }
  window.addEventListener('scroll', scheduleActiveLink, { passive: true });
  window.addEventListener('resize', scheduleActiveLink);
  window.addEventListener('hashchange', scheduleActiveLink);
  updateActiveLink();

  // Let the browser animate mouse-wheel steps; keep touchpads and nested scrollers native.
  const reducedMotion = matchMedia('(prefers-reduced-motion: reduce)');
  let wheelActive = false, wheelTarget = 0, wheelDirection = 0, wheelTimer = 0;
  function clearWheelTarget() {
    clearTimeout(wheelTimer);
    wheelTimer = 0; wheelActive = false; wheelDirection = 0;
  }
  function stopWheelMotion() {
    if (!wheelActive) return;
    clearWheelTarget();
    window.scrollTo({top:window.scrollY, behavior:'instant'});
  }
  function needsNativeWheel(event) {
    if (reducedMotion.matches || event.defaultPrevented || !event.cancelable ||
        event.ctrlKey || event.metaKey || event.altKey || event.shiftKey ||
        !event.deltaY || Math.abs(event.deltaX) > Math.abs(event.deltaY) ||
        document.querySelector('dialog[open]')) return true;
    for (const element of event.composedPath()) {
      if (!(element instanceof Element) || element === root || element === document.body) continue;
      if (element.matches('.chat-wrap, input, textarea, select, [contenteditable]:not([contenteditable="false"])')) return true;
      if (element.scrollHeight > element.clientHeight + 1 && /^(auto|scroll|overlay)$/.test(getComputedStyle(element).overflowY)) return true;
    }
    return false;
  }
  window.addEventListener('wheel', event => {
    // Small or fractional deltas usually come from a touchpad, which already has its own momentum.
    if (!event.deltaX && !event.deltaY && !event.ctrlKey && !event.metaKey && !event.altKey && !event.shiftKey) return;
    if (needsNativeWheel(event)) { clearWheelTarget(); return; }
    if (event.deltaMode === 0 && (Math.abs(event.deltaY) < 50 || !Number.isInteger(event.deltaY))) {
      clearWheelTarget();
      return;
    }
    const delta = event.deltaY * (event.deltaMode === 1 ? 16 : event.deltaMode === 2 ? window.innerHeight : 1);
    const direction = Math.sign(delta);
    const maxScroll = Math.max(0, root.scrollHeight - window.innerHeight);
    const base = wheelActive && direction === wheelDirection ? wheelTarget : window.scrollY;
    const next = Math.max(0, Math.min(maxScroll, base + delta));
    if (!wheelActive && Math.abs(next - window.scrollY) < .01) return;
    event.preventDefault();
    wheelActive = true; wheelTarget = next; wheelDirection = direction;
    window.scrollTo({top:wheelTarget, behavior:'smooth'});
    clearTimeout(wheelTimer);
    wheelTimer = setTimeout(clearWheelTarget, 1200);
  }, {passive:false});
  window.addEventListener('scrollend', () => {
    if (wheelActive && Math.abs(window.scrollY - wheelTarget) < 1) clearWheelTarget();
  });
  for (const name of ['pointerdown', 'touchstart', 'keydown', 'hashchange']) {
    window.addEventListener(name, stopWheelMotion, {passive:true});
  }
  document.addEventListener('visibilitychange', () => { if (document.hidden) clearWheelTarget(); });
  reducedMotion.addEventListener('change', stopWheelMotion);

  if ('IntersectionObserver' in window) {
    if (!matchMedia('(prefers-reduced-motion: reduce)').matches) {
      const reveal = new IntersectionObserver(entries => {
        for (const entry of entries) if (entry.isIntersecting) { entry.target.classList.add('is-visible'); reveal.unobserve(entry.target); }
      }, { threshold: .12 });
      document.querySelectorAll('.reveal').forEach(element => reveal.observe(element));
    }
  }
})();
