package dev.dolp.killaura;

import dev.dolp.killaura.Settings.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public class ClickGuiScreen extends Screen {

    private static final String[] TABS = {"COMBAT", "BYPASS", "CONFIG"};
    private static final int W = 200, TITLE_H = 14, TAB_H = 14, ROW_H = 14, PAD = 4;
    private static final int BG = 0xE0101014, ACCENT = 0xFF8A5CFF, TAB_ON = 0xFF2A2A38, ROW = 0xFF1C1C24,
            FILL = 0xFF3B2A70, TEXT = 0xFFFFFFFF, DIM = 0xFF9A9AA8, GREEN = 0xFF55FF88, RED = 0xFFFF5566;

    private int wx = 20, wy = 20, tab = 0, dragX, dragY;
    private boolean dragging;
    private Setting sliding;
    private String status = "";

    public ClickGuiScreen() {
        super(Text.literal("dolp killaura"));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private List<Setting> rows() {
        List<Setting> out = new ArrayList<>();
        for (Setting s : Settings.ALL) if (s.tab.equals(TABS[tab])) out.add(s);
        return out;
    }

    private int rowY(int i) { return wy + TITLE_H + TAB_H + 6 + i * (ROW_H + 2); }
    private int rowX() { return wx + PAD; }
    private int rowW() { return W - PAD * 2; }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static String fmt(Setting s) {
        return s.step >= 1 ? String.valueOf(Math.round(s.num)) : String.valueOf(Math.round(s.num * 100) / 100.0);
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        TextRenderer font = MinecraftClient.getInstance().textRenderer;
        List<Setting> rows = rows();
        int h = TITLE_H + TAB_H + 6 + rows.size() * (ROW_H + 2) + 14;

        ctx.fill(wx, wy, wx + W, wy + h, BG);
        ctx.fill(wx, wy, wx + W, wy + TITLE_H, ACCENT);
        ctx.drawText(font, "dolp killaura", wx + PAD, wy + 3, TEXT, false);

        int tw = W / TABS.length;
        for (int i = 0; i < TABS.length; i++) {
            int tx = wx + i * tw;
            ctx.fill(tx, wy + TITLE_H, tx + tw, wy + TITLE_H + TAB_H, i == tab ? TAB_ON : ROW);
            ctx.drawText(font, TABS[i], tx + 4, wy + TITLE_H + 3, i == tab ? TEXT : DIM, false);
        }

        for (int i = 0; i < rows.size(); i++) {
            Setting s = rows.get(i);
            int ry = rowY(i), rx = rowX(), rw = rowW();
            ctx.fill(rx, ry, rx + rw, ry + ROW_H, ROW);
            if (s.type == Setting.Type.NUM) ctx.fill(rx, ry, rx + (int) (rw * s.frac()), ry + ROW_H, FILL);
            ctx.drawText(font, s.name, rx + 4, ry + 3, TEXT, false);
            String v = switch (s.type) {
                case BOOL -> s.bool ? "ON" : "OFF";
                case NUM -> fmt(s);
                case MODE -> s.modeName();
                case ACTION -> "";
            };
            int vc = switch (s.type) {
                case BOOL -> s.bool ? GREEN : RED;
                case MODE -> ACCENT;
                default -> TEXT;
            };
            if (!v.isEmpty()) ctx.drawText(font, v, rx + rw - font.getWidth(v) - 4, ry + 3, vc, false);
        }

        if (!status.isEmpty()) ctx.drawText(font, status, wx + PAD, wy + h - 11, DIM, false);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            if (inside(mx, my, wx, wy, W, TITLE_H)) {
                dragging = true;
                dragX = (int) mx - wx;
                dragY = (int) my - wy;
                return true;
            }
            if (inside(mx, my, wx, wy + TITLE_H, W, TAB_H)) {
                tab = Math.min(TABS.length - 1, (int) (mx - wx) / (W / TABS.length));
                return true;
            }
            List<Setting> rows = rows();
            for (int i = 0; i < rows.size(); i++) {
                Setting s = rows.get(i);
                if (!inside(mx, my, rowX(), rowY(i), rowW(), ROW_H)) continue;
                switch (s.type) {
                    case BOOL -> s.bool = !s.bool;
                    case MODE -> s.mode = (s.mode + 1) % s.modes.length;
                    case NUM -> { sliding = s; setFromMouse(s, mx); }
                    case ACTION -> status = s.action.get();
                }
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging) {
            wx = (int) mx - dragX;
            wy = (int) my - dragY;
            return true;
        }
        if (sliding != null) {
            setFromMouse(sliding, mx);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = false;
        sliding = null;
        return super.mouseReleased(mx, my, button);
    }

    private void setFromMouse(Setting s, double mx) {
        double f = Math.max(0, Math.min(1, (mx - rowX()) / rowW()));
        double v = s.min + f * (s.max - s.min);
        s.num = Math.max(s.min, Math.min(s.max, Math.round(v / s.step) * s.step));
    }
}
