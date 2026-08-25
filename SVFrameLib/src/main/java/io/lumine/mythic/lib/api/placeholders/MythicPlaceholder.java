package io.lumine.mythic.lib.api.placeholders;
public interface MythicPlaceholder { String getAuthorName(); String getMythicIdentifier(); String parse(String arg,Object context); boolean forUseWith(Object context); }
