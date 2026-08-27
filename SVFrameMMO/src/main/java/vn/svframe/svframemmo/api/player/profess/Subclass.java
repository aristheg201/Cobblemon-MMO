package vn.svframe.svframemmo.api.player.profess;

import java.util.Objects;

public final class Subclass {
    private final PlayerClass profess;
    private final int level;

    public Subclass(PlayerClass profess, int level) {
        this.profess = Objects.requireNonNull(profess, "Subclass cannot be null");
        this.level = level;
    }

    public PlayerClass getProfess() { return profess; }
    public int getLevel() { return level; }
}
