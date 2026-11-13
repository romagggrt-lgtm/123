package dev.zplast;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.io.*;
import java.net.URI;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;
import net.kyori.adventure.text.*;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.*;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class ZPlast extends JavaPlugin implements Listener,CommandExecutor {
 private static final UUID PACK_ID=UUID.fromString("cb17a088-9734-4ab1-97e0-28639ff80d89");
 private static final LegacyComponentSerializer COLORS=LegacyComponentSerializer.builder().character('&').hexColors().build();
 private Catalog catalog;private Web web;private ExecutorService io;private YamlConfiguration messages;private String publicUrl="";private Material base;int nameLimit;
 private NamespacedKey trackKey;private final Map<UUID,Long> uploadTimes=new HashMap<>();private final Map<UUID,List<Pending>> waiting=new HashMap<>();private final Map<UUID,String> loaded=new HashMap<>();
 private final Map<UUID,Rename> renaming=new ConcurrentHashMap<>();private final Map<UUID,Long> linkInputs=new ConcurrentHashMap<>();private final Set<UUID> activeLinks=new HashSet<>();private final Map<Location,Playing> playing=new HashMap<>();private long packSerial;private int rebuildTask=-1;private boolean stopping;
 private record Pending(String id,long serial){} private record Rename(String id,long expires){} private record Playing(String id,long until,List<UUID> listeners){}
 private final class Screen implements InventoryHolder{final UUID owner;final String type;final String track;final int page;Inventory inventory;Screen(Player p,String type,String track,int page){owner=p.getUniqueId();this.type=type;this.track=track;this.page=page;}public Inventory getInventory(){return inventory;}}
 @Override public void onEnable(){
  saveDefaultConfig();if(!new File(getDataFolder(),"messages.yml").exists())saveResource("messages.yml",false);messages=YamlConfiguration.loadConfiguration(new File(getDataFolder(),"messages.yml"));
  nameLimit=Math.clamp(getConfig().getInt("name.max-length",32),1,128);trackKey=new NamespacedKey("zplast","track_id");base=Material.matchMaterial(getConfig().getString("disc.base-material","MUSIC_DISC_13"));if(base==null||!base.name().startsWith("MUSIC_DISC_"))base=Material.MUSIC_DISC_13;
  try{String value=getConfig().getString("web.public-url","").trim().replaceAll("/+$","");if(!value.isEmpty()){URI uri=URI.create(value);if(!Set.of("http","https").contains(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null)throw new IllegalArgumentException();publicUrl=value;}else getLogger().warning("web.public-url пуст: загрузка и раздача звуков отключены.");}catch(Exception ex){getLogger().warning("Некорректный web.public-url: загрузка отключена.");}
  io=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"Z_Plast-Storage");t.setDaemon(true);return t;});
  try{catalog=new Catalog(this);web=new Web(this,catalog);}catch(IOException ex){getLogger().severe("Не удалось открыть данные или веб-сервер Z_Plast: "+ex.getMessage());getServer().getPluginManager().disablePlugin(this);return;}
  Objects.requireNonNull(getCommand("zbox")).setExecutor(this);getServer().getPluginManager().registerEvents(this,this);
  Bukkit.getScheduler().runTaskTimer(this,this::tick,10,10);if(!catalog.list().isEmpty())scheduleBuild();
 }
 private void say(Player p,String key){String prefix=messages.getString("prefix","");String msg=messages.getString(key,"");p.sendMessage(MiniMessage.miniMessage().deserialize(prefix+msg));}
 private void plain(Player p,String msg){p.sendMessage(Component.text("[Z_Plast] ",NamedTextColor.DARK_PURPLE).append(Component.text(msg,NamedTextColor.GRAY)));}
 static String normalizeName(String raw,boolean allowColor,int max){
  String input=raw==null?"":raw.strip();StringBuilder out=new StringBuilder();int shown=0;
  for(int i=0;i<input.length()&&shown<max;){int cp=input.codePointAt(i);int n=Character.charCount(cp);if(cp=='&'&&i+1<input.length()){
   if(input.charAt(i+1)=='#'&&i+8<=input.length()&&input.substring(i+2,i+8).matches("[0-9a-fA-F]{6}")){if(allowColor)out.append(input,i,i+8);i+=8;continue;}
   if("0123456789abcdefklmnorABCDEFKLMNOR".indexOf(input.charAt(i+1))>=0){if(allowColor)out.append(input,i,i+2);i+=2;continue;}}
   i+=n;if(Character.isISOControl(cp))continue;out.appendCodePoint(cp);shown++;
  }
  String result=out.toString().strip();String visible=PlainTextComponentSerializer.plainText().serialize(COLORS.deserialize(result)).strip();return visible.isEmpty()?"Без названия":result;
 }
 private Component title(String raw){return Component.empty().decoration(TextDecoration.ITALIC,false).append(COLORS.deserialize(raw));}
 @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
  if(!(sender instanceof Player p)){sender.sendMessage("Команда доступна только игроку.");return true;}
  if(args.length>0&&args[0].equalsIgnoreCase("reload")){if(!p.hasPermission("zplast.admin")){say(p,"no-permission");return true;}reloadConfig();nameLimit=Math.clamp(getConfig().getInt("name.max-length",32),1,128);messages=YamlConfiguration.loadConfiguration(new File(getDataFolder(),"messages.yml"));plain(p,"Настройки и сообщения перечитаны. Для изменения адреса, порта и конвертера перезапустите сервер.");return true;}
  if(!p.hasPermission("zplast.use")){say(p,"no-permission");return true;}main(p);return true;
 }
 private ItemStack icon(Material mat,String name,String...lore){ItemStack item=new ItemStack(mat);item.editMeta(m->{m.displayName(Component.text(name,NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC,false));if(lore.length>0)m.lore(Arrays.stream(lore).map(x->Component.text(x,NamedTextColor.GRAY).decoration(TextDecoration.ITALIC,false)).toList());});return item;}
 private Screen screen(Player p,String type,int size,String caption,String track,int page){Screen s=new Screen(p,type,track,page);s.inventory=Bukkit.createInventory(s,size,Component.text(caption,NamedTextColor.LIGHT_PURPLE));if(type.equals("main")){ItemStack glass=icon(Material.GRAY_STAINED_GLASS_PANE," ");for(int n=0;n<size;n++)s.inventory.setItem(n,glass);}return s;}
 private void main(Player p){Screen s=screen(p,"main",27,"Z_Plast",null,0);s.inventory.setItem(11,icon(Material.PAPER,"Добавить по ссылке","Вставьте прямую ссылку на MP3 в чат"));s.inventory.setItem(13,icon(Material.JUKEBOX,"Каталог","Записей: "+visible(p).size()));s.inventory.setItem(15,icon(Material.BARRIER,"Закрыть"));s.inventory.setItem(22,icon(Material.HOPPER,"Загрузить с устройства","Откроется страница для выбора MP3"));p.openInventory(s.inventory);}
 private List<Track> visible(Player p){boolean own=getConfig().getString("catalog.scope","all").equalsIgnoreCase("own");return catalog.list().stream().filter(t->!own||t.owner.equals(p.getUniqueId().toString())).toList();}
 private ItemStack record(Track t){ItemStack item=new ItemStack(base);item.editMeta(m->{m.displayName(title(t.name));m.lore(List.of(Component.text("Z_Plast",NamedTextColor.GRAY).decoration(TextDecoration.ITALIC,false),Component.text("Загрузил: "+t.ownerName,NamedTextColor.GRAY).decoration(TextDecoration.ITALIC,false),Component.text("Длительность: "+time(t.seconds),NamedTextColor.GRAY).decoration(TextDecoration.ITALIC,false)));m.setMaxStackSize(1);m.setItemModel(new NamespacedKey("zplast","record"));m.getPersistentDataContainer().set(trackKey,PersistentDataType.STRING,t.id);});item.unsetData(DataComponentTypes.JUKEBOX_PLAYABLE);return item;}
 private String id(ItemStack item){return item==null||!item.hasItemMeta()?null:item.getItemMeta().getPersistentDataContainer().get(trackKey,PersistentDataType.STRING);}
 private String time(double seconds){int s=(int)seconds;return String.format(Locale.ROOT,"%d:%02d",s/60,s%60);}
 private void catalog(Player p,int page){List<Track> tracks=visible(p);int last=Math.max(0,(tracks.size()-1)/45);page=Math.clamp(page,0,last);Screen s=screen(p,"catalog",54,"Пластинки • "+(page+1),null,page);int from=page*45;
  for(int n=0;n<45&&from+n<tracks.size();n++){Track t=tracks.get(from+n);ItemStack item=record(t);item.editMeta(m->{var lore=new ArrayList<>(m.lore());lore.add(Component.text("ID: "+t.id,NamedTextColor.DARK_GRAY));lore.add(Component.text("ЛКМ — получить, ПКМ — управление",NamedTextColor.YELLOW));m.lore(lore);});s.inventory.setItem(n,item);}
  if(page>0)s.inventory.setItem(45,icon(Material.ARROW,"Предыдущая"));s.inventory.setItem(49,icon(Material.NETHER_STAR,"Главное меню"));if(page<last)s.inventory.setItem(53,icon(Material.ARROW,"Следующая"));p.openInventory(s.inventory);
 }
 private boolean canManage(Player p,Track t){return p.hasPermission("zplast.admin")||t.owner.equals(p.getUniqueId().toString());}
 private void manage(Player p,String id,int page){Track t=catalog.get(id);if(t==null){catalog(p,page);return;}Screen s=screen(p,"manage",27,"Управление записью",id,page);s.inventory.setItem(11,icon(Material.NAME_TAG,"Переименовать",canManage(p,t)?"Введите имя в чат":"Только владелец или администратор"));s.inventory.setItem(13,icon(Material.BARRIER,"Удалить",canManage(p,t)?"Потребуется подтверждение":"Только владелец или администратор"));s.inventory.setItem(15,icon(Material.ARROW,"Назад"));p.openInventory(s.inventory);}
 private void confirm(Player p,String id,int page){Screen s=screen(p,"confirm",27,"Удалить запись?",id,page);s.inventory.setItem(11,icon(Material.RED_WOOL,"Да, удалить"));s.inventory.setItem(15,icon(Material.LIME_WOOL,"Нет, вернуться"));p.openInventory(s.inventory);}
 @EventHandler public void click(InventoryClickEvent e){if(!(e.getWhoClicked() instanceof Player p)||!(e.getView().getTopInventory().getHolder() instanceof Screen s))return;e.setCancelled(true);if(!p.hasPermission("zplast.use")||!s.owner.equals(p.getUniqueId())||e.getRawSlot()<0||e.getRawSlot()>=s.inventory.getSize())return;int slot=e.getRawSlot();
  if(s.type.equals("main")){if(slot==11)beginLink(p);else if(slot==13)catalog(p,0);else if(slot==15)p.closeInventory();else if(slot==22)beginUpload(p);return;}
  if(s.type.equals("catalog")){if(slot==45&&s.page>0)catalog(p,s.page-1);else if(slot==49)main(p);else if(slot==53)catalog(p,s.page+1);else if(slot<45){String id=id(s.inventory.getItem(slot));Track t=id==null?null:catalog.get(id);if(t==null)return;if(e.isRightClick())manage(p,id,s.page);else if(e.isLeftClick())deliverWithPack(p,t);}return;}
  Track t=catalog.get(s.track);if(t==null){catalog(p,s.page);return;}if(s.type.equals("manage")){if(slot==15){catalog(p,s.page);return;}if(!canManage(p,t)){say(p,"no-permission");return;}if(slot==11){p.closeInventory();linkInputs.remove(p.getUniqueId());renaming.put(p.getUniqueId(),new Rename(t.id,System.currentTimeMillis()+60_000));plain(p,"Введите новое название в чат: поддерживаются &#RRGGBB и &-коды. Напишите cancel для отмены.");}else if(slot==13)confirm(p,t.id,s.page);}
  else if(s.type.equals("confirm")){if(slot==15)manage(p,t.id,s.page);else if(slot==11&&canManage(p,t)){p.closeInventory();String track=t.id;io.execute(()->{try{catalog.remove(track);onMain(()->{stopTrack(track);plain(p,"Запись удалена.");scheduleBuild();});}catch(Exception ex){getLogger().warning("Ошибка удаления: "+ex);onMain(()->plain(p,"Не удалось удалить запись."));}});}}
 }
 @EventHandler public void drag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof Screen)e.setCancelled(true);}
 @EventHandler(priority=EventPriority.LOWEST) public void chat(AsyncChatEvent e){UUID uid=e.getPlayer().getUniqueId();Long deadline=linkInputs.remove(uid);Rename rename=deadline==null?renaming.remove(uid):null;if(deadline==null&&rename==null)return;e.setCancelled(true);String value=PlainTextComponentSerializer.plainText().serialize(e.message());Player p=e.getPlayer();
  if(deadline!=null){onMain(()->submitLink(p,value,deadline));return;}
  onMain(()->{if(!p.isOnline())return;if(rename.expires<System.currentTimeMillis()||value.equalsIgnoreCase("cancel")){plain(p,"Переименование отменено.");return;}Track t=catalog.get(rename.id);if(t==null||!canManage(p,t)){say(p,"no-permission");return;}String name=normalizeName(value,p.hasPermission("zplast.color"),nameLimit);io.execute(()->{try{catalog.rename(rename.id,name);onMain(()->{plain(p,"Название изменено.");catalog(p,0);});}catch(IOException ex){onMain(()->plain(p,"Не удалось сохранить название."));}});});
 }
 private boolean uploadAllowed(Player p){
  if(!p.hasPermission("zplast.upload")){say(p,"no-permission");return false;}
  if(web==null||!web.available||publicUrl.isEmpty()){plain(p,"Не настроен web.public-url или Web-порт: они нужны для передачи звука игрокам.");return false;}
  if(activeLinks.contains(p.getUniqueId())){plain(p,"Предыдущая загрузка ещё не завершена.");return false;}
  boolean admin=p.hasPermission("zplast.admin");int limit=Math.max(0,getConfig().getInt("limits.max-tracks-per-player",20));if(!admin&&limit>0&&catalog.count(p.getUniqueId())>=limit){say(p,"limit");return false;}
  long wait=Math.clamp(getConfig().getLong("upload.cooldown-seconds",30),0,86400)*1000;if(!admin&&System.currentTimeMillis()-uploadTimes.getOrDefault(p.getUniqueId(),0L)<wait){say(p,"cooldown");return false;}return true;
 }
 private void beginLink(Player p){p.closeInventory();if(!uploadAllowed(p))return;renaming.remove(p.getUniqueId());linkInputs.put(p.getUniqueId(),System.currentTimeMillis()+120_000);plain(p,"Вставьте в чат прямую ссылку http:// или https:// на MP3. Для отмены напишите cancel.");}
 private void submitLink(Player p,String input,long deadline){
  if(!p.isOnline())return;String value=input.strip();if(deadline<System.currentTimeMillis()||value.equalsIgnoreCase("cancel")||value.equalsIgnoreCase("отмена")){plain(p,"Загрузка отменена.");return;}
  if(!uploadAllowed(p))return;
  try{DirectDownload.validate(URI.create(value));}catch(Exception ex){linkInputs.put(p.getUniqueId(),deadline);plain(p,"Нужна прямая ссылка http:// или https:// на MP3. Попробуйте ещё раз или напишите cancel.");return;}
  UUID id=p.getUniqueId();activeLinks.add(id);Web.Ticket ticket=new Web.Ticket(id,p.getName(),p.hasPermission("zplast.color"),p.hasPermission("zplast.admin"),deadline);
  if(!web.importLink(ticket,value)){activeLinks.remove(id);plain(p,"Очередь загрузки занята. Попробуйте позже.");return;}
  uploadTimes.put(id,System.currentTimeMillis());plain(p,"Загружаю и преобразую MP3. Это может занять некоторое время.");
 }
 void linkError(UUID id,String error){onMain(()->{Player p=Bukkit.getPlayer(id);if(p!=null)plain(p,"Не удалось загрузить запись. "+(error==null?"Проверьте прямую ссылку на MP3.":error));});}
 void linkFinished(UUID id){onMain(()->activeLinks.remove(id));}

 private void beginUpload(Player p){p.closeInventory();if(!uploadAllowed(p))return;
  long now=System.currentTimeMillis();boolean admin=p.hasPermission("zplast.admin");uploadTimes.put(p.getUniqueId(),now);
  String token=web.ticket(p.getUniqueId(),p.getName(),p.hasPermission("zplast.color"),admin,Math.clamp(getConfig().getLong("upload.token-ttl-seconds",600),30,3600)*1000);String url=publicUrl+"/upload/"+token;say(p,"upload-link");p.sendMessage(Component.text(url,NamedTextColor.AQUA).clickEvent(ClickEvent.openUrl(url)));
 }
 void onMain(Runnable action){if(!stopping&&isEnabled())Bukkit.getScheduler().runTask(this,action);}
 void uploadCompleted(UUID id,Track track,boolean duplicate){onMain(()->{Player p=Bukkit.getPlayer(id);if(p!=null){plain(p,duplicate?"Такая запись уже есть в каталоге.":"Запись загружена.");if(!duplicate)queuePending(p,new Pending(track.id,++packSerial));}else recordNotice(id,1);if(!duplicate)scheduleBuild();else if(p!=null)deliverWithPack(p,track);});}
 private void scheduleBuild(){if(stopping||publicUrl.isEmpty())return;if(rebuildTask!=-1)Bukkit.getScheduler().cancelTask(rebuildTask);rebuildTask=Bukkit.getScheduler().runTaskLater(this,()->{rebuildTask=-1;io.execute(()->{try{Catalog.Pack pack=catalog.build();onMain(()->{loaded.clear();for(Player p:Bukkit.getOnlinePlayers())offer(p,pack);});}catch(Exception ex){getLogger().warning("Не удалось собрать звуковой ресурспак: "+ex);onMain(()->{for(var entry:new ArrayList<>(waiting.entrySet())){Player p=Bukkit.getPlayer(entry.getKey());if(p!=null)for(Pending item:List.copyOf(entry.getValue()))givePending(p,item,true);}});}});},60).getTaskId();}
 private void offer(Player p,Catalog.Pack pack){if(publicUrl.isEmpty())return;p.addResourcePack(PACK_ID,publicUrl+"/pack/"+pack.sha()+".zip",pack.digest(),getConfig().getString("resource-pack.prompt","Звуки пластинок"),getConfig().getBoolean("resource-pack.required",false));for(Pending pending:List.copyOf(waiting.getOrDefault(p.getUniqueId(),List.of())))Bukkit.getScheduler().runTaskLater(this,()->givePending(p,pending,true),400);}
 private void deliverWithPack(Player p,Track t){Catalog.Pack pack=catalog.current;if(pack!=null&&pack.sha().equals(loaded.get(p.getUniqueId()))){give(p,t);return;}Pending pending=new Pending(t.id,++packSerial);queuePending(p,pending);if(pack!=null)offer(p,pack);else{scheduleBuild();Bukkit.getScheduler().runTaskLater(this,()->givePending(p,pending,true),400);}}
 private void queuePending(Player p,Pending pending){waiting.computeIfAbsent(p.getUniqueId(),k->new ArrayList<>()).add(pending);}
 private void givePending(Player p,Pending pending,boolean timeout){List<Pending> entries=waiting.get(p.getUniqueId());if(!p.isOnline()||entries==null||!entries.remove(pending))return;if(entries.isEmpty())waiting.remove(p.getUniqueId());Track t=catalog.get(pending.id);if(t!=null)give(p,t);if(timeout)say(p,"pack-timeout");}
 private void giveAllPending(Player p,boolean timeout){for(Pending pending:List.copyOf(waiting.getOrDefault(p.getUniqueId(),List.of())))givePending(p,pending,timeout);}
 private void give(Player p,Track t){ItemStack item=record(t);for(ItemStack leftover:p.getInventory().addItem(item).values())p.getWorld().dropItemNaturally(p.getLocation(),leftover);}
 @EventHandler public void pack(PlayerResourcePackStatusEvent e){if(!e.getID().equals(PACK_ID))return;Player p=e.getPlayer();switch(e.getStatus()){
  case SUCCESSFULLY_LOADED->{Catalog.Pack pack=catalog.current;if(pack!=null)loaded.put(p.getUniqueId(),pack.sha());giveAllPending(p,false);}
  case DECLINED,FAILED_DOWNLOAD,INVALID_URL,FAILED_RELOAD->{loaded.remove(p.getUniqueId());say(p,"pack-declined");giveAllPending(p,false);}
  default->{} }}
 private void recordNotice(UUID id,int count){io.execute(()->{try{catalog.addNotice(id,count);}catch(IOException ex){getLogger().warning("Не удалось сохранить уведомление: "+ex.getMessage());}});}
 @EventHandler public void join(PlayerJoinEvent e){Player player=e.getPlayer();UUID id=player.getUniqueId();io.execute(()->{int count=catalog.noticeCount(id);if(count>0)onMain(()->{if(!player.isOnline())return;plain(player,"Пока вас не было, загружено записей: "+count+". Откройте каталог /zbox.");io.execute(()->{try{catalog.acknowledgeNotice(id,count);}catch(IOException ex){getLogger().warning("Не удалось сохранить уведомление: "+ex.getMessage());}});});});if(!catalog.list().isEmpty())Bukkit.getScheduler().runTaskLater(this,()->{Catalog.Pack pack=catalog.current;if(e.getPlayer().isOnline()&&pack!=null)offer(e.getPlayer(),pack);},40);}
 @EventHandler public void quit(PlayerQuitEvent e){UUID id=e.getPlayer().getUniqueId();List<Pending> missed=waiting.remove(id);if(missed!=null&&!missed.isEmpty())recordNotice(id,missed.size());loaded.remove(id);renaming.remove(id);linkInputs.remove(id);}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
 public void useJukebox(PlayerInteractEvent e){
  if(e.getHand()!=EquipmentSlot.HAND||e.getClickedBlock()==null||e.getClickedBlock().getType()!=Material.JUKEBOX||!e.getAction().isRightClick())return;
  if(e.useInteractedBlock()==Event.Result.DENY)return;Block block=e.getClickedBlock();Jukebox jukebox=(Jukebox)block.getState();ItemStack held=e.getPlayer().getInventory().getItemInMainHand();String id=id(held);
  if(id==null||jukebox.hasRecord())return;e.setCancelled(true);Track track=catalog.get(id);if(track==null){say(e.getPlayer(),"missing-track");return;}
  ItemStack one=held.clone();one.setAmount(1);one.unsetData(DataComponentTypes.JUKEBOX_PLAYABLE);jukebox.setRecord(one);if(!jukebox.update(true,false)){plain(e.getPlayer(),"Не удалось вставить пластинку.");return;}
  if(e.getPlayer().getGameMode()!=GameMode.CREATIVE)held.subtract(1);play(block.getLocation(),track);
 }
 private void play(Location block,Track track){stop(block);Location source=block.clone().add(.5,.8,.5);double radius=Math.clamp(getConfig().getDouble("sound.radius",64),1,256),r2=radius*radius;float volume=(float)(Math.clamp(getConfig().getDouble("sound.volume",1),0,4)*Math.max(1,radius/16));List<UUID> listeners=new ArrayList<>();
  for(Player player:source.getWorld().getPlayers())if(player.getLocation().distanceSquared(source)<=r2){player.playSound(source,"zplast:track."+track.id,SoundCategory.RECORDS,volume,1);player.sendActionBar(Component.text("Сейчас играет: ").append(title(track.name)));listeners.add(player.getUniqueId());}
  playing.put(block,new Playing(track.id,System.currentTimeMillis()+(long)(track.seconds*1000),listeners));
 }
 private void stop(Location block){Playing sound=playing.remove(block);if(sound==null)return;for(UUID id:sound.listeners){Player player=Bukkit.getPlayer(id);if(player!=null)player.stopSound("zplast:track."+sound.id,SoundCategory.RECORDS);}}
 private void stopTrack(String id){for(var entry:List.copyOf(playing.entrySet()))if(entry.getValue().id.equals(id))stop(entry.getKey());}
 @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void breakJukebox(BlockBreakEvent e){if(e.getBlock().getType()==Material.JUKEBOX)stop(e.getBlock().getLocation());}
 private void tick(){
  long now=System.currentTimeMillis();renaming.entrySet().removeIf(e->e.getValue().expires<now);linkInputs.entrySet().removeIf(e->e.getValue()<now);
  for(var entry:List.copyOf(playing.entrySet())){Location pos=entry.getKey();Playing sound=entry.getValue();World world=pos.getWorld();
   if(now>=sound.until||world==null||!world.isChunkLoaded(pos.getBlockX()>>4,pos.getBlockZ()>>4)){stop(pos);continue;}
   Block block=pos.getBlock();if(!(block.getState() instanceof Jukebox jukebox)||!sound.id.equals(id(jukebox.getRecord()))||catalog.get(sound.id)==null){stop(pos);continue;}
   world.spawnParticle(Particle.NOTE,pos.clone().add(.5,1.2,.5),1,0,0,0,1);
  }
 }
 @Override public void onDisable(){stopping=true;if(web!=null)web.close();for(Location pos:List.copyOf(playing.keySet()))stop(pos);for(Player p:Bukkit.getOnlinePlayers())if(p.getOpenInventory().getTopInventory().getHolder() instanceof Screen)p.closeInventory();if(io!=null){io.shutdown();try{if(!io.awaitTermination(3,TimeUnit.SECONDS))io.shutdownNow();}catch(InterruptedException e){Thread.currentThread().interrupt();io.shutdownNow();}}}
}
