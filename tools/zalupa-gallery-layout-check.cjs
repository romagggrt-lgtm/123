const { chromium } = require('playwright');
const fs = require('fs');
(async()=>{
  const result={target:'evrionmc-rebrand-120'},errors=[];
  const browser=await chromium.launch({headless:true,executablePath:'/usr/bin/google-chrome',ignoreDefaultArgs:['--hide-scrollbars'],args:['--no-sandbox']});
  const page=await browser.newPage({viewport:{width:1440,height:900}});
  page.on('pageerror',e=>errors.push(String(e)));
  try{
    const response=await page.goto('https://htmlpreview.github.io/?https://github.com/romagggrt-lgtm/123/blob/evrionmc/site/index.html',{waitUntil:'domcontentloaded',timeout:40000});
    await page.waitForSelector('.gallery-all-button:not([hidden])',{timeout:30000});
    await page.evaluate(()=>document.documentElement.style.zoom='.9');
    result.brand=await page.evaluate(()=>({title:document.title,hero:document.querySelector('#hero-title').innerText,brand:document.querySelector('.brand-name').innerText,footer:document.querySelector('.footer-brand').innerText,background:getComputedStyle(document.querySelector('.hero-image')).backgroundImage,oldNameVisible:/zalupa/i.test(document.body.innerText),photos:document.querySelectorAll('.gallery-grid .gallery-photo').length}));
    result.brand.backgroundLoaded=await page.evaluate(async()=>{const url=getComputedStyle(document.querySelector('.hero-image')).backgroundImage.slice(5,-2);return await new Promise(resolve=>{const image=new Image();image.onload=()=>resolve(image.naturalWidth>0);image.onerror=()=>resolve(false);image.src=url})});
    result.art=[];
    for (const section of ['topic-1','topic-2','topic-4','topic-5']) {
      const img=page.locator('#'+section+' .section-art img');
      await img.scrollIntoViewIfNeeded({timeout:20000});
      await img.evaluate(e=>e.decode());
      result.art.push(await img.evaluate(e=>({src:e.currentSrc,width:e.naturalWidth,height:e.naturalHeight,frame:e.parentElement.getBoundingClientRect().width})));
    }
    await page.locator('.lore-open').click({timeout:20000});await page.waitForTimeout(500);
    const loreOpen=await page.locator('#lore-dialog').evaluate(e=>e.open);
    const loreHeadings=await page.locator('.lore-story h3, .lore-story h4').allTextContents();
    const loreBefore=await page.evaluate(()=>scrollY);await page.keyboard.press('PageDown');await page.waitForTimeout(300);
    const loreScroll=await page.locator('.lore-content').evaluate(e=>e.scrollTop);
    await page.keyboard.press('Escape');await page.waitForTimeout(500);const loreAfter=await page.evaluate(()=>scrollY);
    result.lore={loreOpen,loreHeadings,loreBefore,loreAfter,loreScroll};
    await page.locator('.gallery-all-button').scrollIntoViewIfNeeded();await page.waitForTimeout(1200);
    const snap=()=>page.evaluate(()=>({x:document.querySelector('#topic-3-title').getBoundingClientRect().x,nav:document.querySelector('.topbar-layout').getBoundingClientRect().x,root:document.documentElement.clientWidth,y:scrollY,scrollbar:innerWidth-document.documentElement.clientWidth}));
    const before=await snap();await page.locator('.gallery-all-button').click();await page.waitForTimeout(500);const open=await snap();
    await page.locator('.gallery-grid .gallery-photo').first().click();await page.waitForTimeout(400);const viewer=await snap();
    await page.keyboard.press('Escape');await page.waitForTimeout(500);await page.keyboard.press('Escape');await page.waitForTimeout(500);const after=await snap();
    await page.locator('.rules-open').click({timeout:15000});await page.waitForTimeout(500);
    const rulesBefore=await page.evaluate(()=>scrollY);
    const rulesItems=await page.locator('.rules-item').count(), rulesSections=await page.locator('.rules-section').count();
    await page.locator('[data-rules-target="rules-mods"]').click();await page.waitForTimeout(2500);
    const active=await page.locator('[data-rules-target="rules-mods"]').getAttribute('aria-current');
    await page.mouse.move(1434,800);await page.mouse.wheel(0,600);await page.waitForTimeout(500);
    const rulesDuring=await page.evaluate(()=>scrollY);
    await page.locator('.rules-close').click();await page.waitForTimeout(500);const rulesAfter=await page.evaluate(()=>scrollY);
    const stable=[open,viewer,after].every(v=>Math.abs(v.x-before.x)<.1 && Math.abs(v.nav-before.nav)<.1 && v.root===before.root && Math.abs(v.y-before.y)<1);
    result.browser={status:response.status(),before,open,viewer,after,stable,rulesItems,rulesSections,active,rulesBefore,rulesDuring,rulesAfter,errors};
  }catch(e){result.browser={error:String(e),errors}}
  await browser.close();fs.writeFileSync('ci-bootstrap/zalupa-reference/gallery-layout-check.json',JSON.stringify(result,null,2));console.log(JSON.stringify(result));
})().catch(e=>{console.error(e);process.exitCode=1});
