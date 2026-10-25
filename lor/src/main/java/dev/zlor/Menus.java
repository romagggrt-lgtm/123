package dev.zlor;

import dev.zlor.rules.*;
import io.papermc.paper.dialog.*;
import io.papermc.paper.registry.data.dialog.*;
import io.papermc.paper.registry.data.dialog.action.*;
import io.papermc.paper.registry.data.dialog.body.*;
import io.papermc.paper.registry.data.dialog.input.*;
import io.papermc.paper.registry.data.dialog.type.*;
import java.time.Duration;
import java.util.*;
import java.util.function.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryCloseEvent;

/** Native centred vanilla dialogs; callbacks have owner, dimension and expiry. */
public final class Menus implements Listener,AutoCloseable {
    public static final List<String> ROOT_LABELS=List.of("Войд","Блокировки","Текущее состояние","Драконы","Показать всё");
    public record Session(UUID token,UUID owner,String world,long expires,boolean scen,UUID device){}
    private final ZLorPlugin p;
    private final Map<UUID,Session> sessions=new HashMap<>();
    public Menus(ZLorPlugin p){this.p=p;}
    public boolean current(UUID owner,UUID token){var session=sessions.get(owner);return session!=null&&session.token.equals(token)&&session.expires>p.now();}
    public Session session(UUID owner){return sessions.get(owner);}
    private Session begin(Player player,boolean scen,UUID device){
        if(scen)p.roles.require(player);if(p.audio!=null)p.audio.stopPreview(player);
        var session=new Session(UUID.randomUUID(),player.getUniqueId(),p.worldKey(player.getWorld()),p.now()+300_000,scen,device);sessions.put(player.getUniqueId(),session);return session;
    }
    public boolean valid(Session session,Player actor){
        if(!actor.isOnline()||!actor.getUniqueId().equals(session.owner)||!current(session.owner,session.token)||!session.world.equals(p.worldKey(actor.getWorld()))||session.scen&&!p.roles.scen(actor))return false;
        if(session.device!=null){var d=p.state().devices.get(session.device);return d!=null&&p.devices.canUse(actor,d);}return true;
    }
    private ActionButton button(Session session,String label,BiConsumer<Player,DialogResponseView> callback){return button(session,Component.text(label),callback);}
    private ActionButton button(Session session,Component label,BiConsumer<Player,DialogResponseView> callback){
        return ActionButton.create(label,null,150,DialogAction.customClick((response,audience)->{
            if(!(audience instanceof Player actor)||!actor.getUniqueId().equals(session.owner))return;
            Bukkit.getScheduler().runTask(p,()->{
                try{if(!valid(session,actor))throw new IllegalArgumentException("Меню устарело или доступ изменился");callback.accept(actor,response);}
                catch(RuntimeException error){p.error(actor,error instanceof IllegalArgumentException?error:new IllegalArgumentException("Некорректные или неполные данные меню"));}
            });
        },ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(5)).build()));
    }
    private void show(Player player,Session session,String title,List<String> rows,List<DialogInput> inputs,List<ActionButton> buttons){
        List<DialogBody> body=rows.stream().map(row->(DialogBody)DialogBody.plainMessage(Component.text(row))).toList();
        var exit=button(session,"Закрыть",(actor,response)->clear(actor));
        player.closeInventory();
        player.showDialog(Dialog.create(factory->factory.empty().base(DialogBase.builder(Component.text(title)).body(body).inputs(inputs).canCloseWithEscape(true).pause(false).build()).type(DialogType.multiAction(buttons,exit,2))));
    }
    private DialogInput text(String id,String label,Object initial){return DialogInput.text(id,Component.text(label)).initial(String.valueOf(initial)).width(300).maxLength(40).build();}
    private DialogInput volume(int initial){return DialogInput.numberRange("volume",Component.text("Громкость, %"),0,100).initial((float)initial).step(1f).width(300).build();}
    private DialogInput choice(String id,String label,List<SingleOptionDialogInput.OptionEntry> options){return DialogInput.singleOption(id,300,options,Component.text(label),true);}
    private DialogInput bool(String id,String label,boolean initial){return DialogInput.bool(id,Component.text(label),initial,"true","false");}
    private double number(DialogResponseView response,String field){return DimensionVoid.parse(response.getText(field));}
    public static int slider(DialogResponseView response,String field,int minimum,int maximum,int step){
        Float value=response.getFloat(field);
        if(value==null||!Float.isFinite(value)||value<minimum||value>maximum||Math.abs(value/step-Math.rint(value/step))>.001)throw new IllegalArgumentException("Недопустимое значение: "+field);
        return Math.round(value);
    }
    public static int track(DialogResponseView response){try{int value=Integer.parseInt(response.getText("track"));if(value<0||value>=8)throw new NumberFormatException();return value;}catch(RuntimeException e){throw new IllegalArgumentException("Выберите одну из восьми записей");}}
    public void root(Player player){
        var s=begin(player,true,null);
        show(player,s,"Меню Сценариста",List.of("Z_Lor · личные инструменты сценариста"),List.of(),List.of(
                button(s,Component.text("■ Войд",NamedTextColor.LIGHT_PURPLE),(a,r)->voidMenu(a)),
                button(s,Component.text("■ Блокировки",NamedTextColor.RED),(a,r)->restrictions(a)),
                button(s,Component.text("■ Текущее состояние",NamedTextColor.AQUA),(a,r)->status(a)),
                button(s,Component.text("■ Драконы",NamedTextColor.DARK_PURPLE),(a,r)->dragons(a)),
                button(s,Component.text("■ Показать всё",NamedTextColor.GREEN),(a,r)->{p.visuals.showAll(a);root(a);})));
    }
    public void voidMenu(Player player){
        var s=begin(player,true,null);var state=p.voids.state(player.getWorld());
        show(player,s,"Войд",List.of("Y "+VoidForecast.number(p.voids.height(player.getWorld()))+" · "+VoidForecast.number(state.perFourHours()/240)+" бл/мин"),List.of(),List.of(
                button(s,"Высота",(a,r)->voidForm(a,false)),button(s,"Скорость",(a,r)->voidForm(a,true)),
                button(s,"Остановить",(a,r)->{p.confirmations.ask(a,"void","Вернуть штатную границу и остановить скорость?",30_000,()->p.voids.set(a,"stop",0,false,false,true));pending(a);}),
                button(s,"Показать",(a,r)->{p.visuals.toggleVoid(a);voidMenu(a);}),button(s,"Назад",(a,r)->root(a))));
    }
    private void voidForm(Player player,boolean speed){
        var s=begin(player,true,null);var current=p.voids.state(player.getWorld());
        var fields=new ArrayList<DialogInput>();fields.add(text("value",speed?"Скорость":"Высота Y",VoidForecast.input(speed?current.displaySpeed():p.voids.height(player.getWorld()))));
        fields.add(bool("vector","Прибавить к текущему значению",false));if(speed)fields.add(bool("minute","В блоках за минуту (иначе за 4 часа)",current.minuteMode()));
        show(player,s,speed?"Скорость Войда":"Высота Войда",List.of(),fields,List.of(button(s,"Применить",(a,r)->{p.voids.set(a,speed?"speed":"height",number(r,"value"),Boolean.TRUE.equals(r.getBoolean("vector")),speed&&Boolean.TRUE.equals(r.getBoolean("minute")),false);pending(a);}),button(s,"Назад",(a,r)->voidMenu(a))));
    }
    public void pending(Player player){
        var value=p.confirmations.get(player.getUniqueId());if(value==null)return;var s=begin(player,true,null);
        show(player,s,"Подтверждение",List.of(value.description(),"Осталось: "+Math.max(0,(value.expires()-p.now())/1000)+" сек"),List.of(),List.of(
                button(s,"Подтвердить",(a,r)->p.confirmations.finish(a,value.token().toString(),true)),button(s,"Отмена",(a,r)->{p.confirmations.finish(a,value.token().toString(),false);root(a);})));
    }
    public void restrictions(Player player){
        var s=begin(player,true,null);show(player,s,"Блокировки",List.of(),List.of(),List.of(button(s,"Дальние врата",(a,r)->gate(a)),button(s,"Элитры",(a,r)->flight(a)),button(s,"Назад",(a,r)->root(a))));
    }
    public void gate(Player player){
        var s=begin(player,true,null);var gate=p.state().restrictions.gate();var options=new ArrayList<SingleOptionDialogInput.OptionEntry>();
        for(var entry:GateSounds.ENTRIES)options.add(SingleOptionDialogInput.OptionEntry.create(entry.key(),Component.text(entry.name()),entry.key().equals(gate.sound())));
        p.state().audio.forEach((hash,metadata)->options.add(SingleOptionDialogInput.OptionEntry.create("custom:"+hash,Component.text(metadata.fileName()),gate.sound().equals("custom:"+hash))));
        var fields=List.of(bool("enabled","Включено",gate.enabled()),text("y","Высота блокировки: Войд строго выше Y",VoidForecast.input(gate.y())),choice("sound","Звук отказа",options),volume((int)Math.round(gate.volume()*100)));
        show(player,s,"Дальние врата",List.of("Правило действует только в Энде; центральный портал не блокирует"),fields,List.of(
                button(s,"Применить",(a,r)->{p.restrictions.gate(a,new RestrictionSettings.Gate(Boolean.TRUE.equals(r.getBoolean("enabled")),number(r,"y"),r.getText("sound"),slider(r,"volume",0,100,1)/100d),false);pending(a);}),
                button(s,"Остановить",(a,r)->{p.restrictions.gate(a,gate.enabled(false),false);pending(a);}),
                button(s,"Проверить",(a,r)->{double volume=slider(r,"volume",0,100,1)/100d;gate(a);p.audio.previewGate(a,r.getText("sound"),volume,session(a.getUniqueId()).token);}),
                button(s,"Закончить проверку",(a,r)->{p.audio.stopPreview(a);gate(a);}),
                button(s,"Импортировать",(a,r)->imports(a)),button(s,"Назад",(a,r)->restrictions(a))));
    }
    public void flight(Player player){
        var s=begin(player,true,null);var f=p.state().restrictions.flight();
        show(player,s,"Элитры",List.of("Пороги — высота Войда, а не высота игрока. Под Поглотителем интервал ×4."),List.of(
                bool("enabled","Сбои включены",f.enabled()),text("start","Высота начала",VoidForecast.input(f.startY())),text("end","Высота максимальных сбоев",VoidForecast.input(f.endY())),text("seconds","Начальный интервал, секунды",VoidForecast.input(f.startSeconds())),text("full","Высота полного запрета",VoidForecast.input(f.fullBlockY())),bool("block","Полный запрет включён",f.fullBlockEnabled())),List.of(
                button(s,"Применить",(a,r)->{p.restrictions.flight(a,new RestrictionSettings.Flight(Boolean.TRUE.equals(r.getBoolean("enabled")),number(r,"start"),number(r,"end"),number(r,"seconds"),Boolean.TRUE.equals(r.getBoolean("block")),number(r,"full")),false);pending(a);}),
                button(s,"Остановить",(a,r)->{p.restrictions.flight(a,f.enabled(false),false);pending(a);}),button(s,"Назад",(a,r)->restrictions(a))));
    }
    public void status(Player player){var s=begin(player,true,null);show(player,s,"Текущее состояние",p.commands.status(player),List.of(),List.of(button(s,"Обновить",(a,r)->status(a)),button(s,"Назад",(a,r)->root(a))));}
    public void dragons(Player player){
        var s=begin(player,true,null);show(player,s,"Драконы",List.of("Обычный 200 HP · Обогащённый 500 HP · Царь 1500 logical HP","Облик драконов — ванильный; исключённые старые модели не используются."),List.of(),List.of(
                button(s,"Обычный",(a,r)->confirm(a,"Призвать обычного дракона?",()->p.dragons.spawn(a,"default"))),button(s,"Обогащённый",(a,r)->confirm(a,"Начать призыв обогащённого?",()->p.dragons.spawn(a,"enriched"))),
                button(s,"Царь Войда",(a,r)->confirm(a,"Призвать Царя Войда?",()->p.dragons.spawn(a,"void_king"))),button(s,"Волны 1–10",(a,r)->waves(a)),
                button(s,"Портал и площадка",(a,r)->structures(a)),button(s,"Главный ★",(a,r)->star(a)),button(s,"Информация",(a,r)->status(a)),
                button(s,"Показать",(a,r)->{p.visuals.toggleDragons(a);dragons(a);}),button(s,"Проверка атак",(a,r)->attacks(a)),
                button(s,"Остановить всех",(a,r)->confirm(a,"Остановить бой без победы и дропа?",()->p.dragons.stop(a))),button(s,"Назад",(a,r)->root(a))));
    }
    private void confirm(Player player,String question,Runnable apply){p.confirmations.ask(player,"void",question,30_000,apply);pending(player);}
    public void waves(Player player){
        var s=begin(player,true,null);var actions=new ArrayList<ActionButton>();
        for(int number=1;number<=10;number++){final int n=number;var composition=Dragons.WAVES[number-1];actions.add(button(s,"Волна "+n+" · "+composition[0]+" + "+composition[1],(a,r)->confirm(a,"Запустить волну "+n+"?",()->p.dragons.startWave(a,n))));}
        actions.add(button(s,"Текущая волна",(a,r)->status(a)));actions.add(button(s,"Остановить волну",(a,r)->confirm(a,"Остановить активную волну?",()->p.dragons.stop(a))));actions.add(button(s,"Назад",(a,r)->dragons(a)));
        show(player,s,"Волны: обычные + обогащённые",List.of("Запуск только вручную. Открытие страницы бой не начинает."),List.of(),actions);
    }
    public void star(Player player){
        var s=begin(player,true,null);var options=new ArrayList<SingleOptionDialogInput.OptionEntry>();
        for(var body:p.dragons.bodies())if(body.entity.getWorld().equals(player.getWorld()))options.add(SingleOptionDialogInput.OptionEntry.create(body.id.toString(),Component.text(Dragons.name(body.kind)+" "+body.entity.getLocation().getBlockX()+" "+body.entity.getLocation().getBlockZ()),body.id.equals(p.dragons.leader(player.getWorld()))));
        var fields=options.isEmpty()?List.<DialogInput>of():List.<DialogInput>of(choice("dragon","Главный дракон",options));
        show(player,s,"Главный ★",List.of("Обогащённый раньше обычного; остальные получают треть урона."),fields,List.of(
                button(s,"Включить авто-выбор",(a,r)->p.dragons.command(a,new String[]{"star","enable"})),button(s,"Выключить",(a,r)->p.dragons.command(a,new String[]{"star","disable"})),
                button(s,"Выбрать из списка",(a,r)->{if(options.isEmpty())throw new IllegalArgumentException("Нет загруженных драконов");p.dragons.command(a,new String[]{"star","set",r.getText("dragon")});}),button(s,"Назад",(a,r)->dragons(a))));
    }
    public void structures(Player player){
        var s=begin(player,true,null);var current=p.structures.current(player.getWorld());
        show(player,s,"Портал и площадка",List.of("Сейчас: портал Y="+current.portalY+", вход Y="+current.entryY,"После переноса вход всегда на 10 блоков выше портала."),List.of(
                text("target","Целевая Y портала",current.target),text("threshold","Порог высоты Войда",VoidForecast.input(current.threshold))),List.of(
                button(s,"Немедленно перенести",(a,r)->{int y=Structures.integer(r.getText("target"));confirm(a,"Перенести портал на "+y+", вход на "+(y+10)+"?",()->p.structures.raise(a,y));}),
                button(s,"Применить правило",(a,r)->{String threshold=r.getText("threshold"),target=r.getText("target");confirm(a,"Сохранить одноразовое правило переноса?",()->p.structures.command(a,new String[]{"rule",threshold,target}));}),
                button(s,"Остановить правило",(a,r)->confirm(a,"Остановить правило, сохранив числа?",()->p.structures.command(a,new String[]{"stop"}))),button(s,"Назад",(a,r)->dragons(a))));
    }
    private void attacks(Player player){
        var s=begin(player,true,null);var options=p.dragons.bodies().stream().filter(b->b.entity.getWorld().equals(player.getWorld())&&!b.kind.equals("default")).map(b->SingleOptionDialogInput.OptionEntry.create(b.id.toString(),Component.text(Dragons.name(b.kind)+" · "+p.dragons.phase(b)),false)).toList();
        show(player,s,"Проверка реальных атак",List.of("Проверка наносит настоящий игровой урон. Остановить можно отдельной кнопкой."),options.isEmpty()?List.of():List.of(choice("dragon","Дракон",options)),List.of(
                button(s,"Луч",(a,r)->{if(options.isEmpty())throw new IllegalArgumentException("Нет подходящего дракона");String id=r.getText("dragon");confirm(a,"Запустить луч с настоящим уроном?",()->p.dragons.command(a,new String[]{"attack","beam",id}));}),
                button(s,"Пикирование Царя",(a,r)->{if(options.isEmpty())throw new IllegalArgumentException("Нет Царя");String id=r.getText("dragon");confirm(a,"Запустить пикирование Царя?",()->p.dragons.command(a,new String[]{"attack","dive",id}));}),
                button(s,"Остановить атаку",(a,r)->{p.dragons.command(a,new String[]{"attack","stop"});attacks(a);}),button(s,"Назад",(a,r)->dragons(a))));
    }
    public void device(Player player,State.Device device){
        if(!p.devices.canUse(player,device))throw new IllegalArgumentException("Устройство недоступно");
        var s=begin(player,false,device.id);
        if(device.kind.equals("void_alarm")){
            var modes=List.of(SingleOptionDialogInput.OptionEntry.create("analog",Component.text("Непрерывный 0–15"),device.mode!=AlarmSettings.PULSE),SingleOptionDialogInput.OptionEntry.create("pulse",Component.text("Импульсы 15"),device.mode==AlarmSettings.PULSE));
            show(player,s,"Войдовый сигнализатор",List.of("Аудиоплеера у датчика нет; под Поглотителем выход 0."),List.of(choice("mode","Режим",modes),text("start","Начало предупреждения",device.warning),text("danger","Максимум предупреждения",device.danger)),List.of(
                    button(s,"Применить",(a,r)->p.devices.settings(a,device.id,d->{d.warning=Structures.integer(r.getText("start"));d.danger=Structures.integer(r.getText("danger"));d.mode=r.getText("mode").equals("pulse")?AlarmSettings.PULSE:AlarmSettings.ANALOG;}))));return;
        }
        if(!device.kind.equals("void_siren"))return;
        var options=new ArrayList<SingleOptionDialogInput.OptionEntry>();for(int i=0;i<8;i++)options.add(SingleOptionDialogInput.OptionEntry.create(String.valueOf(i),Component.text(Audio.NAMES.get(i)),device.sound==i));
        show(player,s,"Сирена",List.of("Играет по внешнему редстоуну. Купол самостоятельно звук не выключает."),List.of(choice("track","Запись",options),volume(device.volume),DialogInput.numberRange("range",Component.text("Дальность, блоков"),8,128).initial((float)device.range).step(8f).width(300).build()),List.of(
                button(s,"Применить",(a,r)->p.devices.settings(a,device.id,d->{d.sound=track(r);d.volume=slider(r,"volume",0,100,1);d.range=slider(r,"range",8,128,8);})),
                button(s,"Проверить",(a,r)->{var preview=StateStore.JSON.fromJson(StateStore.JSON.toJson(device),State.Device.class);preview.sound=track(r);preview.volume=slider(r,"volume",0,100,1);preview.range=slider(r,"range",8,128,8);device(a,preview);p.audio.preview(a,preview,session(a.getUniqueId()).token);}),
                button(s,"Закончить проверку",(a,r)->{p.audio.stopPreview(a);device(a,device);})));
    }
    public void imports(Player player){
        var s=begin(player,true,null);var files=p.audio.importFiles();var options=files.stream().map(f->SingleOptionDialogInput.OptionEntry.create(f,Component.text(f),false)).toList();
        show(player,s,"Импорт отказного звука",List.of("Положите OGG / MP3 / WAV в plugins/Z_Lor/audio-import.","До 20 МиБ / 10 минут. Для нового звука нужен обновлённый общий пак."),options.isEmpty()?List.of():List.of(choice("file","Файл на сервере",options)),List.of(
                button(s,"Импортировать",(a,r)->{if(options.isEmpty())throw new IllegalArgumentException("Папка audio-import пуста");p.audio.importFile(a,r.getText("file"));}),button(s,"Отменить импорт",(a,r)->p.audio.cancel(a)),
                button(s,"Предложить новый пак",(a,r)->p.audio.publish(a)),button(s,"Назад",(a,r)->gate(a))));
    }
    public void clear(Player player){sessions.remove(player.getUniqueId());if(p.audio!=null)p.audio.stopPreview(player);player.closeInventory();}
    @EventHandler public void inventoryClose(InventoryCloseEvent event){if(event.getPlayer() instanceof Player player&&p.audio!=null)p.audio.stopPreview(player);}
    @Override public void close(){sessions.clear();}
}
