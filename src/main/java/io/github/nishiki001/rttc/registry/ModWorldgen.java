package io.github.nishiki001.rttc.registry;

import com.mojang.serialization.MapCodec;
import io.github.nishiki001.rttc.world.classic.ClassicBiomeSource;
import io.github.nishiki001.rttc.world.classic.ClassicTerrain;
import net.neoforged.neoforge.registries.DeferredHolder;

/** World-gen codec types used by the Classic preset's JSON ({@code rttc:beta_terrain}, {@code rttc:classic}). */
public final class ModWorldgen {
    public static final DeferredHolder<MapCodec<? extends net.minecraft.world.level.levelgen.DensityFunction>, MapCodec<ClassicTerrain>>
            BETA_TERRAIN = ModRegistries.DENSITY_FUNCTION_TYPES.register("beta_terrain", ClassicTerrain.CODEC::codec);
    public static final DeferredHolder<MapCodec<? extends net.minecraft.world.level.biome.BiomeSource>, MapCodec<ClassicBiomeSource>>
            CLASSIC_BIOME_SOURCE = ModRegistries.BIOME_SOURCES.register("classic", () -> ClassicBiomeSource.CODEC);

    private ModWorldgen() {}

    /** Loads this class so its entries are added before the registers are attached to the bus. */
    static void init() {}
}
