package vn.svframe.svquest.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import vn.svframe.svquest.SVQuest;
import vn.svframe.svquest.quest.QuestCatalog;

import java.util.ArrayList;
import java.util.List;

/** Native SVFrame progression + feature hub. */
public final class QuestScreen extends Screen {
    private static final Identifier LOGO = Identifier.of(SVQuest.MOD_ID, "textures/gui/server_logo.png");
    private static final int LOGO_W = 128, LOGO_H = 89;
    private static final int BG = 0xF20A0E16, HEADER = 0xFA111827, PANEL = 0xF217202D;
    private static final int CARD = 0xFA202B3B, CARD_HOVER = 0xFA29384C, BORDER = 0xFF35445B;
    private static final int MAGENTA = 0xFFFF4FD1, CYAN = 0xFF56DDF2, GREEN = 0xFF75E690;
    private static final int GOLD = 0xFFFFCF5A, RED = 0xFFFF6B7A, TEXT = 0xFFF5F7FB;
    private static final int MUTED = 0xFF9EAABD, DIM = 0xFF667085;

    private enum Tab { PROGRESS, ACTIVITIES, POKEMON, SHOPS, SERVICES }
    private Tab tab = Tab.PROGRESS;
    private int selectedQuest = -1;
    private final List<Hit> hits = new ArrayList<>();

    private int railScroll;
    private int railMaxScroll;
    private int railTrackX, railTrackY, railTrackH;
    private int railThumbY, railThumbH;
    private int railBoundsX, railBoundsY, railBoundsW, railBoundsH;
    private boolean railDragging;
    private double railGrabOffset;

    public QuestScreen() { super(Text.literal("SVQuest")); }
    @Override public boolean shouldPause() { return false; }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        hits.clear();
        super.render(ctx, mouseX, mouseY, delta);

        ctx.fill(0, 0, width, height, 0xB9000000);
        int w = Math.min(1240, Math.max(720, width - 24));
        int h = Math.min(720, Math.max(440, height - 24));
        int x = (width - w) / 2, y = (height - h) / 2;
        panel(ctx, x, y, x + w, y + h, BG, 0xFF2E3B50);
        header(ctx, x, y, w, mouseX, mouseY);
        int contentY = y + 103;
        if (tab == Tab.PROGRESS) progress(ctx, x + 14, contentY, w - 28, h - 117, mouseX, mouseY);
        else featureGrid(ctx, x + 14, contentY, w - 28, h - 117, mouseX, mouseY);
    }

    private void header(DrawContext ctx, int x, int y, int w, int mx, int my) {
        ctx.fill(x + 1, y + 1, x + w - 1, y + 76, HEADER);
        int logoH = 58, logoW = Math.round(logoH * (LOGO_W / (float) LOGO_H));
        try {
            ctx.drawTexture(LOGO, x + 15, y + 8, 0, 0, logoW, logoH, LOGO_W, LOGO_H);
        } catch (Throwable ignored) {
            ctx.drawText(textRenderer, "SVFRAME", x + 18, y + 30, MAGENTA, true);
        }

        int tx = x + 26 + logoW;
        ctx.drawText(textRenderer, "SVFRAME", tx, y + 20, TEXT, true);
        ctx.drawText(textRenderer, "SVQuest", tx, y + 40, CYAN, false);

        int closeX = x + w - 29;
        boolean closeHover = inside(mx, my, closeX, y + 10, 18, 18);
        panel(ctx, closeX, y + 10, closeX + 18, y + 28, closeHover ? 0xFF6B2733 : 0xFF263144, closeHover ? RED : BORDER);
        drawCentered(ctx, "×", closeX, y + 15, 18, closeHover ? 0xFFFFFFFF : MUTED);
        hits.add(new Hit(closeX, y + 10, 18, 18, "close"));

        String[] names = {"TIẾN TRÌNH", "HOẠT ĐỘNG", "POKÉMON", "CỬA HÀNG", "DỊCH VỤ"};
        Tab[] tabs = Tab.values();
        int navX = x + 14, navY = y + 78, gap = 5, bw = (w - 28 - gap * 4) / 5;
        for (int i = 0; i < names.length; i++) {
            boolean active = tab == tabs[i], hover = inside(mx, my, navX, navY, bw, 22);
            panel(ctx, navX, navY, navX + bw, navY + 22, active ? 0xFF762766 : hover ? 0xFF2A3649 : 0xFF1A2331, active ? MAGENTA : BORDER);
            drawCentered(ctx, names[i], navX, navY + 7, bw, active ? 0xFFFFFFFF : 0xFFC9D1DE);
            hits.add(new Hit(navX, navY, bw, 22, "tab:" + tabs[i].name()));
            navX += bw + gap;
        }
    }

    private void progress(DrawContext ctx, int x, int y, int w, int h, int mx, int my) {
        // The screen can be opened one render frame before the async server catalog arrives.
        // Never index the catalog until it is actually present; otherwise byIndex(0) throws on the render thread.
        if (QuestCatalog.QUESTS.isEmpty()) {
            renderCatalogLoading(ctx, x, y, w, h, mx, my);
            return;
        }

        int current = clampCurrent();
        if (selectedQuest < 0 || selectedQuest >= QuestCatalog.QUESTS.size()) {
            selectedQuest = current;
            railScroll = Math.max(0, current - 2);
        }
        int railW = Math.max(205, Math.min(278, w / 4)), gap = 10;
        journeyRail(ctx, x, y, railW, h, current, mx, my);
        questDetail(ctx, x + railW + gap, y, w - railW - gap, h, current, mx, my);
    }

    private void renderCatalogLoading(DrawContext ctx, int x, int y, int w, int h, int mx, int my) {
        panel(ctx, x, y, x + w, y + h, PANEL, BORDER);
        String title = "ĐANG ĐỒNG BỘ DỮ LIỆU QUEST";
        String detail = SVQuestClient.STATE.serverAvailable()
                ? "Đang nhận lộ trình quest từ server..."
                : "Đang chờ dữ liệu từ server. Có thể thử đồng bộ lại.";
        ctx.drawText(textRenderer, title, x + 24, y + 26, CYAN, true);
        ctx.drawText(textRenderer, detail, x + 24, y + 50, MUTED, false);
        actionButton(ctx, x + 24, y + 78, 128, 24, "ĐỒNG BỘ LẠI", "sync", mx, my);
    }

    private void journeyRail(DrawContext ctx, int x, int y, int w, int h, int current, int mx, int my) {
        panel(ctx, x, y, x + w, y + h, PANEL, BORDER);
        railBoundsX = x; railBoundsY = y; railBoundsW = w; railBoundsH = h;

        ctx.drawText(textRenderer, "LỘ TRÌNH GAMEPLAY", x + 12, y + 11, TEXT, true);
        String count = (current + 1) + "/" + QuestCatalog.QUESTS.size() + " mốc";
        ctx.drawText(textRenderer, count, x + w - 16 - textRenderer.getWidth(count), y + 11, CYAN, false);

        int listTop = y + 34;
        int listBottom = y + h - 8;
        int rowH = 44;
        int visibleRows = Math.max(1, (listBottom - listTop) / rowH);
        railMaxScroll = Math.max(0, QuestCatalog.QUESTS.size() - visibleRows);
        railScroll = clamp(railScroll, 0, railMaxScroll);

        int contentRight = x + w - (railMaxScroll > 0 ? 14 : 7);
        for (int slot = 0; slot < visibleRows; slot++) {
            int i = railScroll + slot;
            if (i >= QuestCatalog.QUESTS.size()) break;
            var q = QuestCatalog.QUESTS.get(i);
            int ry = listTop + slot * rowH;
            boolean selected = i == selectedQuest, active = i == current, done = i < current;
            boolean hover = inside(mx, my, x + 7, ry, contentRight - (x + 7), rowH - 4);
            panel(ctx, x + 7, ry, contentRight, ry + rowH - 4,
                    selected ? 0xFF2C3A50 : hover ? 0xFF222D3E : 0xFF171F2B,
                    selected ? CYAN : 0xFF263247);
            int stateColor = done ? GREEN : active ? GOLD : DIM;
            ctx.fill(x + 12, ry + 8, x + 15, ry + rowH - 12, stateColor);
            ctx.drawText(textRenderer, done ? "✓" : active ? "▶" : "◆", x + 20, ry + 7, stateColor, true);
            ctx.drawText(textRenderer, trim(q.title(), Math.max(14, (w - 58) / 6)), x + 34, ry + 7, selected ? TEXT : 0xFFC5CDDA, false);
            ctx.drawText(textRenderer, q.phase(), x + 34, ry + 22, active ? GOLD : DIM, false);
            hits.add(new Hit(x + 7, ry, contentRight - (x + 7), rowH - 4, "quest:" + i));
        }

        if (railMaxScroll > 0) {
            railTrackX = x + w - 8;
            railTrackY = listTop;
            railTrackH = visibleRows * rowH - 4;
            ctx.fill(railTrackX, railTrackY, railTrackX + 3, railTrackY + railTrackH, 0xFF101722);

            railThumbH = Math.max(28, Math.round(railTrackH * (visibleRows / (float) QuestCatalog.QUESTS.size())));
            int travel = Math.max(1, railTrackH - railThumbH);
            railThumbY = railTrackY + Math.round(travel * (railScroll / (float) railMaxScroll));
            boolean thumbHover = inside(mx, my, railTrackX - 4, railThumbY, 11, railThumbH);
            ctx.fill(railTrackX - 1, railThumbY, railTrackX + 4, railThumbY + railThumbH, thumbHover || railDragging ? CYAN : 0xFF65758D);
        } else {
            railTrackH = 0;
            railThumbH = 0;
        }
    }

    private void questDetail(DrawContext ctx, int x, int y, int w, int h, int current, int mx, int my) {
        var q = QuestCatalog.byIndex(selectedQuest);
        boolean active = selectedQuest == current, done = selectedQuest < current, future = selectedQuest > current;
        boolean claimable = active && q.objectives().stream().allMatch(o -> SVQuestClient.STATE.progress(o.key()) >= o.target());
        panel(ctx, x, y, x + w, y + h, PANEL, BORDER);
        int badgeW = Math.min(160, textRenderer.getWidth(q.phase()) + 20);
        panel(ctx, x + 16, y + 13, x + 16 + badgeW, y + 34, active ? 0xFF61461D : done ? 0xFF1F4931 : 0xFF273348, active ? GOLD : done ? GREEN : BORDER);
        drawCentered(ctx, q.phase(), x + 16, y + 20, badgeW, active ? GOLD : done ? GREEN : MUTED);
        String stateText = done ? "HOÀN THÀNH" : claimable ? "CHỜ NHẬN THƯỞNG" : active ? "ĐANG THỰC HIỆN" : "CHƯA MỞ KHÓA";
        int stateColor = done || claimable ? GREEN : active ? GOLD : DIM;
        ctx.drawText(textRenderer, stateText, x + w - 16 - textRenderer.getWidth(stateText), y + 20, stateColor, true);
        ctx.drawText(textRenderer, q.title(), x + 16, y + 46, TEXT, true);
        drawWrapped(ctx, q.description(), x + 16, y + 63, w - 32, MUTED, 2);

        if (future) {
            int fy = y + 96;
            panel(ctx, x + 16, fy, x + w - 16, fy + 38, 0xFF181E29, BORDER);
            ctx.drawText(textRenderer, "YÊU CẦU", x + 27, fy + 8, GOLD, true);
            ctx.drawText(textRenderer, "Hoàn thành “" + QuestCatalog.byIndex(current).title() + "” trước.", x + 27, fy + 22, MUTED, false);
        }

        int oy = future ? y + 148 : y + 101;
        ctx.drawText(textRenderer, "MỤC TIÊU", x + 16, oy, CYAN, true);
        oy += 17;
        for (var o : q.objectives()) {
            int value = SVQuestClient.STATE.progress(o.key());
            boolean objectiveDone = done || value >= o.target();
            int cardH = o.featureId().isBlank() ? 40 : 50;
            panel(ctx, x + 16, oy, x + w - 16, oy + cardH, CARD, objectiveDone ? 0xFF376449 : BORDER);
            ctx.drawText(textRenderer, objectiveDone ? "✓" : "○", x + 26, oy + 8, objectiveDone ? GREEN : MUTED, true);
            ctx.drawText(textRenderer, o.label(), x + 44, oy + 7, TEXT, false);
            String n = Math.min(value, o.target()) + " / " + o.target();
            ctx.drawText(textRenderer, n, x + w - 27 - textRenderer.getWidth(n), oy + 7, objectiveDone ? GREEN : MUTED, false);
            int bx = x + 44, by = oy + 25, barW = Math.max(30, w - 88 - (o.featureId().isBlank() ? 0 : 115));
            ctx.fill(bx, by, bx + barW, by + 5, 0xFF0D131C);
            int pw = o.target() <= 0 ? barW : Math.round(barW * Math.min(1f, value / (float) o.target()));
            ctx.fill(bx, by, bx + pw, by + 5, objectiveDone ? GREEN : CYAN);
            if (!o.featureId().isBlank() && active && !objectiveDone) actionButton(ctx, x + w - 124, oy + 22, 96, 19, "MỞ HỆ", "feature:" + o.featureId(), mx, my);
            oy += cardH + 7;
        }

        oy += 4;
        ctx.drawText(textRenderer, "PHẦN THƯỞNG", x + 16, oy, GOLD, true);
        oy += 16;
        int chipX = x + 16;
        for (String reward : q.rewards()) {
            int cw = textRenderer.getWidth(reward) + 16;
            if (chipX + cw > x + w - 16) { chipX = x + 16; oy += 23; }
            panel(ctx, chipX, oy, chipX + cw, oy + 19, 0xFF293421, 0xFF607543);
            ctx.drawText(textRenderer, reward, chipX + 8, oy + 6, 0xFFE6F5CA, false);
            chipX += cw + 6;
        }

        int bottomY = y + h - 39;
        if (claimable) {
            ctx.drawText(textRenderer, "Hoàn tất mục tiêu. Nhận thưởng để mở mốc tiếp theo.", x + 16, bottomY + 10, GREEN, false);
            actionButton(ctx, x + w - 144, bottomY + 3, 128, 25, "NHẬN THƯỞNG", "claim", mx, my);
        } else if (active && current < QuestCatalog.QUESTS.size() - 1) {
            ctx.drawText(textRenderer, "Tiếp theo: " + QuestCatalog.byIndex(current + 1).title(), x + 16, bottomY + 10, MUTED, false);
        } else if (done) {
            ctx.drawText(textRenderer, "✓ Mốc này đã hoàn thành.", x + 16, bottomY + 10, GREEN, true);
        } else if (future) {
            ctx.drawText(textRenderer, "Nhìn trước mục tiêu để chuẩn bị đội hình và tài nguyên.", x + 16, bottomY + 10, MUTED, false);
        }
    }

    private void featureGrid(DrawContext ctx, int x, int y, int w, int h, int mx, int my) {
        String[][] data = featureData(tab);
        int cols = w > 950 ? 3 : 2;
        int gap = 9, cardW = (w - gap * (cols - 1)) / cols, cardH = 77;
        for (int i = 0; i < data.length; i++) {
            int col = i % cols, row = i / cols;
            int cx = x + col * (cardW + gap), cy = y + row * (cardH + gap);
            if (cy + cardH > y + h) break;
            boolean hover = inside(mx, my, cx, cy, cardW, cardH);
            panel(ctx, cx, cy, cx + cardW, cy + cardH, hover ? CARD_HOVER : CARD, BORDER);
            int accent = categoryColor(tab);
            ctx.fill(cx + 7, cy + 11, cx + 10, cy + cardH - 11, accent);
            ctx.drawText(textRenderer, data[i][0], cx + 19, cy + 14, TEXT, true);
            drawWrapped(ctx, data[i][1], cx + 19, cy + 31, cardW - 132, MUTED, 2);
            actionButton(ctx, cx + cardW - 101, cy + 24, 82, 25, "MỞ", "feature:" + data[i][2], mx, my);
        }
    }

    private String[][] featureData(Tab tab) {
        return switch (tab) {
            case ACTIVITIES -> new String[][]{
                    {"Ranked","Đấu PvP xếp hạng.","ranked"},
                    {"Battle Tower","Chuỗi battle leo tầng.","battle_tower"},
                    {"Battle Factory","Đội hình thuê / thử thách.","battle_factory"},
                    {"Nova Raids","Raid Pokémon theo lịch.","raids"},
                    {"Hunts","Săn Pokémon theo yêu cầu.","hunts"},
                    {"Research","Nghiên cứu / Pokédex mastery.","research"},
                    {"Expeditions","Thám hiểm và tiến trình dài hạn.","expeditions"},
                    {"Showcase","Trưng bày Pokémon theo tiêu chí.","showcase"},
                    {"Minigames","Hoạt động giải trí.","minigames"},
                    {"Daily","Nhận thưởng hằng ngày.","daily"},
                    {"Battle Pass","Tiến trình mùa.","battle_pass"}
            };
            case POKEMON -> new String[][]{
                    {"Pokémon Skills","Mua và quản lý Kỹ năng Pokémon.","pokemon_skills"},
                    {"Sinh sản","Ghép cặp, nhận và ấp trứng.","breeding"},
                    {"Fusion / Potara","Hợp thể Pokémon với nhân vật.","fusion"},
                    {"Tera Lab","Tối ưu Tera Type.","tera_lab"},
                    {"Skin Pokémon","Kho và trang bị skin.","skins"},
                    {"WonderTrade","Trao đổi Pokémon ngẫu nhiên.","wonder_trade"},
                    {"STS","Bán Pokémon trực tiếp cho hệ thống.","sts"}
            };
            case SHOPS -> new String[][]{
                    {"Shop chính","Vật phẩm phổ thông và tài nguyên.","shop"},
                    {"Resource Hub","Tài nguyên xây dựng / sinh tồn.","resource_hub"},
                    {"GTS","Mua bán Pokémon giữa player.","gts"},
                    {"Hunter Shop","Nội dung dùng HunterCoin.","hunter_shop"},
                    {"Tera Lab","Tài nguyên Tera và tối ưu.","tera_lab"},
                    {"Gacha","Key / gacha content.","gacha"},
                    {"Rank Shop","Quyền lợi và rank server.","rank_shop"}
            };
            case SERVICES -> new String[][]{
                    {"Home","Quản lý home cá nhân.","homes"},
                    {"Warp","Đi tới khu chức năng.","warps"},
                    {"Waypoint / GPS","Dẫn đường tới địa điểm.","waypoints"},
                    {"Claim","Bảo vệ vùng đất.","claims"},
                    {"GTS","Dịch vụ marketplace Pokémon.","gts"},
                    {"Skin Inventory","Xem kho skin hiện có.","skin_inventory"},
                    {"Exchange","Đổi tiền/tài nguyên.","exchange"}
            };
            default -> new String[0][0];
        };
    }

    private int categoryColor(Tab tab) {
        return switch (tab) { case ACTIVITIES -> GOLD; case POKEMON -> CYAN; case SHOPS -> GREEN; case SERVICES -> MAGENTA; default -> BORDER; };
    }

    private void actionButton(DrawContext ctx, int x, int y, int w, int h, String label, String action, int mx, int my) {
        boolean hover = inside(mx, my, x, y, w, h);
        panel(ctx, x, y, x + w, y + h, hover ? 0xFF8A3378 : 0xFF68265E, hover ? 0xFFFF82DF : MAGENTA);
        drawCentered(ctx, label, x, y + Math.max(5, (h - 8) / 2), w, 0xFFFFFFFF);
        hits.add(new Hit(x, y, w, h, "action:" + action));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && tab == Tab.PROGRESS && railMaxScroll > 0 &&
                inside(mouseX, mouseY, railTrackX - 5, railTrackY, 13, railTrackH)) {
            railDragging = true;
            if (inside(mouseX, mouseY, railTrackX - 5, railThumbY, 13, railThumbH)) {
                railGrabOffset = mouseY - railThumbY;
            } else {
                railGrabOffset = railThumbH / 2.0;
                updateRailFromMouse(mouseY);
            }
            return true;
        }

        if (button == 0) for (Hit hit : new ArrayList<>(hits)) {
            if (!inside(mouseX, mouseY, hit.x, hit.y, hit.w, hit.h)) continue;
            String a = hit.action;
            if (a.equals("close")) { close(); return true; }
            if (a.startsWith("tab:")) { tab = Tab.valueOf(a.substring(4)); railDragging = false; return true; }
            if (a.startsWith("quest:")) { selectedQuest = Integer.parseInt(a.substring(6)); return true; }
            if (a.startsWith("feature:")) { SVQuestClient.action(a); return true; }
            if (a.startsWith("action:")) { SVQuestClient.action(a.substring(7)); return true; }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button == 0 && railDragging && railMaxScroll > 0) {
            updateRailFromMouse(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && railDragging) {
            railDragging = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (tab == Tab.PROGRESS && railMaxScroll > 0 && inside(mouseX, mouseY, railBoundsX, railBoundsY, railBoundsW, railBoundsH)) {
            if (verticalAmount > 0) railScroll--;
            else if (verticalAmount < 0) railScroll++;
            railScroll = clamp(railScroll, 0, railMaxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private void updateRailFromMouse(double mouseY) {
        int travel = Math.max(1, railTrackH - railThumbH);
        double thumbTop = Math.max(railTrackY, Math.min(railTrackY + travel, mouseY - railGrabOffset));
        double ratio = (thumbTop - railTrackY) / travel;
        railScroll = clamp((int) Math.round(ratio * railMaxScroll), 0, railMaxScroll);
    }

    private int clampCurrent() { return Math.max(0, Math.min(SVQuestClient.STATE.questIndex(), QuestCatalog.QUESTS.size() - 1)); }
    private int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }

    private void drawWrapped(DrawContext ctx, String text, int x, int y, int maxWidth, int color, int maxLines) {
        if (text == null || text.isBlank()) return;
        String[] words = text.split(" "); StringBuilder line = new StringBuilder(); int lines = 0;
        for (String word : words) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (textRenderer.getWidth(candidate) > maxWidth && !line.isEmpty()) {
                ctx.drawText(textRenderer, line.toString(), x, y + lines * 11, color, false); lines++;
                if (lines >= maxLines) return; line.setLength(0); line.append(word);
            } else { if (!line.isEmpty()) line.append(' '); line.append(word); }
        }
        if (!line.isEmpty() && lines < maxLines) ctx.drawText(textRenderer, line.toString(), x, y + lines * 11, color, false);
    }

    private void panel(DrawContext ctx, int x1, int y1, int x2, int y2, int fill, int border) {
        ctx.fill(x1,y1,x2,y2,fill); ctx.fill(x1,y1,x2,y1+1,border); ctx.fill(x1,y2-1,x2,y2,border);
        ctx.fill(x1,y1,x1+1,y2,border); ctx.fill(x2-1,y1,x2,y2,border);
    }
    private void drawCentered(DrawContext ctx, String s, int x, int y, int w, int color) { ctx.drawText(textRenderer, s, x + (w - textRenderer.getWidth(s)) / 2, y, color, false); }
    private String trim(String s, int chars) { return s.length() <= chars ? s : s.substring(0, Math.max(1, chars - 1)) + "…"; }
    private boolean inside(double px, double py, int x, int y, int w, int h) { return px >= x && px < x + w && py >= y && py < y + h; }
    private record Hit(int x, int y, int w, int h, String action) {}
}
