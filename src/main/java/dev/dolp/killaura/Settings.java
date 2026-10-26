package dev.dolp.killaura;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class Settings {

    public static final class Setting {
        public enum Type { BOOL, NUM, MODE, ACTION }

        public final String tab, name, key;
        public final Type type;
        public final boolean defBool;
        public final double defNum, min, max, step;
        public final int defMode;
        public final String[] modes;
        public final Supplier<String> action;

        public boolean bool;
        public double num;
        public int mode;

        Setting(String tab, String name, String key, Type type, boolean defBool, double defNum,
                double min, double max, double step, int defMode, String[] modes, Supplier<String> action) {
            this.tab = tab; this.name = name; this.key = key; this.type = type;
            this.defBool = defBool; this.defNum = defNum; this.defMode = defMode;
            this.min = min; this.max = max; this.step = step;
            this.modes = modes; this.action = action;
            this.bool = defBool; this.num = defNum; this.mode = defMode;
        }

        public String modeName() { return modes[mode]; }
        public double frac() { return (num - min) / (max - min); }
    }

    public static final List<Setting> ALL = new ArrayList<>();

    // ---- COMBAT ----
    public static final Setting ENABLED  = bool("COMBAT", "killaura", "enabled", false);
    public static final Setting RANGE    = num("COMBAT", "range", "range", 3.0, 1.0, 6.0, 0.05);
    public static final Setting COOLDOWN = num("COMBAT", "min charge", "cooldown", 1.0, 0.5, 1.0, 0.01);
    public static final Setting FOV      = num("COMBAT", "fov", "fov", 180, 30, 360, 5);
    public static final Setting PLAYERS  = bool("COMBAT", "players", "players", false);
    public static final Setting MOBS     = bool("COMBAT", "mobs", "mobs", true);

    // ---- BYPASS ----
    public static final Setting ROT_MODE   = mode("BYPASS", "rotation", "rotMode", 1, "snap", "smooth");
    public static final Setting ROT_SPEED  = num("BYPASS", "rot speed", "rotSpeed", 35, 5, 180, 1);
    public static final Setting ROT_JITTER = num("BYPASS", "rot jitter", "rotJitter", 1.5, 0, 10, 0.1);
    public static final Setting AIM_TOL    = num("BYPASS", "aim tolerance", "aimTol", 8, 1, 45, 1);
    public static final Setting LOS        = bool("BYPASS", "no hits thru walls", "los", true);
    public static final Setting SKIP       = num("BYPASS", "random skip %", "skipPct", 5, 0, 50, 1);

    // ---- CONFIG ----
    public static final Setting SAVE  = action("CONFIG", "save", Settings::save);
    public static final Setting LOAD  = action("CONFIG", "load", Settings::load);
    public static final Setting RESET = action("CONFIG", "reset", Settings::reset);

    private Settings() {}

    private static Setting add(String tab, String name, String key, Setting.Type type, boolean defBool,
                               double defNum, double min, double max, double step,
                               int defMode, String[] modes, Supplier<String> action) {
        Setting s = new Setting(tab, name, key, type, defBool, defNum, min, max, step, defMode, modes, action);
        ALL.add(s);
        return s;
    }

    private static Setting bool(String tab, String name, String key, boolean def) {
        return add(tab, name, key, Setting.Type.BOOL, def, 0, 0, 1, 1, 0, null, null);
    }

    private static Setting num(String tab, String name, String key, double def,
                               double min, double max, double step) {
        return add(tab, name, key, Setting.Type.NUM, false, def, min, max, step, 0, null, null);
    }

    private static Setting mode(String tab, String name, String key, int def, String... modes) {
        return add(tab, name, key, Setting.Type.MODE, false, 0, 0, 0, 0, def, modes, null);
    }

    private static Setting action(String tab, String name, Supplier<String> run) {
        return add(tab, name, "", Setting.Type.ACTION, false, 0, 0, 0, 0, 0, null, run);
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("dolp-killaura.json");
    }

    public static String save() {
        JsonObject o = new JsonObject();
        for (Setting s : ALL) {
            switch (s.type) {
                case BOOL -> o.addProperty(s.key, s.bool);
                case NUM -> o.addProperty(s.key, s.num);
                case MODE -> o.addProperty(s.key, s.modeName());
                case ACTION -> { }
            }
        }
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), new GsonBuilder().setPrettyPrinting().create().toJson(o));
            return "saved to config/dolp-killaura.json";
        } catch (Exception e) {
            return "save failed: " + e.getMessage();
        }
    }

    public static String load() {
        if (!Files.exists(file())) return "no config yet";
        try {
            JsonObject o = JsonParser.parseString(Files.readString(file())).getAsJsonObject();
            for (Setting s : ALL) {
                if (!o.has(s.key)) continue;
                switch (s.type) {
                    case BOOL -> s.bool = o.get(s.key).getAsBoolean();
                    case NUM -> s.num = Math.max(s.min, Math.min(s.max, o.get(s.key).getAsDouble()));
                    case MODE -> {
                        String name = o.get(s.key).getAsString();
                        for (int i = 0; i < s.modes.length; i++) if (s.modes[i].equals(name)) s.mode = i;
                    }
                    case ACTION -> { }
                }
            }
            return "loaded";
        } catch (Exception e) {
            return "load failed: " + e.getMessage();
        }
    }

    public static String reset() {
        for (Setting s : ALL) {
            s.bool = s.defBool;
            s.num = s.defNum;
            s.mode = s.defMode;
        }
        return "reset to defaults";
    }
}
