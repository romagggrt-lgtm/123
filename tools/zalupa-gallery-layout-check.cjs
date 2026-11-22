const { chromium } = require('playwright');
const fs = require('fs');
(async()=>{
  const result={target:'evrion-renamed-owner-redirect-final',repository:process.env.GITHUB_REPOSITORY,checks:[]};
  const browser=await chromium.launch({headless:true,executablePath:'/usr/bin/google-chrome',args:['--no-sandbox']});
  for(const [owner,branch] of [['sqrxxxxxx','evrionmc'],['sqrxxxxxx','zalupa-smp-site'],['romagggrt-lgtm','evrionmc']]){
    const page=await browser.newPage({viewport:{width:1440,height:900}});const errors=[];page.on('pageerror',e=>errors.push(String(e)));
    const test={owner,branch};
    try{
      const url=`https://htmlpreview.github.io/?https://github.com/${owner}/123/blob/${branch}/site/index.html#topic-4`;
      try{await page.goto(url,{waitUntil:'domcontentloaded',timeout:30000})}catch(e){if(!String(e).includes('interrupted by another navigation'))throw e}
      await page.waitForSelector('.gallery-all-button:not([hidden])',{timeout:16000});
      await page.waitForTimeout(1200);
      test.finalUrl=page.url();test.title=await page.title();test.heading=await page.locator('#hero-title').innerText();test.photos=await page.locator('.gallery-grid .gallery-photo').count();
    }catch(e){test.error=String(e);test.finalUrl=page.url();test.body=(await page.locator('body').innerText().catch(()=>'' )).slice(0,250)}
    test.errors=errors;result.checks.push(test);await page.close();
  }
  await browser.close();fs.writeFileSync('ci-bootstrap/zalupa-reference/gallery-layout-check.json',JSON.stringify(result,null,2));console.log(JSON.stringify(result));
})().catch(e=>{console.error(e);process.exitCode=1});
