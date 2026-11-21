(() => {
  'use strict';
  const gallery = document.querySelector('#gallery-dialog');
  const viewer = document.querySelector('#viewer-dialog');
  const rules = document.querySelector('#rules-dialog');
  const lore = document.querySelector('#lore-dialog');
  let touchX = null, touchY = null, lockedY = null;
  function current() {
    if (viewer?.open) return {dialog:viewer, scroller:null};
    if (lore?.open) return {dialog:lore, scroller:lore.querySelector('.lore-content')};
    if (rules?.open) return {dialog:rules, scroller:rules.querySelector('.rules-content')};
    if (gallery?.open) return {dialog:gallery, scroller:gallery.querySelector('.gallery-dialog-inner')};
    return null;
  }
  function canScroll(element, delta) {
    if (!element || !delta) return false;
    return delta < 0 ? element.scrollTop > 0 : element.scrollTop + element.clientHeight < element.scrollHeight - 1;
  }
  window.addEventListener('overlay-opening', () => {
    if (current()) return;
    lockedY = window.scrollY;
    window.scrollTo({top:lockedY,behavior:'instant'});
  });
  // The native scrollbar can receive wheel or drag input without a DOM wheel event.
  window.addEventListener('scroll', () => {
    if (current() && lockedY !== null && Math.abs(window.scrollY - lockedY) > .5) {
      window.scrollTo({top:lockedY,behavior:'instant'});
    }
  },{passive:true});
  document.addEventListener('wheel', event => {
    const active = current();
    if (!active || event.ctrlKey || event.metaKey || !event.cancelable) return;
    const {dialog,scroller} = active;
    if (scroller && scroller.contains(event.target)) {
      if (event.deltaY && !canScroll(scroller,event.deltaY)) event.preventDefault();
      return;
    }
    const nav = dialog.querySelector('.rules-nav');
    if (nav && nav.contains(event.target) && (event.shiftKey || Math.abs(event.deltaX) > Math.abs(event.deltaY))) return;
    event.preventDefault();
    if (scroller && event.deltaY) {
      const delta = event.deltaY * (event.deltaMode === 1 ? 16 : event.deltaMode === 2 ? scroller.clientHeight : 1);
      scroller.scrollBy({top:delta,behavior:'instant'});
    }
  },{capture:true,passive:false});
  document.addEventListener('touchstart', event => {
    touchX = event.touches.length === 1 ? event.touches[0].clientX : null;
    touchY = event.touches.length === 1 ? event.touches[0].clientY : null;
  },{capture:true,passive:true});
  document.addEventListener('touchmove', event => {
    const active = current();
    if (!active || event.touches.length !== 1 || touchY === null || !event.cancelable) return;
    const x = event.touches[0].clientX, y = event.touches[0].clientY;
    const deltaX = touchX - x, delta = touchY - y; touchX = x; touchY = y;
    const {dialog,scroller} = active;
    if (dialog.querySelector('.rules-nav')?.contains(event.target)) {
      if (Math.abs(deltaX) > Math.abs(delta)) return;
      event.preventDefault();
      if (scroller) scroller.scrollTop += delta;
      return;
    }
    if (!scroller || !scroller.contains(event.target) || (delta && !canScroll(scroller,delta))) event.preventDefault();
  },{capture:true,passive:false});
  document.addEventListener('keydown', event => {
    const active=current();
    if (!active || event.altKey || event.ctrlKey || event.metaKey) return;
    const scrollKeys=['PageDown','PageUp','Home','End','ArrowDown','ArrowUp'];
    if (!scrollKeys.includes(event.key)) return;
    if (!active.scroller) { event.preventDefault(); return; }
    if ((active.dialog === rules || active.dialog === lore) && !active.scroller.contains(document.activeElement)) {
      const scroller=active.scroller, step=Math.round(scroller.clientHeight*.8);
      if (event.key === 'Home') scroller.scrollTo({top:0,behavior:'instant'});
      else if (event.key === 'End') scroller.scrollTo({top:scroller.scrollHeight,behavior:'instant'});
      else scroller.scrollBy({top: event.key === 'PageUp' ? -step : event.key === 'PageDown' ? step : event.key === 'ArrowUp' ? -48 : 48,behavior:'instant'});
      event.preventDefault();
    }
  },{capture:true});
})();
