(() => {
  'use strict';
  const photos = Array.isArray(window.ZALUPA_GALLERY) ? window.ZALUPA_GALLERY.filter(photo => photo && typeof photo.src === 'string' && photo.src) : [];
  if (!photos.length) return;
  const $ = selector => document.querySelector(selector);
  const preview = $('.gallery-preview'), grid = $('.gallery-grid'), allButton = $('.gallery-all-button');
  const gallery = $('#gallery-dialog'), viewer = $('#viewer-dialog'), image = $('.viewer-image');
  const counter = $('.viewer-counter');
  const reduced = matchMedia('(prefers-reduced-motion: reduce)');
  let current = 0, touchStart = null, imageAnimation = null;
  const exits = new WeakMap();

  function label(photo, index) { return photo.alt || `Фотография сервера ${index + 1}`; }
  function makePhoto(photo, index, lazy) {
    const button = document.createElement('button');
    button.type = 'button'; button.className = 'gallery-photo';
    button.setAttribute('aria-label', `Открыть: ${label(photo, index)}`);
    const img = document.createElement('img'); img.src = photo.thumb || photo.src; img.alt = '';
    img.loading = lazy ? 'lazy' : 'eager'; img.decoding = 'async';
    button.append(img);
    button.addEventListener('click', () => openViewer(index));
    return button;
  }
  const featured = photos.map((photo, index) => ({photo, index})).filter(item => item.photo.featured).slice(0, 4);
  for (let index = 0; featured.length < Math.min(4, photos.length) && index < photos.length; index++) {
    if (!featured.some(item => item.index === index)) featured.push({photo:photos[index], index});
  }
  featured.forEach(({photo,index}) => preview.append(makePhoto(photo,index,true)));
  photos.forEach((photo,index) => grid.append(makePhoto(photo,index,true)));
  preview.hidden = false; allButton.hidden = false; $('.gallery-awaiting').hidden = true;

  function lockPage() { document.documentElement.classList.toggle('gallery-open', gallery.open || viewer.open); }
  function cancelExit(dialog) {
    const pending = exits.get(dialog); if (!pending) return;
    clearTimeout(pending.timeout); dialog.removeEventListener('animationend', pending.onEnd);
    exits.delete(dialog); dialog.classList.remove('is-closing');
  }
  function closeDialog(dialog) {
    if (!dialog.open || exits.has(dialog)) return;
    const finish = () => { cancelExit(dialog); dialog.close(); lockPage(); };
    if (reduced.matches) { finish(); return; }
    const onEnd = event => { if (event.target === dialog && event.animationName === 'gallery-out') finish(); };
    const timeout = setTimeout(finish,400);
    exits.set(dialog,{onEnd,timeout}); dialog.addEventListener('animationend',onEnd); dialog.classList.add('is-closing');
  }
  function showDialog(dialog) { cancelExit(dialog); if (!dialog.open) dialog.showModal(); lockPage(); }
  allButton.addEventListener('click', () => showDialog(gallery));
  $('.gallery-close').addEventListener('click', () => closeDialog(gallery));
  $('.viewer-close').addEventListener('click', () => closeDialog(viewer));
  [gallery,viewer].forEach(dialog => {
    dialog.addEventListener('cancel', event => { event.preventDefault(); closeDialog(dialog); });
    dialog.addEventListener('click', event => { if (event.target === dialog) closeDialog(dialog); });
    dialog.addEventListener('close', lockPage);
  });
  function showPhoto(index, direction = 0) {
    current = (index + photos.length) % photos.length;
    const photo = photos[current];
    if (imageAnimation) imageAnimation.cancel();
    image.src = photo.src; image.alt = label(photo,current);
    counter.textContent = `${String(current + 1).padStart(2,'0')} / ${String(photos.length).padStart(2,'0')}`;
    if (!reduced.matches && image.animate) {
      const distance = direction ? 14 * direction : 0;
      imageAnimation = image.animate([{opacity:.5,transform:`translateX(${distance}px) scale(.99)`},{opacity:1,transform:'translateX(0) scale(1)'}],{duration:250,easing:'cubic-bezier(.2,.75,.25,1)'});
    }
    for (const adjacent of [(current+1)%photos.length,(current-1+photos.length)%photos.length]) { const next = new Image(); next.src = photos[adjacent].src; }
  }
  function openViewer(index) { showPhoto(index); showDialog(viewer); }
  $('.viewer-prev').addEventListener('click', () => showPhoto(current-1,-1));
  $('.viewer-next').addEventListener('click', () => showPhoto(current+1,1));
  viewer.addEventListener('keydown', event => {
    if (event.key === 'ArrowLeft') { event.preventDefault(); showPhoto(current-1,-1); }
    if (event.key === 'ArrowRight') { event.preventDefault(); showPhoto(current+1,1); }
  });
  viewer.addEventListener('touchstart', event => { touchStart = event.changedTouches[0]?.clientX ?? null; },{passive:true});
  viewer.addEventListener('touchend', event => {
    if (touchStart === null) return;
    const distance = (event.changedTouches[0]?.clientX ?? touchStart) - touchStart; touchStart = null;
    if (Math.abs(distance) > 60) showPhoto(current + (distance < 0 ? 1 : -1),distance < 0 ? 1 : -1);
  },{passive:true});
})();
