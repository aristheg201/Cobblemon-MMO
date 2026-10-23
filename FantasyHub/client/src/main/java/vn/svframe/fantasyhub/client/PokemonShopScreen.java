package vn.svframe.fantasyhub.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

public final class PokemonShopScreen extends Screen {
 @Override public void removed(){requested=false;FantasyHubClient.send("ui_close","pokemon",0);}
   private int left;
   private int top;
   private int w;
   private int h;
   private int page;
   private int slot = 1;
   private int paymentOption = 1;
   private int detailScroll;
   private String type = "";
   private String category = "";
   private String selected = "";
   private String query = "";
   private boolean ownedOnly;
   private boolean requested;
   private TextFieldWidget searchField;

   public PokemonShopScreen() {
      super(Text.translatable("fantasyhub.shop.title"));
   }

   public void refresh() {
      this.clearAndInit();
   }

   private JsonObject payment(JsonObject row) {
      if(row.has("paymentOptions"))for(var raw:row.getAsJsonArray("paymentOptions"))if(raw.getAsJsonObject().get("id").getAsInt()==paymentOption)return raw.getAsJsonObject();
      return row;
   }

   private List<JsonObject> rows() {
      JsonArray array = FantasyHubClient.state().getAsJsonArray("pokemonShop");
      if (array == null) {
         return List.of();
      } else {
         ArrayList<JsonObject> result = new ArrayList<>();
         String search = this.query.trim().toLowerCase(Locale.ROOT);

         for (JsonElement raw : array) {
            JsonObject row = raw.getAsJsonObject();
            if ((this.type.isEmpty() || this.type.equals(row.get("type").getAsString()))
               && (this.category.isEmpty() || this.category.equals(row.get("category").getAsString()))
               && (!this.ownedOnly || row.get("owned").getAsBoolean())
               && (
                  search.isEmpty()
                     || row.get("id").getAsString().contains(search)
                     || row.get("name").getAsString().toLowerCase(Locale.ROOT).contains(search)
                     || name(row).getString().toLowerCase(Locale.ROOT).contains(search)
               )) {
               result.add(row);
            }
         }

         return result;
      }
   }

   private JsonObject selection() {
      for (JsonObject row : this.rows()) {
         if (this.selected.equals(row.get("id").getAsString())) {
            return row;
         }
      }

      return null;
   }

   private int visible() {
      return Math.max(1, (this.h - 152) / 23);
   }

   private List<Integer> allowedSlots(JsonObject row) {
      ArrayList<Integer> result = new ArrayList<>();
      if (row != null && row.has("slots")) {
         for (JsonElement raw : row.getAsJsonArray("slots")) {
            result.add(raw.getAsInt());
         }
      }

      return result;
   }

   private void cycle(boolean types) {
      TreeSet<String> values = new TreeSet<>();
      JsonArray array = FantasyHubClient.state().getAsJsonArray("pokemonShop");
      if (array != null) {
         for (JsonElement raw : array) {
            values.add(raw.getAsJsonObject().get(types ? "type" : "category").getAsString());
         }
      }

      ArrayList<String> options = new ArrayList<>();
      options.add("");
      options.addAll(values);
      String old = types ? this.type : this.category;
      String next = options.get((options.indexOf(old) + 1) % options.size());
      if (types) {
         this.type = next;
      } else {
         this.category = next;
      }

      this.page = 0;
      this.clearAndInit();
   }

   private static Text name(JsonObject row) {
      String key = row.get("nameKey").getAsString();
      return (Text)(key.isEmpty() ? FantasyHubClient.name(row.get("name").getAsString()) : Text.translatable(key));
   }

   private static MutableText pokemonType(String type) {
      return Text.translatable("cobblemon.type." + type.toLowerCase(Locale.ROOT));
   }

   protected void init() {
      this.w = Math.min(this.width - 20, 480);
      this.h = Math.min(this.height - 20, 300);
      this.left = (this.width - this.w) / 2;
      this.top = (this.height - this.h) / 2;
      if (!this.requested) {
         this.requested = true;
         FantasyHubClient.send("ui_open", "pokemon", 0);
      }

      int filter = (this.w - 28) / 3;
      this.addDrawableChild(
         FantasyButton.builder(
               Text.translatable(
                  "fantasyhub.shop.filter.type", new Object[]{this.type.isEmpty() ? Text.translatable("fantasyhub.shop.all") : pokemonType(this.type)}
               ),
               b -> this.cycle(true)
            )
            .dimensions(this.left + 10, this.top + 30, filter, 20)
            .build()
      );
      this.addDrawableChild(
         FantasyButton.builder(
               Text.translatable(
                  "fantasyhub.shop.filter.category",
                  new Object[]{
                     this.category.isEmpty() ? Text.translatable("fantasyhub.shop.all") : Text.translatable("fantasyhub.shop.category." + this.category)
                  }
               ),
               b -> this.cycle(false)
            )
            .dimensions(this.left + 14 + filter, this.top + 30, filter, 20)
            .build()
      );
      this.addDrawableChild(FantasyButton.builder(Text.translatable(this.ownedOnly ? "fantasyhub.shop.owned" : "fantasyhub.shop.catalog"), b -> {
         this.ownedOnly = !this.ownedOnly;
         this.page = 0;
         this.clearAndInit();
      }).dimensions(this.left + 18 + filter * 2, this.top + 30, filter, 20).build());
      boolean restoreSearchFocus = this.searchField != null && this.searchField.isFocused();
      this.searchField = new TextFieldWidget(this.textRenderer, this.left + 12, this.top + 56, this.w - 24, 20, Text.translatable("fantasyhub.shop.search"));
      this.searchField.setMaxLength(64);
      this.searchField.setText(this.query);
      this.searchField.setPlaceholder(Text.translatable("fantasyhub.shop.search"));
      this.searchField.setChangedListener(value -> {
         this.query = value;
         this.page = 0;
         this.clearAndInit();
      });
      this.addDrawableChild(this.searchField);
      if (restoreSearchFocus) {
         this.setFocused(this.searchField);
      }

      List<JsonObject> rows = this.rows();
      int visible = this.visible();
      this.page = Math.min(this.page, Math.max(0, (rows.size() - 1) / visible));
      int listWidth = Math.max(92, this.w / 2 - 18);

      for (int i = 0; i < visible && this.page * visible + i < rows.size(); i++) {
         JsonObject row = rows.get(this.page * visible + i);
         String id = row.get("id").getAsString();
         FantasyButton button = FantasyButton.builder(name(row), b -> {
            this.selected = id;
            this.detailScroll = 0;
            this.clearAndInit();
         }).dimensions(this.left + 12, this.top + 82 + i * 23, listWidth, 20).build();
         Text description = row.get("descriptionKey").getAsString().isEmpty()
            ? Text.literal(row.get("description").getAsString())
            : Text.translatable(row.get("descriptionKey").getAsString());
         button.setTooltip(
            Tooltip.of(
               Text.translatable("fantasyhub.shop.price", new Object[]{payment(row).get("price").getAsString(), payment(row).get("currency").getAsString()})
                  .append(Text.literal("\n"))
                  .append(description)
            )
         );
         this.addDrawableChild(button);
      }

      int bottom = this.top + this.h - 57;
      this.addDrawableChild(FantasyButton.builder(Text.literal("‹"), b -> {
         this.page = Math.max(0, this.page - 1);
         this.clearAndInit();
      }).dimensions(this.left + 12, bottom, 24, 20).build());
      this.addDrawableChild(FantasyButton.builder(Text.literal("›"), b -> {
         if ((this.page + 1) * visible < rows.size()) {
            this.page++;
         }

         this.clearAndInit();
      }).dimensions(this.left + 40, bottom, 24, 20).build());
      int actionWidth = Math.max(50, (this.w - 92) / 3);
      JsonObject row = this.selection();
      boolean owned = row != null && row.get("owned").getAsBoolean();
      List<Integer> slots = this.allowedSlots(row);
      if (!slots.isEmpty() && !slots.contains(this.slot)) {
         this.slot = slots.getFirst();
      }

      FantasyButton slotButton = FantasyButton.builder(Text.translatable("fantasyhub.slot", new Object[]{this.slot}), b -> {
         this.slot = slots.get((slots.indexOf(this.slot) + 1) % slots.size());
         this.clearAndInit();
      }).dimensions(this.left + 70, bottom, actionWidth, 20).build();
      slotButton.active = !slots.isEmpty();
      this.addDrawableChild(slotButton);
      FantasyButton purchase = FantasyButton.builder(
            Text.translatable("fantasyhub.shop.purchase"),
            b -> {
               if (this.selection() != null) {
                  JsonObject offer = this.selection();
                  JsonObject quoted=payment(offer);
                  int selectedPayment=paymentOption;
                  this.client
                     .setScreen(
                        new ConfirmScreen(
                           yes -> {
                              if (yes) {
                                 FantasyHubClient.send("purchase", offer.get("id").getAsString(), selectedPayment);
                              }

                              this.client.setScreen(this);
                           },
                           Text.translatable("fantasyhub.shop.confirm"),
                           Text.translatable(
                              "fantasyhub.shop.confirm.detail",
                              new Object[]{name(offer), quoted.get("price").getAsString(), quoted.get("currency").getAsString()}
                           )
                        )
                     );
               }
            }
         )
         .dimensions(this.left + 74 + actionWidth, bottom, actionWidth, 20)
         .build();
      purchase.active = row != null && !owned && payment(row).has("available") && payment(row).get("available").getAsBoolean();
      this.addDrawableChild(purchase);
      FantasyButton equip = FantasyButton.builder(
            Text.translatable("fantasyhub.equip"), b -> FantasyHubClient.send("bind", this.selection().get("skillId").getAsString(), this.slot)
         )
         .dimensions(this.left + 78 + actionWidth * 2, bottom, actionWidth, 20)
         .build();
      equip.active = owned && !slots.isEmpty();
      this.addDrawableChild(equip);
      FantasyButton cast = FantasyButton.builder(
            Text.translatable("fantasyhub.shop.cast"), b -> FantasyHubClient.send("cast_id", this.selection().get("skillId").getAsString(), 0)
         )
         .dimensions(this.left + 12, this.top + this.h - 29, 100, 20)
         .build();
      cast.active = owned;
      this.addDrawableChild(cast);
      this.addDrawableChild(FantasyButton.builder(Text.translatable("fantasyhub.shop.payment",paymentOption==1?"beastcoin":"cobbledollars"),b->{paymentOption=paymentOption==1?2:1;clearAndInit();})
         .dimensions(this.left+118,this.top+this.h-29,Math.max(60,this.w-210),20).build());
      this.addDrawableChild(
         FantasyButton.builder(Text.translatable("gui.done"), b -> this.close())
            .dimensions(this.left + this.w - 84, this.top + this.h - 29, 72, 20)
            .build()
      );
   }

   public void render(DrawContext context, int mouseX, int mouseY, float delta) {
      this.renderBackground(context, mouseX, mouseY, delta);
    context.draw();
      FantasyPanel.frame(context, this.left, this.top, this.w, this.h);
      context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.left + this.w / 2, this.top + 12, -1588355);
      JsonObject row = this.selection();
      if (row != null) {
         int x = this.left + this.w / 2 + 8;
         int available = this.w / 2 - 20;
         int y = this.top + 84 - this.detailScroll;
         context.enableScissor(x, this.top + 80, this.left + this.w - 10, this.top + this.h - 62);
         context.drawText(this.textRenderer, this.textRenderer.trimToWidth(name(row).getString(), available), x, y, -1588355, false);
         y += 13;
         Text classification = pokemonType(row.get("type").getAsString())
            .append(Text.literal(" · "))
            .append(Text.translatable("fantasyhub.shop.category." + row.get("category").getAsString()));
         context.drawText(this.textRenderer, this.textRenderer.trimToWidth(classification.getString(), available), x, y, -2773402, false);
         y += 13;
         context.drawText(
            this.textRenderer,
            this.textRenderer
               .trimToWidth(
                  Text.translatable("fantasyhub.shop.price", new Object[]{payment(row).get("price").getAsString(), payment(row).get("currency").getAsString()}).getString(),
                  available
               ),
            x,
            y,
            -1121592,
            false
         );
         y += 14;
         context.drawText(
            this.textRenderer, Text.translatable(row.get("owned").getAsBoolean() ? "fantasyhub.shop.owned" : "fantasyhub.shop.not_owned"), x, y, -2773402, false
         );
         y += 14;
         if (row.has("effectKey")) {
            for (OrderedText line : this.textRenderer.wrapLines(Text.translatable(row.get("effectKey").getAsString()), available)) {
               context.drawText(this.textRenderer, line, x, y, -1121592, false);
               y += 11;
            }
         }

         Text details = Text.translatable(
            "fantasyhub.shop.details",
            new Object[]{
               row.get("mana").getAsDouble(),
               row.get("stamina").getAsDouble(),
               row.get("cooldown").getAsDouble(),
               row.get("range").getAsDouble(),
               row.get("requiredLevel").getAsInt()
            }
         );

         for (OrderedText line : this.textRenderer.wrapLines(details, available)) {
            context.drawText(this.textRenderer, line, x, y, -1121592, false);
            y += 11;
         }

         context.disableScissor();
      }

      for (Element child : this.children()) {
         if (child instanceof Drawable drawable) {
            drawable.render(context, mouseX, mouseY, delta);
         }
      }
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
      if (mouseX > this.left + this.w / 2 && this.selection() != null) {
         this.detailScroll = Math.max(0, Math.min(120, this.detailScroll - (int)(vertical * 12.0)));
         return true;
      } else {
         List<JsonObject> rows = this.rows();
         this.page = Math.max(0, Math.min(Math.max(0, (rows.size() - 1) / this.visible()), this.page - (int)Math.signum(vertical)));
         this.clearAndInit();
         return true;
      }
   }

   public boolean shouldPause() {
      return false;
   }
}
