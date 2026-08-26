package io.lumine.mythic.lib.gui.editable.placeholder;
public class ErrorPlaceholders extends Placeholders {
    @Override public String parsePlaceholder(String key){String value=super.parsePlaceholder(key);return value==null?"<missing:"+key+">":value;}
}
