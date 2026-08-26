package vn.svframe.svframelib.comp.adventure.tag.implementation.decorations;

import vn.svframe.svframelib.comp.adventure.tag.AdventureTag;

public class UnderlineTag extends AdventureTag {
    public UnderlineTag() {
        super("underlined", (src, args) -> "§n", true, false, "u", "underline");
    }
}
