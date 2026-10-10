package vn.svframe.svframelib.message.actionbar;

import vn.svframe.svframelib.api.player.MMOPlayerData;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Objects;
import java.util.function.Supplier;

/** Native Fabric action-bar arbitration with SVFrameLib 1.7.1 priority/timeout semantics. */
public class ActionBarHandler {
    public static final long DEFAULT_TIME_OUT = 30L;

    private final MMOPlayerData playerData;
    private int lastPriority;
    private long timeOut;

    public ActionBarHandler(MMOPlayerData playerData) {
        this.playerData = Objects.requireNonNull(playerData, "playerData");
    }

    public boolean canShow(int priority) { return !isBusy() || priority >= lastPriority; }

    public boolean hide(int priority, long duration) { return show(priority + 1, duration, (Supplier<String>) null); }
    public boolean show(String message) { return show(ActionBarPriority.NORMAL, DEFAULT_TIME_OUT, message); }
    public boolean show(int priority, String message) { return show(priority, DEFAULT_TIME_OUT, message); }
    public boolean show(int priority, long duration, String message) { return show(priority, duration, () -> message); }
    public boolean show(int priority, Supplier<String> message) { return show(priority, DEFAULT_TIME_OUT, message); }

    public boolean show(int priority, long duration, Supplier<String> message) {
        if (!canShow(priority)) return false;
        lastPriority = priority;
        timeOut = System.currentTimeMillis() + Math.max(0L, duration) * 50L;
        if (message != null && playerData.isOnline()) {
            String resolved = message.get();
            if (resolved != null) playerData.getPlayer().sendMessage(legacyText(resolved), true);
        }
        return true;
    }

    public void reset(int priority) {
        if (!canShow(priority)) return;
        lastPriority = priority;
        timeOut = 0L;
    }

    public int getCurrentPriority() { return lastPriority; }
    public boolean isBusy() { return System.currentTimeMillis() < timeOut; }

    /** Converts the legacy formatting emitted by SVFrameLib/MMO configs into real native Text styles. */
    static Text legacyText(String input) {
        MutableText root = Text.empty();
        Style style = Style.EMPTY;
        StringBuilder part = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c != '§' || i + 1 >= input.length()) {
                part.append(c);
                continue;
            }
            if (!part.isEmpty()) {
                root.append(Text.literal(part.toString()).setStyle(style));
                part.setLength(0);
            }
            char code = Character.toLowerCase(input.charAt(++i));
            if (code == 'x' && i + 12 < input.length()) {
                StringBuilder hex = new StringBuilder(6);
                int cursor = i + 1;
                boolean valid = true;
                for (int n = 0; n < 6; n++) {
                    if (cursor + 1 >= input.length() || input.charAt(cursor) != '§') { valid = false; break; }
                    char digit = Character.toLowerCase(input.charAt(cursor + 1));
                    if (Character.digit(digit, 16) < 0) { valid = false; break; }
                    hex.append(digit);
                    cursor += 2;
                }
                if (valid) {
                    style = Style.EMPTY.withColor(Integer.parseInt(hex.toString(), 16));
                    i = cursor - 1;
                    continue;
                }
            }
            Formatting formatting = Formatting.byCode(code);
            if (formatting == null) continue;
            if (formatting == Formatting.RESET) style = Style.EMPTY;
            else if (formatting.isColor()) style = style.withExclusiveFormatting(formatting);
            else style = style.withFormatting(formatting);
        }
        if (!part.isEmpty()) root.append(Text.literal(part.toString()).setStyle(style));
        return root;
    }
}
