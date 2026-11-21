const { chromium } = require('playwright');
const fs = require('fs');
(async()=>{
  const result={target:'permanent-gallery-stable-073'},errors=[];
  const browser=await chromium.launch({headless:true,executablePath:'/usr/bin/google-chrome',ignoreDefaultArgs:['--hide-scrollbars'],args:['--no-sandbox']});
  const page=await browser.newPage({viewport:{width:1440,height:900}});
  page.on('pageerror',e=>errors.push(String(e)));
  try{
    const r=await page.goto('https://htmlpreview.github.io/?https://github.com/romagggrt-lgtm/123/blob/zalupa-smp-site/site/index.html',{waitUntil:'domcontentloaded',timeout:40000});
    await page.waitForSelector('.gallery-all-button:not([hidden])',{timeout:30000});
    await page.locator('.gallery-all-button').scrollIntoViewIfNeeded();await page.waitForTimeout(1200);
    const snap=()=>page.evaluate(()=>({x:document.querySelector('#topic-3-title').getBoundingClientRect().x,nav:document.querySelector('.topbar-layout').getBoundingClientRect().x,body:document.body.clientWidth,y:scrollY,scrollbar:innerWidth-document.documentElement.clientWidth}));
    const before=await snap();await page.locator('.gallery-all-button').click();await page.waitForTimeout(500);const open=await snap();
    await page.locator('.gallery-grid .gallery-photo').first().click();await page.waitForTimeout(400);const viewer=await snap(),viewerWidth=await page.locator('#viewer-dialog').evaluate(e=>e.getBoundingClientRect().width);
    await page.keyboard.press('Escape');await page.waitForTimeout(500);await page.keyboard.press('Escape');await page.waitForTimeout(500);const after=await snap();
    result.browser={status:r.status(),before,open,viewer,after,viewerWidth,viewport:1440,errors};
  }catch(e){result.browser={error:String(e),errors}}
  await browser.close();fs.writeFileSync('ci-bootstrap/zalupa-reference/gallery-layout-check.json',JSON.stringify(result,null,2));console.log(JSON.stringify(result));
})().catch(e=>{console.error(e);process.exitCode=1});
