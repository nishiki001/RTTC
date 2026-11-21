package io.github.nishiki001.rttc.world.classic;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

/**
 * {@code rttc:classic}: picks the Classic world's 1.2.5-era biomes from the noise router's climate values.
 *
 * <p>The Classic noise settings wire the router so that {@code continents} is the same 2D function that sets
 * {@link ClassicTerrain}'s surface height, {@code erosion} is its river function, {@code temperature} and
 * {@code vegetation} are Beta-like climate noises and {@code ridges} (weirdness) is the mushroom-island bump
 * (&gt; 0 only on islands). Order: mushroom island, deep ocean, ocean, beach, river, hills, then Beta 1.7.3's
 * temperature × humidity lookup mapped onto our land biomes. Biomes are 2D, like the old game: y is ignored.
 */
public final class ClassicBiomeSource extends BiomeSource {
    public record WaterBiomes(Holder<Biome> deepOcean, Holder<Biome> ocean, Holder<Biome> frozenOcean,
                              Holder<Biome> beach, Holder<Biome> river, Holder<Biome> frozenRiver,
                              Holder<Biome> mushroomFields) {
        static final Codec<WaterBiomes> CODEC = RecordCodecBuilder.create(i -> i.group(
                Biome.CODEC.fieldOf("deep_ocean").forGetter(WaterBiomes::deepOcean),
                Biome.CODEC.fieldOf("ocean").forGetter(WaterBiomes::ocean),
                Biome.CODEC.fieldOf("frozen_ocean").forGetter(WaterBiomes::frozenOcean),
                Biome.CODEC.fieldOf("beach").forGetter(WaterBiomes::beach),
                Biome.CODEC.fieldOf("river").forGetter(WaterBiomes::river),
                Biome.CODEC.fieldOf("frozen_river").forGetter(WaterBiomes::frozenRiver),
                Biome.CODEC.fieldOf("mushroom_fields").forGetter(WaterBiomes::mushroomFields)
        ).apply(i, WaterBiomes::new));
    }

    public record LandBiomes(Holder<Biome> plains, Holder<Biome> forest, Holder<Biome> birchForest,
                             Holder<Biome> taiga, Holder<Biome> swamp, Holder<Biome> desert, Holder<Biome> jungle,
                             Holder<Biome> windsweptHills, Holder<Biome> snowyPlains, Holder<Biome> snowyTaiga) {
        static final Codec<LandBiomes> CODEC = RecordCodecBuilder.create(i -> i.group(
                Biome.CODEC.fieldOf("plains").forGetter(LandBiomes::plains),
                Biome.CODEC.fieldOf("forest").forGetter(LandBiomes::forest),
                Biome.CODEC.fieldOf("birch_forest").forGetter(LandBiomes::birchForest),
                Biome.CODEC.fieldOf("taiga").forGetter(LandBiomes::taiga),
                Biome.CODEC.fieldOf("swamp").forGetter(LandBiomes::swamp),
                Biome.CODEC.fieldOf("desert").forGetter(LandBiomes::desert),
                Biome.CODEC.fieldOf("jungle").forGetter(LandBiomes::jungle),
                Biome.CODEC.fieldOf("windswept_hills").forGetter(LandBiomes::windsweptHills),
                Biome.CODEC.fieldOf("snowy_plains").forGetter(LandBiomes::snowyPlains),
                Biome.CODEC.fieldOf("snowy_taiga").forGetter(LandBiomes::snowyTaiga)
        ).apply(i, LandBiomes::new));
    }

    /**
     * Thresholds on the router values. {@code river_width} must match {@link ClassicTerrain.Shape#riverWidth()}.
     * Temperatures here are Beta's 0..1 scale (router value v maps to clamp((v + 1) / 2)).
     */
    public record Thresholds(double deepOcean, double ocean, double beach, double hills,
                             double riverWidth, double mushroom, double frozen) {
        static final Codec<Thresholds> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.fieldOf("deep_ocean").forGetter(Thresholds::deepOcean),
                Codec.DOUBLE.fieldOf("ocean").forGetter(Thresholds::ocean),
                Codec.DOUBLE.fieldOf("beach").forGetter(Thresholds::beach),
                Codec.DOUBLE.fieldOf("hills").forGetter(Thresholds::hills),
                Codec.DOUBLE.fieldOf("river_width").forGetter(Thresholds::riverWidth),
                Codec.DOUBLE.fieldOf("mushroom").forGetter(Thresholds::mushroom),
                Codec.DOUBLE.fieldOf("frozen").forGetter(Thresholds::frozen)
        ).apply(i, Thresholds::new));
    }

    public static final MapCodec<ClassicBiomeSource> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            WaterBiomes.CODEC.fieldOf("water").forGetter(s -> s.water),
            LandBiomes.CODEC.fieldOf("land").forGetter(s -> s.land),
            Thresholds.CODEC.fieldOf("thresholds").forGetter(s -> s.thresholds)
    ).apply(i, ClassicBiomeSource::new));

    private final WaterBiomes water;
    private final LandBiomes land;
    private final Thresholds thresholds;

    public ClassicBiomeSource(WaterBiomes water, LandBiomes land, Thresholds thresholds) {
        this.water = water;
        this.land = land;
        this.thresholds = thresholds;
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        WaterBiomes w = this.water;
        LandBiomes l = this.land;
        return Stream.of(w.deepOcean(), w.ocean(), w.frozenOcean(), w.beach(), w.river(), w.frozenRiver(),
                w.mushroomFields(), l.plains(), l.forest(), l.birchForest(), l.taiga(), l.swamp(), l.desert(),
                l.jungle(), l.windsweptHills(), l.snowyPlains(), l.snowyTaiga()).distinct();
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        Climate.TargetPoint p = sampler.sample(x, y, z);
        Thresholds th = this.thresholds;
        double c = Climate.unquantizeCoord(p.continentalness());
        double river = Math.abs(Climate.unquantizeCoord(p.erosion()));
        double mushroom = Climate.unquantizeCoord(p.weirdness());
        double temp = to01(Climate.unquantizeCoord(p.temperature()));
        double hum = to01(Climate.unquantizeCoord(p.humidity()));
        boolean cold = temp < th.frozen();

        if (mushroom > th.mushroom()) {
            return this.water.mushroomFields();
        }
        if (c < th.deepOcean()) {
            return cold ? this.water.frozenOcean() : this.water.deepOcean();
        }
        if (c < th.ocean()) {
            return cold ? this.water.frozenOcean() : this.water.ocean();
        }
        if (c < th.beach()) {
            return this.water.beach();
        }
        if (river < th.riverWidth()) {
            return cold ? this.water.frozenRiver() : this.water.river();
        }
        if (c > th.hills()) {
            return this.land.windsweptHills();
        }
        return betaLand(temp, hum);
    }

    /**
     * Beta 1.7.3's BiomeGenBase lookup (humidity is multiplied by temperature first), on our biome set. One change:
     * dry tundra needs temp &lt; 0.3 instead of 0.5. With the Classic climate noises (stretch 3.5, temperature +0.3)
     * the land split is roughly plains 29, snowy plains 16, forest 14, birch 9, taiga 8, desert 8, snowy taiga 7,
     * jungle 6, swamp 4 (%).
     */
    private Holder<Biome> betaLand(double temp, double hum) {
        LandBiomes l = this.land;
        double h = hum * temp;
        if (temp < 0.1) {
            return hum > 0.5 ? l.snowyTaiga() : l.snowyPlains(); // tundra
        }
        if (h < 0.2) {
            if (temp < 0.3) { // Beta used 0.5 here, which made ~25% of all land tundra
                return l.snowyPlains(); // tundra
            }
            return temp < 0.95 ? l.plains() /* savanna */ : l.desert();
        }
        if (h > 0.5 && temp < 0.7) {
            return l.swamp();
        }
        if (temp < 0.5) {
            return l.taiga();
        }
        if (temp < 0.97) {
            return h < 0.35 ? l.plains() /* shrubland */ : l.forest();
        }
        if (h < 0.45) {
            return l.plains();
        }
        return h < 0.9 ? l.birchForest() /* seasonal forest */ : l.jungle() /* rainforest */;
    }

    private static double to01(double v) {
        return Mth.clamp((v + 1.0) / 2.0, 0.0, 1.0);
    }
}
