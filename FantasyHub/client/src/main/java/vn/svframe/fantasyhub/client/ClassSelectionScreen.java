package vn.svframe.fantasyhub.client;

import com.google.gson.*;
import java.util.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.text.Text;

/**
 * Registry-backed selection; the server validates source unlock rules and commits the transaction.
 */
public final class ClassSelectionScreen extends Screen {
  private int left, top, panelWidth, panelHeight, page;
  private String selected = "DRAGONBORN";
  private boolean confirming, requested;
  private final ClassModelPreview model = new ClassModelPreview();
  private int detailScroll, contentHeight;
  private String query="";
  private boolean heroesOnly;
  private static final Set<String> HEROES = Set.of("SPIDER_MAN", "SORCERER_SUPREME", "IRON_MAN", "THOR", "FLASH", "BATMAN");

  public ClassSelectionScreen() {
    super(Text.translatable("fantasyhub.classes.title"));
    String current = FantasyHubClient.string("classId");
    if (!current.isBlank()) selected = current;
  }

  public void refresh() {
    clearAndInit();
  }

  private List<JsonObject> rows() {
    var result = new ArrayList<JsonObject>();
    var list = FantasyHubClient.state().getAsJsonArray("classRoster");
    if (list != null) for (var raw : list) {var row=raw.getAsJsonObject();if((!heroesOnly||HEROES.contains(row.get("id").getAsString()))&&(row.get("id").getAsString()+" "+row.get("name").getAsString()).toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT)))result.add(row);}
    result.sort(
        Comparator.comparing(
            r -> r.get("name").getAsString().replaceAll("(?i)[&§][0-9A-FK-OR]", ""),
            String.CASE_INSENSITIVE_ORDER));
    return result;
  }

  private int visible() {
    return Math.max(1, (panelHeight - 127) / 22);
  }

  private JsonObject selection() {
    return rows().stream()
        .filter(r -> r.get("id").getAsString().equals(selected))
        .findFirst()
        .orElse(null);
  }

  @Override
  protected void init() {
    panelWidth = Math.min(width - 16, 580);
    panelHeight = Math.min(height - 16, 360);
    left = (width - panelWidth) / 2;
    top = (height - panelHeight) / 2;
    boolean firstOpen = !requested;
    if (!requested) {
      requested = true;
      FantasyHubClient.send("refresh", "", 0);
    }
    var search=new net.minecraft.client.gui.widget.TextFieldWidget(textRenderer,left+panelWidth-180,top+44,165,18,Text.translatable("fantasyhub.classes.search"));
    search.setText(query);search.setSuggestion(query.isEmpty()?Text.translatable("fantasyhub.classes.search_hint").getString():"");
    addDrawableChild(search);setFocused(search);search.setCursorToEnd(false);
    search.setChangedListener(value->{if(query.equals(value))return;query=value;page=0;confirming=false;clearAndInit();});
    addDrawableChild(FantasyButton.builder(Text.translatable(heroesOnly ? "fantasyhub.classes.heroes" : "fantasyhub.classes.all"), b -> {
      heroesOnly = !heroesOnly; page = 0; confirming = false; clearAndInit();
    }).dimensions(left + 12, top + 44, Math.max(80, panelWidth / 3 - 24), 18).build());
    int listWidth = Math.max(90, panelWidth / 3);
    var rows = rows();
    int count = visible();
    if (firstOpen)
      for (int index = 0; index < rows.size(); index++)
        if (rows.get(index).get("id").getAsString().equals(selected)) {
          page = index / count;
          break;
        }
    page = Math.min(page, Math.max(0, (rows.size() - 1) / count));
    for (int i = 0; i < count && page * count + i < rows.size(); i++) {
      var row = rows.get(page * count + i);
      String id = row.get("id").getAsString();
      var name = FantasyHubClient.name(row.get("name").getAsString());
      var button =
          FantasyButton.builder(
                  name,
                  b -> {
                    selected = id;
                    confirming = false;
                    detailScroll = 0;
                    clearAndInit();
                  })
              .dimensions(left + 12, top + 68 + i * 22, listWidth - 18, 20)
              .build();
      button.setTooltip(
          Tooltip.of(
              Text.translatable(
                  row.get("current").getAsBoolean()
                      ? "fantasyhub.classes.current"
                      : row.get("unlocked").getAsBoolean()
                          ? "fantasyhub.classes.unlocked"
                          : "fantasyhub.classes.locked")));
      addDrawableChild(button);
    }
    addDrawableChild(
        FantasyButton.builder(
                Text.literal("‹"),
                b -> {
                  page = Math.max(0, page - 1);
                  clearAndInit();
                })
            .dimensions(left + 12, top + panelHeight - 52, 24, 20)
            .build());
    addDrawableChild(
        FantasyButton.builder(
                Text.literal("›"),
                b -> {
                  if ((page + 1) * count < rows.size()) page++;
                  clearAndInit();
                })
            .dimensions(left + 40, top + panelHeight - 52, 24, 20)
            .build());
    var row = selection();
    var policy = FantasyHubClient.state().getAsJsonObject("classSwitch");
    var choose =
        FantasyButton.builder(
                Text.translatable(
                    confirming ? "fantasyhub.classes.confirm" : "fantasyhub.classes.choose"),
                b -> {
                  if (!confirming) {
                    confirming = true;
                    clearAndInit();
                  } else {
                    FantasyHubClient.send(row!=null&&!row.get("unlocked").getAsBoolean()?"unlock_class":"switch_class", selected, 0);
                    confirming = false;
                    clearAndInit();
                  }
                })
            .dimensions(
                left + listWidth + 12, top + panelHeight - 52, panelWidth - listWidth - 24, 20)
            .build();
    boolean locked=row!=null&&!row.get("unlocked").getAsBoolean();
    if(locked)choose.setMessage(Text.translatable(confirming ? "fantasyhub.classes.confirm_unlock" : "fantasyhub.classes.unlock_sigil", row.get("unlockCost").getAsInt()));
    if(row!=null)choose.setTooltip(Tooltip.of(Text.translatable(locked ? "fantasyhub.classes.unlock_help" : "fantasyhub.classes.choose_help")));
    choose.active =
        row != null
            && row.get("selectable").getAsBoolean()
            && (locked ? policy!=null&&policy.has("sigils")&&policy.get("sigils").getAsInt()>=row.get("unlockCost").getAsInt() : row.get("unlocked").getAsBoolean())
            && !row.get("current").getAsBoolean()
            && policy != null
            && (locked || row.getAsJsonObject("price").get("economyAvailable").getAsBoolean())
            && (locked || row.getAsJsonObject("price").get("canAfford").getAsBoolean())
            && !policy.get("busy").getAsBoolean()
            && (locked || policy.get("remainingSeconds").getAsLong() == 0);
    addDrawableChild(choose);
    addDrawableChild(
        FantasyButton.builder(Text.translatable("gui.done"), b -> close())
            .dimensions(left + panelWidth - 84, top + panelHeight - 27, 72, 20)
            .build());
  }

  @Override
  public void render(DrawContext context, int mouseX, int mouseY, float delta) {
    renderBackground(context, mouseX, mouseY, delta);
    FantasyPanel.frame(context, left, top, panelWidth, panelHeight);
    context.drawCenteredTextWithShadow(
        textRenderer, title, left + panelWidth / 2, top + 12, 0xffe7c37d);
    int split = left + panelWidth / 3;
    context.fill(split, top + 65, split + 1, top + panelHeight - 57, 0xff6b5030);
    var row = selection();
    int x = split + 12, y = top + 68 - detailScroll, w = left + panelWidth - 12 - x;
    boolean hasPreview =
        FantasyHubClient.state().has("classPreviews")
            && FantasyHubClient.state().getAsJsonObject("classPreviews").has(selected);
    if (hasPreview) w -= 82;
    context.enableScissor(x, top + 65, left + panelWidth - 10, top + panelHeight - 56);
    if (row != null) {
      context.drawText(
          textRenderer,
          FantasyHubClient.name(row.get("name").getAsString()),
          x,
          y,
          0xffe7c37d,
          false);
      y += 17;
      context.drawText(
          textRenderer,
          Text.translatable(
              row.get("current").getAsBoolean()
                  ? "fantasyhub.classes.current"
                  : row.has("owned") && row.get("owned").getAsBoolean()
                      ? "fantasyhub.classes.owned"
                      : row.get("unlocked").getAsBoolean()
                          ? "fantasyhub.classes.unlocked"
                          : "fantasyhub.classes.locked"),
          x,
          y,
          0xffd8bd8b,
          false);
      y += 16;
      if (!row.get("unlocked").getAsBoolean()) {
        for (var line : textRenderer.wrapLines(Text.translatable(row.get("selectable").getAsBoolean()
            ? "fantasyhub.classes.locked_sigil" : "fantasyhub.classes.not_selectable", row.get("unlockCost").getAsInt()), w)) {
          context.drawText(textRenderer, line, x, y, 0xffd8bd8b, false); y += 11;
        }
        y += 4;
      }
      for (var raw : row.getAsJsonArray("description"))
        for (var line : textRenderer.wrapLines(FantasyHubClient.name(raw.getAsString()), w)) {
          context.drawText(textRenderer, line, x, y, 0xffeee2c8, false);
          y += 11;
        }
      String passiveKey = "fantasyhub.rider.passive." + selected.toLowerCase(java.util.Locale.ROOT);
      if (java.util.Set.of("RYUKI", "KNIGHT", "DECADE").contains(selected)) {
        Text passive =
            selected.equals("RYUKI")
                ? Text.translatable("fantasyhub.rider.passive.ryuki.description")
                : Text.translatable(passiveKey);
        for (var line : textRenderer.wrapLines(passive, w)) {
          context.drawText(textRenderer, line, x, y, 0xffc9cfef, false);
          y += 11;
        }
      }
      y += 7;
      context.drawText(
          textRenderer, Text.translatable("fantasyhub.screen.skills"), x, y, 0xffe7c37d, false);
      y += 15;
      for (var raw : row.getAsJsonArray("skills")) {
        var skill = raw.getAsJsonObject();
        Text label =
            Text.translatable(
                skill.get("passive").getAsBoolean()
                    ? "fantasyhub.classes.passive"
                    : "fantasyhub.classes.skill",
                RiderText.name(skill.get("id").getAsString(), skill.get("name").getAsString()),
                skill.get("requiredLevel").getAsInt());
        context.drawText(
            textRenderer,
            textRenderer.trimToWidth(label.getString(), w),
            x,
            y,
            skill.get("available").getAsBoolean() ? 0xffeee2c8 : 0xffcf7767,
            false);
        y += 13;
        if (!skill.get("available").getAsBoolean()) {
          context.drawText(
              textRenderer,
              Text.translatable("fantasyhub.classes.unsupported"),
              x + 5,
              y,
              0xffcf7767,
              false);
          y += 12;
        }
      }
    }
    contentHeight = y - (top + 68 - detailScroll);
    context.disableScissor();
    if (hasPreview)
      model.render(
          context,
          selected,
          left + panelWidth - 88,
          top + 68,
          left + panelWidth - 12,
          top + panelHeight - 60,
          mouseX,
          mouseY);
    var policy = FantasyHubClient.state().getAsJsonObject("classSwitch");
    if (policy != null) {
      context.drawCenteredTextWithShadow(textRenderer,
          Text.literal(textRenderer.trimToWidth(Text.translatable("fantasyhub.classes.material_status", policy.get("sigils").getAsInt(), policy.get("starter").getAsBoolean() ? 1 : 0).getString(), panelWidth - 16)),
          left + panelWidth / 2, top + 28, 0xffd8bd8b);
      var price = row == null ? policy : row.getAsJsonObject("price");
      Text cost =
          Text.translatable(
              "fantasyhub.classes.cost",
              price.get("fee").getAsString(),
              price.get("currency").getAsString(),
              policy.get("remainingSeconds").getAsLong());
      context.drawText(
          textRenderer,
          textRenderer.trimToWidth(cost.getString(), panelWidth - 106),
          left + 12,
          top + panelHeight - 20,
          0xffd8bd8b,
          false);
    }
    for (var child : children())
      if (child instanceof net.minecraft.client.gui.Drawable drawable)
        drawable.render(context, mouseX, mouseY, delta);
  }

  @Override
  public boolean shouldPause() {
    return false;
  }

  @Override
  public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
    if (mouseX > left + panelWidth / 3 && mouseY > top + 65 && mouseY < top + panelHeight - 56) {
      detailScroll =
          Math.max(
              0,
              Math.min(
                  Math.max(0, contentHeight - (panelHeight - 126)),
                  detailScroll - (int) (vertical * 20)));
      return true;
    }
    return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
  }
}
