/*
 * Copyright 2016 toyblocks All rights reserved.
 */
package jp.llv.ltns;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.chat.ComponentSerializer;

/**
 *
 * @author toyblocks
 */
public class TimingManager {

    private static final Pattern DAYS_PATTERN = Pattern.compile("(.*)D\\{(.*?)\\}(.*)");
    private static final Pattern HOURS_PATTERN = Pattern.compile("(.*)H\\{(.*?)\\}(.*)");
    private static final Pattern MINUTES_PATTERN = Pattern.compile("(.*)M\\{(.*?)\\}(.*)");
    private static final Pattern SECONDS_PATTERN = Pattern.compile("(.*)S\\{(.*?)\\}(.*)");

    private final Map<UUID, TimingRecord> cache = new HashMap<>();
    private final Supplier<Logger> logger;
    private final Path dataFolder;
    private final LongTimeNoSeeConfig config;

    public TimingManager(Supplier<Logger> logger, Path dataFolder, LongTimeNoSeeConfig config) {
        this.logger = logger;
        this.dataFolder = dataFolder;
        this.config = config;
    }

    void cache(Collection<UUID> uuids) {
        uuids.forEach(id -> this.cache.put(id, this.getRecord(id)));
    }

    void uncache(Collection<UUID> uuids) {
        uuids.stream().forEach(u -> {
            this.saveRecord(u, this.cache.get(u));
            this.cache.remove(u);
        });
    }

    void uncacheAll() {
        this.cache.entrySet().stream().forEach(ent -> this.saveRecord(ent.getKey(), ent.getValue()));
    }

    BaseComponent[] format(String template, String name, TimingRecord record, int count) {
        SimpleDateFormat format = new SimpleDateFormat(this.config.dateFormat);
        String msgReplaced = template
                .replace("$player", name)
                .replace("$onlinecount", Integer.toString(count))
                .replace("$offlinecount", Integer.toString(this.getOfflineCount()))
                .replace("$now", format.format(new Date()))
                .replace("$first-join", formatDate(record.getFirst(), format))
                .replace("$played", formatDuration(record.getPlayedFor()))
                .replace("$count", Integer.toString(record.getCount()))
                .replace("$total-online", formatDuration(record.getCurrentDuration()))
                .replace("$average-online", formatDuration(record.getAverage()))
                .replace("$last-join", formatDate(record.getLoginnedAt(), format))
                .replace("$online", formatDuration(record.getLoginnedFor()))
                .replace("$last-quit", formatDate(record.getLast(), format))
                .replace("$last-online", formatDate(record.getCurrentLast(), format))
                .replace("$interval", formatDuration(record.getInterval()))
                .replace("$offline", formatDuration(record.getOfflineFor()));
        return ComponentSerializer.parse(msgReplaced);
    }

    String formatDate(Long period, SimpleDateFormat format) {
        if (period == null) {
            return this.config.dateNA;
        }
        if (Math.abs(System.currentTimeMillis() - period) < 1000) {
            return this.config.dateNow;
        } else {
            return format.format(new Date(period));
        }
    }

    String formatDuration(Long period) {
        if (period == null) {
            return this.config.periodNA;
        } else if (period < 1000) {
            return this.config.periodNow;
        }
        String result = this.config.periodFormat;
        long duration = Math.abs(period);
        long days = duration / 86_400_000;
        duration -= days * 86_400_000;
        long hours = duration / 3_600_000;
        duration -= hours * 3_600_000;
        long minutes = duration / 60_000;
        duration -= minutes * 60_000;
        long seconds = duration / 1_000;
        result = DAYS_PATTERN.matcher(result).replaceFirst(days == 0 ? "$1$3" : "$1" + days + "$2$3");
        result = HOURS_PATTERN.matcher(result).replaceFirst(hours == 0 ? "$1$3" : "$1" + hours + "$2$3");
        result = MINUTES_PATTERN.matcher(result).replaceFirst(minutes == 0 ? "$1$3" : "$1" + minutes + "$2$3");
        result = SECONDS_PATTERN.matcher(result).replaceFirst(seconds == 0 ? "$1$3" : "$1" + seconds + "$2$3");
        return result;
    }

    boolean hasRecord(UUID uuid) {
        if (this.cache.containsKey(uuid)) {
            return true;
        }
        Path file = this.dataFolder.resolve(uuid.toString());
        return Files.exists(file);
    }

    TimingRecord getRecord(UUID uuid) {
        if (this.cache.containsKey(uuid)) {
            return this.cache.get(uuid);
        }
        Path file = this.dataFolder.resolve(uuid.toString());

        if (Files.exists(file)) {
            try {
                TimingRecord result = new TimingRecord();
                result.read(file);
                return result;
            } catch (IOException ex) {
                this.logger.get().log(Level.WARNING, "Failed to load player data: " + file, ex);
                try {
                    Files.delete(file);
                } catch (IOException ex0) {
                }
            }
        }
        return new TimingRecord(true);
    }

    void saveRecord(UUID uuid, TimingRecord record) {
        if (record == null) {
            return;
        }
        Path file = this.dataFolder.resolve(uuid.toString());
        try {
            record.write(file);
        } catch (IOException ex) {
            this.logger.get().log(Level.WARNING, "Failed to save player data: " + file, ex);
        }
    }

    int getOfflineCount() {
        return this.dataFolder.toFile().listFiles().length;
    }

}
