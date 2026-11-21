package io.github.nishiki001.rttc.registry;

import com.mojang.serialization.MapCodec;
import io.github.nishiki001.rttc.RTTC;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** All DeferredRegisters live here; content classes (ModBlocks, ModItems, ...) add entries to them. */
public final class ModRegistries {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(RTTC.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(RTTC.MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, RTTC.MODID);
    public static final DeferredRegister<MapCodec<? extends DensityFunction>> DENSITY_FUNCTION_TYPES =
            DeferredRegister.create(Registries.DENSITY_FUNCTION_TYPE, RTTC.MODID);
    public static final DeferredRegister<MapCodec<? extends BiomeSource>> BIOME_SOURCES =
            DeferredRegister.create(Registries.BIOME_SOURCE, RTTC.MODID);

    private ModRegistries() {}

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        CREATIVE_TABS.register(modBus);
        ModWorldgen.init();
        DENSITY_FUNCTION_TYPES.register(modBus);
        BIOME_SOURCES.register(modBus);
    }
}
