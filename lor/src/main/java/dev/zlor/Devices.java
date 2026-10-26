package dev.zlor;

import dev.zlor.rules.*;
import java.util.*;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.attribute.*;
import org.bukkit.block.*;
import org.bukkit.block.data.AnaloguePowerable;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;
import org.joml.Quaternionf;

/** Registered full-block carriers; no client mod and no name-based item identity. */
public final class Devices implements Listener {
    private final ZLorPlugin p;
    private final Map<String,UUID> positions=new HashMap<>();
    private final Map<UUID,ItemDisplay> displays=new HashMap<>();
    private final Map<UUID,FuelMenu> menus=new HashMap<>();
    private final Map<UUID,Integer> outputs=new HashMap<>();
    private final Set<UUID> busy=new HashSet<>(),busyPlayers=new HashSet<>();
    private final Map<UUID,UUID> selections=new HashMap<>();
    private final NamespacedKey displayKey=new NamespacedKey("z_lor","device_model");
    private final NamespacedKey miningKey=new NamespacedKey("z_lor","device_mining");
    private final Map<UUID,State.Pos> mining=new HashMap<>();
    private long indexedRevision=-1;
    private long shieldRevision=-1;
    private final Map<String,Domes.Index> shieldIndices=new HashMap<>();
    private final Map<String,List<Domes.Sphere>> shieldSpheres=new HashMap<>();
    private final Map<Domes.Sphere,UUID> sphereOwners=new IdentityHashMap<>();
    private boolean closing;
    public Devices(ZLorPlugin p){this.p=p;}
    public State.Pos pos(Block block){return new State.Pos(p.worldKey(block.getWorld()),block.getX(),block.getY(),block.getZ());}
    public Block block(State.Device d){World world=p.world(d.pos.world());return world==null?null:world.getBlockAt(d.pos.x(),d.pos.y(),d.pos.z());}
    public Location center(State.Device d){World world=p.world(d.pos.world());return world==null?null:new Location(world,d.pos.x()+.5,d.pos.y()+.5,d.pos.z()+.5);}
    public void index(){
        if(indexedRevision==p.state().revision)return;
        positions.clear();p.state().devices.forEach((id,d)->positions.put(d.pos.key(),id));indexedRevision=p.state().revision;
    }
    public State.Device at(Block block){index();var id=positions.get(pos(block).key());return p.state().devices.get(id);}
    public boolean loaded(State.Device d){World world=p.world(d.pos.world());return world!=null&&world.isChunkLoaded(d.pos.x()>>4,d.pos.z()>>4);}
    public List<State.Device> loaded(){return p.state().devices.values().stream().filter(this::loaded).toList();}
    public List<Domes.Sphere> spheres(World world){
        rebuildShields();return shieldSpheres.getOrDefault(p.worldKey(world),List.of()).stream().filter(this::sphereActive).toList();
    }
    private void rebuildShields(){
        if(shieldRevision==p.state().revision)return;shieldRevision=p.state().revision;shieldIndices.clear();shieldSpheres.clear();sphereOwners.clear();
        for(var d:p.state().devices.values())if(d.kind.equals("void_absorber")){
            var sphere=new Domes.Sphere(d.pos.x()+.5,d.pos.y()+.5,d.pos.z()+.5,50,Domes.Kind.ABSORBER);
            shieldSpheres.computeIfAbsent(d.pos.world(),key->new ArrayList<>()).add(sphere);sphereOwners.put(sphere,d.id);
        }
        shieldSpheres.forEach((key,list)->shieldIndices.put(key,new Domes.Index(list)));
    }
    private boolean sphereActive(Domes.Sphere sphere){var d=p.state().devices.get(sphereOwners.get(sphere));return d!=null&&d.active(p.fuelNow())&&loaded(d);}
    public List<Domes.Sphere> nearSpheres(Location at){
        rebuildShields();var index=shieldIndices.get(p.worldKey(at.getWorld()));return index==null?List.of():index.near(at.getX(),at.getZ()).stream().filter(this::sphereActive).toList();
    }
    public boolean protectedAt(Location location){
        if(location.getWorld()==null)return false;
        rebuildShields();var index=shieldIndices.get(p.worldKey(location.getWorld()));if(index==null)return false;
        for(var sphere:index.near(location.getX(),location.getZ()))if(sphere.contains(location.getX(),location.getY(),location.getZ())&&sphereActive(sphere))return true;
        return false;
    }
    public int power(UUID id){return outputs.getOrDefault(id,0);}
    public boolean busy(UUID id){return busy.contains(id);}
    public boolean busyPlayer(UUID id){return busyPlayers.contains(id);}
    public void refresh(State.Device d){
        if(closing||d==null||!loaded(d))return;Block block=block(d);if(block.getType()!=Material.TARGET)return;
        var display=displays.get(d.id);
        if(display==null||!display.isValid()){
            display=block.getWorld().spawn(center(d),ItemDisplay.class,e->{
                e.setPersistent(false);e.setInvulnerable(true);e.setGravity(false);e.setSilent(true);
                e.getPersistentDataContainer().set(displayKey,PersistentDataType.STRING,d.id.toString());
                e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                e.setTransformation(new Transformation(new Vector3f(),new Quaternionf().rotateY((float)Math.toRadians(-d.yaw)),new Vector3f(1.002f),new Quaternionf()));
                e.setViewRange(1.5f);e.setTeleportDuration(0);e.setDisplayWidth(1);e.setDisplayHeight(1);
            });displays.put(d.id,display);
        }
        var item=p.items.make(d.kind);
        boolean lit=power(d.id)>0||d.active(p.fuelNow());
        item.editMeta(m->m.setItemModel(new NamespacedKey("z_lor","world_"+d.kind+(lit?"_lit":""))));
        if(!Objects.equals(display.getItemStack(),item))display.setItemStack(item);
        if(d.kind.equals("void_absorber")||d.kind.equals("magnetite_core"))display.setBrightness(new Display.Brightness(d.active(p.fuelNow())||power(d.id)>0?12:0,15));
        var menu=menus.get(d.id);if(menu!=null)menu.render(d);
    }
    private void eraseVisual(UUID id){var display=displays.remove(id);if(display!=null)display.remove();var menu=menus.remove(id);if(menu!=null)for(var viewer:List.copyOf(menu.inventory.getViewers()))viewer.closeInventory();outputs.remove(id);}

    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void place(BlockPlaceEvent event){
        String kind=p.items.id(event.getItemInHand());if(!Items.BLOCKS.contains(kind==null?"":kind))return;
        event.setCancelled(true);Player player=event.getPlayer();var target=pos(event.getBlockPlaced());
        if(busyPlayers.contains(player.getUniqueId()))return;
        if(!event.getBlockReplacedState().getType().isAir())return;
        if(p.state().devices.size()>=p.getConfig().getInt("safety.max-devices",4096)){p.error(player,new IllegalArgumentException("Достигнут лимит устройств"));return;}
        ItemStack original=event.getItemInHand().clone();EquipmentSlot hand=event.getHand();
        var device=new State.Device(kind,target,Math.round(player.getLocation().getYaw()/90f)*90f);
        try{p.items.restore(original,device,p.fuelNow());}catch(IllegalArgumentException e){p.error(player,e);return;}
        busyPlayers.add(player.getUniqueId());
        p.store.change(s->{
            if(s.devices.values().stream().anyMatch(d->d.pos.equals(target)))throw new IllegalArgumentException("Место уже занято устройством");
            s.devices.put(device.id,device);if(p.roles.scen(player))p.roles.audit(s,player,"предмет","Установлено: "+Items.NAMES.get(kind),target.world());
        },s->{
            busyPlayers.remove(player.getUniqueId());var current=player.getInventory().getItem(hand);Block block=block(device);
            if(!player.isOnline()||player.isDead()||!current.equals(original)||block==null||!block.getType().isAir()){
                p.store.change(next->next.devices.remove(device.id));return;
            }
            block.setType(Material.TARGET,false);AnaloguePowerable data=(AnaloguePowerable)block.getBlockData();data.setPower(0);block.setBlockData(data,false);
            if(player.getGameMode()!=GameMode.CREATIVE)current.subtract(1);
            refresh(s.devices.get(device.id));
        },e->{busyPlayers.remove(player.getUniqueId());p.error(player,e);});
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void interact(PlayerInteractEvent event){
        if(event.getClickedBlock()==null||!event.getAction().isRightClick()||event.getHand()!=EquipmentSlot.HAND)return;
        var device=at(event.getClickedBlock());if(device==null)return;
        Player player=event.getPlayer();if(player.getGameMode()==GameMode.SPECTATOR)return;
        if(player.isSneaking()&&event.getItem()!=null&&Items.BLOCKS.contains(p.items.id(event.getItem())==null?"":p.items.id(event.getItem())))return;
        event.setCancelled(true);
        if(device.kind.equals("magnetite_core")){
            if(player.isSneaking()&&event.getItem()!=null&&event.getItem().getType()==Material.AMETHYST_SHARD)link(player,device,event.getHand());
            return;
        }
        if(device.kind.equals("void_absorber")){var menu=menus.computeIfAbsent(device.id,FuelMenu::new);menu.render(device);player.openInventory(menu.inventory);}
        else p.menus.device(player,device);
    }
    private void link(Player player,State.Device device,EquipmentSlot hand){
        UUID first=selections.get(player.getUniqueId());
        if(first==null){selections.put(player.getUniqueId(),device.id);player.sendActionBar(Component.text("Выбрано первое ядро"));return;}
        selections.remove(player.getUniqueId());if(first.equals(device.id)){player.sendActionBar(Component.text("Выбор отменён"));return;}
        var a=p.state().devices.get(first);
        if(a==null||!a.kind.equals("magnetite_core")||!a.pos.world().equals(device.pos.world())){p.error(player,new IllegalArgumentException("Нужны два ядра одного измерения"));return;}
        if(busyPlayers.contains(player.getUniqueId()))return;
        ItemStack original=player.getInventory().getItem(hand).clone();busyPlayers.add(player.getUniqueId());
        p.store.change(s->{
            var left=s.devices.get(first);var right=s.devices.get(device.id);
            if(left==null||right==null||left.peer!=null||right.peer!=null)throw new IllegalArgumentException("Ядро уже связано или удалено");
            left.peer=right.id;right.peer=left.id;left.linkPending=true;right.linkPending=true;
        },s->{
            busyPlayers.remove(player.getUniqueId());var current=player.getInventory().getItem(hand);
            if(!player.isOnline()||player.isDead()||!current.equals(original)){
                p.store.change(next->{var left=next.devices.get(first);var right=next.devices.get(device.id);if(left!=null){left.peer=null;left.linkPending=false;}if(right!=null){right.peer=null;right.linkPending=false;}});return;
            }
            if(player.getGameMode()!=GameMode.CREATIVE)current.subtract(1);
            boolean wasScen=p.roles.scen(player);
            p.store.change(next->{var left=next.devices.get(first);var right=next.devices.get(device.id);if(left!=null&&right!=null&&device.id.equals(left.peer)&&first.equals(right.peer)){left.linkPending=false;right.linkPending=false;if(wasScen)p.roles.audit(next,player,"предмет","Соединены два магнетитовых ядра",device.pos.world());}},next->player.sendActionBar(Component.text("Ядра соединены")),e->p.error(player,e));
        },e->{busyPlayers.remove(player.getUniqueId());p.error(player,e);});
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void breakBlock(BlockBreakEvent event){
        var device=at(event.getBlock());if(device==null)return;
        event.setCancelled(true);if(!busy.add(device.id))return;
        Player player=event.getPlayer();boolean drops=player.getGameMode()!=GameMode.CREATIVE&&player.getInventory().getItemInMainHand().getType().name().endsWith("_PICKAXE");
        p.store.change(s->{var removed=s.devices.remove(device.id);if(removed!=null&&removed.peer!=null){var peer=s.devices.get(removed.peer);if(peer!=null)peer.peer=null;}},s->{
            busy.remove(device.id);Block block=block(device);if(block!=null&&block.getType()==Material.TARGET){
                block.setType(Material.AIR,true);if(drops){block.getWorld().dropItemNaturally(center(device),p.items.portable(device,p.fuelNow()));dropContents(device);}
            }
            eraseVisual(device.id);p.redstone.removed(device.id);
        },e->{busy.remove(device.id);p.error(player,e);});
    }
    private void dropContents(State.Device d){
        var location=center(d);if(location==null)return;
        for(int count:d.fuel)if(count>0){var item=p.items.make("liquid_void");item.setAmount(count);location.getWorld().dropItemNaturally(location,item);}
        if(d.bottles>0)location.getWorld().dropItemNaturally(location,new ItemStack(Material.GLASS_BOTTLE,d.bottles));
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void piston(BlockPistonExtendEvent e){if(e.getBlocks().stream().anyMatch(b->at(b)!=null))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void piston(BlockPistonRetractEvent e){if(e.getBlocks().stream().anyMatch(b->at(b)!=null))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void explosion(EntityExplodeEvent e){explode(e.blockList());}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void explosion(BlockExplodeEvent e){explode(e.blockList());}
    private void explode(List<Block> blocks){
        for(var iterator=blocks.iterator();iterator.hasNext();){var b=iterator.next();var d=at(b);if(d==null)continue;
            // Defer custom drops until its record is durable; native explosion must not also drop the carrier.
            iterator.remove();if(!busy.add(d.id))continue;
            p.store.change(s->{s.devices.remove(d.id);if(d.peer!=null){var peer=s.devices.get(d.peer);if(peer!=null)peer.peer=null;}},s->{
                busy.remove(d.id);if(b.getType()==Material.TARGET){b.setType(Material.AIR,true);b.getWorld().dropItemNaturally(center(d),p.items.portable(d,p.fuelNow()));dropContents(d);}eraseVisual(d.id);p.redstone.removed(d.id);
            },e->busy.remove(d.id));
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void redstone(BlockRedstoneEvent event){var d=at(event.getBlock());if(d!=null)event.setNewCurrent(power(d.id));}
    public void output(State.Device device,int power){
        power=Math.clamp(power,0,15);int old=power(device.id);outputs.put(device.id,power);if(!loaded(device))return;
        var block=block(device);if(block.getBlockData() instanceof AnaloguePowerable target&&target.getPower()!=power){
            target.setPower(power);block.setBlockData(target,true);
        }
        if(old!=power)refresh(device);
    }
    public void tick(){
        index();
        for(var entry:new ArrayList<>(displays.entrySet()))if(!p.state().devices.containsKey(entry.getKey()))eraseVisual(entry.getKey());
        for(var device:p.state().devices.values()){
            if(!loaded(device)){eraseVisual(device.id);continue;}
            if(block(device).getType()!=Material.TARGET){
                eraseVisual(device.id);
                if(busy.add(device.id))p.store.change(s->{s.devices.remove(device.id);if(device.peer!=null){var peer=s.devices.get(device.peer);if(peer!=null)peer.peer=null;}},s->busy.remove(device.id),e->busy.remove(device.id));
                continue;
            }
            if(p.tick%20==0)refresh(device);
            switch(device.kind){
                case "void_absorber"->{
                    if(p.tick%20==0&&!device.active(p.fuelNow())&&device.reserves()>0&&p.voids.height(p.world(device.pos.world()))>p.voids.nativeY(p.world(device.pos.world())))beginFuel(device);
                    output(device,AlarmSettings.lowFuel(device.remaining(p.fuelNow()),device.reserves())?15:0);
                    if(p.tick%8==0)hopper(device);
                }
                case "void_alarm"->{
                    var settings=new AlarmSettings(device.warning,device.danger);double distance=device.pos.y()+.5-p.voids.height(p.world(device.pos.world()));
                    boolean safe=protectedAt(center(device));int value=0;
                    if(!safe&&settings.warning(distance))value=device.mode==AlarmSettings.PULSE?(p.now()%settings.interval(distance)<100?15:0):settings.strength(distance);
                    output(device,value);
                }
                case "void_siren"->p.audio.siren(device,p.redstone.externalInput(device)>0);
            }
        }
        updateMining();
    }
    private void beginFuel(State.Device device){
        if(!busy.add(device.id))return;boolean overflow=device.bottles>=64;
        p.store.change(s->{
            var current=s.devices.get(device.id);if(current==null||current.active(p.fuelNow())||current.reserves()==0||!loaded(current))throw new IllegalArgumentException("Порция больше не нужна");
            int slot=0;while(current.fuel[slot]==0)slot++;current.fuel[slot]--;current.fuelUntil=p.fuelNow()+AbsorberTiming.BOTTLE_MILLIS;
            if(!overflow)current.bottles++;
        },s->{busy.remove(device.id);if(overflow&&loaded(device))center(device).getWorld().dropItem(center(device).add(0,1,0),new ItemStack(Material.GLASS_BOTTLE));refresh(s.devices.get(device.id));},e->busy.remove(device.id));
    }
    public void settings(Player player,UUID id,java.util.function.Consumer<State.Device> change){
        if(!busy.add(id))return;
        p.store.change(s->{var device=s.devices.get(id);if(device==null||!canUse(player,device))throw new IllegalArgumentException("Устройство недоступно");change.accept(device);
            new AlarmSettings(device.warning,device.danger);if(p.roles.scen(player))p.roles.audit(s,player,"меню","Настроено: "+Items.NAMES.get(device.kind),device.pos.world());
        },s->{busy.remove(id);var device=s.devices.get(id);refresh(device);p.audio.stopDevice(id);p.menus.device(player,device);},e->{busy.remove(id);p.error(player,e);});
    }
    public boolean canUse(Player player,State.Device device){var c=center(device);return player.isOnline()&&!player.isDead()&&player.getGameMode()!=GameMode.SPECTATOR&&c!=null&&c.getWorld().equals(player.getWorld())&&c.distanceSquared(player.getLocation())<=64&&loaded(device)&&block(device).getType()==Material.TARGET;}

    private final class FuelMenu implements InventoryHolder {
        final UUID id;final Inventory inventory;
        FuelMenu(UUID id){this.id=id;inventory=Bukkit.createInventory(this,9,Component.text("Поглотитель Войда · топливо / тара"));}
        public Inventory getInventory(){return inventory;}
        void slot(int slot,ItemStack value){if(!Objects.equals(inventory.getItem(slot),value))inventory.setItem(slot,value);}
        void render(State.Device d){
            for(int i=0;i<8;i++){ItemStack stack=d.fuel[i]==0?null:p.items.make("liquid_void");if(stack!=null)stack.setAmount(d.fuel[i]);slot(i,stack);}
            slot(8,d.bottles==0?null:new ItemStack(Material.GLASS_BOTTLE,d.bottles));
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void click(InventoryClickEvent event){
        if(!(event.getWhoClicked() instanceof Player player))return;
        if(busyPlayers.contains(player.getUniqueId())){event.setCancelled(true);return;}
        if(!(event.getView().getTopInventory().getHolder() instanceof FuelMenu menu))return;
        // Ordinary pickup/place in the player's inventory must remain native.
        // Collect-to-cursor can cross the protected top inventory, so it stays guarded.
        if(event.getRawSlot()>=9&&!event.isShiftClick()&&event.getAction()!=InventoryAction.COLLECT_TO_CURSOR)return;
        event.setCancelled(true);var d=p.state().devices.get(menu.id);if(d==null||busy.contains(menu.id)||!canUse(player,d))return;
        if(event.getRawSlot()>=0&&event.getRawSlot()<9&&event.getClick()==ClickType.NUMBER_KEY){
            int hotbar=event.getHotbarButton(),slot=event.getRawSlot();if(hotbar<0||hotbar>8)return;
            ItemStack incoming=player.getInventory().getItem(hotbar);boolean empty=incoming==null||incoming.isEmpty();
            if(!empty&&(slot==8||!p.items.is(incoming,"liquid_void")||incoming.getAmount()>16))return;
            ItemStack outgoing=menu.inventory.getItem(slot);int amount=empty?0:incoming.getAmount();
            fuelChange(player,d,x->{if(slot==8)x.bottles=amount;else x.fuel[slot]=amount;},()->player.getInventory().setItem(hotbar,outgoing==null?null:outgoing.clone()));return;
        }
        if(event.getClick()!=ClickType.LEFT&&event.getClick()!=ClickType.RIGHT&&!event.isShiftClick())return;
        int slot=event.getRawSlot();if(slot<0)return;
        ItemStack cursor=player.getItemOnCursor().clone();
        if(slot<9){
            if(event.isShiftClick()){
                ItemStack item=menu.inventory.getItem(slot);if(item==null||!p.items.capacity(player.getInventory(),item))return;
                final int amount=item.getAmount();fuelChange(player,d,x->{if(slot==8)x.bottles=0;else x.fuel[slot]=0;},()->player.getInventory().addItem(item.clone()));return;
            }
            int count=slot==8?d.bottles:d.fuel[slot];
            if(cursor.isEmpty()){
                if(count==0)return;int take=event.getClick()==ClickType.RIGHT?(count+1)/2:count;
                var result=slot==8?new ItemStack(Material.GLASS_BOTTLE,take):p.items.make("liquid_void");result.setAmount(take);
                fuelChange(player,d,x->{if(slot==8)x.bottles-=take;else x.fuel[slot]-=take;},()->player.setItemOnCursor(result));
            }else if(slot<8&&p.items.is(cursor,"liquid_void")){
                int add=Math.min(16-count,event.getClick()==ClickType.RIGHT?1:cursor.getAmount());if(add<=0)return;
                fuelChange(player,d,x->x.fuel[slot]+=add,()->{cursor.subtract(add);player.setItemOnCursor(cursor);});
            }
        }else if(event.isShiftClick()){
            var item=event.getCurrentItem();if(!p.items.is(item,"liquid_void"))return;
            int original=item.getAmount(),remaining=original;int[] replacement=d.fuel.clone();
            for(int i=0;i<8&&remaining>0;i++){int add=Math.min(remaining,16-replacement[i]);replacement[i]+=add;remaining-=add;}
            if(remaining==original)return;final int rest=remaining;var source=event.getClickedInventory();int local=event.getSlot();
            fuelChange(player,d,x->x.fuel=replacement,()->{var next=item.clone();next.setAmount(rest);source.setItem(local,rest==0?null:next);});
        }
    }
    private void fuelChange(Player player,State.Device d,java.util.function.Consumer<State.Device> mutation,Runnable effect){
        if(!busy.add(d.id))return;busyPlayers.add(player.getUniqueId());
        ItemStack[] inventory=Arrays.stream(player.getInventory().getContents()).map(item->item==null?null:item.clone()).toArray(ItemStack[]::new);ItemStack cursor=player.getItemOnCursor().clone();
        int[] oldFuel=d.fuel.clone();int oldBottles=d.bottles;
        p.store.change(s->{var current=s.devices.get(d.id);if(current==null||!canUse(player,current))throw new IllegalArgumentException("Устройство недоступно");mutation.accept(current);},s->{
            if(!canUse(player,d)||!Arrays.equals(inventory,player.getInventory().getContents())||!Objects.equals(cursor,player.getItemOnCursor())){
                p.store.change(next->{var current=next.devices.get(d.id);if(current!=null){current.fuel=oldFuel;current.bottles=oldBottles;}},next->{busy.remove(d.id);busyPlayers.remove(player.getUniqueId());refresh(next.devices.get(d.id));},e->{busy.remove(d.id);busyPlayers.remove(player.getUniqueId());});return;
            }
            busy.remove(d.id);busyPlayers.remove(player.getUniqueId());effect.run();refresh(s.devices.get(d.id));
        },e->{busy.remove(d.id);busyPlayers.remove(player.getUniqueId());p.error(player,e);});
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void drag(InventoryDragEvent event){
        if(busyPlayers.contains(event.getWhoClicked().getUniqueId())){event.setCancelled(true);return;}
        if(!(event.getView().getTopInventory().getHolder() instanceof FuelMenu menu)||event.getRawSlots().stream().allMatch(slot->slot>=9))return;
        event.setCancelled(true);
        if(!(event.getWhoClicked() instanceof Player player)||!p.items.is(event.getOldCursor(),"liquid_void")||event.getRawSlots().contains(8))return;
        var oldCursor=event.getOldCursor().clone();var newCursor=event.getCursor()==null?new ItemStack(Material.AIR):event.getCursor().clone();
        var replacements=new HashMap<Integer,ItemStack>();event.getNewItems().forEach((slot,item)->replacements.put(slot,item.clone()));
        var initial=p.state().devices.get(menu.id);if(initial==null||busy.contains(menu.id))return;int[] before=initial.fuel.clone();
        for(var entry:replacements.entrySet())if(!p.items.is(entry.getValue(),"liquid_void")||entry.getValue().getAmount()>16)return;
        Bukkit.getScheduler().runTask(p,()->{
            var d=p.state().devices.get(menu.id);
            if(d==null||busyPlayer(player.getUniqueId())||busy.contains(d.id)||!canUse(player,d)||player.getOpenInventory().getTopInventory()!=menu.inventory||!Arrays.equals(before,d.fuel)||!Objects.equals(oldCursor,player.getItemOnCursor()))return;
            fuelChange(player,d,x->{for(var entry:replacements.entrySet())if(entry.getKey()<8)x.fuel[entry.getKey()]=entry.getValue().getAmount();},()->{
                replacements.forEach((raw,item)->{if(raw>=9){int local=player.getOpenInventory().convertSlot(raw);player.getInventory().setItem(local,item.clone());}});player.setItemOnCursor(newCursor);
            });
        });
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void drop(PlayerDropItemEvent event){if(busyPlayers.contains(event.getPlayer().getUniqueId()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void pickup(EntityPickupItemEvent event){if(busyPlayers.contains(event.getEntity().getUniqueId()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void held(PlayerItemHeldEvent event){if(busyPlayers.contains(event.getPlayer().getUniqueId()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void swap(PlayerSwapHandItemsEvent event){if(busyPlayers.contains(event.getPlayer().getUniqueId()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void command(PlayerCommandPreprocessEvent event){if(busyPlayers.contains(event.getPlayer().getUniqueId()))event.setCancelled(true);}
    private void hopper(State.Device d){
        if(busy.contains(d.id))return;
        Block block=block(d);
        for(BlockFace face:List.of(BlockFace.UP,BlockFace.NORTH,BlockFace.SOUTH,BlockFace.EAST,BlockFace.WEST)){
            var neighbor=block.getRelative(face);
            if(!(neighbor.getState() instanceof Hopper hopper)||!(neighbor.getBlockData() instanceof org.bukkit.block.data.type.Hopper data)||!data.isEnabled())continue;
            if(!neighbor.getRelative(data.getFacing()).equals(block))continue;
            for(int source=0;source<hopper.getInventory().getSize();source++){
                ItemStack item=hopper.getInventory().getItem(source);if(!p.items.is(item,"liquid_void"))continue;
                int slot=0;while(slot<8&&d.fuel[slot]>=16)slot++;if(slot==8)return;
                final int target=slot,index=source;var snapshot=item.clone();var one=item.asOne();
                var destination=menus.computeIfAbsent(d.id,FuelMenu::new);var event=new InventoryMoveItemEvent(hopper.getInventory(),one,destination.inventory,true);Bukkit.getPluginManager().callEvent(event);
                if(event.isCancelled()||!event.getItem().equals(one))return;
                if(!busy.add(d.id))return;
                p.store.change(s->s.devices.get(d.id).fuel[target]++,s->{
                    busy.remove(d.id);if(Objects.equals(hopper.getInventory().getItem(index),snapshot)){snapshot.subtract(1);hopper.getInventory().setItem(index,snapshot);}
                    else p.store.change(next->{var current=next.devices.get(d.id);if(current!=null)current.fuel[target]--;});refresh(s.devices.get(d.id));
                },e->busy.remove(d.id));return;
            }
        }
        Block below=block.getRelative(BlockFace.DOWN);
        if(d.bottles>0&&below.getState() instanceof Hopper hopper&&below.getBlockData() instanceof org.bukkit.block.data.type.Hopper data&&data.isEnabled()){
            var bottle=new ItemStack(Material.GLASS_BOTTLE);if(!p.items.capacity(hopper.getInventory(),bottle))return;
            var source=menus.computeIfAbsent(d.id,FuelMenu::new);var event=new InventoryMoveItemEvent(source.inventory,bottle,hopper.getInventory(),false);Bukkit.getPluginManager().callEvent(event);
            if(event.isCancelled()||!event.getItem().equals(bottle)||!busy.add(d.id))return;
            p.store.change(s->s.devices.get(d.id).bottles--,s->{busy.remove(d.id);if(p.items.capacity(hopper.getInventory(),bottle))hopper.getInventory().addItem(bottle);else p.store.change(next->{var current=next.devices.get(d.id);if(current!=null)current.bottles++;});refresh(s.devices.get(d.id));},e->busy.remove(d.id));
        }
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void damageBlock(BlockDamageEvent event){var d=at(event.getBlock());if(d!=null){mining.put(event.getPlayer().getUniqueId(),d.pos);applyMining(event.getPlayer(),event.getBlock(),d);}}
    private void applyMining(Player player,Block block,State.Device d){
        var attribute=player.getAttribute(Attribute.BLOCK_BREAK_SPEED);if(attribute==null)return;
        attribute.removeModifier(miningKey);
        double hardness=d.kind.equals("magnetite_core")?5:d.kind.equals("void_absorber")?4:3;
        double ratio=NativeSupport.miningRatio(player,block,hardness);
        attribute.addTransientModifier(new AttributeModifier(miningKey,ratio-1,AttributeModifier.Operation.MULTIPLY_SCALAR_1));
    }
    private void updateMining(){
        for(var iterator=mining.entrySet().iterator();iterator.hasNext();){
            var entry=iterator.next();Player player=Bukkit.getPlayer(entry.getKey());
            Block target=player==null?null:player.getTargetBlockExact(6);var d=target==null?null:at(target);
            if(player==null||d==null||!d.pos.equals(entry.getValue())){if(player!=null&&player.getAttribute(Attribute.BLOCK_BREAK_SPEED)!=null)player.getAttribute(Attribute.BLOCK_BREAK_SPEED).removeModifier(miningKey);iterator.remove();}
        }
    }
    @EventHandler public void unload(ChunkUnloadEvent event){for(var d:p.state().devices.values())if(d.pos.world().equals(p.worldKey(event.getWorld()))&&(d.pos.x()>>4)==event.getChunk().getX()&&(d.pos.z()>>4)==event.getChunk().getZ())eraseVisual(d.id);}
    @EventHandler public void load(ChunkLoadEvent event){for(var d:p.state().devices.values())if(d.pos.world().equals(p.worldKey(event.getWorld()))&&(d.pos.x()>>4)==event.getChunk().getX()&&(d.pos.z()>>4)==event.getChunk().getZ()){outputs.put(d.id,0);refresh(d);}}
    public void leave(UUID id){selections.remove(id);busyPlayers.remove(id);mining.remove(id);Player player=Bukkit.getPlayer(id);if(player!=null){var attribute=player.getAttribute(Attribute.BLOCK_BREAK_SPEED);if(attribute!=null)attribute.removeModifier(miningKey);}}
    public void close(){closing=true;for(var d:loaded())output(d,0);for(var id:new ArrayList<>(displays.keySet()))eraseVisual(id);for(Player player:Bukkit.getOnlinePlayers()){var a=player.getAttribute(Attribute.BLOCK_BREAK_SPEED);if(a!=null)a.removeModifier(miningKey);}}
}
