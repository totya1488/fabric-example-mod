package dev.dolp.killaura;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.Random;

public class KillAuraMod implements ClientModInitializer {

    private static final Random RNG = new Random();
    private static KeyMapping toggleKey, guiKey;
    private static LivingEntity pending; // picked and aimed in START, struck in END

    @Override
    public void onInitializeClient() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.dolp.killaura", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, "category.dolp"));
        guiKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.dolp.clickgui", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, "category.dolp"));
        Settings.load(); // last saved config
        ClientTickEvents.START_CLIENT_TICK.register(KillAuraMod::start);
        ClientTickEvents.END_CLIENT_TICK.register(KillAuraMod::end);
    }

    // before the world tick: pick target and turn, so this tick's movement packet carries the new look
    private static void start(Minecraft mc) {
        while (toggleKey.consumeClick()) {
            if (mc.screen != null) continue;
            Settings.ENABLED.bool = !Settings.ENABLED.bool;
            if (mc.player != null)
                mc.player.displayClientMessage(Component.literal("killaura " + (Settings.ENABLED.bool ? "on" : "off")), true);
        }
        while (guiKey.consumeClick()) {
            if (mc.screen instanceof ClickGuiScreen) mc.setScreen(null);
            else if (mc.screen == null) mc.setScreen(new ClickGuiScreen());
        }

        pending = null;
        if (!Settings.ENABLED.bool || mc.player == null || mc.level == null
                || mc.gameMode == null || mc.screen != null) return;
        aimAndPick(mc, mc.player);
    }

    // after the world tick: this tick's movement packet already left, server sees the aimed look
    private static void end(Minecraft mc) {
        LivingEntity t = pending;
        pending = null;
        Player me = mc.player;
        if (t == null || me == null || mc.gameMode == null || !t.isAlive()) return;
        double r = Settings.RANGE.num;
        if (me.distanceToSqr(t) > r * r) return;
        if (me.getAttackStrengthScale(0.5F) < Settings.COOLDOWN.num) return;
        mc.gameMode.attack(me, t);
        me.swing(InteractionHand.MAIN_HAND);
    }

    private static void aimAndPick(Minecraft mc, Player me) {
        if (me.getAttackStrengthScale(0.5F) < Settings.COOLDOWN.num) return;

        double range = Settings.RANGE.num;
        Vec3 eye = me.getEyePosition();
        Vec3 look = me.getViewVector(1.0F);
        double fovCos = Math.cos(Math.toRadians(Settings.FOV.num / 2.0));

        LivingEntity best = null;
        double bestDist = range * range;
        for (LivingEntity e : mc.level.getEntitiesOfClass(LivingEntity.class,
                me.getBoundingBox().inflate(range), x -> x != me && x.isAlive() && isWanted(x))) {
            double dist = me.distanceToSqr(e);
            if (dist >= bestDist) continue;
            Vec3 p = aimPoint(e);
            if (look.dot(p.subtract(eye).normalize()) < fovCos) continue;
            if (Settings.LOS.bool && blocked(mc, me, eye, p)) continue;
            best = e;
            bestDist = dist;
        }
        if (best == null) return;

        Vec3 d = aimPoint(best).subtract(eye);
        float wantYaw = (float) Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0F;
        float wantPitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));

        float yaw = me.getYRot(), pitch = me.getXRot();
        if ("snap".equals(Settings.ROT_MODE.modeName())) {
            yaw = wantYaw;
            pitch = wantPitch;
        } else {
            float speed = (float) Settings.ROT_SPEED.num;
            float jit = (float) Settings.ROT_JITTER.num;
            float dy = Mth.wrapDegrees(wantYaw - yaw);
            float dp = wantPitch - pitch;
            yaw += Mth.clamp(dy, -speed, speed) + (RNG.nextFloat() * 2 - 1) * jit;
            pitch += Mth.clamp(dp, -speed, speed) + (RNG.nextFloat() * 2 - 1) * jit;
            pitch = Mth.clamp(pitch, -90.0F, 90.0F);
        }
        me.setYRot(yaw);
        me.setXRot(pitch);

        // queue the hit only once the crosshair is actually on it
        double cos = me.getViewVector(1.0F).dot(d.normalize());
        double angle = Math.toDegrees(Math.acos(Mth.clamp(cos, -1.0, 1.0)));
        if (angle > Settings.AIM_TOL.num) return;
        if (RNG.nextDouble() * 100 < Settings.SKIP.num) return;
        pending = best;
    }

    private static boolean isWanted(LivingEntity e) {
        return e instanceof Player ? Settings.PLAYERS.bool : Settings.MOBS.bool;
    }

    private static Vec3 aimPoint(LivingEntity e) {
        return e.position().add(0, e.getBbHeight() * 0.5, 0);
    }

    private static boolean blocked(Minecraft mc, Player me, Vec3 from, Vec3 to) {
        BlockHitResult hit = mc.level.clip(new ClipContext(from, to,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, me));
        return hit.getType() != HitResult.Type.MISS;
    }
}
