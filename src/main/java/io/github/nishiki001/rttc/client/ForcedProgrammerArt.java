package io.github.nishiki001.rttc.client;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
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
 * Forces vanilla's built-in {@code programmer_art} resource pack on, directly above Default.
 *
 * <p>Re-registers the pack under its vanilla id with a required, fixed selection config. Sources added through
 * {@link AddPackFindersEvent} run after vanilla's {@code ClientPackSource}, and {@code PackRepository} keeps the
 * last pack per id, so ours replaces the removable vanilla entry. Required means it is always re-selected;
 * fixed means the pack screen won't let anyone move it and {@code Options} never saves it.
 *
 * <p>The config alone does NOT give the right order: {@code Pack.Position.insert} puts a fixed BOTTOM pack below
 * every non-fixed pack, and "vanilla" is BOTTOM but not fixed, so the pack would land under Default and lose.
 * {@code mixin.client.PackRepositoryMixin} calls {@link #placeAboveVanilla} on every selected-list rebuild.
 * Result, top to bottom: user packs, Mod Resources, Programmer Art, Default.
 */
public final class ForcedProgrammerArt {
    static final String PACK_ID = "programmer_art";
    private static final String VANILLA_ID = "vanilla";
    private static final PackSelectionConfig FORCED = new PackSelectionConfig(true, Pack.Position.BOTTOM, true);

    private ForcedProgrammerArt() {}

    static void onAddPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        event.addRepositorySource(new Source(mc));
    }

    /**
     * Returns {@code packs} (index 0 = bottom) with programmer_art moved to directly after vanilla. Lists
     * without both packs, such as data-pack repositories, are returned unchanged.
     */
    public static List<Pack> placeAboveVanilla(List<Pack> packs) {
        int art = indexOf(packs, PACK_ID);
        int vanilla = indexOf(packs, VANILLA_ID);
        if (art < 0 || vanilla < 0 || art == vanilla + 1) {
            return packs;
        }
        List<Pack> out = new ArrayList<>(packs);
        Pack pack = out.remove(art);
        out.add(indexOf(out, VANILLA_ID) + 1, pack);
        return ImmutableList.copyOf(out);
    }

    private static int indexOf(List<Pack> packs, String id) {
        for (int i = 0; i < packs.size(); i++) {
            if (packs.get(i).getId().equals(id)) {
                return i;
            }
        }
        return -1;
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
