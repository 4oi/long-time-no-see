/* 
 * Copyright (C) 2016 toyblocks
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package jp.llv.ltns;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.logging.Level;
import java.util.stream.Collectors;
import net.md_5.bungee.api.chat.BaseComponent;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.Configuration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

/**
 *
 * @author toyblocks
 */
public class LongTimeNoSeeBukkit extends JavaPlugin implements Listener {

    private TimingManager manager;
    private LongTimeNoSeeConfig config;

    @Override
    public void onEnable() {
        File fileConf = new File(this.getDataFolder(), "config.yml");
        if (!fileConf.exists()) {
            this.saveDefaultConfig();
        }

        Configuration rawConfig = this.getConfig();
        this.config = new LongTimeNoSeeConfig();
        this.config.joinMessage = rawConfig.getString("join.message", null);
        this.config.joinBroadcast = rawConfig.getString("join.broadcast", null);
        this.config.joinOverride = rawConfig.getBoolean("join.override", true);
        this.config.firstJoinMessage = rawConfig.getString("firstjoin.message", null);
        this.config.firstJoinBroadcast = rawConfig.getString("firstjoin.broadcast", null);
        this.config.joinOverride = rawConfig.getBoolean("firstjoin.override", true);
        this.config.quitBroadcast = rawConfig.getString("quit.broadcast", null);
        this.config.quitOverride = rawConfig.getBoolean("quit.override", true);
        this.config.commandMessage = rawConfig.getString("command.message", null);
        this.config.dateFormat = rawConfig.getString("date.format", "yyyy/MM/dd H:mm");
        this.config.dateNow = rawConfig.getString("date.now", "now");
        this.config.dateNA = rawConfig.getString("date.na", "N/A");
        this.config.periodFormat = rawConfig.getString("period.format", "Ddays Hhours Mminutes Sseconds");
        this.config.periodNow = rawConfig.getString("period.now", "now");
        this.config.periodNA = rawConfig.getString("period.na", "N/A");

        if (this.config.commandMessage == null) {
            this.getLogger().log(Level.WARNING, "A command message is not defined.");
            return;
        }

        Path dataFolder = this.getDataFolder().toPath().resolve("data");
        this.manager = new TimingManager(this::getLogger, dataFolder, this.config);
        try {
            Files.createDirectories(dataFolder);
        } catch (IOException ex) {
            this.getLogger().log(Level.WARNING, "Failed to create data directory.", ex);
            return;
        }

        if (rawConfig.getBoolean("import", true)) {
            this.getLogger().info("Importing from bukkit cache...");
            rawConfig.set("import", false);
            this.saveConfig();
            for (OfflinePlayer op : this.getServer().getOfflinePlayers()) {
                if (this.manager.hasRecord(op.getUniqueId())) {
                    continue;
                }
                TimingRecord record = new TimingRecord();
                record.setFirst(op.getFirstPlayed());
                record.setLast(op.getLastPlayed());
                this.manager.saveRecord(op.getUniqueId(), record);
            }
        }

        this.manager.cache(this.getServer().getOnlinePlayers().stream().map(Player::getUniqueId).collect(Collectors.toList()));

        this.getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        this.manager.uncacheAll();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        OfflinePlayer player;
        if (!sender.hasPermission("longtimenosee.command")) {
            sender.sendMessage(ChatColor.RED + "Yout don't have permission to execute this command");
            return true;
        } else if (args.length < 1 && sender instanceof Player) {
            player = (Player) sender;
        } else if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Wrong command syntax: /seen <player>");
            return true;
        } else {
            player = this.getServer().getPlayerExact(args[0]);
        }

        if (player == null) {
            player = this.getServer().getOfflinePlayer(args[0]);
            if (!player.hasPlayedBefore()) {
                sender.sendMessage(ChatColor.RED + "The player has not played before");
                return true;
            }
        }
        TimingRecord record = this.manager.getRecord(player.getUniqueId());

        try {
            BaseComponent[] message = this.manager.format(this.config.commandMessage, player.getName(), record, this.getServer().getOnlinePlayers().size());
            if (sender instanceof Player) {
                ((Player) sender).spigot().sendMessage(message);
            } else {
                sender.sendMessage(BaseComponent.toLegacyText(message));
            }
        } catch (RuntimeException ex) {
            this.getLogger().log(Level.WARNING, "Failed to load a message. Is the message valid?", ex);
        }
        return true;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void on(PlayerJoinEvent eve) {
        if (this.config.joinOverride || (this.config.firstjoinOverride && !eve.getPlayer().hasPlayedBefore())) {
            eve.setJoinMessage(null);
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                LongTimeNoSeeBukkit.this.manager.cache(Collections.singletonList(eve.getPlayer().getUniqueId()));
                TimingRecord record = LongTimeNoSeeBukkit.this.manager.getRecord(eve.getPlayer().getUniqueId());

                record.setCount(record.getCount() + 1);
                record.setLoginnedAt(System.currentTimeMillis());

                try {
                    if (LongTimeNoSeeBukkit.this.config.firstJoinBroadcast != null && !eve.getPlayer().hasPlayedBefore()) {
                        BaseComponent[] component = LongTimeNoSeeBukkit.this.manager.format(LongTimeNoSeeBukkit.this.config.firstJoinBroadcast, eve.getPlayer().getName(), record, LongTimeNoSeeBukkit.this.getServer().getOnlinePlayers().size());
                        LongTimeNoSeeBukkit.this.getServer().getOnlinePlayers().stream()
                                .filter(p -> p != eve.getPlayer())
                                .map(Player::spigot)
                                .forEach(s -> s.sendMessage(component));
                    } else if (LongTimeNoSeeBukkit.this.config.joinBroadcast != null) {
                        BaseComponent[] component = LongTimeNoSeeBukkit.this.manager.format(LongTimeNoSeeBukkit.this.config.joinBroadcast, eve.getPlayer().getName(), record, LongTimeNoSeeBukkit.this.getServer().getOnlinePlayers().size());
                        LongTimeNoSeeBukkit.this.getServer().getOnlinePlayers().stream()
                                .filter(p -> p != eve.getPlayer())
                                .map(Player::spigot)
                                .forEach(s -> s.sendMessage(component));
                    }
                    if (LongTimeNoSeeBukkit.this.config.firstJoinMessage != null && !eve.getPlayer().hasPlayedBefore()) {
                        BaseComponent[] component = LongTimeNoSeeBukkit.this.manager.format(LongTimeNoSeeBukkit.this.config.firstJoinMessage, eve.getPlayer().getName(), record, LongTimeNoSeeBukkit.this.getServer().getOnlinePlayers().size());
                        eve.getPlayer().spigot().sendMessage(component);
                    } else if (LongTimeNoSeeBukkit.this.config.joinMessage != null) {
                        BaseComponent[] component = LongTimeNoSeeBukkit.this.manager.format(LongTimeNoSeeBukkit.this.config.joinMessage, eve.getPlayer().getName(), record, LongTimeNoSeeBukkit.this.getServer().getOnlinePlayers().size());
                        eve.getPlayer().spigot().sendMessage(component);
                    }
                } catch (RuntimeException ex) {
                    LongTimeNoSeeBukkit.this.getLogger().log(Level.WARNING, "Failed to load a message. Is the message valid?", ex);
                }
            }
        }.runTaskAsynchronously(this);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void on(PlayerQuitEvent eve) {
        TimingRecord record = this.manager.getRecord(eve.getPlayer().getUniqueId());

        this.manager.uncache(Collections.singleton(eve.getPlayer().getUniqueId()));
        record.setDuration(record.getCurrentDuration());
        record.setLast(System.currentTimeMillis());

        try {
            if (this.config.quitBroadcast != null) {
                BaseComponent[] component = this.manager.format(this.config.quitBroadcast, eve.getPlayer().getName(), record, this.getServer().getOnlinePlayers().size());
                this.getServer().getOnlinePlayers().stream()
                        .filter(p -> p != eve.getPlayer())
                        .map(Player::spigot)
                        .forEach(s -> s.sendMessage(component));
            }
            if (this.config.quitOverride) {
                eve.setQuitMessage(null);
            }
        } catch (RuntimeException ex) {
            this.getLogger().log(Level.WARNING, "Failed to load a message. Is the message valid?", ex);
        }

        this.manager.saveRecord(eve.getPlayer().getUniqueId(), record);
    }

}
