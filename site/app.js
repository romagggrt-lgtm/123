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
    try { localStorage.setItem('zalupa-theme', root.dataset.theme); } catch (_) {}
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

  // Ease wheel movement on the page while leaving nested controls and overlays native.
  const reducedMotion = matchMedia('(prefers-reduced-motion: reduce)');
  let wheelFrame = 0, wheelTarget = 0, wheelPosition = 0, lastWheelFrame = 0;
  function stopWheelMotion() {
    if (wheelFrame) cancelAnimationFrame(wheelFrame);
    wheelFrame = 0; lastWheelFrame = 0;
  }
  function wheelStep(time) {
    const elapsed = lastWheelFrame ? Math.min(time - lastWheelFrame, 64) : 16.7;
    lastWheelFrame = time;
    const maxScroll = Math.max(0, root.scrollHeight - window.innerHeight);
    wheelTarget = Math.max(0, Math.min(maxScroll, wheelTarget));
    const distance = wheelTarget - wheelPosition;
    if (Math.abs(distance) < .5) {
      window.scrollTo({top:wheelTarget, behavior:'instant'});
      stopWheelMotion();
      return;
    }
    wheelPosition += distance * (1 - Math.exp(-elapsed / 110));
    window.scrollTo({top:wheelPosition, behavior:'instant'});
    wheelFrame = requestAnimationFrame(wheelStep);
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
    if (needsNativeWheel(event)) { stopWheelMotion(); return; }
    const delta = event.deltaY * (event.deltaMode === 1 ? 16 : event.deltaMode === 2 ? window.innerHeight : 1);
    const maxScroll = Math.max(0, root.scrollHeight - window.innerHeight);
    if (!wheelFrame) { wheelPosition = window.scrollY; wheelTarget = wheelPosition; }
    else if (Math.sign(delta) !== Math.sign(wheelTarget - wheelPosition)) wheelTarget = wheelPosition;
    const next = Math.max(0, Math.min(maxScroll, wheelTarget + delta));
    if (!wheelFrame && Math.abs(next - wheelPosition) < .01) return;
    event.preventDefault();
    wheelTarget = next;
    if (!wheelFrame) wheelFrame = requestAnimationFrame(wheelStep);
  }, {passive:false});
  for (const name of ['pointerdown', 'touchstart', 'keydown', 'hashchange']) {
    window.addEventListener(name, stopWheelMotion, {passive:true});
  }
  document.addEventListener('visibilitychange', () => { if (document.hidden) stopWheelMotion(); });
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
