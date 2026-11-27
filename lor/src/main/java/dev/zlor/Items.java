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
    public static final Set<String> BLOCKS=Set.of("magnetite_core","void_absorber","void_alarm","void_siren","stationary_voidometer");
    public static final List<String> ALL=List.of("voidometer","magnetite_core","void_absorber","void_alarm","void_siren","stationary_voidometer","liquid_void","void_breath","lord_staff","eneit_crown");
    public static final Map<String,String> NAMES=Map.of("voidometer","Войдометр","magnetite_core","Магнетитовое ядро",
            "void_absorber","Поглотитель Войда","void_alarm","Войдовый сигнализатор","void_siren","Сирена",
            "liquid_void","Жидкий Войд","void_breath","Дыхание Войда","stationary_voidometer","Стационарный войдометр","lord_staff","Посох Лорда","eneit_crown","Корона Енейта");
    public final NamespacedKey identity=new NamespacedKey("z_lor","item"), portable=new NamespacedKey("z_lor","portable");
    private final ZLorPlugin plugin;
    private final Map<String,NamespacedKey> recipes=new HashMap<>();
    public Items(ZLorPlugin plugin){this.plugin=plugin;}
    public ItemStack make(String id){
        if(!ALL.contains(id))throw new IllegalArgumentException("Неизвестный предмет");
        var stack=new ItemStack(material(id));var meta=stack.getItemMeta();
        if(id.equals("lord_staff")){
            var legacy=net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.builder().character('&').hexColors().build();
            meta.displayName(Component.empty().decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC,false).append(legacy.deserialize("&#FF00E3&kф&#E600E6П&#CD00E9&oо&#B300EBс&#9A00EEо&#8100F1х &#4F00F7Л&#3500F9&oо&#1C00FCр&#0300FFда&#0300FF&kф")));
            meta.lore(List.of(Component.text("+4 здоровья в основной руке",NamedTextColor.LIGHT_PURPLE),Component.text("ПКМ — снаряд шалкера",NamedTextColor.GRAY),Component.text("Shift + ПКМ — телепортация",NamedTextColor.GRAY),Component.text("F — призвать защитников",NamedTextColor.GRAY)));
            meta.addAttributeModifier(org.bukkit.attribute.Attribute.MAX_HEALTH,new org.bukkit.attribute.AttributeModifier(new NamespacedKey("z_lor","lord_staff_health"),4,org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER,EquipmentSlotGroup.MAINHAND));
            meta.setItemModel(new NamespacedKey("z_lor","lord_staff"));
        }else meta.displayName(Component.text(NAMES.get(id),NamedTextColor.LIGHT_PURPLE));
        meta.getPersistentDataContainer().set(identity,PersistentDataType.STRING,id);
        if(id.equals("eneit_crown")){
            meta.setItemModel(new NamespacedKey("z_lor","eneit_crown"));
            var equip=meta.getEquippable();equip.setCameraOverlay(null);meta.setEquippable(equip);
        }else if(!id.equals("lord_staff"))meta.setItemModel(new NamespacedKey("smplore",id));
        meta.setMaxStackSize(id.equals("voidometer")||id.equals("void_breath")||id.equals("lord_staff")||id.equals("eneit_crown")?1:id.equals("liquid_void")?16:64);
        stack.setItemMeta(meta);return stack;
    }
    private Material material(String id){return BLOCKS.contains(id)?Material.TARGET:id.equals("lord_staff")?Material.STICK:id.equals("eneit_crown")?Material.CARVED_PUMPKIN:Material.PAPER;}
    public String id(ItemStack stack){
        if(stack==null||!stack.hasItemMeta())return null;
        String value=stack.getItemMeta().getPersistentDataContainer().get(identity,PersistentDataType.STRING);
        return ALL.contains(value==null?"":value)&&stack.getType()==material(value)?value:null;
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
        for(String id:List.of("voidometer","magnetite_core","void_absorber","void_alarm","void_siren","stationary_voidometer")){
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
