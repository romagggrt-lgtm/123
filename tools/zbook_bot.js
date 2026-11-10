const mineflayer=require(process.cwd()+'/node_modules/mineflayer');const fs=require('fs');const dir=process.argv[2];
const sleep=ms=>new Promise(r=>setTimeout(r,ms));let bot;const checks={};let messages=[];
function connect(){return new Promise((resolve,reject)=>{bot=mineflayer.createBot({host:'127.0.0.1',port:25591,username:'BookTester',version:'1.21.11',auth:'offline'});bot.on('messagestr',m=>messages.push(m));bot.on('error',e=>console.error(e));bot.once('spawn',resolve);bot.once('error',reject)})}
async function chat(s){bot.chat(s);await sleep(550)}
async function probe(){let n=messages.length;await chat('/bookprobe');let r=messages.slice(n).find(x=>x.includes('PROBE '));if(!r)throw Error('probe missing: '+JSON.stringify(messages.slice(n)));return r}
async function clear(){await chat('/minecraft:clear BookTester');}
async function give(){await chat('/zbook give book');await chat('/zbook give ekspanor_fanfiction BookTester 4');}
function find(name){return bot.inventory.slots.findIndex((s,i)=>i>=9&&i<45&&s?.name===name)}
async function put(slot,dest){await bot.clickWindow(slot,0,0);await sleep(150);await bot.clickWindow(dest,0,0);await sleep(400)}
async function run(){await connect();fs.writeFileSync(dir+'/ready','1');while(!fs.existsSync(dir+'/begin'))await sleep(100);await clear();await give();let initial=await probe();checks.items_identified=initial.includes('books=1 pages=4 learned=0')&&initial.includes('model=zbook:ekspanor_fanfiction');
 await put(find('book'),1);await put(find('paper'),2);await sleep(700);console.log('recipe result',JSON.stringify(bot.inventory.slots[0]));checks.craft_result_exists=bot.inventory.slots[0]?.name==='book';
 await bot.clickWindow(0,0,0);await sleep(400);await bot.clickWindow(36,0,0);await sleep(500);await put(2,37);let crafted=await probe();console.log('crafted',crafted);checks.normal_craft=crafted.includes('books=1 pages=3 learned=1');
 await put(find('book'),1);await put(find('paper'),2);await sleep(700);checks.duplicate_has_no_result=!bot.inventory.slots[0];await put(1,36);await put(2,37);await sleep(400);
 await clear();await give();await put(find('book'),1);await put(find('paper'),2);await sleep(600);await bot.clickWindow(0,0,1);await sleep(600);await put(2,37);let shifted=await probe();console.log('shifted',shifted);checks.shift_craft_once=shifted.includes('books=1 pages=3 learned=1');
 let bookSlot=find('book');if(bookSlot<36||bookSlot>44){await put(bookSlot,36);bookSlot=36}bot.setQuickBarSlot(bookSlot-36);await sleep(300);
 await chat('/zbook cast ekspanor_fanfiction');bot.activateItem();await sleep(600);let noHit=await probe();checks.no_target_no_cooldown=noHit.includes('cooldown=false')&&messages.some(m=>m.includes('Цель слишком далеко'));
 fs.writeFileSync(dir+'/target-ready','1');while(!fs.existsSync(dir+'/target-set'))await sleep(100);await chat('/zbook cast ekspanor_fanfiction');bot.activateItem();await sleep(900);let hit=await probe();checks.hit_cast_and_cooldown=hit.includes('cooldown=true')&&/strikes=[1-9]/.test(hit);
 await chat('/zbook cast ekspanor_fanfiction');bot.activateItem();await sleep(500);let repeat=await probe();checks.cooldown_prevents_second_strike=repeat.match(/strikes=(\d+)/)?.[1]===hit.match(/strikes=(\d+)/)?.[1];
 bot.quit();await sleep(1500);await connect();await sleep(800);let relog=await probe();checks.cooldown_persists_relog=relog.includes('cooldown=true');bot.quit();fs.writeFileSync(dir+'/bot-result.json',JSON.stringify({checks,initial,crafted,shifted,noHit,hit,relog},null,2));if(Object.values(checks).some(v=>!v))throw Error('failed checks '+JSON.stringify(checks));process.exit(0)}
run().catch(e=>{console.error(e);fs.writeFileSync(dir+'/bot-result.json',JSON.stringify({checks,error:String(e),messages},null,2));if(bot)bot.quit();process.exit(1)});setTimeout(()=>process.exit(2),150000).unref();
