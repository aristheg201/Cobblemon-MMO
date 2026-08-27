package vn.svframe.svframemmo.config;

import net.fabricmc.loader.api.FabricLoader;
import java.io.*;import java.nio.file.*;import java.util.List;
public final class DefaultFiles {
 public static final Path ROOT=FabricLoader.getInstance().getConfigDir().resolve("SVFrameMMO").toAbsolutePath().normalize();
 private static final List<String> FILES=List.of("config.yml","classes/human.yml","classes/marksman.yml","classes/paladin.yml","classes/rogue.yml","classes/warrior.yml","classes/mage/mage.yml","classes/mage/arcane-mage.yml","attributes/default_attributes.yml","skills/ambers.yml","skills/neptune-gift.yml","skills/sneaky-picky.yml");
 private DefaultFiles(){}
 public static void ensure() throws IOException {Files.createDirectories(ROOT);ClassLoader l=DefaultFiles.class.getClassLoader();for(String rel:FILES){Path out=ROOT.resolve(rel).normalize();if(!out.startsWith(ROOT))throw new IOException("Unsafe default path: "+rel);if(Files.exists(out))continue;try(InputStream in=l.getResourceAsStream("defaults/"+rel)){if(in==null)throw new IOException("Missing bundled SVFrameMMO default: "+rel);Files.createDirectories(out.getParent());Path tmp=Files.createTempFile(out.getParent(),out.getFileName().toString(),".tmp");try{Files.copy(in,tmp,StandardCopyOption.REPLACE_EXISTING);try{Files.move(tmp,out,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(tmp,out,StandardCopyOption.REPLACE_EXISTING);}}finally{Files.deleteIfExists(tmp);}}}}
}
