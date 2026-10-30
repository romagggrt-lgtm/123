package dev.zap;

import java.io.IOException;
import java.io.File;
import org.bukkit.configuration.file.YamlConfiguration;
import java.util.*;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.bukkit.inventory.view.AnvilView;
import org.bukkit.plugin.java.JavaPlugin;
import org.mindrot.jbcrypt.BCrypt;

public final class ZAPPlugin extends JavaPlugin implements Listener,CommandExecutor,TabCompleter {
 private Auth auth; private YamlConfiguration messages; private final Map<UUID,Long> sessions=new HashMap<>(),lastClick=new HashMap<>();
 private final Set<UUID> pending=new HashSet<>(),packed=new HashSet<>(); private final Map<UUID,Screen> inputs=new HashMap<>();
 private long generation=0;
 private static final NamedTextColor PURPLE=NamedTextColor.LIGHT_PURPLE;
 private final class Screen implements InventoryHolder {
  Inventory inventory; final UUID owner; final String kind; final Map<Integer,Consumer<Player>> actions=new HashMap<>(); Consumer<String> input; boolean busy;
  Screen(Player p,String kind){owner=p.getUniqueId();this.kind=kind;}
  @Override public Inventory getInventory(){return inventory;}
 }
 @Override public void onEnable(){saveDefaultConfig();if(!new File(getDataFolder(),"messages.yml").exists())saveResource("messages.yml",false);messages=YamlConfiguration.loadConfiguration(new File(getDataFolder(),"messages.yml"));try{auth=new Auth(getDataFolder().toPath(),getConfig().getInt("max-failures",5),getConfig().getInt("lock-seconds",900),getConfig().getInt("bcrypt-cost",10));}catch(IOException e){getLogger().severe("Credentials unavailable; panel disabled: "+e.getMessage());getServer().getPluginManager().disablePlugin(this);return;}getServer().getPluginManager().registerEvents(this,this);Objects.requireNonNull(getCommand("ap")).setExecutor(this);Objects.requireNonNull(getCommand("ap")).setTabCompleter(this);Bukkit.getScheduler().runTaskTimer(this,this::reapSessions,100,100);}
 @Override public void onDisable(){generation++;for(Player p:Bukkit.getOnlinePlayers())if(screen(p,p.getOpenInventory().getTopInventory())!=null)p.closeInventory();sessions.clear();pending.clear();packed.clear();inputs.clear();}
 private void msg(Player p,String text){String key=switch(text){case "§cНедостаточно прав."->"no-access";case "§cСлишком много неверных попыток. Повторите позже."->"locked";case "§eСессия завершена. Войдите снова."->"expired";default->null;};if(key!=null)text=messages.getString(key,text);String prefix=messages.getString("prefix","§8[§dZ_AP§8] §r");p.sendMessage(LegacyComponentSerializer.legacySection().deserialize(prefix+text));}
 private void reapSessions(){long now=System.currentTimeMillis(),timeout=Math.clamp(getConfig().getInt("session-idle-seconds",600),30,86400)*1000L;for(var entry:new ArrayList<>(sessions.entrySet())){Player p=Bukkit.getPlayer(entry.getKey());if(p==null||!access(p)||now-entry.getValue()>timeout){sessions.remove(entry.getKey());if(p!=null&&screen(p,p.getOpenInventory().getTopInventory())!=null){p.closeInventory();msg(p,"§eСессия завершена. Войдите снова.");}}}}
 private boolean access(Player p){return p.hasPermission("z_ap.use");}
 private boolean allowed(Player p,String action){if(!access(p)||!p.hasPermission("z_ap.actions."+action)){msg(p,"§cНедостаточно прав.");return false;}return true;}
 private boolean live(Player p){Long t=sessions.get(p.getUniqueId());long now=System.currentTimeMillis(),timeout=Math.clamp(getConfig().getInt("session-idle-seconds",600),30,86400)*1000L;if(!access(p)||t==null||now-t>timeout){sessions.remove(p.getUniqueId());return false;}sessions.put(p.getUniqueId(),now);return true;}
 @Override public boolean onCommand(CommandSender sender,Command cmd,String label,String[] args){
  if(!(sender instanceof Player p)){sender.sendMessage("Команда доступна только игроку.");return true;}
  if(args.length>0&&args[0].equalsIgnoreCase("reload")){if(!p.hasPermission("z_ap.reload")){msg(p,"§cНедостаточно прав.");return true;}reloadConfig();messages=YamlConfiguration.loadConfiguration(new File(getDataFolder(),"messages.yml"));auth.configure(getConfig().getInt("max-failures",5),getConfig().getInt("lock-seconds",900),getConfig().getInt("bcrypt-cost",10));sessions.clear();generation++;msg(p,"§aКонфигурация перезагружена; сессии закрыты.");return true;}
  if(!access(p)){msg(p,"§cНедостаточно прав.");return true;}
  if(args.length>0&&args[0].equalsIgnoreCase("lock")){sessions.remove(p.getUniqueId());p.closeInventory();msg(p,"§eСессия закрыта.");return true;}
  if(args.length>0&&args[0].equalsIgnoreCase("help")){msg(p,"/ap — открыть панель; /ap lock — закрыть сессию; /ap reload — настройки.");return true;}
  if(args.length>0){msg(p,"Неизвестная команда. /ap help");return true;}
  if(live(p))home(p);else login(p);return true;
 }
 @Override public List<String> onTabComplete(CommandSender s,Command c,String a,String[] args){if(!s.hasPermission("z_ap.use"))return List.of();if(args.length!=1)return List.of();List<String> o=new ArrayList<>(List.of("lock","help"));if(s.hasPermission("z_ap.reload"))o.add("reload");return o.stream().filter(v->v.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();}
 private Screen chest(Player p,String title,String type){Screen h=new Screen(p,type);h.inventory=Bukkit.createInventory(h,54,Component.text(title,PURPLE));ItemStack glass=item(Material.BLACK_STAINED_GLASS_PANE," ",null);for(int i=0;i<54;i++)h.inventory.setItem(i,glass);return h;}
 private ItemStack item(Material type,String name,String icon,String... lore){ItemStack i=new ItemStack(type);i.editMeta(m->{m.displayName(Component.text(name,PURPLE));if(lore.length>0)m.lore(Arrays.stream(lore).map(s->Component.text(s,NamedTextColor.GRAY)).toList());if(icon!=null)m.setItemModel(new NamespacedKey("z_ap",icon));});return i;}
 private void button(Screen h,int slot,Material mat,String name,String icon,Consumer<Player> action,String... lore){h.inventory.setItem(slot,item(mat,name,packed.contains(h.owner)?icon:null,lore));h.actions.put(slot,action);}
 private void open(Player p,Screen h){p.openInventory(h.inventory);}
 private void navigation(Screen h){button(h,45,Material.ARROW,"Назад","back",this::home);button(h,49,Material.NETHER_STAR,"Главная","home",this::home);button(h,53,Material.BARRIER,"Закрыть","close",Player::closeInventory);}
 private void login(Player p){if(auth.locked(p.getUniqueId(),System.currentTimeMillis())){msg(p,"§cСлишком много неверных попыток. Повторите позже.");return;}input(p,"Введите пароль","auth",password->verify(p,password));}
 private Screen screen(Player p,Inventory inv){if(inv.getHolder() instanceof Screen h)return h;Screen h=inputs.get(p.getUniqueId());return h!=null&&h.inventory.equals(inv)?h:null;}
 private void input(Player p,String title,String kind,Consumer<String> submit){Screen h=new Screen(p,kind);AnvilView view=MenuType.ANVIL.builder().title(Component.text(title,PURPLE)).checkReachable(false).build(p);h.inventory=view.getTopInventory();h.input=submit;inputs.put(p.getUniqueId(),h);p.openInventory(view);h.inventory.setItem(0,item(Material.PAPER,"Введите текст",null,"Введите текст в верхнем поле, затем нажмите этот предмет."));}
 private void verify(Player p,String password){
  UUID id=p.getUniqueId();if(!access(p)||auth.locked(id,System.currentTimeMillis())||!pending.add(id))return;
  Screen screen=screen(p,p.getOpenInventory().getTopInventory());long g=generation;String expected=auth.currentHash();
  Bukkit.getScheduler().runTaskAsynchronously(this,()->{boolean ok=Auth.verify(password,expected);if(!isEnabled())return;Bukkit.getScheduler().runTask(this,()->{
   pending.remove(id);Player live=Bukkit.getPlayer(id);if(g!=generation||!Objects.equals(expected,auth.currentHash()))return;
   try{
    if(!ok){auth.fail(id,System.currentTimeMillis());if(live!=null&&screen(live,live.getOpenInventory().getTopInventory())==screen){msg(live,auth.locked(id,System.currentTimeMillis())?"§cДоступ временно заблокирован.":"§cНеверный пароль.");live.closeInventory();}return;}
    if(live==null||!access(live)||screen(live,live.getOpenInventory().getTopInventory())!=screen)return;
    auth.success(id);sessions.put(id,System.currentTimeMillis());live.showTitle(Title.title(Component.text("ДОБРО ПОЖАЛОВАТЬ",PURPLE),Component.text("Z_AP • ADMIN PANEL",NamedTextColor.GRAY)));home(live);
   }catch(IOException e){getLogger().severe("Authentication storage failed: "+e.getMessage());if(live!=null){msg(live,"§cОшибка сохранения авторизации. Доступ не выдан.");live.closeInventory();}}
  });});
 }
 @EventHandler public void prepare(PrepareAnvilEvent e){if(e.getView().getPlayer() instanceof Player p){Screen h=screen(p,e.getInventory());if(h!=null&&h.input!=null){e.setResult(item(Material.PAPER,"Подтвердить",null));if(e.getView() instanceof AnvilView v)v.setRepairCost(0);}}}
 @EventHandler public void click(InventoryClickEvent e){
  if(!(e.getWhoClicked() instanceof Player p))return;Screen h=screen(p,e.getView().getTopInventory());if(h==null)return;e.setCancelled(true);if(!h.owner.equals(p.getUniqueId()))return;
  if(e.getRawSlot()<0||e.getRawSlot()>=e.getView().getTopInventory().getSize())return;
  if(h.input!=null){if((e.getRawSlot()!=0&&e.getRawSlot()!=2)||h.busy)return;if(!h.kind.equals("auth")&&!live(p)){p.closeInventory();return;}if(!(e.getView() instanceof AnvilView view))return;String s=view.getRenameText();if(s==null||s.length()>128){msg(p,"§cНекорректный ввод.");return;}h.busy=true;h.input.accept(s);return;}
  if(!live(p)){p.closeInventory();msg(p,"§eСессия завершена. Войдите снова.");return;}
  long now=System.currentTimeMillis();if(now-lastClick.getOrDefault(p.getUniqueId(),0L)<200)return;lastClick.put(p.getUniqueId(),now);Consumer<Player> action=h.actions.get(e.getRawSlot());if(action!=null)action.accept(p);
 }
 @EventHandler public void drag(InventoryDragEvent e){if(e.getWhoClicked() instanceof Player p&&screen(p,e.getView().getTopInventory())!=null)e.setCancelled(true);}
 @EventHandler public void close(InventoryCloseEvent e){if(e.getPlayer() instanceof Player p){Screen h=inputs.get(p.getUniqueId());if(h!=null&&h.inventory.equals(e.getInventory()))inputs.remove(p.getUniqueId());}}
 @EventHandler public void quit(PlayerQuitEvent e){UUID id=e.getPlayer().getUniqueId();sessions.remove(id);lastClick.remove(id);packed.remove(id);inputs.remove(id);}
 @EventHandler public void pack(PlayerResourcePackStatusEvent e){if(e.getStatus()==PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED)packed.add(e.getPlayer().getUniqueId());else if(e.getStatus()==PlayerResourcePackStatusEvent.Status.DECLINED||e.getStatus()==PlayerResourcePackStatusEvent.Status.FAILED_DOWNLOAD)packed.remove(e.getPlayer().getUniqueId());}
 private boolean installed(String plugin){return Bukkit.getPluginManager().isPluginEnabled(plugin);}
 private void external(Player p,String action,String plugin,String perm,String command){
  if(!live(p)||!allowed(p,action))return;if(!installed(plugin)){msg(p,"§cПлагин "+plugin+" не установлен или не включён.");return;}
  if(perm!=null&&!p.hasPermission(perm)){msg(p,"§cНе хватает права "+perm+".");return;}
  if(plugin.equals("InventoryCache")&&!p.isOp()){msg(p,"§cУстановленная версия InventoryCache требует OP.");return;}
  p.closeInventory();if(!p.performCommand(command))msg(p,"§cКоманда не найдена; проверьте версию плагина.");
 }
 private void home(Player p){if(!live(p)){login(p);return;}Screen h=chest(p,"Z_AP • Главная","home");
  h.inventory.setItem(4,item(Material.PLAYER_HEAD,p.getName(),null,"Мир: "+p.getWorld().getName(),"Режим: "+p.getGameMode(),"Игроков онлайн: "+Bukkit.getOnlinePlayers().size()));
  button(h,10,Material.PLAYER_HEAD,"Игроки","players",q->{if(allowed(q,"players"))players(q,0,"");});
  button(h,11,Material.ENDER_PEARL,"Телепортация","teleport",q->{if(allowed(q,"teleport")&&allowed(q,"players"))players(q,0,"");});
  button(h,12,Material.GRASS_BLOCK,"Режим игры","mode",q->{if(allowed(q,"gamemode"))modes(q,q.getUniqueId());});
  button(h,13,Material.CREEPER_HEAD,"Морфы","morph",q->{if(allowed(q,"morph"))morph(q,0,"");});
  button(h,14,Material.CHEST,"Наборы инвентаря","kits",q->{if(allowed(q,"kits"))kits(q);});
  button(h,15,Material.NAME_TAG,"Никнейм","nick",q->{if(allowed(q,"nick"))nick(q,q.getUniqueId());});
  button(h,16,Material.ENDER_EYE,"Невидимка","vanish",q->{if(allowed(q,"vanish"))vanish(q);});
  button(h,21,Material.CAMPFIRE,"Баня","banya",q->{if(allowed(q,"banya"))banya(q);});
  button(h,22,Material.CLOCK,"Мир и инструменты","world",q->{if(allowed(q,"world"))world(q);});
  button(h,23,Material.WOODEN_AXE,"Регионы и WorldEdit","region",q->{if(allowed(q,"regions")||q.hasPermission("z_ap.actions.worldedit"))regions(q);});
  button(h,31,Material.COMPARATOR,"Настройки","settings",q->{if(allowed(q,"settings"))settings(q);});
  h.inventory.setItem(40,item(Material.BOOK,"Подключения",null,"Z_Morph: "+status("Z_Morph"),"InventoryCache: "+status("InventoryCache"),"Z_Nick: "+status("Z_Nick"),"Nevidimka: "+status("Nevidimka"),"Z_Banya: "+status("Z_Banya")));
  button(h,49,Material.BARRIER,"Заблокировать панель","close",q->{sessions.remove(q.getUniqueId());q.closeInventory();});open(p,h);
 }
 private String status(String name){return installed(name)?"доступен":"не установлен";}
 private void modes(Player viewer,UUID targetId){Screen h=chest(viewer,"Режим игры","modes");int slot=19;for(GameMode m:GameMode.values()){
  GameMode mode=m;button(h,slot++,switch(mode){case SURVIVAL->Material.IRON_SWORD;case CREATIVE->Material.GRASS_BLOCK;case ADVENTURE->Material.MAP;case SPECTATOR->Material.ENDER_EYE;},mode.name(),"mode",p->{if(!allowed(p,"gamemode"))return;Player target=visible(p,targetId);if(target==null)return;if(!target.equals(p)&&!allowed(p,"players"))return;target.setGameMode(mode);msg(p,"§aРежим игрока "+target.getName()+": "+target.getGameMode());home(p);});
 }navigation(h);open(viewer,h);}
 private Player visible(Player viewer,UUID id){if(!viewer.getUniqueId().equals(id)&&!allowed(viewer,"players"))return null;Player t=Bukkit.getPlayer(id);if(t==null||!t.isOnline()||(!viewer.canSee(t)&&!viewer.hasPermission("z_ap.see_vanish"))){msg(viewer,"§cИгрок недоступен.");return null;}return t;}
 private List<Player> visiblePlayers(Player p,String filter){return Bukkit.getOnlinePlayers().stream().map(t->(Player)t).filter(t->p.canSee(t)||p.hasPermission("z_ap.see_vanish")).filter(t->t.getName().toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT))).sorted(Comparator.comparing(Player::getName,String.CASE_INSENSITIVE_ORDER)).toList();}
 private void players(Player p,int page,String filter){if(!live(p)||!allowed(p,"players"))return;Screen h=chest(p,"Игроки • "+(page+1),"players");List<Player> list=visiblePlayers(p,filter);int start=Math.max(0,page)*28;for(int n=0;n<28&&start+n<list.size();n++){
  Player t=list.get(start+n);UUID id=t.getUniqueId();int slot=10+(n/7)*9+n%7;button(h,slot,Material.PLAYER_HEAD,t.getName(),null,q->{if(allowed(q,"players")&&visible(q,id)!=null)profile(q,id);},"Мир: "+t.getWorld().getName(),"Режим: "+t.getGameMode(),"Ping: "+t.getPing());ItemStack skull=h.inventory.getItem(slot);skull.editMeta(SkullMeta.class,meta->meta.setOwningPlayer(t));h.inventory.setItem(slot,skull);
 }navigation(h);if(page>0)button(h,46,Material.ARROW,"Предыдущая","back",q->players(q,page-1,filter));if(start+28<list.size())button(h,52,Material.ARROW,"Следующая","next",q->players(q,page+1,filter));button(h,48,Material.OAK_SIGN,"Поиск","search",q->input(q,"Имя игрока","search",s->{if(s.matches("[A-Za-z0-9_]{0,16}"))players(q,0,s);else{msg(q,"§cНеверное имя.");players(q,0,"");}}));button(h,50,Material.SUNFLOWER,"Обновить","refresh",q->players(q,page,filter));open(p,h);}
 private boolean safe(Location l){if(l==null||l.getWorld()==null||!l.getWorld().getWorldBorder().isInside(l))return false;World w=l.getWorld();if(l.getY()<w.getMinHeight()||l.getY()+1>=w.getMaxHeight()||!w.isChunkLoaded(l.getBlockX()>>4,l.getBlockZ()>>4))return false;Block feet=l.getBlock(),head=feet.getRelative(0,1,0),floor=feet.getRelative(0,-1,0);if(!feet.isPassable()||!head.isPassable()||!floor.getType().isSolid())return false;return !Set.of(Material.LAVA,Material.FIRE,Material.SOUL_FIRE,Material.MAGMA_BLOCK,Material.CAMPFIRE,Material.SOUL_CAMPFIRE).contains(feet.getType())&&!Set.of(Material.LAVA,Material.FIRE,Material.SOUL_FIRE,Material.MAGMA_BLOCK,Material.CAMPFIRE,Material.SOUL_CAMPFIRE).contains(floor.getType());}
 private void teleport(Player actor,Player who,Location where){if(!allowed(actor,"teleport"))return;if(!safe(where)){msg(actor,"§cТочка небезопасна или чанк не загружен.");return;}if(who.teleport(where)){msg(actor,"§aТелепортация выполнена.");actor.closeInventory();}else msg(actor,"§cТелепортация отменена сервером или другим плагином.");}
 private void profile(Player p,UUID id){Player t=visible(p,id);if(t==null)return;Screen h=chest(p,"Игрок • "+t.getName(),"profile");Location l=t.getLocation();h.inventory.setItem(4,item(Material.PLAYER_HEAD,t.getName(),null,"UUID: "+id,"Мир: "+l.getWorld().getName(),"Координаты: "+l.getBlockX()+" "+l.getBlockY()+" "+l.getBlockZ(),"Здоровье: "+String.format(Locale.ROOT,"%.1f",t.getHealth()),"Ping: "+t.getPing()));
  button(h,19,Material.ENDER_PEARL,"К игроку","teleport",q->{Player v=visible(q,id);if(v!=null)teleport(q,q,v.getLocation());});
  button(h,21,Material.ENDER_EYE,"Игрока ко мне","summon",q->{if(!allowed(q,"summon"))return;Player v=visible(q,id);if(v!=null)teleport(q,v,q.getLocation());});
  button(h,23,Material.GRASS_BLOCK,"Режим игрока","mode",q->{if(allowed(q,"gamemode"))modes(q,id);});
  button(h,25,Material.NAME_TAG,"Переименовать","nick",q->{if(allowed(q,"nick"))nick(q,id);});
  button(h,29,Material.COMPASS,"К координатам","teleport",q->{if(!allowed(q,"teleport"))return;input(q,"мир x y z","coords",text->{Player v=visible(q,id);if(v==null)return;String[] a=text.trim().split(" +");try{if(a.length!=4)throw new IllegalArgumentException();World w=Bukkit.getWorld(a[0]);double x=Double.parseDouble(a[1]),y=Double.parseDouble(a[2]),z=Double.parseDouble(a[3]);if(w==null||!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z))throw new IllegalArgumentException();teleport(q,v,new Location(w,x,y,z));}catch(IllegalArgumentException ex){msg(q,"§cФормат: мир x y z");}});});
  button(h,33,Material.PACKED_ICE,"Проверка игрока","settings",q->{if(!allowed(q,"moderation"))return;Player v=visible(q,id);if(v==null)return;confirm(q,"Переключить проверку для "+v.getName()+"?",r->{Player target=visible(r,id);if(target!=null)external(r,"moderation","Z_Check","zcheck.use","z_check:zcheck "+target.getName());});});
  navigation(h);open(p,h);
 }
 private void morph(Player p,int page,String filter){morph(p,page,filter,"all");}
 private void morph(Player p,int page,String filter,String category){if(!live(p)||!allowed(p,"morph"))return;if(!installed("Z_Morph")||!p.hasPermission("zmorph.use")){msg(p,"§cZ_Morph не установлен или нет права zmorph.use.");return;}
  PluginCommand command=Bukkit.getPluginCommand("z_morph:morph");if(command==null)command=Bukkit.getPluginCommand("morph");if(command==null){msg(p,"§cКоманда морфа недоступна.");return;}
  List<String> list;try{list=command.tabComplete(p,"morph",new String[]{"set",filter.toLowerCase(Locale.ROOT)});}catch(Exception ex){msg(p,"§cНе удалось получить список мобов.");return;}
  if(!category.equals("all"))list=list.stream().filter(name->{try{Class<?> c=org.bukkit.entity.EntityType.valueOf(name.toUpperCase(Locale.ROOT)).getEntityClass();boolean animal=c!=null&&org.bukkit.entity.Animals.class.isAssignableFrom(c),hostile=c!=null&&org.bukkit.entity.Monster.class.isAssignableFrom(c);return category.equals("animals")?animal:category.equals("hostile")?hostile:!animal&&!hostile;}catch(IllegalArgumentException ex){return false;}}).toList();
  Screen h=chest(p,"Морфы • "+(page+1),"morph");int start=page*28;for(int n=0;n<28&&start+n<list.size();n++){
   String name=list.get(start+n);if(!name.matches("[a-z0-9_]{1,64}"))continue;int slot=10+(n/7)*9+n%7;button(h,slot,Material.CREEPER_HEAD,name,"morph",q->external(q,"morph","Z_Morph","zmorph.use","z_morph:morph set "+name));
  }navigation(h);if(page>0)button(h,46,Material.ARROW,"Предыдущая","back",q->morph(q,page-1,filter,category));if(start+28<list.size())button(h,52,Material.ARROW,"Следующая","next",q->morph(q,page+1,filter,category));button(h,48,Material.OAK_SIGN,"Поиск моба","search",q->input(q,"Имя моба","morph-search",s->{if(s.matches("[a-zA-Z0-9_]{0,64}"))morph(q,0,s,category);else msg(q,"§cНекорректное имя.");}));button(h,47,Material.BOOK,"Категория: "+switch(category){case "animals"->"мирные";case "hostile"->"враждебные";case "other"->"прочие";default->"все";},"morph",q->morph(q,0,filter,switch(category){case "all"->"animals";case "animals"->"hostile";case "hostile"->"other";default->"all";}));button(h,50,Material.MILK_BUCKET,"Вернуть облик","morph",q->external(q,"morph","Z_Morph","zmorph.use","z_morph:morph remove"));open(p,h);
 }
 private void confirm(Player p,String title,Consumer<Player> action){Screen h=chest(p,"Подтверждение","confirm");h.inventory.setItem(4,item(Material.PAPER,title,null));button(h,20,Material.LIME_CONCRETE,"Подтвердить","confirm",action);button(h,24,Material.RED_CONCRETE,"Отмена","close",this::home);navigation(h);open(p,h);}
 private void kits(Player p){Screen h=chest(p,"InventoryCache","kits");h.inventory.setItem(4,item(Material.BOOK,"Наборы по имени",null,"Установленная версия поддерживает /dk save и load.","При загрузке InventoryCache создаёт резервную копию."));
  button(h,20,Material.CHEST,"Сохранить инвентарь","kits",q->input(q,"Название набора","kit-save",name->{if(!name.matches("[a-zA-Z0-9_-]{1,48}")){msg(q,"§cНазвание: латиница, цифры, _ или -.");return;}confirm(q,"Сохранить набор "+name,r->external(r,"kits","InventoryCache",null,"inventorycache:dk save "+name));}));
  button(h,24,Material.ENDER_CHEST,"Загрузить инвентарь","kits",q->input(q,"Название набора","kit-load",name->{if(!name.matches("[a-zA-Z0-9_-]{1,48}")){msg(q,"§cНекорректное название.");return;}confirm(q,"Заменить инвентарь набором "+name+"?",r->external(r,"kits","InventoryCache",null,"inventorycache:dk load "+name));}));navigation(h);open(p,h);
 }
 private void nick(Player p,UUID targetId){Player target=visible(p,targetId);if(target==null)return;Screen h=chest(p,"Ник • "+target.getName(),"nick");
  button(h,20,Material.NAME_TAG,"Новый ник","nick",q->input(q,"Новый ник","nick-input",name->{if(!name.matches("[\\p{L}\\p{N}_&#<>!:/.-]{1,80}")){msg(q,"§cНекорректный ник.");return;}Player t=visible(q,targetId);if(t==null)return;confirm(q,"Применить ник "+name+" для "+t.getName()+"?",r->{Player v=visible(r,targetId);if(v==null)return;if(!v.equals(r)&&!allowed(r,"players"))return;external(r,"nick","Z_Nick","znick.set","z_nick:znick set "+name+" "+v.getName());});}));
  button(h,24,Material.BARRIER,"Сбросить ник","nick",q->{Player v=visible(q,targetId);if(v==null)return;if(!v.equals(q)&&!allowed(q,"players"))return;external(q,"nick","Z_Nick","znick.remove","z_nick:znick remove "+v.getName());});navigation(h);open(p,h);
 }
 private void vanish(Player p){Screen h=chest(p,"Невидимка","vanish");var plugin=Bukkit.getPluginManager().getPlugin("Nevidimka");boolean active=plugin!=null&&p.getMetadata("vanished").stream().anyMatch(v->v.getOwningPlugin()==plugin&&v.asBoolean());h.inventory.setItem(4,item(Material.ENDER_EYE,"Состояние: "+(plugin==null?"плагин недоступен":active?"включена":"выключена"),null));
  button(h,20,Material.LIME_DYE,"Включить","vanish",q->external(q,"vanish","Nevidimka","nevidimka.use","nevidimka:nevidimka on"));button(h,24,Material.GRAY_DYE,"Выключить","vanish",q->external(q,"vanish","Nevidimka","nevidimka.use","nevidimka:nevidimka off"));navigation(h);open(p,h);
 }
 private void banya(Player p){Screen h=chest(p,"Баня","banya");button(h,20,Material.CAMPFIRE,"Построить по имени","banya",q->banyaInput(q,"set"));button(h,24,Material.BARRIER,"Удалить по имени","banya",q->banyaInput(q,"remove"));button(h,31,Material.BOOK,"Список бань","banya",q->external(q,"banya","Z_Banya","zbanya.admin","z_banya:zbanya list"));navigation(h);open(p,h);}
 private void banyaInput(Player p,String operation){input(p,"Название бани","banya-name",name->{if(!name.matches("[\\p{L}\\p{N}_-]{1,32}")){msg(p,"§cНекорректное название.");return;}confirm(p,(operation.equals("set")?"Построить":"Удалить")+" баню "+name+"?",q->external(q,"banya","Z_Banya","zbanya.admin","z_banya:zbanya "+operation+" "+name));});}
 private void world(Player p){Screen h=chest(p,"Мир и инструменты","world");button(h,19,Material.SUNFLOWER,"День","world",q->{if(!allowed(q,"world"))return;q.getWorld().setTime(1000);msg(q,"§aВремя изменено.");});button(h,20,Material.CLOCK,"Ночь","world",q->{if(!allowed(q,"world"))return;q.getWorld().setTime(13000);msg(q,"§aВремя изменено.");});button(h,22,Material.WATER_BUCKET,"Ясная погода","world",q->{if(!allowed(q,"world"))return;q.getWorld().setStorm(false);q.getWorld().setThundering(false);msg(q,"§aПогода изменена.");});button(h,24,Material.FEATHER,"Скорость 1×","speed",q->{if(!allowed(q,"speed"))return;q.setWalkSpeed(.2f);q.setFlySpeed(.1f);msg(q,"§aСтандартная скорость восстановлена.");});button(h,25,Material.RABBIT_FOOT,"Скорость 2×","speed",q->{if(!allowed(q,"speed"))return;q.setWalkSpeed(.4f);q.setFlySpeed(.2f);msg(q,"§aСкорость изменена.");});navigation(h);open(p,h);}
 private void regions(Player p){Screen h=chest(p,"Регионы и WorldEdit","regions");button(h,19,Material.MAP,"Информация о регионе","region",q->external(q,"regions","Z_RG",null,"z_rg:rg info"));button(h,21,Material.WOODEN_AXE,"Топор WorldEdit","region",q->external(q,"worldedit","WorldEdit","worldedit.wand","/wand"));button(h,23,Material.ARROW,"Отменить изменение","back",q->confirm(q,"Отменить последнее изменение WorldEdit?",r->external(r,"worldedit","WorldEdit","worldedit.history.undo","/undo")));button(h,25,Material.BRICKS,"Заполнить выделение","world",q->input(q,"Материал","we-set",name->{String value=name.toLowerCase(Locale.ROOT);Material m=Material.matchMaterial(value);if(m==null||!m.isBlock()||!value.matches("[a-z0-9_]{1,64}")){msg(q,"§cНеизвестный материал.");return;}confirm(q,"Заменить выделение на "+value+"?",r->external(r,"worldedit","WorldEdit","worldedit.region.set","/set "+value));}));navigation(h);open(p,h);}
 private void settings(Player p){Screen h=chest(p,"Настройки","settings");button(h,22,Material.TRIPWIRE_HOOK,"Сменить пароль","settings",q->{if(!allowed(q,"settings"))return;input(q,"Новый пароль","new-password",first->{if(!Auth.validPassword(first)){msg(q,"§cПароль должен содержать 1–72 байта UTF-8.");return;}input(q,"Повторите пароль","confirm-password",second->{if(!first.equals(second)){msg(q,"§cПароли не совпали.");q.closeInventory();return;}changePassword(q,first);});});});navigation(h);open(p,h);}
 private void changePassword(Player p,String value){if(!live(p)||!allowed(p,"settings"))return;UUID id=p.getUniqueId();if(!pending.add(id))return;Screen holder=screen(p,p.getOpenInventory().getTopInventory());long g=generation;int cost=auth.cost();Bukkit.getScheduler().runTaskAsynchronously(this,()->{String hash=BCrypt.hashpw(value,BCrypt.gensalt(cost));if(!isEnabled())return;Bukkit.getScheduler().runTask(this,()->{pending.remove(id);Player now=Bukkit.getPlayer(id);if(now==null||generation!=g||!live(now)||!now.hasPermission("z_ap.actions.settings")||screen(now,now.getOpenInventory().getTopInventory())!=holder)return;try{auth.change(hash);sessions.clear();generation++;now.closeInventory();msg(now,"§aПароль изменён. Войдите снова.");}catch(IOException ex){getLogger().severe("Password update failed: "+ex.getMessage());msg(now,"§cПароль не изменён: ошибка сохранения.");}});});}
}
