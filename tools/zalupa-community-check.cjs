const { chromium } = require('playwright');
const fs = require('fs');
(async()=>{
  const url='https://nacmraajxilyvyjaptwb.supabase.co';
  const key='sb_publishable_EL011m7qSbLxG0pDQCd0aQ_fhNZd7wS';
  const result={target:'permanent-single-step-075'};
  try {
    const r=await fetch(url+'/rest/v1/rpc/zs_me',{method:'POST',headers:{apikey:key,'Content-Type':'application/json'},body:JSON.stringify({p_token:'invalid'})});
    const data=await r.json();result.api={status:r.status,invalidSession:data.error==='session',errorCode:data.code||null};
  } catch(e) {result.api={error:String(e)}}
  const browser=await chromium.launch({headless:true,executablePath:'/usr/bin/google-chrome',args:['--no-sandbox']});
  const page=await browser.newPage({viewport:{width:1440,height:900}});const errors=[],responses=[];
  page.on('pageerror',e=>errors.push(String(e)));
  page.on('response',r=>{if(r.url().includes('.supabase.co/rest/v1/'))responses.push({status:r.status(),route:r.url().split('/').pop()})});
  try{
    const r=await page.goto('https://htmlpreview.github.io/?https://github.com/romagggrt-lgtm/123/blob/zalupa-smp-site/site/index.html',{waitUntil:'domcontentloaded',timeout:40000});
    await page.waitForSelector('.chat-toggle',{timeout:30000});await page.waitForTimeout(2000);
    await page.locator('.chat-toggle').click();await page.waitForTimeout(7000);
    result.browser={status:r.status(),profileVisible:await page.locator('.profile-button').isVisible(),chatVisible:await page.locator('.chat-panel').isVisible(),chatServiceUnavailable:await page.locator('.chat-empty').count() ? (await page.locator('.chat-empty').innerText()).includes('не подключён') : false,chatNetworkError:await page.locator('.chat-empty').count() ? (await page.locator('.chat-empty').innerText()).includes('Не удалось связаться') : false,requests:responses,errors};
    result.browser.closeAnimation=await page.evaluate(()=>{document.querySelector('.chat-close').click();const el=document.querySelector('.chat-panel');return {closing:el.classList.contains('is-closing'),animation:getComputedStyle(el).animationName,hidden:el.hidden};});
    await page.waitForTimeout(500);
    result.browser.closedAfterAnimation=await page.locator('.chat-panel').evaluate(el=>el.hidden);
    await page.locator('.profile-button').click();
    await page.waitForTimeout(350);
    result.browser.authTransition=await page.evaluate(async()=>{
      const frames=[], tabs=document.querySelector('.auth-tabs'), fields=document.querySelector('.auth-register-fields');
      if (!fields) return {error:'updated form not loaded'};
      document.querySelector('#register-tab').click();
      const start=performance.now();
      await new Promise(resolve=>{function frame(){frames.push({height:fields.getBoundingClientRect().height,transform:getComputedStyle(tabs,'::before').transform});if(performance.now()-start<500)requestAnimationFrame(frame);else resolve()}requestAnimationFrame(frame)});
      const final=frames.at(-1), mid=frames.some(f=>f.height>1&&f.height<final.height-1&&f.transform!==final.transform);
      return {mode:tabs.dataset.mode,expanded:final.height>100,intermediateFrame:mid,pressed:document.querySelector('#register-tab').getAttribute('aria-pressed')};
    });
    await page.keyboard.press('Escape'); await page.waitForTimeout(400);
    await page.locator('.gallery-all-button').click({timeout:15000});
    const galleryCount=await page.locator('.gallery-grid .gallery-photo').count();
    await page.locator('.gallery-grid .gallery-photo').first().click();
    await page.locator('.viewer-image').evaluate(img=>img.decode());
    const first=await page.locator('.viewer-counter').innerText();
    await page.keyboard.press('ArrowRight');
    await page.locator('.viewer-image').evaluate(img=>img.decode());
    result.browser.gallery={count:galleryCount,featured:await page.locator('.gallery-preview .gallery-photo').count(),first,next:await page.locator('.viewer-counter').innerText(),imageLoaded:await page.locator('.viewer-image').evaluate(img=>img.naturalWidth>0),captionCount:await page.locator('.gallery-photo-label, .viewer-caption').count(),tileText:(await page.locator('.gallery-grid .gallery-photo').allTextContents()).some(text=>text.trim().length>0)};
    await page.keyboard.press('Escape'); await page.waitForTimeout(400);
    await page.keyboard.press('Escape'); await page.waitForTimeout(400);
    result.browser.wheel=await page.evaluate(async()=>{
      window.scrollTo({top:0,behavior:'instant'});
      const event=new WheelEvent('wheel',{deltaY:100,bubbles:true,cancelable:true});
      document.querySelector('.hero').dispatchEvent(event);
      const frames=[], start=performance.now();
      await new Promise(resolve=>{function frame(){frames.push(scrollY);if(performance.now()-start<2200)requestAnimationFrame(frame);else resolve()}requestAnimationFrame(frame)});
      return {prevented:event.defaultPrevented,positions:new Set(frames).size,final:frames.at(-1),gentlerScript:[...document.scripts].some(script=>script.textContent.includes('Math.exp(-elapsed / 350)'))};
    });


  }catch(e){result.browser={error:String(e),requests:responses,errors}}
  await browser.close();fs.writeFileSync('ci-bootstrap/zalupa-reference/community-check.json',JSON.stringify(result,null,2));console.log(JSON.stringify(result));
})().catch(e=>{console.error(e);process.exitCode=1});
