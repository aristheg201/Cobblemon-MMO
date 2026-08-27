package vn.svframe.svframemmo.manager;

import vn.svframe.svframelib.config.YamlLite;
import vn.svframe.svframemmo.skilltree.SkillTree;
import vn.svframe.svframemmo.skilltree.SkillTreeNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class SkillTreeManager {
    private final Map<String, SkillTree> trees=new LinkedHashMap<>();
    public void reload(Path directory)throws IOException{
        trees.clear(); if(!Files.isDirectory(directory))throw new IOException("Missing skill-tree directory: "+directory);
        try(var stream=Files.list(directory)){for(Path file:stream.filter(Files::isRegularFile).filter(p->p.toString().endsWith(".yml")).sorted().toList()){SkillTree tree=new SkillTree(YamlLite.map(YamlLite.parse(file)));if(trees.putIfAbsent(tree.getId(),tree)!=null)throw new IOException("Duplicate skill tree: "+tree.getId());}}
        if(trees.isEmpty())throw new IOException("No skill trees loaded from "+directory);
    }
    public SkillTree get(String id){return id==null?null:trees.get(normalize(id));}
    public SkillTree getOrThrow(String id){SkillTree t=get(id);if(t==null)throw new IllegalArgumentException("Unknown skill tree '"+id+"'");return t;}
    public Collection<SkillTree> getAll(){return List.copyOf(trees.values());} public int size(){return trees.size();}
    public SkillTreeNode findNode(String fullId){if(fullId==null)return null;String n=fullId.trim().toLowerCase(Locale.ROOT).replace('-','_');for(SkillTree tree:trees.values())for(SkillTreeNode node:tree.getNodes())if(node.getFullId().replace('-','_').equalsIgnoreCase(n))return node;return null;}
    private static String normalize(String v){return v.trim().toLowerCase(Locale.ROOT).replace('_','-').replace(' ','-');}
}
