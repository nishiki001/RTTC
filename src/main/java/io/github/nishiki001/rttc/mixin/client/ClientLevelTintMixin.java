package io.github.nishiki001.rttc.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.nishiki001.rttc.RTTC;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Lets river and beach biomes take their grass and foliage colour from the land around them, so a river through
 * taiga is taiga-coloured instead of a lime stripe.
 *
 * <p>{@code ClientLevel.calculateBlockTint} averages a resolver over a (2r+1)² square of biome samples (biome blend).
 * We swap the biome of a sample that sits in {@code #rttc:tint_borrows_neighbors} (rivers) for the nearest
 * non-borrowing biome; for {@code #rttc:tint_blends_neighbors} (beaches) we do that for 60% of sample positions,
 * chosen by a fixed position hash, so the blend lands between the neighbour colour and the beach's own colour.
 * Grass and foliage only; water keeps its own biome colour.
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelTintMixin {
    private static final TagKey<Biome> BORROWS = TagKey.create(Registries.BIOME, RTTC.id("tint_borrows_neighbors"));
    private static final TagKey<Biome> BLENDS = TagKey.create(Registries.BIOME, RTTC.id("tint_blends_neighbors"));
    private static final int STEP = 4;
    private static final int MAX_DISTANCE = 48;
    private static final int BLEND_PERCENT = 60;
    private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}};

    @WrapOperation(
            method = "calculateBlockTint",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/ClientLevel;getBiome(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/Holder;"))
    private Holder<Biome> rttc$neighbourTint(ClientLevel level, BlockPos pos, Operation<Holder<Biome>> original,
                                             @Local(argsOnly = true) ColorResolver resolver) {
        Holder<Biome> biome = original.call(level, pos);
        if (resolver != BiomeColors.GRASS_COLOR_RESOLVER && resolver != BiomeColors.FOLIAGE_COLOR_RESOLVER) {
            return biome;
        }
        boolean borrow = biome.is(BORROWS);
        if (!borrow && !(biome.is(BLENDS) && Math.floorMod(hash(pos.getX(), pos.getZ()), 100) < BLEND_PERCENT)) {
            return biome;
        }
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        for (int d = STEP; d <= MAX_DISTANCE; d += STEP) {
            for (int[] dir : DIRS) {
                probe.set(pos.getX() + dir[0] * d, pos.getY(), pos.getZ() + dir[1] * d);
                Holder<Biome> other = original.call(level, probe.immutable());
                if (!other.is(BORROWS) && !other.is(BLENDS)) {
                    return other;
                }
            }
        }
        return biome; // open water or a huge beach: keep the biome's own colour
    }

    private static int hash(int x, int z) {
        int h = x * 0x27d4eb2d ^ z * 0x165667b1;
        return h ^ (h >>> 15);
    }
}
