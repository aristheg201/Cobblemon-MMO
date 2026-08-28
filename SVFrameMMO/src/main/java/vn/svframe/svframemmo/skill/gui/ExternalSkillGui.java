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
import vn.svframe.svframemmo.skill.ClassSkill;
import vn.svframe.svframemmo.skill.ExternalSkillProgression;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Vanilla-server GUI for browsing learned integration skills and editing the four-slot external loadout. */
public final class ExternalSkillGui {
    private static final int MENU_SIZE = 54;
    private static final int[] LOADOUT_SLOTS = {1, 3, 5, 7};
    private static final int FIRST_SKILL_SLOT = 9;
    private static final int SKILLS_PER_PAGE = 36;
    private static final int PREVIOUS_PAGE = 45;
    private static final int STATUS = 49;
    private static final int NEXT_PAGE = 53;

    private ExternalSkillGui() { }

    public static void open(ServerPlayerEntity player) {
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, inventory, ignored) -> new Handler(syncId, inventory, player),
                Text.literal("SVFrameMMO Skills")));
    }

    private static final class Handler extends GenericContainerScreenHandler {
        private final SimpleInventory menu;
        private final ServerPlayerEntity owner;
        private final Map<Integer, String> visibleSkills = new LinkedHashMap<>();
        private int page;
        private int selectedLoadoutSlot = 1;

        private Handler(int syncId, PlayerInventory playerInventory, ServerPlayerEntity owner) {
            this(syncId, playerInventory, owner, new SimpleInventory(MENU_SIZE));
        }

        private Handler(int syncId, PlayerInventory playerInventory, ServerPlayerEntity owner, SimpleInventory menu) {
            super(ScreenHandlerType.GENERIC_9X6, syncId, playerInventory, menu, 6);
            this.menu = menu;
            this.owner = owner;
            render();
        }

        @Override
        public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
            if (!player.getUuid().equals(owner.getUuid())) return;
            // This is a control GUI. Never allow menu/player inventory item movement through it.
            if (slotIndex < 0 || slotIndex >= MENU_SIZE) return;

            for (int i = 0; i < LOADOUT_SLOTS.length; i++) {
                if (slotIndex != LOADOUT_SLOTS[i]) continue;
                int loadoutSlot = i + 1;
                if (button == 1) {
                    SVFrameMMO.externalProgression().unbind(owner.getUuid(), loadoutSlot);
                    SVFrameMMO.externalProgression().save();
                } else {
                    selectedLoadoutSlot = loadoutSlot;
                }
                render();
                return;
            }

            String skillId = visibleSkills.get(slotIndex);
            if (skillId != null) {
                try {
                    SVFrameMMO.externalProgression().bind(owner.getUuid(), selectedLoadoutSlot, skillId);
                    SVFrameMMO.externalProgression().save();
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
        public ItemStack quickMove(PlayerEntity player, int slot) {
            return ItemStack.EMPTY;
        }

        private void render() {
            menu.clear();
            visibleSkills.clear();

            Map<Integer, String> bindings = SVFrameMMO.externalProgression().bindings(owner.getUuid());
            for (int i = 0; i < LOADOUT_SLOTS.length; i++) {
                int loadout = i + 1;
                String boundId = bindings.get(loadout);
                ClassSkill bound = boundId == null ? null : SVFrameMMO.externalSkills().get(boundId);
                ItemStack icon;
                String name;
                if (bound != null) {
                    icon = new ItemStack(Items.ENCHANTED_BOOK);
                    name = "Slot " + loadout + ": " + bound.getSkill().getName();
                } else {
                    icon = new ItemStack(loadout == selectedLoadoutSlot ? Items.LIME_DYE : Items.GRAY_DYE);
                    name = "Slot " + loadout + ": Empty";
                }
                if (loadout == selectedLoadoutSlot) name = "[Selected] " + name;
                icon.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name));
                menu.setStack(LOADOUT_SLOTS[i], icon);
            }

            List<ClassSkill> learned = learnedDefinitions();
            int maxPage = maxPage(learned.size());
            if (page > maxPage) page = maxPage;
            int from = page * SKILLS_PER_PAGE;
            int to = Math.min(learned.size(), from + SKILLS_PER_PAGE);
            for (int index = from; index < to; index++) {
                ClassSkill skill = learned.get(index);
                int menuSlot = FIRST_SKILL_SLOT + (index - from);
                ItemStack icon = new ItemStack(Items.BOOK);
                int level = SVFrameMMO.externalProgression().level(owner.getUuid(), skill.getSkill().getId());
                String bound = boundSlot(bindings, skill.getSkill().getId());
                String suffix = bound == null ? "" : " [Slot " + bound + "]";
                icon.set(DataComponentTypes.CUSTOM_NAME,
                        Text.literal(skill.getSkill().getName() + " Lv." + Math.max(1, level) + suffix));
                menu.setStack(menuSlot, icon);
                visibleSkills.put(menuSlot, skill.getSkill().getId());
            }

            if (learned.isEmpty()) {
                ItemStack empty = new ItemStack(Items.BARRIER);
                empty.set(DataComponentTypes.CUSTOM_NAME, Text.literal("No learned external skills"));
                menu.setStack(22, empty);
            }

            if (page > 0) menu.setStack(PREVIOUS_PAGE, named(new ItemStack(Items.ARROW), "Previous page"));
            menu.setStack(STATUS, named(new ItemStack(Items.PAPER),
                    "Page " + (page + 1) + "/" + (maxPage + 1) + " | " + learned.size() + " learned | right-click loadout to unbind"));
            if (page < maxPage) menu.setStack(NEXT_PAGE, named(new ItemStack(Items.ARROW), "Next page"));
            sendContentUpdates();
        }

        private List<ClassSkill> learnedDefinitions() {
            ArrayList<ClassSkill> out = new ArrayList<>();
            for (String id : SVFrameMMO.externalProgression().learned(owner.getUuid()).keySet()) {
                ClassSkill skill = SVFrameMMO.externalSkills().get(id);
                if (skill != null) out.add(skill);
            }
            out.sort(Comparator.comparing(skill -> skill.getSkill().getName(), String.CASE_INSENSITIVE_ORDER));
            return List.copyOf(out);
        }

        private int maxPage() { return maxPage(learnedDefinitions().size()); }
        private static int maxPage(int count) { return Math.max(0, (count - 1) / SKILLS_PER_PAGE); }

        private static String boundSlot(Map<Integer, String> bindings, String skillId) {
            for (Map.Entry<Integer, String> entry : bindings.entrySet())
                if (skillId.equals(entry.getValue())) return Integer.toString(entry.getKey());
            return null;
        }

        private static ItemStack named(ItemStack stack, String name) {
            stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name));
            return stack;
        }
    }
}
