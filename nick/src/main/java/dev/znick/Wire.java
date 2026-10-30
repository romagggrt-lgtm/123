package dev.znick;

import org.bukkit.entity.Player;

interface Wire extends AutoCloseable {
    void attach(Player viewer) throws Exception;
    void refresh(Player target, Nick previous) throws Exception;
    void forget(Player viewer);
    @Override void close();
}
