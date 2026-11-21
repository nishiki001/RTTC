package io.github.nishiki001.rttc.mixin.client;

import com.google.common.collect.ImmutableList;
import io.github.nishiki001.rttc.client.ForcedProgrammerArt;
import java.util.Collection;
import java.util.List;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps {@code programmer_art} directly above {@code vanilla} in every selected-pack list.
 *
 * <p>{@code reload()} and {@code setSelected()} both go through {@code rebuildSelected}; {@code addPack} and
 * {@code removePack} write {@code selected} directly, so they are fixed up afterwards too.
 */
@Mixin(PackRepository.class)
public abstract class PackRepositoryMixin {
    @Shadow
    private List<Pack> selected;

    @Inject(method = "rebuildSelected", at = @At("RETURN"), cancellable = true)
    private void rttc$orderProgrammerArt(Collection<String> ids, CallbackInfoReturnable<List<Pack>> cir) {
        cir.setReturnValue(ForcedProgrammerArt.placeAboveVanilla(cir.getReturnValue()));
    }

    @Inject(method = {"addPack", "removePack"}, at = @At("TAIL"))
    private void rttc$orderAfterEdit(String id, CallbackInfoReturnable<Boolean> cir) {
        this.selected = ForcedProgrammerArt.placeAboveVanilla(this.selected);
    }
}
