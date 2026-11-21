(() => {
  'use strict';
  const dialog = document.querySelector('#lore-dialog');
  const open = document.querySelector('.lore-open');
  const close = document.querySelector('.lore-close');
  const content = document.querySelector('.lore-content');
  const reduced = matchMedia('(prefers-reduced-motion: reduce)');
  let closeTimer = 0, onEnd = null;
  function closeDialog() {
    if (!dialog.open || dialog.classList.contains('is-closing')) return;
    const finish = () => {
      clearTimeout(closeTimer); closeTimer = 0;
      if (onEnd) dialog.removeEventListener('animationend',onEnd);
      onEnd = null; dialog.classList.remove('is-closing');
      if (dialog.open) dialog.close();
    };
    if (reduced.matches) { finish(); return; }
    onEnd = event => { if (event.target === dialog && event.animationName === 'lore-out') finish(); };
    dialog.addEventListener('animationend',onEnd); closeTimer = setTimeout(finish,400);
    dialog.classList.add('is-closing');
  }
  open.addEventListener('click', () => {
    if (dialog.open) return;
    window.dispatchEvent(new Event('overlay-opening')); dialog.showModal();
    content.scrollTo({top:0,behavior:'instant'});
  });
  close.addEventListener('click',closeDialog);
  dialog.addEventListener('cancel',event => { event.preventDefault(); closeDialog(); });
  dialog.addEventListener('click',event => { if (event.target === dialog) closeDialog(); });
})();
