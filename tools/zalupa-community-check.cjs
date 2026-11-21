const { chromium } = require('playwright');
const fs = require('fs');
(async()=>{
  const url='https://nacmraajxilyvyjaptwb.supabase.co';
  const key='sb_publishable_EL011m7qSbLxG0pDQCd0aQ_fhNZd7wS';
  const result={};
  try {
    const r=await fetch(url+'/rest/v1/rpc/zs_me',{method:'POST',headers:{apikey:key,'Content-Type':'application/json'},body:JSON.stringify({p_token:'invalid'})});
    const data=await r.json();result.api={status:r.status,invalidSession:data.error==='session',errorCode:data.code||null};
  } catch(e) {result.api={error:String(e)}}
  const browser=await chromium.launch({headless:true,executablePath:'/usr/bin/google-chrome',args:['--no-sandbox']});
  const page=await browser.newPage({viewport:{width:1440,height:900}});const errors=[],responses=[];
  page.on('pageerror',e=>errors.push(String(e)));
  page.on('response',r=>{if(r.url().includes('.supabase.co/rest/v1/'))responses.push({status:r.status(),route:r.url().split('/').pop()})});
  try{
    const r=await page.goto('https://htmlpreview.github.io/?https://github.com/romagggrt-lgtm/123/blob/zalupa-smp-community/site/index.html',{waitUntil:'domcontentloaded',timeout:40000});
    await page.waitForSelector('.chat-toggle',{timeout:30000});await page.waitForTimeout(2000);
    await page.locator('.chat-toggle').click();await page.waitForTimeout(7000);
    result.browser={status:r.status(),profileVisible:await page.locator('.profile-button').isVisible(),chatVisible:await page.locator('.chat-panel').isVisible(),chatServiceUnavailable:await page.locator('.chat-empty').count() ? (await page.locator('.chat-empty').innerText()).includes('не подключён') : false,chatNetworkError:await page.locator('.chat-empty').count() ? (await page.locator('.chat-empty').innerText()).includes('Не удалось связаться') : false,requests:responses,errors};
  }catch(e){result.browser={error:String(e),requests:responses,errors}}
  await browser.close();fs.writeFileSync('ci-bootstrap/zalupa-reference/community-check.json',JSON.stringify(result,null,2));console.log(JSON.stringify(result));
})().catch(e=>{console.error(e);process.exitCode=1});
