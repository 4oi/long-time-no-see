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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;

/**
 *
 * @author toyblocks
 */
/*package*/ class TimingRecord {

    private long first;
    private int count;
    private long duration;
    private Long last;
    private Long loginnedAt;
    private boolean firsttime = false;

    public TimingRecord() {
        this.first = System.currentTimeMillis();
        this.count = 0;
        this.duration = 0;
    }

    public TimingRecord(boolean firsttime) {
        this();
        this.firsttime = true;
    }

    public long getFirst() {
        return first;
    }

    public void setFirst(long first) {
        this.first = first;
    }
    
    public long getPlayedFor() {
        return System.currentTimeMillis() - this.first;
    }

    public long getDuration() {
        return duration;
    }

    public long getCurrentDuration() {
        if (this.loginnedAt == null) {
            return this.duration;
        }
        return this.duration + getLoginnedFor();
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public Long getLast() {
        return last;
    }
    
    public Long getInterval() {
        return this.last == null ? null : System.currentTimeMillis() - this.last;
    }

    public Long getCurrentLast() {
        return this.loginnedAt == null ? this.last : System.currentTimeMillis();
    }
    
    public Long getOfflineFor() {
        if (this.last == null) {
            return null;
        } else if (this.loginnedAt == null) {
            return System.currentTimeMillis() - this.last;
        } else {
            return 0L;
        }
    }

    public void setLast(long last) {
        this.last = last;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public Long getAverage() {
        return this.count == 0 ? null : this.getCurrentDuration() / this.count;
    }

    public Long getLoginnedAt() {
        return loginnedAt;
    }

    public void setLoginnedAt(long loginnedAt) {
        this.loginnedAt = loginnedAt;
    }

    public Long getLoginnedFor() {
        return this.loginnedAt == null ? null : System.currentTimeMillis() - this.loginnedAt;
    }

    public boolean isFirsttime() {
        return firsttime;
    }

    public void write(Path path) throws IOException {
        Files.write(path,
                Arrays.asList(toString(this.first), toString(this.count), toString(this.duration), toString(this.last)),
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE
        );
    }

    public void read(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path);
        if (lines.size() < 4) {
            throw new IOException("Invalid file format: " + path.toString());
        }
        try {
            this.first = Long.parseLong(lines.get(0));
            this.count = Integer.parseInt(lines.get(1));
            this.duration = Long.parseLong(lines.get(2));
            this.last = Long.parseLong(lines.get(3));
            if (this.last < 0) {
                this.last = null;
            }
        } catch (NumberFormatException ex) {
            throw new IOException("Invalid number format: " + path.toString(), ex);
        }
    }
    
    private static String toString(Number number) {
        return number == null ? "-1" : number.toString();
    }

}
