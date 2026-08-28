package vn.svframe.svframemmo.skill.gui;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.api.player.PlayerData;
import vn.svframe.svframemmo.skill.PlayerSkillCatalog;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Player-facing MMOCore-style RPG skill list. Current-class skills and learned integration skills share one catalog
 * and one active loadout UI; their persistence models remain independent behind {@link PlayerSkillCatalog}.
 */
public final class SkillListGui {
    private static final int MENU_SIZE = 54;
    private static final int FIRST_SKILL_SLOT = 9;
    private static final int SKILLS_PER_PAGE = 36;
    private static final int PREVIOUS_PAGE = 45;
    private static final int STATUS = 49;
    private static final int NEXT_PAGE = 53;

    private SkillListGui() { }

    public static void open(ServerPlayerEntity player) {
        PlayerData data = SVFrameMMO.playerData().get(player);
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, inventory, ignored) -> new Handler(syncId, inventory, player),
                Text.literal("Skills - " + data.getProfess().getName())));
    }

    private static final class Handler extends GenericContainerScreenHandler {
        private final SimpleInventory menu;
        private final ServerPlayerEntity owner;
        private final Map<Integer, PlayerSkillCatalog.Entry> visibleSkills = new LinkedHashMap<>();
        private final Map<Integer, Integer> visibleLoadoutSlots = new LinkedHashMap<>();
        private int page;
        private int selectedLoadoutSlot;

        private Handler(int syncId, PlayerInventory playerInventory, ServerPlayerEntity owner) {
            this(syncId, playerInventory, owner, new SimpleInventory(MENU_SIZE));
        }

        private Handler(int syncId, PlayerInventory playerInventory, ServerPlayerEntity owner, SimpleInventory menu) {
            super(ScreenHandlerType.GENERIC_9X6, syncId, playerInventory, menu, 6);
            this.menu = menu;
            this.owner = owner;
            List<Integer> slots = PlayerSkillCatalog.slots(data());
            this.selectedLoadoutSlot = slots.isEmpty() ? 1 : slots.getFirst();
            render();
        }

        @Override
        public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
            if (!player.getUuid().equals(owner.getUuid())) return;
            if (slotIndex < 0 || slotIndex >= MENU_SIZE) return;

            Integer loadoutSlot = visibleLoadoutSlots.get(slotIndex);
            if (loadoutSlot != null) {
                if (button == 1) {
                    String removed = PlayerSkillCatalog.unbind(data(), loadoutSlot);
                    if (removed == null) owner.sendMessage(Text.literal("Slot " + loadoutSlot + " is already empty."), true);
                } else selectedLoadoutSlot = loadoutSlot;
                render();
                return;
            }

            PlayerSkillCatalog.Entry entry = visibleSkills.get(slotIndex);
            if (entry != null) {
                if (!entry.learned()) {
                    owner.sendMessage(Text.literal(entry.skill().getSkill().getName() + " is locked."), true);
                    return;
                }
                if (!entry.bindable()) {
                    owner.sendMessage(Text.literal(entry.skill().getSkill().getName() + " is passive/permanent and does not use an active slot."), true);
                    return;
                }
                try {
                    PlayerSkillCatalog.bind(data(), selectedLoadoutSlot, entry.id());
                } catch (RuntimeException exception) {
                    owner.sendMessage(Text.literal(exception.getMessage() == null ? "Could not bind skill." : exception.getMessage()), true);
                }
                render();
                return;
            }

            int maxPage = maxPage();
            if (slotIndex == PREVIOUS_PAGE && page > 0) {
                page--;
                render();
            } else if (slotIndex == NEXT_PAGE && page < maxPage) {
                page++;
                render();
            }
        }

        @Override
        public ItemStack quickMove(PlayerEntity player, int slot) { return ItemStack.EMPTY; }

        private PlayerData data() { return SVFrameMMO.playerData().get(owner); }

        private void render() {
            menu.clear();
            visibleSkills.clear();
            visibleLoadoutSlots.clear();
            PlayerData data = data();
            Map<Integer, PlayerSkillCatalog.Entry> bindings = PlayerSkillCatalog.bindings(data);
            List<Integer> loadoutSlots = PlayerSkillCatalog.slots(data);

            int displayedSlots = Math.min(9, loadoutSlots.size());
            for (int index = 0; index < displayedSlots; index++) {
                int slot = loadoutSlots.get(index);
                PlayerSkillCatalog.Entry bound = bindings.get(slot);
                boolean selected = slot == selectedLoadoutSlot;
                ItemStack icon;
                String name;
                if (bound != null) {
                    icon = new ItemStack(Items.ENCHANTED_BOOK);
                    name = "Slot " + slot + ": " + bound.skill().getSkill().getName() + sourceSuffix(bound);
                } else {
                    icon = new ItemStack(selected ? Items.LIME_DYE : Items.GRAY_DYE);
                    name = "Slot " + slot + ": Empty";
                }
                if (selected) name = "[Selected] " + name;
                icon.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name));
                menu.setStack(index, icon);
                visibleLoadoutSlots.put(index, slot);
            }

            List<PlayerSkillCatalog.Entry> skills = PlayerSkillCatalog.entries(data);
            int maxPage = maxPage(skills.size());
            if (page > maxPage) page = maxPage;
            int from = page * SKILLS_PER_PAGE;
            int to = Math.min(skills.size(), from + SKILLS_PER_PAGE);
            for (int index = from; index < to; index++) {
                PlayerSkillCatalog.Entry entry = skills.get(index);
                int menuSlot = FIRST_SKILL_SLOT + (index - from);
                ItemStack icon;
                if (!entry.learned()) icon = new ItemStack(Items.BARRIER);
                else if (entry.origin() == PlayerSkillCatalog.Origin.EXTERNAL) icon = new ItemStack(Items.ENCHANTED_BOOK);
                else icon = new ItemStack(Items.BOOK);

                Integer boundSlot = boundSlot(bindings, entry.id());
                StringBuilder name = new StringBuilder();
                name.append(entry.origin() == PlayerSkillCatalog.Origin.CLASS ? "[Class] " : "[Integration] ")
                        .append(entry.skill().getSkill().getName());
                if (entry.learned()) name.append(" Lv.").append(Math.max(1, entry.level()));
                else name.append(" [Locked]");
                if (boundSlot != null) name.append(" [Slot ").append(boundSlot).append(']');
                if (!entry.bindable()) name.append(" [Passive]");
                icon.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name.toString()));
                menu.setStack(menuSlot, icon);
                visibleSkills.put(menuSlot, entry);
            }

            if (skills.isEmpty()) {
                ItemStack empty = new ItemStack(Items.BARRIER);
                empty.set(DataComponentTypes.CUSTOM_NAME, Text.literal("No RPG skills are available"));
                menu.setStack(22, empty);
            }

            if (page > 0) menu.setStack(PREVIOUS_PAGE, named(new ItemStack(Items.ARROW), "Previous page"));
            String slotWarning = loadoutSlots.size() > 9 ? " | first 9 slots shown" : "";
            menu.setStack(STATUS, named(new ItemStack(Items.PAPER),
                    "Page " + (page + 1) + "/" + (maxPage + 1) + " | " + skills.size()
                            + " skills | left-click skill to bind | right-click slot to unbind" + slotWarning));
            if (page < maxPage) menu.setStack(NEXT_PAGE, named(new ItemStack(Items.ARROW), "Next page"));
            sendContentUpdates();
        }

        private int maxPage() { return maxPage(PlayerSkillCatalog.entries(data()).size()); }
        private static int maxPage(int count) { return Math.max(0, (count - 1) / SKILLS_PER_PAGE); }

        private static Integer boundSlot(Map<Integer, PlayerSkillCatalog.Entry> bindings, String skillId) {
            for (Map.Entry<Integer, PlayerSkillCatalog.Entry> entry : bindings.entrySet())
                if (skillId.equals(entry.getValue().id())) return entry.getKey();
            return null;
        }

        private static String sourceSuffix(PlayerSkillCatalog.Entry entry) {
            return entry.origin() == PlayerSkillCatalog.Origin.EXTERNAL ? " [Integration]" : " [Class]";
        }

        private static ItemStack named(ItemStack stack, String name) {
            stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name));
            return stack;
        }
    }
}
