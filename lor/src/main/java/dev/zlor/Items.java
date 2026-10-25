package dev.zlor;

import com.google.gson.*;
import java.io.*;
import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class Items {
    public static final Set<String> BLOCKS=Set.of("magnetite_core","void_absorber","void_alarm","void_siren");
    public static final List<String> ALL=List.of("voidometer","magnetite_core","void_absorber","void_alarm","void_siren","liquid_void","void_breath");
    public static final Map<String,String> NAMES=Map.of("voidometer","Войдометр","magnetite_core","Магнетитовое ядро",
            "void_absorber","Поглотитель Войда","void_alarm","Войдовый сигнализатор","void_siren","Сирена",
            "liquid_void","Жидкий Войд","void_breath","Дыхание Войда");
    public final NamespacedKey identity=new NamespacedKey("z_lor","item"), portable=new NamespacedKey("z_lor","portable");
    private final ZLorPlugin plugin;
    private final Map<String,NamespacedKey> recipes=new HashMap<>();
    public Items(ZLorPlugin plugin){this.plugin=plugin;}
    public ItemStack make(String id){
        if(!ALL.contains(id))throw new IllegalArgumentException("Неизвестный предмет");
        var stack=new ItemStack(BLOCKS.contains(id)?Material.TARGET:Material.PAPER);var meta=stack.getItemMeta();
        meta.displayName(Component.text(NAMES.get(id),NamedTextColor.LIGHT_PURPLE));
        meta.getPersistentDataContainer().set(identity,PersistentDataType.STRING,id);
        meta.setItemModel(new NamespacedKey("smplore",id));
        meta.setMaxStackSize(id.equals("voidometer")||id.equals("void_breath")?1:id.equals("liquid_void")?16:64);
        stack.setItemMeta(meta);return stack;
    }
    public String id(ItemStack stack){
        if(stack==null||!stack.hasItemMeta())return null;
        String value=stack.getItemMeta().getPersistentDataContainer().get(identity,PersistentDataType.STRING);
        return ALL.contains(value==null?"":value)&&stack.getType()==(BLOCKS.contains(value)?Material.TARGET:Material.PAPER)?value:null;
    }
    public boolean is(ItemStack item,String id){return id.equals(id(item));}
    public boolean capacity(Inventory inventory,ItemStack incoming){
        int left=incoming.getAmount();
        for(var current:inventory.getStorageContents()){
            if(current==null||current.isEmpty())left-=incoming.getMaxStackSize();
            else if(current.isSimilar(incoming))left-=Math.max(0,incoming.getMaxStackSize()-current.getAmount());
            if(left<=0)return true;
        }
        return false;
    }
    public ItemStack portable(State.Device device,long fuelNow){
        var item=make(device.kind);
        if(!device.kind.equals("magnetite_core")){
            var data=new JsonObject();data.addProperty("paid",device.remaining(fuelNow));data.addProperty("volume",device.volume);
            data.addProperty("sound",device.sound);data.addProperty("range",device.range);data.addProperty("warning",device.warning);
            data.addProperty("danger",device.danger);data.addProperty("mode",device.mode);
            item.editMeta(m->m.getPersistentDataContainer().set(portable,PersistentDataType.STRING,data.toString()));
        }
        return item;
    }
    public void restore(ItemStack item,State.Device d,long fuelNow){
        String raw=item.getItemMeta().getPersistentDataContainer().get(portable,PersistentDataType.STRING);
        if(raw==null)return;
        try{
            var j=JsonParser.parseString(raw).getAsJsonObject();
            long paid=j.get("paid").getAsLong();if(paid<0||paid>3_600_000)throw new IllegalArgumentException("Некорректное топливо");
            d.fuelUntil=Math.addExact(fuelNow,paid);d.volume=j.get("volume").getAsInt();d.sound=j.get("sound").getAsInt();
            d.range=j.get("range").getAsInt();d.warning=j.get("warning").getAsInt();d.danger=j.get("danger").getAsInt();d.mode=j.get("mode").getAsInt();
            if(d.volume<0||d.volume>100||d.sound<0||d.sound>=8||d.range<8||d.range>128)throw new IllegalArgumentException();
            new dev.zlor.rules.AlarmSettings(d.warning,d.danger);
        }catch(RuntimeException error){throw new IllegalArgumentException("Повреждены данные переносимого устройства",error);}
    }
    public void registerRecipes(){
        for(String id:List.of("voidometer","magnetite_core","void_absorber","void_alarm","void_siren")){
            try(var stream=plugin.getResource("recipes/"+id+".json")){
                if(stream==null)throw new IOException("Нет рецепта "+id);
                var json=JsonParser.parseReader(new InputStreamReader(stream,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                var key=new NamespacedKey(plugin,id);recipes.put(id,key);Bukkit.removeRecipe(key);
                var recipe=new ShapedRecipe(key,make(id));var pattern=json.getAsJsonArray("pattern");
                recipe.shape(pattern.get(0).getAsString(),pattern.get(1).getAsString(),pattern.get(2).getAsString());
                json.getAsJsonObject("key").entrySet().forEach(e->{
                    var value=e.getValue();String ingredient=value.isJsonPrimitive()?value.getAsString():value.getAsJsonObject().get("item").getAsString();
                    if(ingredient.startsWith("smplore:"))recipe.setIngredient(e.getKey().charAt(0),new RecipeChoice.ExactChoice(make(ingredient.substring(8))));
                    else {var material=Material.matchMaterial(ingredient);if(material==null)throw new IllegalArgumentException(ingredient);recipe.setIngredient(e.getKey().charAt(0),material);}
                });
                Bukkit.addRecipe(recipe);
            }catch(IOException error){throw new IllegalStateException("Не удалось зарегистрировать крафты",error);}
        }
    }
    public NamespacedKey recipe(String id){return recipes.get(id);}
    public void unregisterRecipes(){recipes.values().forEach(Bukkit::removeRecipe);}
}
