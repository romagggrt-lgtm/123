package dev.zbanya;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.util.logging.Level;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.*;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.hanging.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.*;
import org.bukkit.scheduler.BukkitTask;

public class ZBanyaPlugin extends JavaPlugin implements Listener,CommandExecutor,TabCompleter {
    final Map<UUID,Bath> baths=new LinkedHashMap<>();
    private record Session(UUID bath,long next,String key,SoundCategory category){}
    private final Set<UUID> waitingPacks=new HashSet<>();
    private final Set<UUID> droppedFood=new HashSet<>();
    private final Map<UUID,Session> listeners=new HashMap<>();
    private final NamespacedKey recoveryKey=new NamespacedKey("zbanya","recovery");
    private final NamespacedKey foodKey=new NamespacedKey("zbanya","vkusnyashka"),originKey=new NamespacedKey("zbanya","origin"),claimedKey=new NamespacedKey("zbanya","claimed"),decorKey=new NamespacedKey("zbanya","decor"),indexKey=new NamespacedKey("zbanya","slot");
    Journal journal;private BukkitTask task;private boolean busy,closing;private long ticks;private Bath running;
    private final Set<UUID> recoveryAttempted=new HashSet<>();
    protected java.util.function.LongSupplier nanoClock=System::nanoTime;
    protected Journal openJournal(java.nio.file.Path path)throws Exception{return new Journal(path);}
    private final List<int[]> tickets=new ArrayList<>();private World ticketWorld;
    private double clipSeconds=10.032;
    protected boolean roll(double chance){return ThreadLocalRandom.current().nextDouble()<chance;}
    protected void ticket(World world,int x,int z,boolean add){if(add)world.addPluginChunkTicket(x,z,this);else world.removePluginChunkTicket(x,z,this);}
    @Override public void onEnable(){
        try{
            saveDefaultConfig();validateConfig();clipSeconds=Double.parseDouble(new String(Objects.requireNonNull(getResource("ambient-duration.txt")).readAllBytes()).trim());
            journal=openJournal(getDataFolder().toPath().resolve("baths"));for(var b:journal.load()){if(baths.put(b.id,b)!=null)throw new IllegalArgumentException("Повтор UUID бани");}
            assignSavedNames();
            if(getConfig().isSet("banya.max-per-player")){getConfig().set("banya.max-per-player",null);saveConfig();}
            getServer().getPluginManager().registerEvents(this,this);getCommand("zbanya").setExecutor(this);getCommand("zbanya").setTabCompleter(this);
            task=getServer().getScheduler().runTaskTimer(this,this::tick,1,1);
            Bukkit.getScheduler().runTask(this,()->{for(World w:Bukkit.getWorlds())for(Entity e:w.getEntities())if(e instanceof Item item)unlockRecovered(item);});
            Bukkit.getScheduler().runTask(this,()->{for(Bath b:baths.values())if(b.phase.equals("ACTIVE")&&Bukkit.getWorld(b.world)!=null)try{decorate(b);}catch(Exception e){getLogger().warning("Декор ожидает загрузки чанка: "+e.getMessage());}});
            getLogger().info("Z_Banya 1.0.2: загружено бань "+baths.size()+". Общий ресурс-пак задаётся в server.properties.");
        }catch(Exception e){getLogger().log(Level.SEVERE,"Z_Banya не запущен. Исходные журналы сохранены.",e);getServer().getPluginManager().disablePlugin(this);}
    }
    private void assignSavedNames(){
        var used=new HashSet<String>();var pending=new ArrayList<CompletableFuture<Void>>();
        for(Bath b:baths.values())if(b.name!=null&&!used.add(Bath.canonical(b.name)))throw new IllegalArgumentException("Повтор имени бани: "+b.name);
        for(Bath b:baths.values().stream().sorted(java.util.Comparator.comparing(v->v.id)).toList()){
            boolean changed=b.captureLegacyClearance();if(b.name==null){int number=1;while(used.contains(Integer.toString(number)))number++;b.name=Integer.toString(number);used.add(b.name);changed=true;}
            if(changed)pending.add(journal.save(b));
        }
        CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new)).join();
    }
    public Bath named(String name){if(name==null)return null;String key=Bath.canonical(name);return baths.values().stream().filter(b->b.name!=null&&Bath.canonical(b.name).equals(key)).findFirst().orElse(null);}
    public String nextName(){int n=1;while(named(Integer.toString(n))!=null)n++;return Integer.toString(n);}
    private void namedMessage(CommandSender sender,String id,Bath b){String text=getConfig().getString("messages."+id,id);if(!text.contains("{name}"))text+=" &7["+b.name+"]";sender.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&',text.replace("{name}",b.name)));}
    private boolean locked(Block block){Bath b=region(block);return b!=null&&!b.phase.equals("ACTIVE");}
    private Bath decorBath(Entity entity){String id=entity.getPersistentDataContainer().get(decorKey,PersistentDataType.STRING);if(id==null)return null;try{return baths.get(UUID.fromString(id));}catch(IllegalArgumentException ignored){return null;}}
    private void edited(Bath b){if(b==null||b.modified||!b.phase.equals("ACTIVE"))return;b.modified=true;after(journal.save(b),()->{},e->getLogger().log(Level.SEVERE,"Изменение бани сохранится при следующей операции",e));}
    private void validateConfig(){
        int min=getConfig().getInt("banya.search-min-radius",5),max=getConfig().getInt("banya.search-max-radius",20);
        if(min<5||max>20||max<min)throw new IllegalArgumentException("Радиус должен быть в пределах 5–20");
        soundCategory();
        Key.key(getConfig().getString("sound.sound-key","zbanya:ambient"));
        if(!Double.isFinite(getConfig().getDouble("sound.volume",.95))||getConfig().getDouble("sound.volume",.95)<0||getConfig().getDouble("sound.volume",.95)>4)throw new IllegalArgumentException("Громкость: 0–4");
        double chance=getConfig().getDouble("vkusnyashka.spawn-chance",.5);if(!Double.isFinite(chance)||chance<0||chance>1)throw new IllegalArgumentException("Шанс появления: 0–1");
        double pitch=getConfig().getDouble("sound.pitch",1);if(!Double.isFinite(pitch)||pitch<.5||pitch>2)throw new IllegalArgumentException("Высота звука: 0.5–2");
    }
    @Override public void onDisable(){closing=true;if(task!=null)task.cancel();for(Player p:Bukkit.getOnlinePlayers())stop(p,true);release();if(journal!=null)journal.close();}
    void message(CommandSender p,String id){String s=getConfig().getString("messages."+id,id);if(!s.isBlank())p.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&',s));}
    void error(CommandSender p,String message){p.sendMessage("§c[Z_Banya] "+message);}
    private void after(CompletableFuture<Void> future,Runnable ok,Consumer<Throwable> fail){future.whenComplete((v,e)->{if(closing||!isEnabled())return;Bukkit.getScheduler().runTask(this,()->{if(e==null)ok.run();else fail.accept(e);});});}
    public Collection<Bath> all(){return List.copyOf(baths.values());}
    public boolean idle(){return !busy;}
    public boolean listening(UUID player){return listeners.containsKey(player);}
    public Bath inside(Location at){if(at==null||at.getWorld()==null)return null;return baths.values().stream().filter(b->b.phase.equals("ACTIVE")&&b.contains(at)&&b.operational(at.getWorld())).findFirst().orElse(null);}
    Bath region(Block block){return baths.values().stream().filter(b->b.region(block)).findFirst().orElse(null);}
    private void tick(){
        ticks++;
        if(ticks%20==0&&!busy){var unfinished=baths.values().stream().filter(b->!recoveryAttempted.contains(b.id)&&!b.phase.equals("ACTIVE")&&Bukkit.getWorld(b.world)!=null).findFirst();unfinished.ifPresent(b->remove(b,null,true));}
        for(Player p:Bukkit.getOnlinePlayers()){
            if(ticks%5==0)check(p);
            if(ticks%4==0&&!p.isHandRaised()){animate(p.getInventory().getItemInMainHand(),item->p.getInventory().setItemInMainHand(item));animate(p.getInventory().getItemInOffHand(),item->p.getInventory().setItemInOffHand(item));}
            Session s=listeners.get(p.getUniqueId());if(s!=null&&nanoClock.getAsLong()>=s.next){Bath b=baths.get(s.bath);if(b==null||!b.phase.equals("ACTIVE")||!b.contains(p.getLocation()))stop(p,true);else play(p,b);}
        }
        if(ticks%4==0)for(UUID id:List.copyOf(droppedFood)){var e=Bukkit.getEntity(id);if(e instanceof Item item&&item.isValid()&&isFood(item.getItemStack()))animate(item.getItemStack(),item::setItemStack);else droppedFood.remove(id);}
        int interval=Math.max(5,getConfig().getInt("steam.interval-ticks",20));
        if(ticks%interval==0&&getConfig().getBoolean("steam.enabled",true))for(Bath b:baths.values())if(b.phase.equals("ACTIVE")){
            World w=Bukkit.getWorld(b.world);if(w==null||!b.operational(w))continue;var source=b.steam(w);if(!w.isChunkLoaded(source.getBlockX()>>4,source.getBlockZ()>>4))continue;
            for(Player p:w.getPlayers())if(p.getLocation().distanceSquared(source)<24*24)for(int n=0;n<Math.clamp(getConfig().getInt("steam.particle-count",4),0,24);n++)p.spawnParticle(Particle.CLOUD,source.clone().add(ThreadLocalRandom.current().nextDouble(-.22,.22),0,ThreadLocalRandom.current().nextDouble(-.22,.22)),0,0,.035,0,1);
        }
    }
    SoundCategory soundCategory(){return SoundCategory.valueOf(getConfig().getString("sound.category","MASTER").toUpperCase(Locale.ROOT));}
    protected void emitSound(Player p,String key,SoundCategory category,float volume,float pitch){p.playSound(p,key,category,volume,pitch);}
    protected void haltSound(Player p,String key,SoundCategory category){p.stopSound(key,category);}
    private void play(Player p,Bath b){
        String key=getConfig().getString("sound.sound-key","zbanya:ambient");float pitch=(float)getConfig().getDouble("sound.pitch",1);SoundCategory category=soundCategory();
        Session old=listeners.get(p.getUniqueId());if(old!=null)haltSound(p,old.key,old.category);else haltSound(p,key,category);
        // Entity sound follows its private recipient instead of fading away from the entry point.
        emitSound(p,key,category,(float)getConfig().getDouble("sound.volume",1),pitch);
        listeners.put(p.getUniqueId(),new Session(b.id,nanoClock.getAsLong()+(long)Math.ceil(clipSeconds*1_000_000_000d/pitch),key,category));
    }
    private void check(Player p){
        Bath bath=p.isDead()?null:inside(p.getLocation());Session s=listeners.get(p.getUniqueId());
        if(bath==null||waitingPacks.contains(p.getUniqueId())||!getConfig().getBoolean("sound.enabled",true)){if(s!=null)stop(p,false);return;}
        if(s==null||!s.bath.equals(bath.id)){stop(p,true);message(p,"welcome");play(p,bath);}
    }
    private void stop(Player p,boolean force){Session s=listeners.remove(p.getUniqueId());if(s!=null&&(force||getConfig().getBoolean("sound.stop-on-exit",true)))haltSound(p,s.key,s.category);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void enterWorksite(PlayerMoveEvent e){if(running!=null&&!running.phase.equals("ACTIVE")&&running.region(e.getTo().getBlock())&&!running.region(e.getFrom().getBlock()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void unload(WorldUnloadEvent e){if(running!=null&&running.world.equals(e.getWorld().getUID()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void move(PlayerMoveEvent e){if(e.hasChangedPosition())checkAt(e.getPlayer(),e.getTo());}
    private void checkAt(Player p,Location to){var s=listeners.get(p.getUniqueId());if(s!=null){var b=baths.get(s.bath);if(b==null||!b.contains(to))stop(p,false);}}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void teleport(PlayerTeleportEvent e){stop(e.getPlayer(),true);Bukkit.getScheduler().runTask(this,()->check(e.getPlayer()));}
    @EventHandler public void quit(PlayerQuitEvent e){stop(e.getPlayer(),true);waitingPacks.remove(e.getPlayer().getUniqueId());}
    @EventHandler public void death(PlayerDeathEvent e){stop(e.getEntity(),true);}
    @EventHandler(priority=EventPriority.MONITOR) public void packStatus(PlayerResourcePackStatusEvent e){
        var player=e.getPlayer();var status=e.getStatus();
        if(status==PlayerResourcePackStatusEvent.Status.ACCEPTED||status==PlayerResourcePackStatusEvent.Status.DOWNLOADED){waitingPacks.add(player.getUniqueId());stop(player,true);return;}
        waitingPacks.remove(player.getUniqueId());
        if(status==PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED){stop(player,true);Bukkit.getScheduler().runTask(this,()->{if(player.isOnline())check(player);});}
    }
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(!sender.hasPermission("zbanya.admin")){message(sender,"no-permission");return true;}
        try{
            if(args.length==1&&args[0].equalsIgnoreCase("reload")){if(busy){error(sender,"Дождитесь завершения текущей операции.");return true;}String previous=getConfig().saveToString();reloadConfig();try{validateConfig();}catch(Exception invalid){getConfig().loadFromString(previous);throw invalid;}for(Player p:Bukkit.getOnlinePlayers())stop(p,true);sender.sendMessage("§a[Z_Banya] Настройки перезагружены.");return true;}
            if(!(sender instanceof Player p)){error(sender,"Команды set/remove выполняются игроком в мире.");return true;}
            if(args.length<1||args.length>2||args.length==2&&!Set.of("set","remove").contains(args[0].toLowerCase(Locale.ROOT))){sender.sendMessage("§e[Z_Banya] /zbanya set <имя> | remove <имя> | list | reload | sound | status");return true;}
            if(busy){error(sender,"Дождитесь завершения текущей операции.");return true;}
            if(args[0].equalsIgnoreCase("status")){
                var status=p.getResourcePackStatus();p.sendMessage("§e[Z_Banya] Версия: 1.0.2; внутри: "+(inside(p.getLocation())!=null?"да":"нет")+"; звук: "+(getConfig().getBoolean("sound.enabled",true)?"включён":"выключен")+"; канал: "+soundCategory()+"; громкость: "+getConfig().getDouble("sound.volume",1));
                p.sendMessage("§e[Z_Banya] RP: "+(status==null?"серверный пакет не загружен":status)+"; событие: "+getConfig().getString("sound.sound-key","zbanya:ambient"));
            }
            else if(args[0].equalsIgnoreCase("sound")){
                Bath bath=inside(p.getLocation());if(bath==null)error(p,"Зайдите внутрь бани для проверки звука.");else if(!getConfig().getBoolean("sound.enabled",true))error(p,"Звук выключен в config.yml: sound.enabled.");else if(waitingPacks.contains(p.getUniqueId()))error(p,"Ресурс-пак ещё загружается.");else{play(p,bath);p.sendMessage("§a[Z_Banya] Звук перезапущен. Если тишина, проверьте /zbanya status и общую громкость клиента.");}
            }
            else if(args[0].equalsIgnoreCase("list")){p.sendMessage("§e[Z_Banya] Бани: "+(baths.isEmpty()?"нет":baths.values().stream().map(b->b.name+" ("+b.x+", "+b.z+")").collect(java.util.stream.Collectors.joining(", "))));}
            else if(args[0].equalsIgnoreCase("set"))create(p,args.length==2?args[1]:nextName());
            else if(args[0].equalsIgnoreCase("remove")){
                Bath b=args.length==2?named(args[1]):baths.values().stream().filter(a->a.world.equals(p.getWorld().getUID())).filter(a->a.location(p.getWorld(),0,2,0).distanceSquared(p.getLocation())<=400).min(java.util.Comparator.comparingDouble(a->a.location(p.getWorld(),0,2,0).distanceSquared(p.getLocation()))).orElse(null);
                if(b==null){if(args.length==2)error(p,"Баня «"+args[1]+"» не найдена.");else message(p,"no-banya");}else remove(b,p,false);
            }else sender.sendMessage("§e[Z_Banya] /zbanya set <имя> | remove <имя> | list | reload | sound | status");
        }catch(Exception e){error(sender,"Операция не выполнена: "+e.getMessage());getLogger().log(Level.WARNING,"Ошибка команды",e);}return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command c,String alias,String[] args){
        if(!sender.hasPermission("zbanya.admin"))return List.of();
        if(args.length==1)return List.of("set","remove","list","reload","sound","status").stream().filter(v->v.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        if(args.length==2&&args[0].equalsIgnoreCase("remove"))return baths.values().stream().map(b->b.name).filter(n->Bath.canonical(n).startsWith(Bath.canonical(args[1]))).sorted().toList();
        if(args.length==2&&args[0].equalsIgnoreCase("set"))return List.of(nextName()).stream().filter(n->n.startsWith(args[1])).toList();
        return List.of();
    }
    public void create(Player player){create(player,nextName());}
    public void create(Player player,String name){
        if(busy)return;
        if(!Bath.validName(name)){error(player,"Имя: 1–32 буквы, цифры, _ или - без пробелов.");return;}
        name=java.text.Normalizer.normalize(name,java.text.Normalizer.Form.NFC);final String chosen=name;
        if(named(name)!=null){error(player,"Баня «"+name+"» уже существует.");return;}
        if(baths.size()>=Math.clamp(getConfig().getInt("banya.max-total",64),1,128)){error(player,"Достигнут общий лимит бань.");return;}
        busy=true;
        var origin=player.getLocation().clone();int min=getConfig().getInt("banya.search-min-radius",5),max=getConfig().getInt("banya.search-max-radius",20);var candidates=Sites.candidates(min,max);
        new org.bukkit.scheduler.BukkitRunnable(){int index;
            public void run(){
                if(!player.isOnline()||!player.getWorld().equals(origin.getWorld())){cancel();busy=false;return;}
                for(int i=0;i<8&&index<candidates.size();i++,index++){
                    Bath found=Sites.probe(origin,player.getUniqueId(),baths.values(),candidates.get(index),min,max,getConfig().getBoolean("banya.allow-overlap",false));
                    if(found!=null){found.name=chosen;cancel();begin(found,player);return;}
                }
                if(index>=candidates.size()){cancel();busy=false;message(player,"no-site");}
            }
        }.runTaskTimer(this,1,1);
    }
    private void begin(Bath b,Player player){
        b.hasFood=roll(Math.clamp(getConfig().getDouble("vkusnyashka.spawn-chance",.5),0,1));b.foodAmount=Math.clamp(getConfig().getInt("vkusnyashka.amount",1),1,16);b.restore=getConfig().getBoolean("banya.restore-terrain-on-remove",true);
        baths.put(b.id,b);running=b;
        after(journal.save(b),()->load(b,()->build(b,player)),e->{baths.remove(b.id);busy=false;running=null;error(player,"Не удалось записать журнал. Мир не изменён.");});
    }
    protected void load(Bath b,Runnable action){
        World w=Bukkit.getWorld(b.world);if(w==null){busy=false;running=null;return;}int[] a=b.bounds();var futures=new ArrayList<CompletableFuture<?>>();ticketWorld=w;
        for(int x=a[0]>>4;x<=a[3]>>4;x++)for(int z=a[2]>>4;z<=a[5]>>4;z++){
            final int cx=x,cz=z;futures.add(w.getChunkAtAsync(cx,cz,true).thenAccept(chunk->{if(closing)return;Bukkit.getScheduler().runTask(this,()->{if(!closing){ticket(w,cx,cz,true);tickets.add(new int[]{cx,cz});}});}));
        }
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).whenComplete((v,e)->{if(closing)return;Bukkit.getScheduler().runTask(this,()->{if(e!=null){finishJob();getLogger().log(Level.SEVERE,"Чанки бани не загружены; журнал оставлен для восстановления",e);}else action.run();});});
    }
    private void build(Bath b,Player player){
        World w=Bukkit.getWorld(b.world);
        try{
            for(var c:b.cells)if(!c.before().equals(c.after())&&!w.getBlockAt(c.x(),c.y(),c.z()).getBlockData().getAsString().equals(c.before()))throw new IllegalStateException("Участок изменился до начала строительства");
        }catch(Exception e){after(journal.delete(b.id),()->{baths.remove(b.id);finishJob();error(player,e.getMessage());},bad->{finishJob();error(player,"Журнал сохранён для восстановления.");});return;}
        apply(b,false,()->{
            try{decorate(b);if(b.hasFood){Container c=foodContainer(b);c.getInventory().setItem(0,food(b.id,b.foodAmount));}
                b.phase="ACTIVE";after(journal.save(b),()->{finishJob();decorate(b);namedMessage(player,"built",b);},e->{b.phase="BUILDING";getLogger().log(Level.SEVERE,"Не сохранено завершение бани; выполняется откат",e);rollback(b,player);});
            }catch(Exception e){getLogger().log(Level.SEVERE,"Постройка не завершена; выполняется откат",e);rollback(b,player);}
        },e->{getLogger().log(Level.SEVERE,"Постройка остановлена; выполняется откат",e);rollback(b,player);});
    }
    private void rollback(Bath b,Player player){b.restore=true;b.phase="REMOVING";after(journal.save(b),()->restore(b,player),e->{finishJob();error(player,"Изменения остановлены. Журнал сохранён; восстановление продолжится после перезапуска.");});}
    protected String encodeItem(ItemStack item){return Base64.getEncoder().encodeToString(item.serializeAsBytes());}
    protected ItemStack decodeItem(String data){return ItemStack.deserializeBytes(Base64.getDecoder().decode(data));}
    private Inventory ownInventory(Container container){return container instanceof Chest chest?chest.getBlockInventory():container.getInventory();}
    private void captureItems(Bath b){
        if(b.rescueCaptured)return;World w=Bukkit.getWorld(b.world);var saved=new ArrayList<Bath.Rescued>();
        for(var c:b.cells)if(w.getBlockAt(c.x(),c.y(),c.z()).getState() instanceof Container container){Inventory inv=ownInventory(container);for(int slot=0;slot<inv.getSize();slot++){var item=inv.getItem(slot);if(item!=null&&!item.isEmpty()&&!unclaimed(item,b.id))saved.add(new Bath.Rescued(c.x(),c.y(),c.z(),slot,encodeItem(item)));}}
        if(saved.size()>32768)throw new IllegalStateException("Слишком много вещей для одного журнала");
        b.rescued=saved;b.rescueCaptured=true;
    }
    public void remove(Bath b,Player player,boolean recovering){
        if(busy)return;if(b.name==null)b.name=nextName();if(recovering)recoveryAttempted.add(b.id);busy=true;running=b;String old=b.phase;if(recovering&&old.equals("BUILDING"))b.restore=true;b.captureLegacyClearance();b.phase="REMOVING";
        for(Player p:Bukkit.getOnlinePlayers()){Session session=listeners.get(p.getUniqueId());if(session!=null&&session.bath.equals(b.id))stop(p,true);if(topBath(p.getOpenInventory().getTopInventory())==b)p.closeInventory();}
        load(b,()->{
            try{captureItems(b);}catch(Exception e){b.phase=old;finishJob();if(player!=null)error(player,"Не удалось сохранить вещи: "+e.getMessage());return;}
            after(journal.save(b),()->restore(b,player),e->{b.phase=old;if(old.equals("ACTIVE")){b.rescueCaptured=false;b.rescued.clear();}finishJob();if(player!=null)error(player,"Не удалось сохранить журнал удаления. Мир не изменён.");});
        });
    }
    private void restore(Bath b,Player player){
        if(!b.rescueCaptured){try{captureItems(b);}catch(Exception e){finishJob();getLogger().log(Level.SEVERE,"Вещи не сохранены; восстановление приостановлено",e);return;}after(journal.save(b),()->restore(b,player),e->{finishJob();getLogger().log(Level.SEVERE,"Вещи не записаны; мир не изменён",e);});return;}
        World w=Bukkit.getWorld(b.world);var saved=new HashMap<String,String>();for(var item:b.rescued)saved.put(item.x()+":"+item.y()+":"+item.z()+":"+item.slot(),item.data());
        try{for(var entry:b.rescued)if(decodeItem(entry.data()).isEmpty())throw new IllegalStateException("Повреждены сохранённые вещи; исходные контейнеры оставлены");for(var c:b.cells)if(w.getBlockAt(c.x(),c.y(),c.z()).getState() instanceof Container container){Inventory inv=ownInventory(container);for(int slot=0;slot<inv.getSize();slot++){var item=inv.getItem(slot);if(item!=null&&!item.isEmpty()&&!unclaimed(item,b.id)&&!Objects.equals(saved.get(c.x()+":"+c.y()+":"+c.z()+":"+slot),encodeItem(item)))throw new IllegalStateException("Содержимое контейнера изменено сторонним плагином во время удаления");}}}
        catch(Exception e){finishJob();getLogger().log(Level.SEVERE,"Удаление остановлено; сохранённые вещи остаются в журнале",e);return;}
        for(Entity e:List.copyOf(w.getEntities()))if(b.id.toString().equals(e.getPersistentDataContainer().get(decorKey,PersistentDataType.STRING))||e instanceof Item item&&unclaimed(item.getItemStack(),b.id))e.remove();
        for(var c:b.cells)if(w.getBlockAt(c.x(),c.y(),c.z()).getState() instanceof Container container)ownInventory(container).clear();
        apply(b,true,()->deliverItems(b,player),e->{finishJob();getLogger().log(Level.SEVERE,"Удаление прервано; журнал сохранён для восстановления",e);});
    }
    private void deliverItems(Bath b,Player player){
        World w=Bukkit.getWorld(b.world);
        try{
            if(!b.rescueDelivered){
                var existing=new HashSet<String>();for(Entity e:w.getEntities())if(e instanceof Item){String token=e.getPersistentDataContainer().get(recoveryKey,PersistentDataType.STRING);if(token!=null)existing.add(token);}
                for(int i=0;i<b.rescued.size();i++){String token=b.id+"/"+i;if(existing.contains(token))continue;ItemStack stack=decodeItem(b.rescued.get(i).data());Item item=w.dropItem(new Location(w,b.x+.5,b.y+.65,b.z+.5),stack,e->{configureRecovered(e,true);e.getPersistentDataContainer().set(recoveryKey,PersistentDataType.STRING,token);});if(!item.isValid())throw new IllegalStateException("Другой плагин запретил сохранение выпавших вещей");}
                b.rescueDelivered=true;after(journal.save(b),()->finishRemoval(b,player),e->{b.rescueDelivered=false;finishJob();getLogger().log(Level.SEVERE,"Вещи сохранены и заблокированы до восстановления журнала",e);});
            }else finishRemoval(b,player);
        }catch(Exception e){finishJob();getLogger().log(Level.SEVERE,"Вещи остаются в журнале для повторной выдачи",e);}
    }
    private void finishRemoval(Bath b,Player player){
        after(journal.delete(b.id),()->{
            baths.remove(b.id);World w=Bukkit.getWorld(b.world);for(Entity e:w.getEntities())if(e instanceof Item item)unlockRecovered(item);
            for(Player guest:w.getPlayers())if(b.region(guest.getLocation().getBlock())){var at=guest.getLocation();int ground=w.getHighestBlockYAt(at.getBlockX(),at.getBlockZ());while(ground>w.getMinHeight()&&Sites.replaceable(w.getBlockAt(at.getBlockX(),ground,at.getBlockZ()).getType()))ground--;at.setY(ground+1.01);guest.setFallDistance(0);guest.teleport(at);}
            finishJob();if(player!=null){namedMessage(player,"removed",b);if(!b.rescued.isEmpty())player.sendMessage("§e[Z_Banya] Вещи из контейнеров сохранены без исчезновения: "+w.getName()+" "+b.x+" "+(b.y+1)+" "+b.z+".");}
        },e->{finishJob();getLogger().log(Level.SEVERE,"Территория восстановлена; журнал будет завершён при следующем запуске",e);});
    }
    protected void configureRecovered(Item item,boolean locked){item.setGravity(false);item.setVelocity(new org.bukkit.util.Vector());item.setInvulnerable(true);item.setPersistent(true);item.setUnlimitedLifetime(true);item.setCanMobPickup(false);item.setCanPlayerPickup(!locked);if(locked)item.setPickupDelay(32767);else if(item.getPickupDelay()>10)item.setPickupDelay(10);}
    private boolean recoveryLocked(Item item){String token=item.getPersistentDataContainer().get(recoveryKey,PersistentDataType.STRING);if(token==null)return false;try{Bath b=baths.get(UUID.fromString(token.split("/")[0]));return b!=null&&!b.rescueDelivered;}catch(IllegalArgumentException ignored){return true;}}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void merge(ItemMergeEvent e){if(e.getEntity().getPersistentDataContainer().has(recoveryKey,PersistentDataType.STRING)||e.getTarget().getPersistentDataContainer().has(recoveryKey,PersistentDataType.STRING))e.setCancelled(true);}
    private void unlockRecovered(Item item){String token=item.getPersistentDataContainer().get(recoveryKey,PersistentDataType.STRING);if(token==null)return;try{Bath b=baths.get(UUID.fromString(token.split("/")[0]));if(b==null||b.rescueDelivered){configureRecovered(item,false);}}catch(IllegalArgumentException ignored){}}
    private void apply(Bath b,boolean restore,Runnable done,Consumer<Throwable> fail){
        var cells=new ArrayList<>(b.cells);if(restore)Collections.reverse(cells);World w=Bukkit.getWorld(b.world);int batch=Math.clamp(getConfig().getInt("banya.blocks-per-tick",160),16,500);
        new org.bukkit.scheduler.BukkitRunnable(){int index;
            public void run(){try{
                for(int n=0;n<batch&&index<cells.size();n++,index++){
                    var c=cells.get(index);if(!restore&&c.before().equals(c.after()))continue;if(restore&&!b.restore&&c.y()<b.y)continue;Block block=w.getBlockAt(c.x(),c.y(),c.z());
                    String data=restore?c.before():c.after();
                    if(block.getBlockData().getAsString().equals(data))continue;
                    if(restore){if(block.getState() instanceof Container box&&!ownInventory(box).isEmpty())throw new IllegalStateException("В контейнер добавлены вещи; удаление остановлено");}
                    else if(!block.getBlockData().getAsString().equals(c.before()))throw new IllegalStateException("Участок изменён; строительство остановлено");
                    setBlock(block,Bukkit.createBlockData(data));
                }
                if(index>=cells.size()){cancel();done.run();}
            }catch(Throwable e){cancel();fail.accept(e);}}
        }.runTaskTimer(this,1,1);
    }
    protected void setBlock(Block block,org.bukkit.block.data.BlockData data){block.setBlockData(data,false);}
    private void release(){if(ticketWorld!=null)for(var c:tickets)ticket(ticketWorld,c[0],c[1],false);tickets.clear();ticketWorld=null;}
    private void finishJob(){release();running=null;busy=false;}
    public Container foodContainer(Bath b){World w=Bukkit.getWorld(b.world);var at=b.foodPoint();return (Container)w.getBlockAt(at[0],at[1],at[2]).getState();}
    protected void decorate(Bath b){
        World w=Bukkit.getWorld(b.world);int index=0;var existing=w.getEntities();for(Entity entity:existing)if(entity instanceof ItemFrame frame&&b.id.toString().equals(entity.getPersistentDataContainer().get(decorKey,PersistentDataType.STRING))){frame.setFixed(!b.phase.equals("ACTIVE"));frame.setInvulnerable(!b.phase.equals("ACTIVE"));frame.setItemDropChance(1);}
        if(b.modified)return;for(var d:b.decor()){final int slot=index++;
            var at=b.location(w,d.x(),d.y(),d.z());if(!w.isChunkLoaded(at.getBlockX()>>4,at.getBlockZ()>>4))continue;
            if(existing.stream().anyMatch(e->b.id.toString().equals(e.getPersistentDataContainer().get(decorKey,PersistentDataType.STRING))&&Objects.equals(slot,e.getPersistentDataContainer().get(indexKey,PersistentDataType.INTEGER))))continue;
            w.spawn(at,ItemFrame.class,e->{e.setFacingDirection(Blueprint.facing(d.face(),b.turn),true);e.setItem(new ItemStack(Material.WATER_BUCKET),false);e.setItemDropChance(1);e.setFixed(!b.phase.equals("ACTIVE"));e.setInvulnerable(!b.phase.equals("ACTIVE"));e.setPersistent(true);e.getPersistentDataContainer().set(decorKey,PersistentDataType.STRING,b.id.toString());e.getPersistentDataContainer().set(indexKey,PersistentDataType.INTEGER,slot);});
        }
    }
    @EventHandler public void chunk(ChunkLoadEvent e){Bukkit.getScheduler().runTask(this,()->{
        if(closing)return;for(Entity entity:e.getChunk().getEntities()){if(entity instanceof Item item){unlockRecovered(item);if(isFood(item.getItemStack()))droppedFood.add(item.getUniqueId());}String id=entity.getPersistentDataContainer().get(decorKey,PersistentDataType.STRING);if(id!=null&&baths.values().stream().noneMatch(b->b.id.toString().equals(id)))entity.remove();}
        for(Bath b:baths.values())if(b.phase.equals("ACTIVE")&&b.world.equals(e.getWorld().getUID())){var box=b.bounds();if(e.getChunk().getX()>=box[0]>>4&&e.getChunk().getX()<=box[3]>>4&&e.getChunk().getZ()>=box[2]>>4&&e.getChunk().getZ()<=box[5]>>4)try{decorate(b);}catch(Exception failure){getLogger().warning("Декор будет восстановлен после загрузки чанка: "+failure.getMessage());}}
    });}
    public ItemStack food(UUID origin,int count){
        var item=new ItemStack(Material.BREAD,count);item.editMeta(meta->{meta.displayName(Component.text("Вкусняшка Тома",NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD).decoration(TextDecoration.ITALIC,false));meta.lore(List.of(Component.text("Загадочное лакомство из русской бани.",NamedTextColor.GRAY),Component.text("Дарует невероятную силу,",NamedTextColor.GRAY),Component.text("но слегка кружит голову...",NamedTextColor.GRAY),Component.text("Редкий предмет • Z_Banya",NamedTextColor.DARK_GRAY)));meta.setItemModel(new NamespacedKey("zbanya","vkusnyashka"));meta.getPersistentDataContainer().set(foodKey,PersistentDataType.BYTE,(byte)1);meta.getPersistentDataContainer().set(originKey,PersistentDataType.STRING,origin.toString());meta.setMaxStackSize(16);var f=meta.getFood();f.setNutrition(6);f.setSaturation(1.2f);f.setCanAlwaysEat(true);meta.setFood(f);});return item;
    }
    private void animate(ItemStack item,Consumer<ItemStack> update){if(!isFood(item))return;var next=item.clone();next.editMeta(m->{var model=m.getCustomModelDataComponent();model.setFloats(List.of((float)((ticks/4)%8)));m.setCustomModelDataComponent(model);});if(!next.equals(item))update.accept(next);}
    public boolean isFood(ItemStack item){return item!=null&&item.getType()==Material.BREAD&&item.hasItemMeta()&&item.getItemMeta().getPersistentDataContainer().has(foodKey,PersistentDataType.BYTE);}
    private boolean unclaimed(ItemStack item,UUID origin){return isFood(item)&&origin.toString().equals(item.getItemMeta().getPersistentDataContainer().get(originKey,PersistentDataType.STRING))&&!item.getItemMeta().getPersistentDataContainer().has(claimedKey,PersistentDataType.BYTE);}
    private ItemStack claim(ItemStack item){if(!isFood(item))return item;var result=item.clone();result.editMeta(m->m.getPersistentDataContainer().set(claimedKey,PersistentDataType.BYTE,(byte)1));return result;}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void consume(PlayerItemConsumeEvent e){if(isFood(e.getItem()))effects(e.getPlayer());}
    public void effects(Player p){
        effect(p,PotionEffectType.REGENERATION,1,"regeneration-seconds",10);effect(p,PotionEffectType.ABSORPTION,2,"absorption-seconds",120);effect(p,PotionEffectType.RESISTANCE,0,"resistance-seconds",60);effect(p,PotionEffectType.FIRE_RESISTANCE,0,"fire-resistance-seconds",120);effect(p,PotionEffectType.NAUSEA,0,"nausea-seconds",8);
    }
    private void effect(Player p,PotionEffectType type,int amplifier,String option,int fallback){int duration=Math.clamp(getConfig().getInt("vkusnyashka."+option,fallback),0,3600)*20;if(duration>0)p.addPotionEffect(new PotionEffect(type,duration,amplifier,false,true,true));}
    Bath topBath(Inventory inventory){if(inventory==null)return null;Object holder=inventory.getHolder();if(holder instanceof BlockState b)return region(b.getBlock());if(holder instanceof DoubleChest chest){if(chest.getLeftSide() instanceof BlockState left&&region(left.getBlock())!=null)return region(left.getBlock());if(chest.getRightSide() instanceof BlockState right)return region(right.getBlock());}return null;}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void inventory(InventoryClickEvent e){Bath b=topBath(e.getView().getTopInventory());if(b!=null&&!b.phase.equals("ACTIVE")){e.setCancelled(true);return;}if(isFood(e.getCurrentItem()))e.setCurrentItem(claim(e.getCurrentItem()));}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void drag(InventoryDragEvent e){Bath b=topBath(e.getView().getTopInventory());if(b!=null&&!b.phase.equals("ACTIVE"))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void transfer(InventoryMoveItemEvent e){Bath from=topBath(e.getSource()),to=topBath(e.getDestination());if(from!=null&&!from.phase.equals("ACTIVE")||to!=null&&!to.phase.equals("ACTIVE")||from!=null&&isFood(e.getItem()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void spawnFood(ItemSpawnEvent e){if(isFood(e.getEntity().getItemStack()))droppedFood.add(e.getEntity().getUniqueId());}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void pickup(EntityPickupItemEvent e){if(recoveryLocked(e.getItem())){e.setCancelled(true);return;}if(e.getEntity() instanceof Player&&isFood(e.getItem().getItemStack()))e.getItem().setItemStack(claim(e.getItem().getItemStack()));}
    @EventHandler(priority=EventPriority.HIGHEST) public void dropped(PlayerDropItemEvent e){if(isFood(e.getItemDrop().getItemStack()))e.getItemDrop().setItemStack(claim(e.getItemDrop().getItemStack()));}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void breakBlock(BlockBreakEvent e){if(locked(e.getBlock()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void place(BlockPlaceEvent e){if(locked(e.getBlock()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void editedBlock(BlockBreakEvent e){Bath b=region(e.getBlock());if(b==null||!b.phase.equals("ACTIVE"))return;edited(b);if(e.getBlock().getState() instanceof Container box){Inventory inv=ownInventory(box);for(int i=0;i<inv.getSize();i++)if(isFood(inv.getItem(i)))inv.setItem(i,claim(inv.getItem(i)));}}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void editedBlock(BlockPlaceEvent e){edited(region(e.getBlock()));}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void hopperPickup(InventoryPickupItemEvent e){if(recoveryLocked(e.getItem())){e.setCancelled(true);return;}Bath b=topBath(e.getInventory());if(b!=null&&!b.phase.equals("ACTIVE"))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void interact(PlayerInteractEvent e){if(e.getClickedBlock()==null)return;Bath b=region(e.getClickedBlock());if(b==null)return;if(!b.phase.equals("ACTIVE")||e.getClickedBlock().getType()==Material.BLAST_FURNACE||e.getMaterial()==Material.LAVA_BUCKET||e.getMaterial()==Material.FLINT_AND_STEEL||e.getMaterial()==Material.FIRE_CHARGE)e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void frame(PlayerInteractEntityEvent e){Bath b=decorBath(e.getRightClicked());if(b!=null){if(!b.phase.equals("ACTIVE"))e.setCancelled(true);else edited(b);}}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void hanging(HangingBreakEvent e){Bath b=decorBath(e.getEntity());if(b!=null){if(!b.phase.equals("ACTIVE"))e.setCancelled(true);else edited(b);}}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void hurt(EntityDamageEvent e){Bath b=decorBath(e.getEntity());if(b!=null){if(!b.phase.equals("ACTIVE"))e.setCancelled(true);else edited(b);}else if(e.getEntity() instanceof Player p&&(e.getCause()==EntityDamageEvent.DamageCause.HOT_FLOOR||e.getCause()==EntityDamageEvent.DamageCause.CAMPFIRE)&&inside(p.getLocation())!=null)e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void explode(EntityExplodeEvent e){e.blockList().removeIf(b->region(b)!=null);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void explodeBlock(BlockExplodeEvent e){e.blockList().removeIf(b->region(b)!=null);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void ignite(BlockIgniteEvent e){if(region(e.getBlock())!=null)e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void burn(BlockBurnEvent e){if(region(e.getBlock())!=null)e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void flow(BlockFromToEvent e){if(region(e.getBlock())!=null||region(e.getToBlock())!=null)e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void piston(BlockPistonExtendEvent e){if(region(e.getBlock().getRelative(e.getDirection()))!=null||e.getBlocks().stream().anyMatch(b->region(b)!=null||region(b.getRelative(e.getDirection()))!=null||region(b.getRelative(e.getDirection().getOppositeFace()))!=null))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void pistonBack(BlockPistonRetractEvent e){if(region(e.getBlock().getRelative(e.getDirection()))!=null||e.getBlocks().stream().anyMatch(b->region(b)!=null||region(b.getRelative(e.getDirection()))!=null||region(b.getRelative(e.getDirection().getOppositeFace()))!=null))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void grow(StructureGrowEvent e){if(e.getBlocks().stream().anyMatch(b->region(b.getBlock())!=null))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void bucket(PlayerBucketEmptyEvent e){if(region(e.getBlock())!=null)e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void change(EntityChangeBlockEvent e){if(region(e.getBlock())!=null)e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void fertilize(BlockFertilizeEvent e){if(e.getBlocks().stream().anyMatch(b->region(b.getBlock())!=null))e.setCancelled(true);}
}
