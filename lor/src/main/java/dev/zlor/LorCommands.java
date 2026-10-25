package dev.zlor;

import dev.zlor.rules.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class LorCommands {
    public static final List<String> BRANCHES=List.of("help","voidmeter","give","void","immune","unimmune","recipe","default","dragons","end","audio","status","diagnostics");
    private final ZLorPlugin p;
    public LorCommands(ZLorPlugin p){this.p=p;}
    public void execute(CommandSender sender,String[] args){
        if(args.length>0&&args[0].equals("give")){give(sender,Arrays.copyOfRange(args,1,args.length));return;}
        if(args.length>0&&args[0].equals("diagnostics")&&!(sender instanceof Player)){
            if(args.length!=1)throw new IllegalArgumentException("/lore diagnostics");diagnostics(sender);return;
        }
        Player player=p.roles.require(sender);
        if(args.length==0){p.menus.root(player);return;}
        String[] tail=Arrays.copyOfRange(args,1,args.length);
        switch(args[0]){
            case "help"->{exact(tail,0,"/lore help");help(player);}
            case "voidmeter"->{exact(tail,0,"/lore voidmeter");give(player,new String[]{player.getName(),"voidometer","1"});}
            case "void"->p.voids.command(player,tail);
            case "immune","unimmune"->{
                exact(tail,1,"/lore "+args[0]+" <ник|@s>");UUID id=tail[0].equals("@s")?player.getUniqueId():p.roles.known(tail[0]);if(id==null)throw new IllegalArgumentException("Игрок не известен серверу");
                boolean immune=args[0].equals("immune");p.change(player,(immune?"Выдан":"Снят")+" иммунитет Войда: "+tail[0],s->{if(immune)s.immune.add(id);else s.immune.remove(id);},s->player.sendMessage("Иммунитет "+(immune?"выдан":"снят")));
            }
            case "recipe"->{
                exact(tail,2,"/lore recipe lock|unlock <ник|@s>");if(!Set.of("lock","unlock").contains(tail[0]))throw new IllegalArgumentException("Нужно lock или unlock");
                Player target=tail[1].equals("@s")?player:Bukkit.getPlayerExact(tail[1]);if(target==null)throw new IllegalArgumentException("Нужен игрок онлайн");boolean unlock=tail[0].equals("unlock");
                p.change(player,"Рецепт Войдометра "+tail[0]+": "+target.getName(),s->{},s->{if(unlock)target.discoverRecipe(p.items.recipe("voidometer"));else target.undiscoverRecipe(p.items.recipe("voidometer"));player.sendMessage("Доступ к рецепту изменён");});
            }
            case "default"->{if(tail.length==0)p.restrictions.reset(player,false);else if(tail.length==2&&Set.of("confirm","cancel").contains(tail[0]))p.confirmations.finish(player,tail[1],tail[0].equals("confirm"));else throw new IllegalArgumentException("/lore default [confirm|cancel TOKEN]");}
            case "dragons"->p.dragons.command(player,tail);
            case "end"->p.structures.command(player,tail);
            case "status"->{exact(tail,0,"/lore status");status(player).forEach(player::sendMessage);}
            case "diagnostics"->{exact(tail,0,"/lore diagnostics");diagnostics(player);}
            case "audio"->{
                if(tail.length==0)p.menus.imports(player);
                else if(tail.length==2&&tail[0].equals("import"))p.audio.importFile(player,tail[1]);
                else if(tail.length==1&&tail[0].equals("cancel"))p.audio.cancel(player);
                else if(tail.length==1&&tail[0].equals("publish"))p.audio.publish(player);
                else throw new IllegalArgumentException("/lore audio [import <файл>|cancel|publish]");
            }
            default->throw new IllegalArgumentException("Неизвестная ветка; /lore help");
        }
    }
    private static void exact(String[] args,int length,String usage){if(args.length!=length)throw new IllegalArgumentException(usage);}
    public void give(CommandSender sender,String[] args){
        boolean scen=sender instanceof Player player&&p.roles.scen(player);
        if(!p.roles.admin(sender)&&!scen)throw new IllegalArgumentException("Нет доступа к выдаче");
        if(args.length<2||args.length>3)throw new IllegalArgumentException("/lore give <игрок|@s> <предмет> [количество]");
        Player target=args[0].equals("@s")&&sender instanceof Player player?player:Bukkit.getPlayerExact(args[0]);if(target==null)throw new IllegalArgumentException("Нужен игрок онлайн");
        String id=args[1].startsWith("smplore:")?args[1].substring(8):args[1].startsWith("z_lor:")?args[1].substring(6):args[1];if(!Items.ALL.contains(id))throw new IllegalArgumentException("Предметы: "+String.join(", ",Items.ALL));
        int amount=args.length==3?Integer.parseInt(args[2]):1;if(amount<1||amount>64)throw new IllegalArgumentException("Количество: 1–64");
        var item=p.items.make(id);item.setAmount(amount);if(!p.items.capacity(target.getInventory(),item))throw new IllegalArgumentException("Недостаточно места в инвентаре");
        p.store.change(s->{if(!p.roles.admin(sender)&&(!(sender instanceof Player player)||!s.roles.containsKey(player.getUniqueId())))throw new IllegalArgumentException("Доступ изменился");},s->{
            if(!target.isOnline()||!p.items.capacity(target.getInventory(),item)){sender.sendMessage("Выдача отменена: игрок вышел или инвентарь заполнен");return;}
            var leftovers=target.getInventory().addItem(item);if(!leftovers.isEmpty())throw new IllegalStateException("Неожиданный остаток выдачи");
            p.store.change(current->p.roles.audit(current,sender,"команда","Выдано "+amount+" × "+Items.NAMES.get(id)+" игроку "+target.getName(),p.worldKey(target.getWorld())));
            sender.sendMessage("Выдано: "+Items.NAMES.get(id)+" × "+amount+" → "+target.getName());
        },e->p.error(sender,e));
    }
    public List<String> status(Player player){
        var rows=new ArrayList<String>();
        for(World world:Bukkit.getWorlds()){
            var state=p.voids.state(world);double height=p.voids.height(world);rows.add(p.worldKey(world)+": Y "+VoidForecast.number(height)+", "+VoidForecast.number(state.perFourHours()/240)+" бл/мин, "+VoidForecast.number(state.perFourHours()/4)+" бл/ч");
            rows.add("Прогноз +10: "+VoidForecast.target(height,state.perFourHours(),VoidForecast.nextLevel(height,state.perFourHours(),10),365));
            rows.add("Прогноз +50: "+VoidForecast.target(height,state.perFourHours(),VoidForecast.nextLevel(height,state.perFourHours(),50),365));
        }
        var restrictions=p.state().restrictions;rows.add("Дальние врата: "+(p.restrictions.blocked(player.getWorld())?"закрыты":"проход разрешён")+"; правило "+restrictions.gate().enabled()+", порог "+VoidForecast.number(restrictions.gate().y()));
        rows.add("Сбои элитр: "+restrictions.flight().enabled()+", фактический интервал "+(Double.isFinite(p.restrictions.interval(player))?VoidForecast.number(p.restrictions.interval(player)):"нет")+" с; полный запрет "+restrictions.flight().fullBlockEnabled()+" от Y "+VoidForecast.number(restrictions.flight().fullBlockY()));
        var pending=p.confirmations.get(player.getUniqueId());rows.add("Подтверждение: "+(pending==null?"нет":Math.max(0,(pending.expires()-p.now())/1000)+" с"));
        for(var d:p.devices.loaded())if(d.kind.equals("void_absorber"))rows.add("Поглотитель "+d.pos.x()+" "+d.pos.y()+" "+d.pos.z()+": "+d.remaining(p.fuelNow())/1000+" с, запас "+d.reserves());
        for(var body:p.dragons.bodies())rows.add(Dragons.name(body.kind)+": "+VoidForecast.input(p.dragons.health(body)/2)+"/"+VoidForecast.input(body.max()/2)+" ❤, "+p.dragons.phase(body)+", "+body.entity.getLocation().getBlockX()+" "+body.entity.getLocation().getBlockY()+" "+body.entity.getLocation().getBlockZ());
        var arena=p.state().arenas.get(p.worldKey(player.getWorld()));if(arena!=null)rows.add("Волна "+arena.wave+": "+arena.phase);
        rows.add("Импортированный звук: "+GateSounds.name(restrictions.gate().sound()));
        return rows;
    }
    public void diagnostics(CommandSender sender){
        if(!p.roles.admin(sender)&&(!(sender instanceof Player player)||!p.roles.scen(player)))throw new IllegalArgumentException("Нет доступа");
        sender.sendMessage("Z_Lor 1.0.0 · Paper 1.21.11 · Java "+Runtime.version().feature());sender.sendMessage("Хранилище: "+(p.store.healthy()?"готово":"остановлено")+", revision="+p.state().revision+", сохранений="+p.store.writes());
        for(World world:Bukkit.getWorlds())sender.sendMessage(p.worldKey(world)+": "+p.nativeBoundary.status(world));
        sender.sendMessage("Устройства: "+p.state().devices.size()+", загружено "+p.devices.loaded().size()+", tickets "+p.redstone.leasedChunks()+", native redstone "+p.redstone.nativeReaderAvailable());
        sender.sendMessage("Топливо: uptime, "+p.fuelNow()+" мс. Базовая граница Paper, per-entity shield hook. RP автоматически не переотправляется.");
        sender.sendMessage("Перенос Fabric: новые зелья, алтари, посох и рост мобов остаются не определёнными; Fabric shader/локальный file picker не входят в vanilla-клиент.");
    }
    public void help(Player player){
        for(String line:List.of("/lore — меню; /scen [add|delete] ник — роли","/lore give ник предмет [количество]; /lore voidmeter","/lore void [Y|height set|vector число|speed set|vector [minute] число|stop|view]","/lore immune|unimmune ник|@s; /lore recipe lock|unlock ник|@s","/lore default; /lore end status|raise Y|rule voidY targetY|stop|recover","/lore dragons spawn default|enriched|void_king|stop; dragons view","/lore dragons star enable|disable|set UUID; dragons wave start 1..10|status|stop","/lore dragons attack beam|dive UUID; dragons attack stop","/lore audio [import файл|cancel|publish]; /lore status; /lore diagnostics"))player.sendMessage(line);
    }
    public List<String> complete(CommandSender sender,String command,String[] args){
        List<String> choices=new ArrayList<>();String current=args.length==0?"":args[args.length-1].toLowerCase(Locale.ROOT);
        if(command.equals("scen")){
            if(!p.roles.admin(sender)&&(!(sender instanceof Player player)||!p.roles.scen(player)))return List.of();
            if(args.length==1)choices.addAll(List.of("add","delete"));else if(args.length==2)Bukkit.getOnlinePlayers().forEach(player->choices.add(player.getName()));
        }else{
            boolean allowed=sender instanceof Player player&&p.roles.scen(player);
            if(!allowed){if(p.roles.admin(sender)&&args.length==1)choices.addAll(List.of("give","diagnostics"));else if(p.roles.admin(sender)&&args.length==2&&args[0].equals("give"))Bukkit.getOnlinePlayers().forEach(player->choices.add(player.getName()));else if(p.roles.admin(sender)&&args.length==3&&args[0].equals("give"))choices.addAll(Items.ALL);}
            else if(args.length==1)choices.addAll(BRANCHES);
            else switch(args[0]){
                case "give"->{if(args.length==2){choices.add("@s");Bukkit.getOnlinePlayers().forEach(player->choices.add(player.getName()));}else if(args.length==3)choices.addAll(Items.ALL);}
                case "void"->{if(args.length==2)choices.addAll(List.of("height","speed","stop","view"));else if(args.length==3)choices.addAll(List.of("set","vector"));else if(args[1].equals("speed")&&args.length==4)choices.add("minute");}
                case "dragons"->{if(args.length==2)choices.addAll(List.of("spawn","star","view","wave","attack"));else if(args.length==3)choices.addAll(switch(args[1]){case "spawn"->List.of("default","enriched","void_king","stop");case "star"->List.of("enable","disable","set");case "wave"->List.of("start","status","stop");case "attack"->List.of("beam","dive","stop");default->List.of();});else if(args.length==4&&args[1].equals("wave"))for(int n=1;n<=10;n++)choices.add(String.valueOf(n));else if(args.length==4)p.dragons.bodies().forEach(body->choices.add(body.id.toString()));}
                case "end"->{if(args.length==2)choices.addAll(List.of("status","raise","rule","stop","recover"));}
                case "immune","unimmune"->{if(args.length==2){choices.add("@s");choices.addAll(p.state().names.values());}}
                case "recipe"->{if(args.length==2)choices.addAll(List.of("lock","unlock"));else if(args.length==3){choices.add("@s");Bukkit.getOnlinePlayers().forEach(player->choices.add(player.getName()));}}
                case "audio"->{if(args.length==2)choices.addAll(List.of("import","cancel","publish"));else if(args.length==3&&args[1].equals("import"))choices.addAll(p.audio.importFiles());}
            }
        }
        return choices.stream().filter(value->value.toLowerCase(Locale.ROOT).startsWith(current)).distinct().sorted().toList();
    }
}
