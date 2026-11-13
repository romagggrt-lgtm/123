const mineflayer = require('mineflayer');
const fs = require('fs');
const Vec3=require('vec3').Vec3;
const path=process.argv[2], wait=ms=>new Promise(r=>setTimeout(r,ms));
const mp3='https://raw.githubusercontent.com/romagggrt-lgtm/123/feature/z-plast-1.1.0/tests/zplast/sample.mp3';
const invalid='https://raw.githubusercontent.com/romagggrt-lgtm/123/feature/z-plast-1.1.0/tests/zplast/not-audio.txt';
let bot,observer;let messages=[],other=[];const checks={};
async function until(fn,timeout,why){const end=Date.now()+timeout;while(Date.now()<end){let x=fn();if(x)return x;await wait(200)}throw Error('timeout '+why+' '+JSON.stringify(messages.slice(-12)))}
async function cmd(text){bot.chat(text);await wait(600)}
function connect(username,messages){return new Promise((resolve,reject)=>{let b=mineflayer.createBot({host:'127.0.0.1',port:25589,username,version:'1.21.11',auth:'offline'});b.on('messagestr',m=>messages.push(m));b.on('error',e=>console.error(username,e));b.once('spawn',()=>resolve(b));b.once('error',reject)})}
async function open(){let win=new Promise(r=>bot.once('windowOpen',r));await cmd('/zbox');return await win}
async function sendLink(link){let win=await open();if(win.slots[11]?.name!=='paper')throw Error('link button missing');let mark=messages.length;await bot.clickWindow(11,0,0);await until(()=>messages.slice(mark).some(m=>m.includes('Вставьте в чат')),5000,'link prompt');await cmd(link);}
async function run(){
 bot=await connect('PlastTester',messages);observer=await connect('PlastObserver',other);fs.writeFileSync(path+'/ready','1');await until(()=>fs.existsSync(path+'/begin'),30000,'setup');
 await sendLink('http://127.0.0.1:18865/secret');await until(()=>messages.some(m=>m.includes('публичные адреса')),10000,'private IP denial');checks.localhost_blocked=true;
 await sendLink(invalid);await until(()=>messages.some(m=>m.includes('нужен сам MP3')),20000,'html denial');checks.html_rejected=true;
 await sendLink(mp3);await until(()=>messages.some(m=>m.includes('Запись загружена')),45000,'first download and conversion');checks.direct_link_import=true;
 await until(()=>fs.existsSync(path+'/plugins/Z_Plast/pack/Z_Plast_Sounds.zip'),30000,'dynamic pack');checks.pack_built=true;
 await until(()=>bot.inventory.items().some(i=>i.name==='music_disc_13'),30000,'record');checks.record_given=true;
 checks.url_private=!other.some(m=>m.includes(mp3)||m.includes(invalid)||m.includes('127.0.0.1:18865'));
 const data=JSON.parse(fs.readFileSync(path+'/plugins/Z_Plast/tracks.json'));checks.one_track=data.length===1;
 let win=await open();await bot.clickWindow(13,0,0);await wait(600);checks.catalog=bot.currentWindow?.slots[0]?.name==='music_disc_13';bot.closeWindow(bot.currentWindow);await wait(300);
 await cmd('/minecraft:setblock 0 71 2 minecraft:jukebox');let item=bot.inventory.items().find(i=>i.name==='music_disc_13');await bot.equip(item,'hand');let pos=new Vec3(0,71,2);await bot.lookAt(pos.offset(.5,.5,.5),true);await bot.activateBlock(bot.blockAt(pos));await wait(700);checks.jukebox=bot.blockAt(pos)?.getProperties().has_record===true;
 await sendLink(mp3);await until(()=>messages.some(m=>m.includes('Такая запись уже есть')),20000,'duplicate');checks.duplicate_reused=JSON.parse(fs.readFileSync(path+'/plugins/Z_Plast/tracks.json')).length===1;
 win=await open();checks.browser_button=win.slots[22]?.name==='hopper';let from=messages.length;await bot.clickWindow(22,0,0);let token=await until(()=>messages.slice(from).join(' ').match(/http:\/\/127\.0\.0\.1:18865\/upload\/[A-Za-z0-9_-]+/)?.[0],8000,'browser link');let payload=fs.readFileSync(path+'/sample.mp3');let response=await fetch(token,{method:'POST',headers:{'X-Filename':'sample.mp3'},body:payload});checks.browser_upload=response.status===200;checks.one_use=(await fetch(token)).status===410;
 fs.writeFileSync(path+'/bot-result.json',JSON.stringify({checks,messages,other},null,2));bot.quit();observer.quit();if(Object.values(checks).some(x=>!x))throw Error('checks failed '+JSON.stringify(checks));process.exit(0);
}
run().catch(e=>{console.error(e);fs.writeFileSync(path+'/bot-result.json',JSON.stringify({checks,messages,other,error:String(e)},null,2));if(bot)bot.quit();if(observer)observer.quit();process.exit(1)});setTimeout(()=>process.exit(2),160000).unref();
