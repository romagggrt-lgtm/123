const fs = require('fs');
const mineflayer = require('mineflayer');
for (const username of (process.env.ZLOR_VISUAL === '1' ? ['LorTarget'] : ['LorObserver', 'LorTarget'])) {
  const bot = mineflayer.createBot({host:'127.0.0.1',port:25585,username,version:'1.21.11',auth:'offline'});
  // The published Mineflayer release only installs its pack listener after
  // login; Paper may require an answer while still in configuration.
  bot._client.on('add_resource_pack', data => {
    if (bot._client.state !== 'configuration') return;
    console.log(username, 'CONFIG_PACK_ACCEPTED', data.uuid);
    bot._client.write('resource_pack_receive', {uuid:data.uuid,result:3});
    bot._client.write('resource_pack_receive', {uuid:data.uuid,result:0});
  });
  bot.on('resourcePack', () => {if (bot._client.state === 'play') bot.acceptResourcePack();});
  bot.on('spawn', () => console.log(username, 'PLAY_SPAWN'));
  bot._client.on('state', state => console.log(username, 'STATE', state));
  bot.on('error', error => console.error(username, error.message));
  bot.on('kicked', reason => console.error(username, JSON.stringify(reason)));
  let tested = false;
  bot.on('windowOpen', async window => {
    if (username !== 'LorObserver' || tested) return;
    tested = true;
    try {
      await new Promise(resolve => setTimeout(resolve, 300));
      const slot = window.slots.findIndex((item, index) => index >= window.inventoryStart && item && item.name === 'paper');
      if (slot < 0) throw new Error('Fuel missing from native inventory window');
      await bot.clickWindow(slot, 0, 0);
      await new Promise(resolve => setTimeout(resolve, 200));
      await bot.clickWindow(0, 0, 0);
      console.log('GUI_PROTOCOL_CLICKS_SENT');
    } catch(error) { console.error('GUI_PROTOCOL_FAIL', error.stack); }
  });
}
