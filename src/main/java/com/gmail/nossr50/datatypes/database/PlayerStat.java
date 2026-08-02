package com.gmail.nossr50.datatypes.database;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public final class PlayerStat implements Comparable<PlayerStat> {
    private final String playerName;
    private final int value;

    public PlayerStat(String playerName, int value) {
        this.playerName = playerName;
        this.value = value;
    }

    public String playerName() {
        return playerName;
    }

    public int value() {
        return value;
    }

    @Override
    public int compareTo(@NotNull PlayerStat o) {
        // Descending order
        int cmp = Integer.compare(o.value, this.value);
        if (cmp != 0) return cmp;
        // Tie-breaker
        return this.playerName.compareTo(o.playerName);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PlayerStat)) return false;
        PlayerStat that = (PlayerStat) o;
        return value == that.value && Objects.equals(playerName, that.playerName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(playerName, value);
    }

    @Override
    public String toString() {
        return "PlayerStat[" +
                "playerName=" + playerName + ", " +
                "value=" + value + ']';
    }
}
