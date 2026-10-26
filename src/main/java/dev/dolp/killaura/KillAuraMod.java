package dev.dolp.killaura;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.lwjgl.glfw.GLFW;

import java.util.Random;

public class KillAuraMod implements ClientModInitializer {

    private static final Random RNG = new Random();
    private static KeyBinding toggleKey, guiKey;
    private static LivingEntity pending; // picked and aimed in START, struck in END

    @Override
    public void onInitializeClient() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.dolp.killaura", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, "category.dolp"));
        guiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.dolp.clickgui", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, "category.dolp"));
        Settings.load(); // last saved config
        ClientTickEvents.START_CLIENT_TICK.register(KillAuraMod::start);
        ClientTickEvents.END_CLIENT_TICK.register(KillAuraMod::end);
    }

    // before the world tick: pick target and turn, so this tick's movement packet carries the new look
    private static void start(MinecraftClient mc) {
        while (toggleKey.wasPressed()) {
            if (mc.currentScreen != null) continue;
            Settings.ENABLED.bool = !Settings.ENABLED.bool;
            if (mc.player != null)
                mc.player.sendMessage(Text.literal("killaura " + (Settings.ENABLED.bool ? "on" : "off")), true);
        }
        while (guiKey.wasPressed()) {
            if (mc.currentScreen instanceof ClickGuiScreen) mc.setScreen(null);
            else if (mc.currentScreen == null) mc.setScreen(new ClickGuiScreen());
        }

        pending = null;
        if (!Settings.ENABLED.bool || mc.player == null || mc.world == null
                || mc.interactionManager == null || mc.currentScreen != null) return;
        aimAndPick(mc, mc.player);
    }

    // after the world tick: this tick's movement packet already left, server sees the aimed look
    private static void end(MinecraftClient mc) {
        LivingEntity t = pending;
        pending = null;
        PlayerEntity me = mc.player;
        if (t == null || me == null || mc.interactionManager == null || !t.isAlive()) return;
        double r = Settings.RANGE.num;
        if (me.squaredDistanceTo(t) > r * r) return;
        if (me.getAttackCooldownProgress(0.5F) < Settings.COOLDOWN.num) return;
        mc.interactionManager.attackEntity(me, t);
        me.swingHand(Hand.MAIN_HAND);
    }

    private static void aimAndPick(MinecraftClient mc, PlayerEntity me) {
        if (me.getAttackCooldownProgress(0.5F) < Settings.COOLDOWN.num) return;

        double range = Settings.RANGE.num;
        Vec3d eye = me.getEyePos();
        Vec3d look = me.getRotationVector();
        double fovCos = Math.cos(Math.toRadians(Settings.FOV.num / 2.0));

        LivingEntity best = null;
        double bestDist = range * range;
        for (LivingEntity e : mc.world.getEntitiesByClass(LivingEntity.class,
                me.getBoundingBox().expand(range), x -> x != me && x.isAlive() && isWanted(x))) {
            double dist = me.squaredDistanceTo(e);
            if (dist >= bestDist) continue;
            Vec3d p = aimPoint(e);
            if (look.dotProduct(p.subtract(eye).normalize()) < fovCos) continue;
            if (Settings.LOS.bool && blocked(mc, me, eye, p)) continue;
            best = e;
            bestDist = dist;
        }
        if (best == null) return;

        Vec3d d = aimPoint(best).subtract(eye);
        float wantYaw = (float) Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0F;
        float wantPitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));

        float yaw = me.getYaw(), pitch = me.getPitch();
        if ("snap".equals(Settings.ROT_MODE.modeName())) {
            yaw = wantYaw;
            pitch = wantPitch;
        } else {
            float speed = (float) Settings.ROT_SPEED.num;
            float jit = (float) Settings.ROT_JITTER.num;
            float dy = MathHelper.wrapDegrees(wantYaw - yaw);
            float dp = wantPitch - pitch;
            yaw += MathHelper.clamp(dy, -speed, speed) + (RNG.nextFloat() * 2 - 1) * jit;
            pitch += MathHelper.clamp(dp, -speed, speed) + (RNG.nextFloat() * 2 - 1) * jit;
            pitch = MathHelper.clamp(pitch, -90.0F, 90.0F);
        }
        me.setYaw(yaw);
        me.setPitch(pitch);

        // queue the hit only once the crosshair is actually on it
        double cos = me.getRotationVector().dotProduct(d.normalize());
        double angle = Math.toDegrees(Math.acos(MathHelper.clamp(cos, -1.0, 1.0)));
        if (angle > Settings.AIM_TOL.num) return;
        if (RNG.nextDouble() * 100 < Settings.SKIP.num) return;
        pending = best;
    }

    private static boolean isWanted(LivingEntity e) {
        return e instanceof PlayerEntity ? Settings.PLAYERS.bool : Settings.MOBS.bool;
    }

    private static Vec3d aimPoint(LivingEntity e) {
        return e.getPos().add(0, e.getHeight() * 0.5, 0);
    }

    private static boolean blocked(MinecraftClient mc, PlayerEntity me, Vec3d from, Vec3d to) {
        BlockHitResult hit = mc.world.raycast(new RaycastContext(from, to,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, me));
        return hit.getType() != HitResult.Type.MISS;
    }
}
