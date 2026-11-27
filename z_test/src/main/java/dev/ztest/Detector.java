package dev.ztest;

import java.io.IOException;
import java.util.List;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;

public final class Detector implements Listener {
    private final ZTestPlugin p;
    private final NamespacedKey type,recipe;
    Detector(ZTestPlugin plugin){p=plugin;type=new NamespacedKey(p,"item");recipe=new NamespacedKey(p,"anomalous_detector");}
    private ItemStack item(Material material,String id,String name,List<String> lore){
        ItemStack stack=new ItemStack(material);var meta=stack.getItemMeta();meta.setDisplayName(name);meta.setLore(lore);
        meta.setEnchantmentGlintOverride(true);meta.getPersistentDataContainer().set(type,PersistentDataType.STRING,id);stack.setItemMeta(meta);return stack;
    }
    public ItemStack legacyShard(){return item(Material.AMETHYST_SHARD,"soul_shard","§dОсколок души",List.of("§7Остаток души из Чистилища.","§7Используется для создания детектора."));}
    public ItemStack shard(){var stack=legacyShard();stack.editMeta(meta->meta.setItemModel(new NamespacedKey("z_test","soul_shard")));return stack;}
    public ItemStack detector(){return item(Material.LODESTONE,"detector","§dАномальный детектор",List.of("§7Поставьте блок и нажмите ПКМ,","§7чтобы найти ближайший разлом."));}
    public boolean is(ItemStack item,String id){if(item==null||item.getType().isAir()||!item.hasItemMeta())return false;Material expected=id.equals("soul_shard")?Material.AMETHYST_SHARD:Material.LODESTONE;return item.getType()==expected&&id.equals(item.getItemMeta().getPersistentDataContainer().get(type,PersistentDataType.STRING));}
    void registerRecipe(){
        Bukkit.removeRecipe(recipe);var r=new ShapedRecipe(recipe,detector());r.shape("RSR","SLS","RSR");r.setIngredient('R',Material.REDSTONE);r.setIngredient('S',new RecipeChoice.ExactChoice(List.of(shard(),legacyShard())));r.setIngredient('L',Material.LODESTONE);
        if(!Bukkit.addRecipe(r))throw new IllegalStateException("Не удалось зарегистрировать рецепт детектора");
        for(Player player:Bukkit.getOnlinePlayers())player.discoverRecipe(recipe);
    }
    void unregisterRecipe(){Bukkit.removeRecipe(recipe);}
    private boolean upgradeShard(ItemStack stack){
        if(!is(stack,"soul_shard"))return false;
        var meta=stack.getItemMeta();var model=new NamespacedKey("z_test","soul_shard");
        if(model.equals(meta.getItemModel()))return false;
        meta.setItemModel(model);stack.setItemMeta(meta);return true;
    }
    private void upgradeInventory(Inventory inventory){
        for(int i=0;i<inventory.getSize();i++){ItemStack stack=inventory.getItem(i);if(upgradeShard(stack))inventory.setItem(i,stack);}
    }
    void upgradePlayer(Player player){player.discoverRecipe(recipe);upgradeInventory(player.getInventory());}
    @EventHandler public void join(PlayerJoinEvent event){upgradePlayer(event.getPlayer());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void open(InventoryOpenEvent event){upgradeInventory(event.getInventory());upgradeInventory(event.getPlayer().getInventory());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void itemSpawn(ItemSpawnEvent event){var stack=event.getEntity().getItemStack();if(upgradeShard(stack))event.getEntity().setItemStack(stack);}

    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void place(BlockPlaceEvent e){
        Point at=Point.of(e.getBlockPlaced());
        if(is(e.getItemInHand(),"detector")){
            try{p.state.addDetector(at);}catch(IOException ex){e.setCancelled(true);p.failed(ex);e.getPlayer().sendMessage(ZTestPlugin.msg("§cНе удалось сохранить блок; установка отменена."));return;}
            Bukkit.getScheduler().runTask(p,()->{if(e.isCancelled()||e.getBlockPlaced().getType()!=Material.LODESTONE){try{p.state.removeDetector(at);}catch(IOException ex){p.failed(ex);}}else p.visuals.show(at);});
        }else if(p.state.hasDetector(at)){
            try{p.state.removeDetector(at);}catch(IOException ex){e.setCancelled(true);p.failed(ex);}
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void breakBlock(BlockBreakEvent e){
        Point at=Point.of(e.getBlock());if(!p.state.hasDetector(at)||e.getBlock().getType()!=Material.LODESTONE)return;
        try{p.state.removeDetector(at);}catch(IOException ex){e.setCancelled(true);p.failed(ex);e.getPlayer().sendMessage(ZTestPlugin.msg("§cНе удалось сохранить данные детектора; добыча отменена."));return;}
        e.setDropItems(false);boolean drop=e.getPlayer().getGameMode()!=GameMode.CREATIVE;
        Bukkit.getScheduler().runTask(p,()->{
            if(e.isCancelled()||e.getBlock().getType()==Material.LODESTONE){try{p.state.addDetector(at);}catch(IOException ex){p.failed(ex);}return;}
            p.visuals.remove(at);
            if(drop)e.getBlock().getWorld().dropItemNaturally(e.getBlock().getLocation().add(.5,.5,.5),detector());
        });
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void interact(PlayerInteractEvent e){
        if(e.getHand()!=EquipmentSlot.HAND||e.getAction()!=Action.RIGHT_CLICK_BLOCK||e.getClickedBlock()==null)return;
        Block block=e.getClickedBlock();if(block.getType()!=Material.LODESTONE||!p.state.hasDetector(Point.of(block)))return;
        e.setCancelled(true);Point nearest=p.rifts.nearestEndpoint(block.getLocation().add(.5,0,.5));
        if(nearest==null)e.getPlayer().sendActionBar("§dРазломы в этом мире не обнаружены");
        else e.getPlayer().sendActionBar("§dРазлом: §fX "+nearest.x()+" §7| §fY "+nearest.y()+" §7| §fZ "+nearest.z());
        block.getWorld().spawnParticle(Particle.REVERSE_PORTAL,block.getLocation().add(.5,1,.5),12,.3,.15,.3,.02);
    }
    private boolean tracked(Block b){return b.getType()==Material.LODESTONE&&p.state.hasDetector(Point.of(b));}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void piston(BlockPistonExtendEvent e){if(e.getBlocks().stream().anyMatch(this::tracked))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void piston(BlockPistonRetractEvent e){if(e.getBlocks().stream().anyMatch(this::tracked))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void explosion(EntityExplodeEvent e){e.blockList().removeIf(this::tracked);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void explosion(BlockExplodeEvent e){e.blockList().removeIf(this::tracked);}
}
