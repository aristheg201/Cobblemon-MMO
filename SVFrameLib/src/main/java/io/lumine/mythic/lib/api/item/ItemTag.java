package io.lumine.mythic.lib.api.item;

import java.util.*;

public final class ItemTag {
    private final String path;private final Object value;
    public ItemTag(String path,Object value){this.path=Objects.requireNonNull(path,"path");this.value=value;}
    public String getPath(){return path;}public Object getValue(){return value;}
    @Override public boolean equals(Object o){return o instanceof ItemTag t&&path.equals(t.path)&&Objects.deepEquals(value,t.value);}@Override public int hashCode(){return 31*path.hashCode()+Objects.hashCode(value);}
    public static ItemTag getTagAtPath(String path,ArrayList<ItemTag> tags){if(tags==null)return null;for(ItemTag t:tags)if(t.path.equals(path))return t;return null;}
    public static ItemTag getTagAtPath(String path,NBTItem item,SupportedNBTTagValues expected){if(item==null||!item.hasTag(path))return null;return new ItemTag(path,item.get(path));}
    public static ItemTag fromStringList(String path,List<String> list){return new ItemTag(path,List.copyOf(list));}
    public static ArrayList<String> getStringListFromTag(ItemTag tag){ArrayList<String> out=new ArrayList<>();if(tag!=null&&tag.value instanceof Iterable<?> it)for(Object o:it)out.add(String.valueOf(o));return out;}
}
