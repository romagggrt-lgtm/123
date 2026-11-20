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

  if ('IntersectionObserver' in window) {
    if (!matchMedia('(prefers-reduced-motion: reduce)').matches) {
      const reveal = new IntersectionObserver(entries => {
        for (const entry of entries) if (entry.isIntersecting) { entry.target.classList.add('is-visible'); reveal.unobserve(entry.target); }
      }, { threshold: .12 });
      document.querySelectorAll('.reveal').forEach(element => reveal.observe(element));
    }
  }
})();
