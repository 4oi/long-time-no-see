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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.logging.Level;

import com.github.kory33.mojangapi.MojangAPI;
import com.google.common.io.ByteStreams;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;

/**
 *
 * @author toyblocks
 */
public class LongTimeNoSeeBungee extends Plugin implements Listener {

    private TimingManager manager;
    private LongTimeNoSeeConfig config;

    @Override
    public void onEnable() {
        File fileConf = new File(this.getDataFolder(), "config.yml");
        if (!fileConf.exists()) {
            try {
                this.getDataFolder().mkdirs();
                fileConf.createNewFile();
            } catch (IOException ex) {
            }
            try (FileOutputStream os = new FileOutputStream(fileConf);
                    InputStream is = this.getResourceAsStream("config.yml")) {
                ByteStreams.copy(is, os);
            } catch (IOException ex) {
                this.getLogger().log(Level.WARNING, "Failed to save default config", ex);
                return;
            }
        }

        try {
            Configuration rawConfig = ConfigurationProvider.getProvider(YamlConfiguration.class).load(fileConf);
            this.config = new LongTimeNoSeeConfig();
            this.config.firstJoinMessage = rawConfig.getString("join.message", null);
            this.config.firstJoinBroadcast = rawConfig.getString("join.broadcast", null);
            this.config.joinMessage = rawConfig.getString("join.message", null);
            this.config.joinBroadcast = rawConfig.getString("join.broadcast", null);
            this.config.quitBroadcast = rawConfig.getString("quit.broadcast", null);
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
        } catch (IOException ex) {
            this.getLogger().log(Level.WARNING, "Failed to save default config", ex);
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

        this.getProxy().getPluginManager().registerListener(this, this);
        this.getProxy().getPluginManager().registerCommand(this, new Command("seen", "longtimenosee.command", "nosee", "ltns") {
            @Override
            public void execute(CommandSender sender, String[] args) {
                LongTimeNoSeeBungee.this.execute(sender, args);
            }
        });
    }

    @Override
    public void onDisable() {
        this.manager.uncacheAll();
    }

    private void sendTimingRecordData(CommandSender target, TimingRecord record, String playerName) {
        try {
            target.sendMessage(this.manager.format(this.config.commandMessage, playerName, record, this.getProxy().getPlayers().size()));
        } catch (RuntimeException ex) {
            this.getLogger().log(Level.WARNING, "Failed to load a message. Is the message valid?", ex);
        }
    }
    
    public void execute(CommandSender sender, String[] args) {
        ProxiedPlayer player;
        if (args.length < 1 && sender instanceof ProxiedPlayer) {
            player = (ProxiedPlayer) sender;
        } else if (args.length < 1) {
            sender.sendMessage(new ComponentBuilder("Wrong command syntax: /seen <player>").color(ChatColor.RED).create());
            return;
        } else {
            player = this.getProxy().getPlayer(args[0]);
        }

        if (player != null) {
            TimingRecord record = this.manager.getRecord(player.getUniqueId());
            this.sendTimingRecordData(sender, record, player.getName());
            return;
        }

        String playerName = args[0];
        MojangAPI.asyncGetUUIDFromUsername(playerName)
            .thenAccept((uuid) -> {
                if (uuid == null) {
                    sender.sendMessage(new ComponentBuilder("The player is not found.").color(ChatColor.RED).create());
                    return;
                }
                TimingRecord record = this.manager.getRecord(uuid);
                
                if (record.isFirsttime()) {
                    sender.sendMessage(new ComponentBuilder("The player has not played before").color(ChatColor.RED).create());
                    return;
                }
                
                this.sendTimingRecordData(sender, record, playerName);
                return;
            });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(PostLoginEvent eve) {
        this.getProxy().getScheduler().runAsync(this, () -> {
            this.manager.cache(Collections.singletonList(eve.getPlayer().getUniqueId()));
            TimingRecord record = this.manager.getRecord(eve.getPlayer().getUniqueId());

            record.setCount(record.getCount() + 1);
            record.setLoginnedAt(System.currentTimeMillis());

            try {
                if (this.config.firstJoinBroadcast != null && record.isFirsttime()) {
                    BaseComponent[] component = this.manager.format(this.config.joinBroadcast, eve.getPlayer().getName(), record, this.getProxy().getPlayers().size());
                    this.getProxy().getPlayers().stream()
                            .filter(p -> p != eve.getPlayer())
                            .forEach(s -> s.sendMessage(component));
                } else if (this.config.joinBroadcast != null) {
                    BaseComponent[] component = this.manager.format(this.config.firstJoinBroadcast, eve.getPlayer().getName(), record, this.getProxy().getPlayers().size());
                    this.getProxy().getPlayers().stream()
                            .filter(p -> p != eve.getPlayer())
                            .forEach(s -> s.sendMessage(component));
                }
                if (this.config.firstJoinMessage != null && record.isFirsttime()) {
                    BaseComponent[] component = this.manager.format(this.config.firstJoinMessage, eve.getPlayer().getName(), record, this.getProxy().getPlayers().size());
                    eve.getPlayer().sendMessage(component);
                } else if (this.config.joinMessage != null) {
                    BaseComponent[] component = this.manager.format(this.config.joinMessage, eve.getPlayer().getName(), record, this.getProxy().getPlayers().size());
                    eve.getPlayer().sendMessage(component);
                }
            } catch (RuntimeException ex) {
                this.getLogger().log(Level.WARNING, "Failed to load a message. Is the message valid?", ex);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(PlayerDisconnectEvent eve) {
        TimingRecord record = this.manager.getRecord(eve.getPlayer().getUniqueId());

        this.manager.uncache(Collections.singleton(eve.getPlayer().getUniqueId()));
        record.setDuration(record.getCurrentDuration());
        record.setLast(System.currentTimeMillis());

        try {
            if (this.config.quitBroadcast != null) {
                BaseComponent[] component = this.manager.format(this.config.quitBroadcast, eve.getPlayer().getName(), record, this.getProxy().getPlayers().size());
                this.getProxy().getPlayers().stream()
                        .filter(p -> p != eve.getPlayer())
                        .forEach(s -> s.sendMessage(component));
            }
        } catch (RuntimeException ex) {
            this.getLogger().log(Level.WARNING, "Failed to load a message. Is the message valid?", ex);
        }

        this.manager.saveRecord(eve.getPlayer().getUniqueId(), record);
    }

}
