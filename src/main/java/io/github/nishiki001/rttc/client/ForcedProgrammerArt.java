package io.github.nishiki001.rttc.client;

import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.BuiltInPackSource;
import net.minecraft.server.packs.repository.KnownPack;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.neoforge.event.AddPackFindersEvent;

/**
 * Forces vanilla's built-in {@code programmer_art} resource pack on.
 *
 * <p>Re-registers the pack under its vanilla id with a required, fixed-bottom selection config. Sources
 * added through {@link AddPackFindersEvent} run after vanilla's {@code ClientPackSource}, and
 * {@code PackRepository} keeps the last pack per id, so ours replaces the removable vanilla entry. A fixed
 * BOTTOM position puts it directly above "vanilla" and below mod resources and user packs, so those can
 * still override it.
 */
final class ForcedProgrammerArt {
    static final String PACK_ID = "programmer_art";
    private static final PackSelectionConfig FORCED = new PackSelectionConfig(true, Pack.Position.BOTTOM, true);

    private ForcedProgrammerArt() {}

    static void onAddPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        event.addRepositorySource(new Source(mc));
    }

    /** Scans vanilla's bundled {@code resourcepacks/} like ClientPackSource, but only emits programmer_art. */
    private static final class Source extends BuiltInPackSource {
        Source(Minecraft mc) {
            super(PackType.CLIENT_RESOURCES, mc.getVanillaPackResources(),
                    ResourceLocation.withDefaultNamespace("resourcepacks"), mc.directoryValidator());
        }

        @Nullable
        @Override
        protected Pack createVanillaPack(PackResources resources) {
            return null; // vanilla itself stays with ClientPackSource
        }

        @Override
        protected Component getPackTitle(String id) {
            return Component.translatable("resourcePack.programmer_art.name");
        }

        @Nullable
        @Override
        protected Pack createBuiltinPack(String id, Pack.ResourcesSupplier resources, Component title) {
            if (!PACK_ID.equals(id)) {
                return null;
            }
            PackLocationInfo location = new PackLocationInfo(id, title, PackSource.BUILT_IN, Optional.of(KnownPack.vanilla(id)));
            return Pack.readMetaAndCreate(location, resources, PackType.CLIENT_RESOURCES, FORCED);
        }
    }
}
