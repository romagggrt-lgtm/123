const mineflayer = require('mineflayer');
const fs = require('fs');
const Vec3=require('vec3').Vec3;
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
 bot.closeWindow(bot.currentWindow);await wait(300);
 await command('/minecraft:setblock 0 71 2 minecraft:jukebox');let disc=bot.inventory.items().find(i=>i.name==='music_disc_13');await bot.equip(disc,'hand');let jpos=new Vec3(0,71,2),jukebox=bot.blockAt(jpos);await bot.lookAt(jpos.offset(.5,.5,.5),true);await bot.activateBlock(jukebox);await wait(1000);
 checks.jukebox_insert=bot.blockAt(jpos)?.getProperties().has_record===true;
 await command('/minecraft:clear ExpansionTester');await command('/minecraft:kill @e[type=minecraft:villager,distance=..10]');
 for(let [x,z] of [[3,-1],[1,-1],[3,1],[1,1]])await command(`/minecraft:setblock ${x} 70 ${z} minecraft:obsidian`);
 await command('/minecraft:give ExpansionTester minecraft:end_crystal 4');bot.setQuickBarSlot(0);await wait(400);
 for(let [x,z] of [[3,-1],[1,-1],[3,1],[1,1]]){let pos=new Vec3(x,70,z);await bot.lookAt(pos.offset(.5,1,.5),true);await bot.activateBlock(bot.blockAt(pos),new Vec3(0,1,0));await wait(600)}
 await wait(3000);console.log('end_crystals',Object.values(bot.entities).filter(e=>e.name?.includes('crystal')).map(e=>({name:e.name,pos:e.position})));console.log('remaining_crystals',bot.inventory.items().filter(i=>i.name==='end_crystal').map(i=>i.count));checks.crystal_portal=bot.blockAt(new Vec3(2,71,0))?.name==='end_portal';
 await command('/minecraft:clear ExpansionTester');await command('/minecraft:difficulty normal');await command('/minecraft:give ExpansionTester minecraft:amethyst_shard');await command('/minecraft:summon minecraft:creaking -1 71 -1');await wait(500);console.log('nearby entities',Object.values(bot.entities).map(e=>({name:e.name,type:e.type,displayName:e.displayName,id:e.id})));let target=Object.values(bot.entities).find(e=>e.name==='creaking'||e.displayName==='Creaking');let shard=bot.inventory.items().find(i=>i.name==='amethyst_shard');if(target&&shard){await bot.equip(shard,'hand');bot.attack(target);await wait(4000);checks.creaking=!bot.entities[target.id]||!bot.entities[target.id].isValid;}else checks.creaking=false;
 fs.writeFileSync(dir+'/bot-result.json',JSON.stringify({checks,messages},null,2));bot.quit();if(Object.values(checks).some(v=>!v))throw Error('failed: '+JSON.stringify(checks));process.exit(0);
}
run().catch(e=>{console.error(e);fs.writeFileSync(dir+'/bot-result.json',JSON.stringify({checks,messages,error:String(e)},null,2));if(bot)bot.quit();process.exit(1)});setTimeout(()=>process.exit(2),110000).unref();
