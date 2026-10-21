package vn.svframe.svframemmo.cobblemon.gameplay;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;

/** Server-owned progression and aggression settings, migrated by the integration config loader. */
public final class PokemonGameplayConfig {
  @SerializedName("experience-enabled")
  public boolean experienceEnabled = true;

  public Reward battle = new Reward(20, 5, 1000);
  public Reward overworld = new Reward(10, 2.5, 250);
  public Hostility hostility = new Hostility();

  public static final class Reward {
    public double base, perLevel, maximum;

    public Reward(double base, double perLevel, double maximum) {
      this.base = base;
      this.perLevel = perLevel;
      this.maximum = maximum;
    }

    public double amount(int levels) {
      return Math.min(maximum, base + Math.max(0, levels) * perLevel);
    }

    void validate() {
      if (!Double.isFinite(base)
          || !Double.isFinite(perLevel)
          || !Double.isFinite(maximum)
          || base < 0
          || perLevel < 0
          || maximum <= 0)
        throw new IllegalArgumentException(
            "Pokemon rewards must be finite and nonnegative with positive maximum");
    }
  }

  public static final class Hostility {
    public boolean enabled = true;

    @SerializedName("detect-range")
    public double detectRange = 16;

    @SerializedName("leash-range")
    public double leashRange = 32;

    @SerializedName("max-attackers-per-player")
    public int maxAttackersPerPlayer = 4;

    @SerializedName("windup-ticks")
    public int windupTicks = 16;

    @SerializedName("cooldown-ticks")
    public int cooldownTicks = 50;

    @SerializedName("lose-target-ticks")
    public int loseTargetTicks = 80;

    @SerializedName("movement-speed")
    public double movementSpeed = 1.15;

    @SerializedName("maximum-damage")
    public double maximumDamage = 8;

    @SerializedName("excluded-species")
    public List<String> excludedSpecies = new ArrayList<>();

    @SerializedName("excluded-dimensions")
    public List<String> excludedDimensions = new ArrayList<>();

    @SerializedName("excluded-regions")
    public List<Region> excludedRegions = new ArrayList<>();
  }

  public static final class Region {
    public String dimension = "minecraft:overworld";
    public double minX, minY, minZ, maxX, maxY, maxZ;

    public boolean contains(String world, double x, double y, double z) {
      return dimension.equals(world)
          && x >= minX
          && x <= maxX
          && y >= minY
          && y <= maxY
          && z >= minZ
          && z <= maxZ;
    }
  }

  public void validate() {
    if (battle == null || overworld == null || hostility == null)
      throw new IllegalArgumentException("Pokemon gameplay sections cannot be null");
    battle.validate();
    overworld.validate();
    var h = hostility;
    if (!Double.isFinite(h.detectRange)
        || h.detectRange < 1
        || h.detectRange > 64
        || !Double.isFinite(h.leashRange)
        || h.leashRange < h.detectRange
        || h.leashRange > 128)
      throw new IllegalArgumentException(
          "Pokemon detect/leash range must be 1..64 and detect..128");
    if (h.maxAttackersPerPlayer < 1
        || h.maxAttackersPerPlayer > 32
        || h.windupTicks < 5
        || h.cooldownTicks < h.windupTicks
        || h.cooldownTicks > 1200
        || h.loseTargetTicks < 1
        || h.loseTargetTicks > 1200)
      throw new IllegalArgumentException("Invalid Pokemon attack count/timings");
    if (!Double.isFinite(h.movementSpeed)
        || h.movementSpeed <= 0
        || h.movementSpeed > 3
        || !Double.isFinite(h.maximumDamage)
        || h.maximumDamage <= 0
        || h.maximumDamage > 100)
      throw new IllegalArgumentException("Invalid Pokemon movement/damage bounds");
    if (h.excludedSpecies == null || h.excludedDimensions == null || h.excludedRegions == null)
      throw new IllegalArgumentException("Pokemon exclusions cannot be null");
    for (var region : h.excludedRegions)
      if (region == null
          || region.dimension == null
          || !Double.isFinite(region.minX)
          || !Double.isFinite(region.maxX)
          || !Double.isFinite(region.minY)
          || !Double.isFinite(region.maxY)
          || !Double.isFinite(region.minZ)
          || !Double.isFinite(region.maxZ)
          || region.minX > region.maxX
          || region.minY > region.maxY
          || region.minZ > region.maxZ)
        throw new IllegalArgumentException("Invalid excluded Pokemon region");
  }
}
