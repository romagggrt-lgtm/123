package dev.ztest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Random;
import org.bukkit.*;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;

public final class ZTestPlugin extends JavaPlugin {
    StateStore state;
    RiftManager rifts;
    Detector detector;
    Souls souls;
    DetectorVisuals visuals;
    World purgatory;
    String worldName;

    @Override public void onEnable(){
        try{
            saveDefaultConfig();worldName=getConfig().getString("world-name","z_test_purgatory");
            if(worldName==null||!worldName.matches("[A-Za-z0-9_-]{1,48}"))throw new IllegalArgumentException("Недопустимое имя мира в config.yml");
            state=new StateStore(getDataFolder());
            purgatory=loadOwnedWorld();
            rifts=new RiftManager(this);detector=new Detector(this);souls=new Souls(this);visuals=new DetectorVisuals(this);
            var pm=getServer().getPluginManager();pm.registerEvents(rifts,this);pm.registerEvents(detector,this);pm.registerEvents(souls,this);pm.registerEvents(visuals,this);
            Commands commands=new Commands(this);var command=Objects.requireNonNull(getCommand("ztest"));command.setExecutor(commands);command.setTabCompleter(commands);
            detector.registerRecipe();
            rifts.start();souls.start();Bukkit.getScheduler().runTask(this,visuals::restoreLoaded);
            getLogger().info("Чистилище готово; разломов: "+state.rifts().size()+", детекторов: "+state.detectorCount());
        }catch(Exception e){getLogger().severe("Z_Test не запущен: "+e.getMessage());e.printStackTrace();getServer().getPluginManager().disablePlugin(this);}
    }
    private World loadOwnedWorld()throws IOException {
        Path marker=getDataFolder().toPath().resolve("world-owner.txt");
        Path worldDir=Bukkit.getWorldContainer().toPath().resolve(worldName);
        boolean owned=Files.exists(marker), exists=Files.exists(worldDir)||Bukkit.getWorld(worldName)!=null;
        if(owned&&!Files.readString(marker).trim().equals(worldName))throw new IOException("Имя мира отличается от world-owner.txt; мир не изменён");
        if(exists&&!owned)throw new IOException("Мир с именем "+worldName+" уже существует, но не принадлежит Z_Test. Выберите другое world-name.");
        if(owned&&!exists)throw new IOException("Мир Z_Test отсутствует. Восстановите его из резервной копии вместе с данными плагина.");
        if(!owned)Files.writeString(marker,worldName+"\n");
        World w=Bukkit.getWorld(worldName);
        if(w==null)w=new WorldCreator(worldName).environment(World.Environment.NORMAL).generator(new PurgatoryGenerator()).generateStructures(false).createWorld();
        if(w==null)throw new IOException("Не удалось создать мир Чистилища");
        if(!(w.getGenerator() instanceof PurgatoryGenerator))throw new IOException("Мир уже загружен с другим генератором. Остановите сервер и проверьте конфигурацию миров.");
        w.setGameRule(GameRule.DO_DAYLIGHT_CYCLE,false);w.setGameRule(GameRule.DO_WEATHER_CYCLE,false);w.setTime(6000);w.setStorm(false);w.setThundering(false);
        return w;
    }
    @Override public ChunkGenerator getDefaultWorldGenerator(String name,String id){return name.equals(getConfig().getString("world-name","z_test_purgatory"))?new PurgatoryGenerator():null;}
    @Override public void onDisable(){if(visuals!=null)visuals.clearLoaded();if(detector!=null)detector.unregisterRecipe();}
    void failed(Exception e){getLogger().severe("Не удалось сохранить данные Z_Test: "+e.getMessage());}
    static String msg(String message){return "§8[§dZ_Test§8] §r"+message;}
    public World getPurgatory(){return purgatory;}
    public StateStore getState(){return state;}
    public Detector getDetector(){return detector;}
    public RiftManager getRifts(){return rifts;}
    public Souls getSouls(){return souls;}
}
