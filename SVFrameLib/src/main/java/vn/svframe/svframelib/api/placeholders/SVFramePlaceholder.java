package vn.svframe.svframelib.api.placeholders;
public interface SVFramePlaceholder { String getAuthorName(); String getSVFrameIdentifier(); String parse(String arg,Object context); boolean forUseWith(Object context); }
