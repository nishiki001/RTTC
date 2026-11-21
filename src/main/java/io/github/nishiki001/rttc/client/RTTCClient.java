package io.github.nishiki001.rttc.client;

import io.github.nishiki001.rttc.RTTC;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/** Client-only entry point. Never referenced from common code. */
@Mod(value = RTTC.MODID, dist = Dist.CLIENT)
public final class RTTCClient {
    public RTTCClient(ModContainer container) {
    }
}
