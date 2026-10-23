package com.novaclient.ui;

import com.novaclient.NovaClient;
import com.novaclient.NovaConfig;
import com.novaclient.module.KeyOption;
import com.novaclient.module.Module;
import com.novaclient.module.ModuleManager;
import com.novaclient.module.Setting;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Lunar-style menu: blurred world behind a dark rounded window, sidebar tabs, mod cards
 * (NAME / description / smooth ON-OFF bar), and a Settings page (GUI colour + keybinds) at the bottom.
 */
public class MenuScreen extends Screen {
    // ---- fixed palette (ARGB); the accent colour comes from NovaConfig ----
    private static final int SIDEBAR  = 0xFF0E0E12;
    private static final int WINDOW   = 0xF2141419;
    private static final int CARD     = 0xFF1D1D24;
    private static final int CARD_HOV = 0xFF2A2A35;
    private static final int OFF      = 0xFF3A3A46;
    private static final int TEXT     = 0xFFFFFFFF;
    private static final int SUB      = 0xFF9A9AAB;
    private static final int[] PRESETS = {
            0xFF3D8BFF, 0xFF8B5CF6, 0xFFEC4899, 0xFFEF4444, 0xFFF59E0B, 0xFF22C55E, 0xFF14B8A6, 0xFFE5E7EB };

    private enum Page { MODS, SETTINGS }
    private record Hit(int x, int y, int w, int h, boolean content, Runnable run) {
        boolean in(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }
    private record SliderHit(int x, int y, int w, int h, boolean content, Setting.Slider s) {
        boolean in(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }
    /** Applies a new binding: (mouse?, code, esc?). esc=true means the user pressed ESC. */
    private interface BindApply { void set(boolean mouse, int code, boolean esc); }
    private record BindRow(String id, String label, java.util.function.Supplier<String> display, BindApply apply) {}

    /** Row for a normal Minecraft KeyBinding (module on/off key, menu key). */
    private BindRow kbRow(String label, KeyBinding kb, boolean isMenuKey) {
        return new BindRow("kb" + System.identityHashCode(kb), label, () -> keyLabel(kb), (mouse, code, esc) -> {
            InputUtil.Key k;
            if (esc) k = isMenuKey ? InputUtil.Type.KEYSYM.createFromCode(GLFW.GLFW_KEY_RIGHT_SHIFT) : InputUtil.UNKNOWN_KEY;
            else k = (mouse ? InputUtil.Type.MOUSE : InputUtil.Type.KEYSYM).createFromCode(code);
            bindKey(kb, k);
        });
    }

    /** Row for a module-owned key option (e.g. Safe Anchor's run key). */
    private BindRow optRow(String label, KeyOption o) {
        return new BindRow("ko" + System.identityHashCode(o), label,
                () -> textRenderer.trimToWidth(o.get.getAsInt() == -1 ? "None" : o.name.apply(o.get.getAsInt()), 94),
                (mouse, code, esc) -> { o.set.accept(esc ? -1 : (mouse ? 1000 + code : code)); });
    }

    private final Screen parent;
    private final List<Hit> hits = new ArrayList<>();
    private final List<SliderHit> sliders = new ArrayList<>();
    private final Map<String, Float> anim = new HashMap<>();
    private SliderHit dragging;
    private BindRow listening;

    private Page page = Page.MODS;
    private Module.Category filter = null;
    private Module open = null;

    // animation + scrolling state
    private long lastNs = System.nanoTime();
    private float dt, fade;
    private float scrollTarget, scrollShown, maxScroll;
    private int vx, vy, vw, vh;       // scrollable viewport
    private boolean bgDrawn;
    private TextFieldWidget search;
    private final Map<Setting.Text, TextFieldWidget> textFields = new HashMap<>();
    private float collapseT;   // 0 = sidebar open, 1 = icons only

    private final Setting.Slider redS   = new Setting.Slider("Red",   0, 255, "", () -> NovaConfig.r, v -> NovaConfig.r = v);
    private final Setting.Slider greenS = new Setting.Slider("Green", 0, 255, "", () -> NovaConfig.g, v -> NovaConfig.g = v);
    private final Setting.Slider blueS  = new Setting.Slider("Blue",  0, 255, "", () -> NovaConfig.b, v -> NovaConfig.b = v);

    public MenuScreen(Screen parent) {
        super(Text.literal("Nova Client"));
        this.parent = parent;
        anim.put("fade", 0f);
    }

    @Override
    protected void init() {
        textFields.clear();
        String old = search != null ? search.getText() : "";
        search = new TextFieldWidget(textRenderer, 0, 0, 100, 12, Text.literal("Search"));
        search.setDrawsBackground(false);
        search.setMaxLength(24);
        search.setPlaceholder(Text.literal("Search mods..."));
        search.setText(old);
        search.setChangedListener(t -> resetScroll());
        addSelectableChild(search);          // rendered manually, on top of our window
    }

    private boolean anyTextFocused() {
        if (search != null && search.isFocused()) return true;
        for (TextFieldWidget f : textFields.values()) if (f.isFocused()) return true;
        return false;
    }

    private void unfocusAll() {
        if (search != null) search.setFocused(false);
        for (TextFieldWidget f : textFields.values()) f.setFocused(false);
    }

    private String query() { return search == null ? "" : search.getText().trim().toLowerCase(java.util.Locale.ROOT); }

    private boolean matches(Module m) {
        String q = query();
        if (!q.isEmpty()) {
            return m.name.toLowerCase(java.util.Locale.ROOT).contains(q)
                    || m.description.toLowerCase(java.util.Locale.ROOT).contains(q)
                    || m.category.label.toLowerCase(java.util.Locale.ROOT).contains(q);
        }
        return filter == null || m.category == filter;
    }

    @Override public boolean shouldPause() { return false; }
    @Override public void close() { if (client != null) client.setScreen(parent); }
    @Override public void removed() { NovaConfig.save(); }

    // ================= background (blur) =================
    @Override
    public void renderBackground(DrawContext g, int mx, int my, float delta) {
        bgDrawn = true;
        if (NovaConfig.blur) super.renderBackground(g, mx, my, delta);   // vanilla blur + soft darkening
        else g.fill(0, 0, width, height, 0x99000000);
    }

    // ================= helpers =================
    private int accent() { return NovaConfig.accent(); }

    private float anim(String key, float target, float speed) {
        float cur = anim.getOrDefault(key, target);
        cur += (target - cur) * (1f - (float) Math.exp(-dt * speed));
        if (Math.abs(target - cur) < 0.002f) cur = target;
        anim.put(key, cur);
        return cur;
    }

    private static int mix(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int ar = a >> 16 & 255, ag = a >> 8 & 255, ab = a & 255, br = b >> 16 & 255, bg = b >> 8 & 255, bb = b & 255;
        return 0xFF000000 | ((int) (ar + (br - ar) * t) << 16) | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    /** multiplies the colour's alpha by f */
    private static int fa(int c, float f) {
        int al = Math.max(0, Math.min(255, Math.round((c >>> 24) * f)));
        return (al << 24) | (c & 0xFFFFFF);
    }
    private int a(int c) { return fa(c, fade); }

    /** rounded rectangle */
    private void rr(DrawContext g, int x, int y, int w, int h, int r, int color) {
        int c = a(color);
        if (w <= 0 || h <= 0 || (c >>> 24) == 0) return;
        r = Math.min(r, Math.min(w, h) / 2);
        if (r <= 0) { g.fill(x, y, x + w, y + h, c); return; }
        g.fill(x, y + r, x + w, y + h - r, c);
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = (int) Math.ceil(r - Math.sqrt(r * r - dy * dy));
            g.fill(x + inset, y + i, x + w - inset, y + i + 1, c);
            g.fill(x + inset, y + h - 1 - i, x + w - inset, y + h - i, c);
        }
    }

    private void txt(DrawContext g, String s, int x, int y, int color) {
        int c = a(color);
        if ((c >>> 24) <= 3) return;
        g.drawTextWithShadow(textRenderer, s, x, y, c);
    }

    private void hit(int x, int y, int w, int h, boolean content, Runnable run) {
        if (content && (y + h < vy || y > vy + vh)) return;
        hits.add(new Hit(x, y, w, h, content, run));
    }

    /** Click-to-toggle button: lime ENABLED / red DISABLED, colour fades smoothly. No slider. */
    private static final int LIME = 0xFF7ED321, RED = 0xFFE5484D;

    private void toggleBtn(DrawContext g, String key, int x, int y, int w, int h, boolean on, String onLbl, String offLbl) {
        float t = anim("sw:" + key, on ? 1f : 0f, 16f);
        rr(g, x, y, w, h, 4, mix(RED, LIME, t));
        String l = t >= 0.5f ? onLbl : offLbl;
        txt(g, l, x + (w - textRenderer.getWidth(l)) / 2, y + (h - 8) / 2, TEXT);
    }

    /** Smooth sliding ON/OFF switch (used inside mod options and Settings). Follows the GUI colour. */
    private void switchBar(DrawContext g, String key, int x, int y, int w, int h, boolean on) {
        float t = anim("sw:" + key, on ? 1f : 0f, 16f);
        rr(g, x, y, w, h, h / 2, mix(OFF, accent(), t));
        int kd = h - 4;
        rr(g, x + 2 + Math.round(t * (w - kd - 4)), y + 2, kd, kd, kd / 2, TEXT);
    }

    private void ctxt(DrawContext g, String s, int cx, int y, int color) {
        txt(g, s, cx - textRenderer.getWidth(s) / 2, y, color);
    }

    // ---- tiny 9x9 pixel icons ----
    private static final Map<String, String[]> ICONS = new HashMap<>();
    static {
        ICONS.put("all", new String[]{
                ".........", ".###.###.", ".###.###.", ".###.###.", ".........",
                ".###.###.", ".###.###.", ".###.###.", "........."});
        ICONS.put("combat", new String[]{
                ".......##", "......###", ".....###.", ".#..###..", "..#####..",
                "...###...", "..#.##...", ".#...#...", "#........"});
        ICONS.put("utility", new String[]{
                "..#####..", "..#####..", "...###...", "...###...", "...###...",
                "...###...", "...###...", "...###...", "........."});
        ICONS.put("visual", new String[]{
                ".........", "...###...", ".##...##.", "#..###..#", "#..###..#",
                ".##...##.", "...###...", ".........", "........."});
        ICONS.put("crystal", new String[]{
                "....#....", "...#.#...", "..#.#.#..", ".#..#..#.", "#########",
                ".#..#..#.", "..#.#.#..", "...#.#...", "....#...."});
        ICONS.put("search", new String[]{
                ".####....", "#....#...", "#....#...", "#....#...", ".####....",
                ".....#...", "......#..", ".......#.", "........."});
        ICONS.put("arrowL", new String[]{
                ".........", "....#....", "...##....", "..##.....", ".##......",
                "..##.....", "...##....", "....#....", "........."});
        ICONS.put("arrowR", new String[]{
                ".........", "....#....", "....##...", ".....##..", "......##.",
                ".....##..", "....##...", "....#....", "........."});
        ICONS.put("settings", new String[]{
                "....#....", ".#.###.#.", "..#####..", ".###.###.", "###...###",
                ".###.###.", "..#####..", ".#.###.#.", "....#...."});
    }

    private void icon(DrawContext g, String name, int x, int y, int color) {
        String[] rows = ICONS.get(name);
        if (rows == null) return;
        int c = a(color);
        for (int r = 0; r < rows.length; r++)
            for (int col = 0; col < rows[r].length(); col++)
                if (rows[r].charAt(col) == '#') g.fill(x + col, y + r, x + col + 1, y + r + 1, c);
    }

    private static int keyCode(KeyBinding kb) {
        try {
            InputUtil.Key k = InputUtil.fromTranslationKey(kb.getBoundKeyTranslationKey());
            return k.getCategory() == InputUtil.Type.KEYSYM ? k.getCode() : -2;
        } catch (Throwable t) { return GLFW.GLFW_KEY_RIGHT_SHIFT; }
    }

    // ================= layout =================
    private int winW() { return Math.min(width - 24, 540); }
    private int winH() { return Math.min(height - 24, 320); }

    @Override
    public void render(DrawContext g, int mx, int my, float delta) {
        delta0 = delta;
        long now = System.nanoTime();
        dt = Math.min(0.05f, (now - lastNs) / 1_000_000_000f);
        lastNs = now;
        fade = anim("fade", 1f, 14f);
        hits.clear(); sliders.clear();
        for (TextFieldWidget tf : textFields.values()) tf.visible = false;
        lastMx = mx; lastMy = my;

        bgDrawn = false;
        super.render(g, mx, my, delta);                          // draws the (blurred) background
        if (!bgDrawn) renderBackground(g, mx, my, delta);

        int w = winW(), h = winH();
        int x = (width - w) / 2;
        int y = (height - h) / 2 + Math.round((1f - fade) * 10f);  // small slide-in

        rr(g, x, y, w, h, 8, WINDOW);

        // ---- sidebar ----
        collapseT = anim("sbcol", NovaConfig.sidebarCollapsed ? 1f : 0f, 14f);
        int sb = Math.round(118 - collapseT * 78);   // 118 open -> 40 collapsed
        rr(g, x, y, sb, h, 8, SIDEBAR);
        g.fill(x + sb - 8, y, x + sb, y + h, a(SIDEBAR));          // square off the inner edge
        float titleA = Math.max(0f, 1f - collapseT * 2.5f);
        txt(g, "NOVA", x + 14, y + 14, fa(accent(), titleA));
        txt(g, "CLIENT", x + 14 + textRenderer.getWidth("NOVA "), y + 14, fa(TEXT, titleA));

        // collapse / expand button (chevron)
        int ax = x + 94 + Math.round(collapseT * (15 - 94)), ay = y + 10;
        boolean ah = mx >= ax - 4 && mx < ax + 13 && my >= ay - 2 && my < ay + 13;
        float aT = anim("collapseBtn", ah ? 1f : 0f, 16f);
        rr(g, ax - 4, ay - 2, 17, 15, 4, fa(CARD, aT));
        icon(g, NovaConfig.sidebarCollapsed ? "arrowR" : "arrowL", ax, ay, mix(SUB, TEXT, aT));
        hit(ax - 4, ay - 2, 17, 15, false, () -> { NovaConfig.sidebarCollapsed = !NovaConfig.sidebarCollapsed; NovaConfig.save(); });

        int ty = y + 42;
        ty = tab(g, mx, my, x, ty, sb, "All Mods", "all", page == Page.MODS && open == null && filter == null, () -> go(Page.MODS, null));
        for (Module.Category c : Module.Category.values()) {
            ty = tab(g, mx, my, x, ty, sb, c.label, c.name(), page == Page.MODS && open == null && filter == c, () -> go(Page.MODS, c));
        }
        // Settings tab pinned to the bottom
        int sy = y + h - 52;
        g.fill(x + 12, sy - 8, x + sb - 12, sy - 7, a(0xFF2A2A35));
        tab(g, mx, my, x, sy, sb, "Settings", "settings", page == Page.SETTINGS, () -> go(Page.SETTINGS, null));

        // ---- content area ----
        int cx = x + sb + 14, cy = y + 14, cw = w - sb - 28;
        vx = cx; vy = cy + 24; vw = cw; vh = y + h - 12 - vy;

        // header (not scrolled)
        String title;
        if (page == Page.SETTINGS) title = "Settings";
        else if (open != null) title = open.name;
        else if (!query().isEmpty()) title = "Search";
        else title = filter == null ? "All Mods" : filter.label;

        if (page == Page.MODS && open != null) {
            boolean bh = mx >= cx && mx < cx + 46 && my >= cy - 2 && my < cy + 12;
            float bt = anim("back", bh ? 1f : 0f, 14f);
            txt(g, "< Back", cx, cy, mix(SUB, accent(), bt));
            hit(cx, cy - 2, 46, 14, false, () -> { open = null; resetScroll(); });
            txt(g, title, cx + 58, cy, TEXT);
        } else {
            txt(g, title, cx, cy, TEXT);
        }

        // search box (mods grid only)
        boolean showSearch = page == Page.MODS && open == null;
        search.visible = showSearch;
        if (!showSearch && search.isFocused()) search.setFocused(false);
        if (showSearch) {
            int sw = Math.min(150, cw - 90), sbX = cx + cw - sw, sbY = cy - 4;
            float fo = anim("searchFocus", search.isFocused() ? 1f : 0f, 16f);
            if (fo > 0.01f) rr(g, sbX - 1, sbY - 1, sw + 2, 17, 6, fa(accent(), fo));
            rr(g, sbX, sbY, sw, 15, 5, CARD);
            icon(g, "search", sbX + 5, sbY + 3, SUB);
            search.setX(sbX + 19); search.setY(sbY + 3); search.setWidth(sw - 23);
            search.render(g, mx, my, delta);
        }

        // scrolling
        scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget));
        scrollShown += (scrollTarget - scrollShown) * (1f - (float) Math.exp(-dt * 18f));
        int oy = vy - Math.round(scrollShown);

        g.enableScissor(vx, vy, vx + vw, vy + vh);
        int endY;
        if (page == Page.SETTINGS) endY = renderSettings(g, mx, my, vx, oy, vw);
        else if (open != null) endY = renderModule(g, mx, my, vx, oy, vw);
        else endY = renderGrid(g, mx, my, vx, oy, vw);
        g.disableScissor();

        maxScroll = Math.max(0, (endY - oy) - vh);
        if (maxScroll > 0) {                                      // slim scrollbar
            int trackH = vh, barH = Math.max(18, trackH * vh / (vh + (int) maxScroll));
            int by = vy + Math.round((trackH - barH) * (scrollShown / maxScroll));
            rr(g, vx + vw + 4, by, 3, barH, 1, fa(accent(), 0.8f));
        }
    }

    private void go(Page p, Module.Category f) { page = p; filter = f; open = null; listening = null; resetScroll(); }
    private void resetScroll() { scrollTarget = 0; scrollShown = 0; }

    private int tab(DrawContext g, int mx, int my, int x, int ty, int sb, String label, String id,
                    boolean sel, Runnable click) {
        String iconName = switch (id) { case "all" -> "all"; case "COMBAT" -> "combat"; case "UTILITY" -> "utility";
            case "VISUAL" -> "visual"; default -> "settings"; };
        boolean hov = mx >= x && mx < x + sb && my >= ty && my < ty + 22;
        float s = anim("tab:" + id, sel ? 1f : 0f, 16f);
        float hv = anim("tabh:" + id, hov ? 1f : 0f, 16f);
        if (s > 0.01f || hv > 0.01f) rr(g, x + 8, ty, sb - 16, 22, 5, fa(CARD, Math.max(s, hv * 0.6f)));
        if (s > 0.01f) rr(g, x + 8, ty + 5, 3, 12, 1, fa(accent(), s));
        int tc = mix(SUB, TEXT, Math.max(s, hv));
        icon(g, iconName, x + 16, ty + 7, mix(tc, accent(), s));
        txt(g, label, x + 31, ty + 7, fa(tc, Math.max(0f, 1f - collapseT * 2.5f)));
        hit(x, ty, sb, 22, false, click);
        return ty + 25;
    }

    // ================= mods grid =================
    private static String catIcon(Module.Category c) {
        return switch (c) { case COMBAT -> "combat"; case UTILITY -> "utility"; case VISUAL -> "visual"; };
    }

    private int renderGrid(DrawContext g, int mx, int my, int x0, int y0, int cw) {
        int cols = 3, gap = 8, cardW = (cw - gap * (cols - 1)) / cols, cardH = 104;
        int i = 0;
        for (Module m : ModuleManager.MODULES) {
            if (!matches(m)) continue;
            int x = x0 + (i % cols) * (cardW + gap), y = y0 + (i / cols) * (cardH + gap);
            i++;
            int mid = x + cardW / 2;

            boolean inView = my >= vy && my < vy + vh;
            boolean hov = inView && mx >= x && mx < x + cardW && my >= y && my < y + cardH;
            float hv = anim("hov:" + m.name, hov ? 1f : 0f, 14f);
            rr(g, x, y, cardW, cardH, 6, mix(CARD, CARD_HOV, hv));

            icon(g, m.icon != null ? m.icon : catIcon(m.category), mid - 4, y + 8, accent());
            ctxt(g, m.name, mid, y + 21, TEXT);
            List<OrderedText> lines = textRenderer.wrapLines(Text.literal(m.description), cardW - 14);
            for (int l = 0; l < Math.min(2, lines.size()); l++) {
                int c = a(SUB);
                if ((c >>> 24) > 3) g.drawTextWithShadow(textRenderer, lines.get(l),
                        mid - textRenderer.getWidth(lines.get(l)) / 2, y + 34 + l * 10, c);
            }

            // OPTIONS button (with gear icon)
            int bx = x + 8, bw = cardW - 16, oy = y + cardH - 44, ey = y + cardH - 24;
            boolean oh = inView && mx >= bx && mx < bx + bw && my >= oy && my < oy + 16;
            float ot = anim("opt:" + m.name, oh ? 1f : 0f, 16f);
            rr(g, bx, oy, bw, 16, 4, mix(OFF, mix(OFF, accent(), 0.6f), ot));
            String ol = "OPTIONS";
            int tw = textRenderer.getWidth(ol);
            txt(g, ol, mid - tw / 2 - 6, oy + 4, TEXT);
            icon(g, "settings", mid + tw / 2 + 2, oy + 4, TEXT);
            hit(bx, oy, bw, 16, true, () -> { open = m; resetScroll(); });

            // ENABLED / DISABLED button (click to toggle)
            toggleBtn(g, "card:" + m.name, bx, ey, bw, 16, m.isEnabled(), "ENABLED", "DISABLED");
            hit(bx, ey, bw, 16, true, () -> m.setEnabled(!m.isEnabled()));
        }
        if (i == 0) { txt(g, query().isEmpty() ? "No mods in this category yet." : "No mods match your search.", x0, y0 + 4, SUB); return y0 + 20; }
        int rows = (i + cols - 1) / cols;
        return y0 + rows * (cardH + gap);
    }

    // ================= single mod page =================
    private int renderModule(DrawContext g, int mx, int my, int x, int y, int w) {
        if (open.author != null) { txt(g, "by " + open.author, x, y, SUB); y += 13; }
        y = toggleRow(g, "en:" + open.name, x, y, w, "Enabled", open::isEnabled, open::setEnabled);
        y += 4;
        String last = null;
        for (Setting s : open.settings) {
            if (!s.section.equals(last)) { y = section(g, s.section.toUpperCase(), x, y); last = s.section; }
            String key = open.name + s.name;
            if (s instanceof Setting.Toggle t) y = toggleRow(g, "st:" + key, x, y, w, t.name, t.get, t.set::accept);
            else if (s instanceof Setting.Slider sl) y = sliderRow(g, "sl:" + key, x, y, w, sl, false);
            else if (s instanceof Setting.Choice c) y = choiceRow(g, "ch:" + key, x, y, w, c);
            else if (s instanceof Setting.Button b) y = buttonRow(g, mx, my, "bt:" + key, x, y, w, b);
            else if (s instanceof Setting.Text t) y = textRow(g, mx, my, delta0, "tx:" + key, x, y, w, t);
            y += 2;
        }
        y = section(g, "KEYBINDS", x, y);
        if (open.bind != null) y = bindRow(g, mx, my, x, y, w, kbRow("Toggle mod", open.bind, false));
        for (KeyOption o : open.keys) y = bindRow(g, mx, my, x, y, w, optRow(o.label, o));
        return y + 4;
    }

    private float delta0;   // frame delta for manually rendered widgets

    private int textRow(DrawContext g, int mx, int my, float delta, String key, int x, int y, int w, Setting.Text t) {
        TextFieldWidget f = textFields.computeIfAbsent(t, k -> {
            TextFieldWidget nf = new TextFieldWidget(textRenderer, 0, 0, 100, 12, Text.literal(k.name));
            nf.setDrawsBackground(false);
            nf.setMaxLength(k.max);
            nf.setPlaceholder(Text.literal(k.placeholder));
            nf.setText(k.get.get());
            nf.setChangedListener(v -> k.set.accept(v));
            addSelectableChild(nf);
            return nf;
        });
        rr(g, x, y, w, 40, 6, CARD);
        txt(g, t.name, x + 10, y + 6, TEXT);
        float fo = anim(key, f.isFocused() ? 1f : 0f, 16f);
        int bx = x + 10, by = y + 20, bw = w - 20;
        if (fo > 0.01f) rr(g, bx - 1, by - 1, bw + 2, 17, 5, fa(accent(), fo));
        rr(g, bx, by, bw, 15, 4, OFF);
        f.setX(bx + 6); f.setY(by + 3); f.setWidth(bw - 12);
        f.visible = y >= vy && y + 40 <= vy + vh;         // only usable while fully inside the viewport
        if (f.visible) f.render(g, mx, my, delta);
        return y + 44;
    }

    private int buttonRow(DrawContext g, int mx, int my, String key, int x, int y, int w, Setting.Button b) {
        rr(g, x, y, w, 24, 6, CARD);
        txt(g, b.name, x + 10, y + 8, TEXT);
        int bw = 104, bx = x + w - bw - 6, by = y + 4;
        boolean hov = my >= vy && my < vy + vh && mx >= bx && mx < bx + bw && my >= by && my < by + 16;
        float t = anim(key, hov ? 1f : 0f, 16f);
        rr(g, bx, by, bw, 16, 5, mix(OFF, accent(), 0.35f + 0.65f * t));
        txt(g, b.label, bx + (bw - textRenderer.getWidth(b.label)) / 2, by + 4, TEXT);
        hit(bx, by, bw, 16, true, b.action);
        return y + 28;
    }

    /** Segmented selector, e.g. Mode: [Safe Anchor | Normal Anchor] with a sliding highlight. */
    private int choiceRow(DrawContext g, String key, int x, int y, int w, Setting.Choice c) {
        rr(g, x, y, w, 44, 6, CARD);
        txt(g, c.name, x + 10, y + 6, TEXT);
        int n = c.options.length, sel = c.get.getAsInt();
        int bx = x + 10, bw = w - 20, by = y + 20, bh = 18, segW = bw / n;
        rr(g, bx, by, segW * n, bh, 5, OFF);
        float pos = anim(key, sel, 18f);
        rr(g, bx + Math.round(pos * segW), by, segW, bh, 5, accent());
        for (int i = 0; i < n; i++) {
            String o = c.options[i];
            float on = Math.max(0f, 1f - Math.abs(pos - i));
            txt(g, o, bx + i * segW + (segW - textRenderer.getWidth(o)) / 2, by + 5, mix(SUB, TEXT, on));
            final int idx = i;
            hit(bx + i * segW, by, segW, bh, true, () -> c.set.accept(idx));
        }
        return y + 48;
    }

    // ================= settings page =================
    private int renderSettings(DrawContext g, int mx, int my, int x, int y, int w) {
        y = section(g, "APPEARANCE", x, y);

        // colour card: label + preset swatches
        rr(g, x, y, w, 50, 6, CARD);
        txt(g, "GUI colour", x + 10, y + 8, TEXT);
        int sx = x + 10, sy = y + 22;
        for (int p : PRESETS) {
            boolean sel = (p & 0xFFFFFF) == (accent() & 0xFFFFFF);
            float st = anim("sw" + p, sel ? 1f : 0f, 16f);
            if (st > 0.01f) rr(g, sx - 2, sy - 2, 24, 24, 7, fa(TEXT, st));
            rr(g, sx, sy, 20, 20, 5, p);
            final int pc = p;
            hit(sx, sy, 20, 20, true, () -> { NovaConfig.setAccent(pc); NovaConfig.save(); });
            sx += 26;
        }
        y += 56;
        y = sliderRow(g, "set:r", x, y, w, redS, true);
        y = sliderRow(g, "set:g", x, y, w, greenS, true);
        y = sliderRow(g, "set:b", x, y, w, blueS, true);
        y = toggleRow(g, "set:blur", x, y, w, "Background blur",
                () -> NovaConfig.blur, v -> { NovaConfig.blur = v; NovaConfig.save(); });

        y += 6;
        y = section(g, "KEYBINDS", x, y);
        txt(g, "Click a bind, then press a key or mouse button.", x, y, SUB); y += 11;
        txt(g, "ESC = none (unbound).", x, y, SUB); y += 16;

        y = bindRow(g, mx, my, x, y, w, kbRow("Open menu", NovaClient.openMenu, true));
        for (Module m : ModuleManager.MODULES) {
            if (m.bind != null) y = bindRow(g, mx, my, x, y, w, kbRow(m.name, m.bind, false));
            for (KeyOption o : m.keys) y = bindRow(g, mx, my, x, y, w, optRow(m.name + " - " + o.label, o));
        }
        return y + 4;
    }

    private int section(DrawContext g, String label, int x, int y) {
        txt(g, label, x, y, accent());
        return y + 14;
    }

    // ================= row widgets =================
    private int toggleRow(DrawContext g, String key, int x, int y, int w, String label,
                          BooleanSupplier get, Consumer<Boolean> set) {
        boolean hov = overY(y, 24);
        float hv = anim("rowh:" + key, hov ? 1f : 0f, 14f);
        rr(g, x, y, w, 24, 6, mix(CARD, CARD_HOV, hv));
        txt(g, label, x + 10, y + 8, TEXT);
        switchBar(g, key, x + w - 42, y + 5, 32, 14, get.getAsBoolean());
        hit(x, y, w, 24, true, () -> set.accept(!get.getAsBoolean()));
        return y + 28;
    }

    private boolean overY(int y, int h) { return lastMy >= vy && lastMy < vy + vh && lastMy >= y && lastMy < y + h; }
    private int lastMx, lastMy;

    private int sliderRow(DrawContext g, String key, int x, int y, int w, Setting.Slider s, boolean isConfig) {
        rr(g, x, y, w, 34, 6, CARD);
        int val = s.get.getAsInt();
        txt(g, s.name, x + 10, y + 6, TEXT);
        String v = val + s.unit;
        txt(g, v, x + w - 10 - textRenderer.getWidth(v), y + 6, SUB);
        int tx = x + 10, tw = w - 20, ty = y + 22;
        float shown = anim(key, val, 22f);
        float f = s.max == s.min ? 0f : (shown - s.min) / (s.max - s.min);
        int fx = tx + Math.round(Math.max(0f, Math.min(1f, f)) * tw);
        rr(g, tx, ty, tw, 4, 2, OFF);
        rr(g, tx, ty, Math.max(4, fx - tx), 4, 2, accent());
        rr(g, fx - 4, ty - 3, 8, 10, 4, TEXT);
        if (!(y + 34 < vy || y > vy + vh)) sliders.add(new SliderHit(tx - 4, y + 14, tw + 8, 20, true, s));
        return y + 38;
    }

    private int bindRow(DrawContext g, int mx, int my, int x, int y, int w, BindRow r) {
        rr(g, x, y, w, 24, 6, CARD);
        txt(g, textRenderer.trimToWidth(r.label, w - 130), x + 10, y + 8, TEXT);
        boolean lis = listening != null && listening.id.equals(r.id);
        int bw = 104, bx = x + w - bw - 6, by = y + 4;
        boolean hov = my >= vy && my < vy + vh && mx >= bx && mx < bx + bw && my >= by && my < by + 16;
        float lt = anim("bind:" + r.id, lis ? 1f : (hov ? 0.35f : 0f), 16f);
        rr(g, bx, by, bw, 16, 5, mix(OFF, accent(), lt));
        String s = lis ? "Press a key..." : r.display.get();
        txt(g, s, bx + (bw - textRenderer.getWidth(s)) / 2, by + 4, TEXT);
        hit(bx, by, bw, 16, true, () -> listening = lis ? null : r);
        return y + 28;
    }

    private String keyLabel(KeyBinding kb) {
        if (kb == null || kb.isUnbound()) return "None";
        String s = kb.getBoundKeyLocalizedText().getString();
        return textRenderer.trimToWidth(s, 94);
    }

    // ================= input =================
    private void bindKey(KeyBinding kb, InputUtil.Key key) {
        kb.setBoundKey(key);
        KeyBinding.updateKeysByCode();
        if (client != null) client.options.write();
    }

    private void setSlider(SliderHit sh, double mouseX) {
        double f = Math.max(0, Math.min(1, (mouseX - (sh.x + 4)) / (double) (sh.w - 8)));
        sh.s.set.accept(sh.s.min + (int) Math.round(f * (sh.s.max - sh.s.min)));
    }

    private boolean inViewport(double mx, double my) { return mx >= vx && mx < vx + vw && my >= vy && my < vy + vh; }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        lastMx = (int) click.x(); lastMy = (int) click.y();
        if (listening != null) {                                   // mouse buttons can be bound too
            listening.apply.set(true, click.button(), false);
            listening = null;
            return true;
        }
        if (search != null && search.isFocused() && !search.isMouseOver(click.x(), click.y())) search.setFocused(false);
        for (TextFieldWidget tf : textFields.values())
            if (tf.isFocused() && !tf.isMouseOver(click.x(), click.y())) tf.setFocused(false);
        if (click.button() == 0) {
            for (SliderHit sh : sliders) {
                if (sh.in(click.x(), click.y()) && inViewport(click.x(), click.y())) {
                    dragging = sh; setSlider(sh, click.x()); return true;
                }
            }
            for (int i = hits.size() - 1; i >= 0; i--) {           // last drawn = on top
                Hit h = hits.get(i);
                if (!h.in(click.x(), click.y())) continue;
                if (h.content && !inViewport(click.x(), click.y())) continue;
                h.run.run();
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        lastMx = (int) click.x(); lastMy = (int) click.y();
        if (dragging != null) { setSlider(dragging, click.x()); return true; }
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (dragging != null) NovaConfig.save();
        dragging = null;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        scrollTarget -= (float) (vertical * 22);
        scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget));
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (anyTextFocused()) {
            if (input.key() == GLFW.GLFW_KEY_ESCAPE) { unfocusAll(); return true; }
            return super.keyPressed(input);          // typing goes to the focused text box
        }
        if (listening != null) {
            // ESC = none (the menu key falls back to Right Shift so you can't lock yourself out)
            if (input.key() == GLFW.GLFW_KEY_ESCAPE) listening.apply.set(false, 0, true);
            else listening.apply.set(false, input.key(), false);
            listening = null;
            return true;
        }
        if (input.key() == keyCode(NovaClient.openMenu)) { close(); return true; }
        return super.keyPressed(input);
    }
}
