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

  if ('IntersectionObserver' in window) {
    const active = new IntersectionObserver(entries => {
      for (const entry of entries) {
        if (!entry.isIntersecting) continue;
        for (const link of links) {
          if (link.getAttribute('href') === '#' + entry.target.id) link.setAttribute('aria-current', 'location');
          else link.removeAttribute('aria-current');
        }
      }
    }, { rootMargin: '-30% 0px -55% 0px', threshold: 0 });
    sections.forEach(section => active.observe(section));

    if (!matchMedia('(prefers-reduced-motion: reduce)').matches) {
      const reveal = new IntersectionObserver(entries => {
        for (const entry of entries) if (entry.isIntersecting) { entry.target.classList.add('is-visible'); reveal.unobserve(entry.target); }
      }, { threshold: .12 });
      document.querySelectorAll('.reveal').forEach(element => reveal.observe(element));
    }
  }
})();
