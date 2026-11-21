package io.github.nishiki001.rttc.client.dev;

import io.github.nishiki001.rttc.RTTC;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.BackupConfirmScreen;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Dev-only scripted screenshots for comparing world-gen variants. Inactive unless the {@code rttc.devshots} system
 * property names a viewpoint file (see the {@code devshots} run in build.gradle). Each non-empty, non-# line is
 * {@code name x y z yaw pitch}; lines starting with {@code /} are server commands run right after the next shot's
 * teleport, once its chunks are loaded (e.g. to build a calibration scene). Smooth lighting and clouds are turned off. In a singleplayer world it switches to spectator, noon, clear weather, hides the HUD,
 * then for each viewpoint teleports, waits for the chunks to render and saves
 * {@code screenshots/<world>-<name>.png}. It quits the game after the last one. Test worlds carry world-gen
 * datapacks, which the game flags as experimental, so it also confirms that warning screen on its own.
 */
public final class DevShots {
    private record Shot(String name, double x, double y, double z, float yaw, float pitch, List<String> commands) {}

    private static final int MIN_WAIT = 160;  // ticks after each teleport
    private static final int MAX_WAIT = 900;
    private static final int COMMAND_DELAY = 40; // ticks after a teleport before that shot's commands run
    private final List<Shot> shots;
    private int index;
    private int wait = -1;
    private boolean prepared;
    private int quitIn = -1;

    private DevShots(List<Shot> shots) {
        this.shots = shots;
    }

    /** Called from the client entry point; a no-op unless the property is set. */
    public static void registerIfRequested() {
        String spec = System.getProperty("rttc.devshots", "");
        if (spec.isBlank()) {
            return;
        }
        List<Shot> shots = new ArrayList<>();
        List<String> pending = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(Path.of(spec))) {
                String l = line.strip();
                if (l.isEmpty() || l.startsWith("#")) {
                    continue;
                }
                if (l.startsWith("/")) { // server command, run after the NEXT shot's teleport
                    pending.add(l.substring(1));
                    continue;
                }
                String[] p = l.split("\\s+");
                shots.add(new Shot(p[0], Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]),
                        Float.parseFloat(p[4]), Float.parseFloat(p[5]), List.copyOf(pending)));
                pending.clear();
            }
        } catch (IOException | RuntimeException e) {
            RTTC.LOGGER.error("[devshots] cannot read viewpoint file {}", spec, e);
            return;
        }
        RTTC.LOGGER.info("[devshots] {} viewpoints from {}", shots.size(), spec);
        DevShots handler = new DevShots(shots);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> handler.tick());
    }

    /** Presses "I know what I'm doing!" on the experimental-settings backup prompt (dev runs only). */
    private static void confirmExperimentalWarning(BackupConfirmScreen screen) {
        for (GuiEventListener child : screen.children()) {
            if (child instanceof Button button && button.getMessage().getContents() instanceof TranslatableContents tc
                    && tc.getKey().equals("selectWorld.backupJoinSkipButton")) {
                RTTC.LOGGER.info("[devshots] skipping the experimental-settings warning for this test world");
                button.onPress();
                return;
            }
        }
    }

    private void tick() {
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        if (mc.level == null && mc.screen instanceof BackupConfirmScreen screen) {
            confirmExperimentalWarning(screen); // test worlds carry world-gen datapacks, which count as experimental
            return;
        }
        if (mc.level == null || mc.player == null || server == null) {
            return;
        }
        if (this.quitIn >= 0) {
            if (this.quitIn-- == 0) {
                RTTC.LOGGER.info("[devshots] done, quitting");
                mc.stop();
            }
            return;
        }
        if (!this.prepared) {
            this.prepared = true;
            mc.options.hideGui = true;
            mc.options.pauseOnLostFocus = false; // otherwise an unfocused window pauses and we shoot the menu
            mc.options.ambientOcclusion().set(false); // smooth lighting off: flat Alpha-style face shading
            mc.options.cloudStatus().set(CloudStatus.OFF); // clouds drift through calibration samples
            server.execute(() -> {
                ServerLevel level = server.overworld();
                server.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
                server.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
                level.setDayTime(6000);
                level.setWeatherParameters(1_000_000, 0, false, false);
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    p.setGameMode(GameType.SPECTATOR);
                }
            });
        }
        if (this.index >= this.shots.size()) {
            this.quitIn = 40;
            return;
        }
        Shot shot = this.shots.get(this.index);
        if (this.wait < 0) {
            server.execute(() -> {
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    p.teleportTo(server.overworld(), shot.x(), shot.y(), shot.z(), shot.yaw(), shot.pitch());
                }
            });
            this.wait = 0;
            return;
        }
        if (mc.screen != null) {
            mc.setScreen(null); // close pause menu / any screen, then wait the full time again
            this.wait = 0;
            return;
        }
        this.wait++;
        if (this.wait == COMMAND_DELAY && !shot.commands().isEmpty()) {
            // chunks around the new position are loaded by now; /fill refuses unloaded positions
            server.execute(() -> {
                for (String cmd : shot.commands()) {
                    RTTC.LOGGER.info("[devshots] /{}", cmd);
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), cmd);
                }
            });
        }
        int minWait = shot.commands().isEmpty() ? MIN_WAIT : COMMAND_DELAY + MIN_WAIT;
        boolean rendered = mc.levelRenderer.hasRenderedAllSections();
        if (this.wait >= MAX_WAIT || (this.wait >= minWait && rendered)) {
            String name = server.getWorldData().getLevelName() + "-" + shot.name() + ".png";
            int waited = this.wait;
            Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(),
                    msg -> RTTC.LOGGER.info("[devshots] {} ({} ticks, rendered={})", name, waited, rendered));
            this.index++;
            this.wait = -1;
        }
    }
}
