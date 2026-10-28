const fs = require('fs');
const mineflayer = require('mineflayer');
for (const username of ['BanyaTester','BanyaOutside']) {
 const bot=mineflayer.createBot({host:'127.0.0.1',port:25585,username,version:'1.21.11',auth:'offline'});
 bot._client.on('add_resource_pack',data=>{if(bot._client.state==='configuration'){bot._client.write('resource_pack_receive',{uuid:data.uuid,result:3});bot._client.write('resource_pack_receive',{uuid:data.uuid,result:0});}});
 bot.on('resourcePack',()=>{if(bot._client.state==='play')bot.acceptResourcePack();});
 bot.on('error',e=>console.error(username,e.message));bot.on('kicked',r=>console.error(username,JSON.stringify(r)));
 bot._client.on('packet',(data,meta)=>{if(meta.name.includes('sound')){const body=JSON.stringify(data,(_,v)=>typeof v==='bigint'?v.toString():v);if(body.includes('zbanya:ambient'))fs.appendFileSync('sound-packets.jsonl',JSON.stringify({username,packet:meta.name,body,time:Date.now()})+'\n');}});
 let eating=false;
 setInterval(()=>{if(username==='BanyaTester'&&!eating&&bot.heldItem&&bot.heldItem.name==='bread'){eating=true;bot.activateItem();console.log('NATIVE_USE_FOOD_AT_FULL_HUNGER',bot.food);}},250);
}
