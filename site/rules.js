(() => {
  'use strict';
  const dialog = document.querySelector('#rules-dialog');
  const open = document.querySelector('.rules-open');
  const close = document.querySelector('.rules-close');
  const content = document.querySelector('.rules-content');
  const links = [...document.querySelectorAll('[data-rules-target]')];
  const sections = links.map(link => document.getElementById(link.dataset.rulesTarget));
  const reduced = matchMedia('(prefers-reduced-motion: reduce)');
  let closeTimer = 0, onEnd = null, scrollFrame = 0;
  function setActive(id) {
    for (const link of links) {
      if (link.dataset.rulesTarget === id) link.setAttribute('aria-current','location');
      else link.removeAttribute('aria-current');
    }
  }
  function closeDialog() {
    if (!dialog.open || dialog.classList.contains('is-closing')) return;
    const finish = () => {
      clearTimeout(closeTimer); closeTimer = 0;
      if (onEnd) dialog.removeEventListener('animationend',onEnd);
      onEnd = null; dialog.classList.remove('is-closing');
      if (dialog.open) dialog.close();
    };
    if (reduced.matches) { finish(); return; }
    onEnd = event => { if (event.target === dialog && event.animationName === 'rules-out') finish(); };
    dialog.addEventListener('animationend',onEnd); closeTimer = setTimeout(finish,400);
    dialog.classList.add('is-closing');
  }
  open.addEventListener('click', () => {
    if (dialog.open) return;
    content.scrollTop = 0; setActive(sections[0].id);
    window.dispatchEvent(new Event('overlay-opening')); dialog.showModal();
  });
  close.addEventListener('click',closeDialog);
  dialog.addEventListener('cancel',event => { event.preventDefault(); closeDialog(); });
  dialog.addEventListener('click',event => { if (event.target === dialog) closeDialog(); });
  links.forEach((link,index) => link.addEventListener('click',() => {
    const top = sections[index].offsetTop - 20;
    content.scrollTo({top,behavior:reduced.matches?'instant':'smooth'}); setActive(sections[index].id);
  }));
  content.addEventListener('scroll',() => {
    if (scrollFrame) return;
    scrollFrame = requestAnimationFrame(() => {
      scrollFrame = 0;
      const marker = content.getBoundingClientRect().top + 50;
      let active = sections[0].id;
      for (const section of sections) if (section.getBoundingClientRect().top <= marker) active = section.id;
      if (content.scrollTop + content.clientHeight >= content.scrollHeight - 2) active = sections[sections.length - 1].id;
      setActive(active);
    });
  },{passive:true});
})();
