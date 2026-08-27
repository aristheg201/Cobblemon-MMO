package vn.svframe.svframeitems.validation;

import vn.svframe.svframeitems.registry.SVFrameItemsRegistry;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class NativeCoreSmoke {
    private static final List<String> FILES=List.of("types.yml","rarities.yml","upgrades.yml","sets.yml","recipes.yml","loot.yml","items/examples.yml");
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("svframeitems-smoke");
        for(String relative:FILES){Path target=root.resolve(relative);Files.createDirectories(target.getParent());try(InputStream input=NativeCoreSmoke.class.getResourceAsStream("/default/"+relative)){if(input==null)throw new IllegalStateException("missing "+relative);Files.copy(input,target);}}
        SVFrameItemsRegistry registry=new SVFrameItemsRegistry();registry.reload(root);
        require(registry.types().size()==10,"types");require(registry.rarities().size()==5,"rarities");require(registry.items().size()==3,"items");require(registry.sets().size()==1,"sets");require(registry.upgrades().size()==2,"upgrades");require(registry.recipes().size()==1,"recipes");require(registry.lootTables().size()==1,"loot");
        require(registry.item("ruby_gem").isGem(),"gem definition");require(registry.item("trailblazer_blade").stats().size()==2,"item stats");
        System.out.println("SVFRAMEITEMS_NATIVE_CORE=PASS "+registry.summary());
    }
    private static void require(boolean value,String label){if(!value)throw new AssertionError(label);}
}
