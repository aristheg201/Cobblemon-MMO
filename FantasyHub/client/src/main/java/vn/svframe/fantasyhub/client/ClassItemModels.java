package vn.svframe.fantasyhub.client;
import net.minecraft.util.Identifier;
public final class ClassItemModels {
  private ClassItemModels(){}
  public static boolean supports(Identifier id){return id!=null&&id.getNamespace().equals("fantasyhub")&&java.util.List.of("item/riders/","item/additions/","item/dragonborn/","item/hunter/","item/heroes/").stream().anyMatch(id.getPath()::startsWith);}
}
