package dev.znick;

import io.netty.channel.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Paper 1.21.11 adapter. Client-only profile and scoreboard changes; server UUID and profile stay untouched. */
final class NmsWire implements Wire {
    private final ZNickPlugin plugin;
    private final Map<UUID, View> views = new ConcurrentHashMap<>();
    private final Class<?> info, entry, teamPacket, playerTeam, scoreboard, nmsComponent, adventure, profile, packet;
    private final Constructor<?> infoCtor, teamCtor, teamPacketCtor, profileCtor;
    private final Method asVanilla, asAdventure;
    private final Class<?> action;

    NmsWire(ZNickPlugin plugin) throws Exception {
        this.plugin = plugin;
        info = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket");
        entry = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket$Entry");
        action = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket$Action");
        teamPacket = Class.forName("net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket");
        playerTeam = Class.forName("net.minecraft.world.scores.PlayerTeam");
        scoreboard = Class.forName("net.minecraft.world.scores.Scoreboard");
        nmsComponent = Class.forName("net.minecraft.network.chat.Component");
        adventure = Class.forName("io.papermc.paper.adventure.PaperAdventure");
        profile = Class.forName("com.mojang.authlib.GameProfile");
        packet = Class.forName("net.minecraft.network.protocol.Packet");
        infoCtor = accessible(info.getDeclaredConstructor(EnumSet.class, List.class));
        teamCtor = accessible(playerTeam.getDeclaredConstructor(scoreboard, String.class));
        teamPacketCtor = accessible(teamPacket.getDeclaredConstructor(String.class, int.class, Optional.class, Collection.class));
        profileCtor = accessible(profile.getDeclaredConstructor(UUID.class, String.class, Class.forName("com.mojang.authlib.properties.PropertyMap")));
        asVanilla = adventure.getMethod("asVanilla", Component.class);
        asAdventure = adventure.getMethod("asAdventure", nmsComponent);
        if (!entry.isRecord() || !profile.isRecord()) throw new IllegalStateException("Unsupported Paper profile packet layout");
    }
    private static <T extends AccessibleObject> T accessible(T x) { x.setAccessible(true); return x; }
    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) try { return accessible(c.getDeclaredField(name)); } catch (NoSuchFieldException ignored) { }
        throw new NoSuchFieldException(type.getName() + "." + name);
    }
    private static Object field(Object o, String name) throws Exception { return field(o.getClass(), name).get(o); }
    private static Object invoke(Object o, String name, Class<?>[] args, Object... values) throws Exception {
        Method m = o.getClass().getMethod(name, args); return m.invoke(o, values);
    }
    private Object vanilla(Component component) throws Exception { return asVanilla.invoke(null, component); }
    private Component kyori(Object component) throws Exception { return (Component) asAdventure.invoke(null, component); }
    private Object changedRecord(Object original, Map<String, Object> replacements) throws Exception {
        RecordComponent[] parts = original.getClass().getRecordComponents();
        if (parts == null) throw new IllegalStateException("Not a record: " + original.getClass());
        Class<?>[] types = new Class<?>[parts.length]; Object[] values = new Object[parts.length];
        for (int i = 0; i < parts.length; i++) {
            types[i] = parts[i].getType();
            values[i] = replacements.containsKey(parts[i].getName()) ? replacements.get(parts[i].getName()) : parts[i].getAccessor().invoke(original);
        }
        return accessible(original.getClass().getDeclaredConstructor(types)).newInstance(values);
    }
    private Object hiddenProfile(Object original, Nick n) throws Exception {
        return profileCtor.newInstance(n.id(), n.alias(), profile.getMethod("properties").invoke(original));
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    private Object buildTeam(Nick n, boolean remove) throws Exception {
        Object board = scoreboard.getDeclaredConstructor().newInstance();
        Object team = teamCtor.newInstance(board, n.team());
        invoke(team, "setPlayerPrefix", new Class<?>[]{nmsComponent}, vanilla(n.text()));
        ((Collection)invoke(team, "getPlayers", new Class<?>[]{})).add(n.alias());
        Class<?> rule = Class.forName("net.minecraft.world.scores.Team$CollisionRule");
        Object selected = Arrays.stream(rule.getEnumConstants()).filter(v -> {
            try { return String.valueOf(invoke(v, "getSerializedName", new Class<?>[]{})).equalsIgnoreCase(plugin.collision); }
            catch (Exception e) { return ((Enum<?>)v).name().equalsIgnoreCase(plugin.collision); }
        }).findFirst().orElse(null);
        if (selected != null) invoke(team, "setCollisionRule", new Class<?>[]{rule}, selected);
        if (remove) return teamPacket.getMethod("createRemovePacket", playerTeam).invoke(null, team);
        return teamPacket.getMethod("createAddOrModifyPacket", playerTeam, boolean.class).invoke(null, team, true);
    }
    private Object handle(Player p) throws Exception { return p.getClass().getMethod("getHandle").invoke(p); }
    private Object listener(Player p) throws Exception { return field(handle(p), "connection"); }
    private Channel channel(Player p) throws Exception { return (Channel) field(field(listener(p), "connection"), "channel"); }
    private void send(Player p, Object data) throws Exception { listener(p).getClass().getMethod("send", packet).invoke(listener(p), data); }
    @Override public void attach(Player p) throws Exception {
        if (views.containsKey(p.getUniqueId())) return;
        Channel channel = channel(p); View view = new View(channel); views.put(p.getUniqueId(), view);
        try {
            channel.eventLoop().submit(() -> { if (channel.pipeline().get("znick") == null) channel.pipeline().addBefore("packet_handler", "znick", view); }).syncUninterruptibly();
            if (channel.pipeline().get("znick") != view) throw new IllegalStateException("Unable to install packet handler");
        } catch (Exception error) { views.remove(p.getUniqueId()); throw error; }
    }
    @Override public void forget(Player p) { View view = views.remove(p.getUniqueId()); if (view != null) view.detach(); }
    @Override public void close() { for (View v : views.values()) v.detach(); views.clear(); }
    @Override public void refresh(Player target, Nick previous) throws Exception {
        Class<?> craft = target.getClass();
        Method hide = accessible(craft.getDeclaredMethod("untrackAndHideEntity", org.bukkit.entity.Entity.class));
        Method show = accessible(craft.getDeclaredMethod("trackAndShowEntity", org.bukkit.entity.Entity.class));
        Class<?> remove = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket");
        Object removePacket = remove.getConstructor(List.class).newInstance(List.of(target.getUniqueId()));
        Method initial = info.getMethod("createSinglePlayerInitializing", Class.forName("net.minecraft.server.level.ServerPlayer"), boolean.class);
        Object initialPacket = initial.invoke(null, handle(target), true);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.canSee(target)) continue;
            if (previous != null) send(viewer, buildTeam(previous, true));
            if (viewer.equals(target)) { send(viewer, removePacket); send(viewer, initialPacket); }
            else { hide.invoke(viewer, target); show.invoke(viewer, target); }
        }
    }
    private final class View extends ChannelDuplexHandler {
        final Channel channel;
        final Map<UUID, String> sentTeams = new HashMap<>();
        View(Channel channel) { this.channel = channel; }
        void detach() { channel.eventLoop().execute(() -> { if (channel.pipeline().get("znick") == this) channel.pipeline().remove(this); }); }
        @SuppressWarnings({"rawtypes", "unchecked"})
        @Override public void write(ChannelHandlerContext ctx, Object message, ChannelPromise promise) throws Exception {
            try {
                NickState snapshot = plugin.state;
                String type = message.getClass().getSimpleName();
                if (info.isInstance(message)) {
                    EnumSet actions = ((EnumSet) field(message, "actions")).clone();
                    Collection<?> records = (Collection<?>) field(message, "entries");
                    boolean updatesDisplay = actions.contains(Enum.valueOf((Class<Enum>) action, "UPDATE_DISPLAY_NAME"));
                    List<Object> rewritten = new ArrayList<>();
                    for (Object record : records) {
                        UUID id = (UUID)entry.getMethod("profileId").invoke(record);
                        Nick nick = snapshot.players.get(id);
                        if (nick == null) { rewritten.add(record); continue; }
                        String teamSignature = nick.alias() + nick.raw() + plugin.collision;
                        if (!teamSignature.equals(sentTeams.get(id))) {
                            ctx.write(buildTeam(nick, false)); sentTeams.put(id, teamSignature);
                        }
                        Map<String,Object> changes = new HashMap<>();
                        Object oldProfile = entry.getMethod("profile").invoke(record);
                        if (oldProfile != null) changes.put("profile", hiddenProfile(oldProfile, nick));
                        if (updatesDisplay) changes.put("displayName", vanilla(nick.text()));
                        rewritten.add(changedRecord(record, changes));
                    }
                    message = infoCtor.newInstance(actions, rewritten);
                } else if (teamPacket.isInstance(message)) {
                    String name = (String)field(message, "name");
                    if (!name.startsWith("zn$")) {
                        int method = (int)field(message, "method");
                        Collection<String> players = (Collection<String>)field(message, "players");
                        List<String> keep = players.stream().filter(s -> !snapshot.managed(s)).toList();
                        if (keep.size() != players.size()) message = teamPacketCtor.newInstance(name, method, field(message,"parameters"), keep);
                    }
                } else if (type.equals("ClientboundPlayerInfoRemovePacket")) {
                    Collection<UUID> ids = (Collection<UUID>)field(message, "profileIds");
                    for (UUID id : ids) { Nick n = snapshot.players.get(id); if (n != null && sentTeams.remove(id) != null) ctx.write(buildTeam(n, true)); }
                } else if (type.equals("ClientboundBundlePacket")) {
                    // The encoder unbundles this type upstream on Paper; no mutable packet is shared.
                } else if (Set.of("ClientboundSystemChatPacket", "ClientboundDisguisedChatPacket", "ClientboundPlayerChatPacket", "ClientboundPlayerCombatKillPacket", "ClientboundSetTitleTextPacket", "ClientboundSetSubtitleTextPacket", "ClientboundSetActionBarTextPacket", "ClientboundTabListPacket").contains(type)) {
                    message = rewriteRecord(message, snapshot);
                } else if (Set.of("ClientboundSetScorePacket", "ClientboundResetScorePacket").contains(type)) {
                    if (message.getClass().isRecord()) {
                        Map<String,Object> changes = new HashMap<>();
                        for (RecordComponent part : message.getClass().getRecordComponents()) if (part.getType() == String.class && (part.getName().equals("owner") || part.getName().equals("ownerName"))) {
                            String value = (String)part.getAccessor().invoke(message); changes.put(part.getName(), snapshot.entry(value));
                        }
                        if (!changes.isEmpty()) message = changedRecord(message, changes);
                    }
                }
                ctx.write(message, promise);
            } catch (Throwable error) {
                plugin.getLogger().log(Level.SEVERE, "Ошибка обработки пакета Z_Nick: " + message.getClass().getName(), error);
                promise.tryFailure(error); ctx.close();
            }
        }
        private Object rewriteRecord(Object value, NickState state) throws Exception {
            if (value == null) return null;
            if (nmsComponent.isInstance(value)) return vanilla(state.rewrite(kyori(value)));
            if (value instanceof Optional<?> o) return o.isPresent() ? Optional.ofNullable(rewriteRecord(o.get(),state)) : o;
            if (!value.getClass().isRecord()) return value;
            String name = value.getClass().getName();
            if (!name.startsWith("net.minecraft.network.protocol.game.Clientbound") && !name.startsWith("net.minecraft.network.chat.ChatType$Bound")) return value;
            Map<String,Object> changes = new HashMap<>();
            for (RecordComponent part : value.getClass().getRecordComponents()) {
                Object old = part.getAccessor().invoke(value);
                Object newer = rewriteRecord(old,state);
                if (newer != old) changes.put(part.getName(),newer);
            }
            return changes.isEmpty() ? value : changedRecord(value,changes);
        }
    }
}
