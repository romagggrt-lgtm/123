const mineflayer = require('mineflayer');
const fs = require('fs');
const wait = ms => new Promise(r => setTimeout(r, ms));
const dir=process.argv[2], checks={}, messages=[];
let bot;
async function command(text){bot.chat(text);await wait(650)}
async function waitUntil(fn,ms,label){const end=Date.now()+ms;while(Date.now()<end){const value=fn();if(value)return value;await wait(150)}throw Error('timeout: '+label+' '+JSON.stringify(messages.slice(-12)))}
async function run(){
 bot=mineflayer.createBot({host:'127.0.0.1',port:25589,username:'ExpansionTester',version:'1.21.11',auth:'offline'});
 bot.on('messagestr',m=>messages.push(m));bot.on('error',e=>console.error(e));
 await new Promise((resolve,reject)=>{bot.once('spawn',resolve);bot.once('error',reject)});
 fs.writeFileSync(dir+'/ready','1');await waitUntil(()=>fs.existsSync(dir+'/begin'),30000,'setup');
 await command('/minecraft:clear ExpansionTester');await command('/minecraft:give ExpansionTester minecraft:diamond_sword');bot.setQuickBarSlot(0);await wait(250);
 await command('/zt enchanted sharpness 100');checks.high_enchant=messages.some(m=>m.includes('sharpness 100'));
 await bot.lookAt(bot.entity.position.offset(0,-1,0),true);await command('/zt set sharpness 5');checks.villager=messages.some(m=>m.includes('Житель создан')&&m.includes('sharpness'));
 await command('/minecraft:clear ExpansionTester');await command('/zbook give book');bot.setQuickBarSlot(0);await wait(250);await command('/zbook unlock');checks.book_unlock=messages.some(m=>m.includes('заклинания изучены'));
 await command('/zbook cast redus_flimano');bot.activateItem();await wait(350);checks.fireball_cast=messages.some(m=>m.includes('Заклинание применено'));
 let w=new Promise(r=>bot.once('windowOpen',r));await command('/zbox');let window=await w;checks.menu=window.slots[11]?.name==='hopper';let old=messages.length;await bot.clickWindow(11,0,0);
 let link=await waitUntil(()=>messages.slice(old).join(' ').match(/http:\/\/127\.0\.0\.1:8765\/upload\/[A-Za-z0-9_-]+/)?.[0],10000,'upload link');
 let page=await fetch(link);checks.web_page=page.status===200&&(await page.text()).includes('Z_Plast');
 let sound=fs.readFileSync(dir+'/sample.mp3');let response=await fetch(link,{method:'POST',headers:{'X-Filename':'test.mp3'},body:sound});let result=await response.text();checks.upload=response.status===200;console.log('upload',response.status,result);
 checks.token_once=(await fetch(link)).status===410;
 await waitUntil(()=>fs.existsSync(dir+'/plugins/Z_Plast/pack/Z_Plast_Sounds.zip'),25000,'pack');checks.pack=true;
 await waitUntil(()=>bot.inventory.items().some(i=>i.name==='music_disc_13'),30000,'record');checks.given=true;
 w=new Promise(r=>bot.once('windowOpen',r));await command('/zbox');window=await w;await bot.clickWindow(13,0,0);await wait(600);checks.catalog=bot.currentWindow?.slots[0]?.name==='music_disc_13';
 fs.writeFileSync(dir+'/bot-result.json',JSON.stringify({checks,messages},null,2));bot.quit();if(Object.values(checks).some(v=>!v))throw Error('failed: '+JSON.stringify(checks));
}
run().catch(e=>{console.error(e);fs.writeFileSync(dir+'/bot-result.json',JSON.stringify({checks,messages,error:String(e)},null,2));if(bot)bot.quit();process.exitCode=1});setTimeout(()=>process.exit(2),110000).unref();
