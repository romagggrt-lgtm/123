const { chromium } = require('playwright');
const fs = require('fs');
(async()=>{
  const result={target:'evrionmc-header-logo-1'},errors=[];
  const browser=await chromium.launch({headless:true,executablePath:'/usr/bin/google-chrome',ignoreDefaultArgs:['--hide-scrollbars'],args:['--no-sandbox']});
  const page=await browser.newPage({viewport:{width:1440,height:900}});
  page.on('pageerror',e=>errors.push(String(e)));
  const oldUrl='https://htmlpreview.github.io/?https://github.com/romagggrt-lgtm/123/blob/zalupa-smp-site/site/index.html#topic-4';
  try {
    try {await page.goto(oldUrl,{waitUntil:'domcontentloaded',timeout:40000})} catch(e) {if(!String(e).includes('interrupted by another navigation'))throw e;}
    await page.waitForURL(url=>url.href.includes('/blob/evrionmc/site/index.html')&&url.hash==='#topic-4',{timeout:30000});
    await page.waitForSelector('.gallery-all-button:not([hidden])',{timeout:30000});
    const logo=page.locator('.brand-mark img');await logo.evaluate(e=>e.decode(),{timeout:20000});
    result.logo=await logo.evaluate(e=>({src:e.currentSrc,width:e.naturalWidth,height:e.naturalHeight,clip:getComputedStyle(e).clipPath}));
    result.browser={finalUrl:page.url(),title:await page.title(),heading:await page.locator('#hero-title').innerText(),galleryCount:await page.locator('.gallery-grid .gallery-photo').count(),oldNameVisible:await page.evaluate(()=>/zalupa/i.test(document.body.innerText)),errors};
  } catch(e) {result.browser={error:String(e),finalUrl:page.url(),title:await page.title().catch(()=>''),errors}}
  await browser.close();fs.writeFileSync('ci-bootstrap/zalupa-reference/gallery-layout-check.json',JSON.stringify(result,null,2));console.log(JSON.stringify(result));
})().catch(e=>{console.error(e);process.exitCode=1});
