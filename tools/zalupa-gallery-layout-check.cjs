const { chromium } = require('playwright');
const fs = require('fs');
(async()=>{
  const result={target:'find-renamed-repository',repository:process.env.GITHUB_REPOSITORY,errors:[]};
  const browser=await chromium.launch({headless:true,executablePath:'/usr/bin/google-chrome',args:['--no-sandbox']});
  const page=await browser.newPage({viewport:{width:1440,height:900}});
  page.on('pageerror',e=>result.errors.push(String(e)));
  try {
    const old=await page.goto('https://github.com/romagggrt-lgtm/123',{waitUntil:'domcontentloaded',timeout:30000});
    result.github={status:old.status(),url:page.url()};
  } catch(e) {result.github={error:String(e)}}
  try {
    result.newUrl='https://htmlpreview.github.io/?https://github.com/'+process.env.GITHUB_REPOSITORY+'/blob/evrionmc/site/index.html';
    const response=await page.goto(result.newUrl,{waitUntil:'domcontentloaded',timeout:40000});
    await page.waitForSelector('.gallery-all-button:not([hidden])',{timeout:30000});
    const logo=page.locator('.brand-mark img');await logo.evaluate(e=>e.decode());
    result.site={status:response.status(),url:page.url(),title:await page.title(),heading:await page.locator('#hero-title').innerText(),logo:await logo.evaluate(e=>e.naturalWidth),gallery:await page.locator('.gallery-grid .gallery-photo').count()};
  } catch(e) {result.site={error:String(e),url:page.url(),title:await page.title().catch(()=>'')}}
  await browser.close();fs.writeFileSync('ci-bootstrap/zalupa-reference/gallery-layout-check.json',JSON.stringify(result,null,2));console.log(JSON.stringify(result));
})().catch(e=>{console.error(e);process.exitCode=1});
