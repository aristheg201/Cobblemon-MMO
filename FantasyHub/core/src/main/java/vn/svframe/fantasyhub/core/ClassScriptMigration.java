package vn.svframe.fantasyhub.core;

import java.io.IOException;
import java.nio.file.*;
import java.security.*;
import java.util.HexFormat;

/** Only the exact supplied legacy class script is migrated; server-authored scripts are preserved. */
final class ClassScriptMigration {
    static void migrate(Path config) throws IOException {
        Path scripts=config.resolve("SVFrameSkript/scripts");if(!Files.isDirectory(scripts))return;
        try(var paths=Files.walk(scripts)) {
            for(Path file:paths.filter(Files::isRegularFile).filter(p->p.getFileName().toString().equals("sv_class_switch.sk")).toList()) {
                byte[] content=Files.readAllBytes(file);
                String hash;
                try {hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));}
                catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
                if(!hash.equals("83c38c37bfcdfc156d743cab01645ee19916270b456d587d7405b8223209be8f"))continue;
                String text=new String(content,java.nio.charset.StandardCharsets.UTF_8);
                text=text.substring(0,text.indexOf("        if svclass_begin(player) is false:"))+"        execute console command \"fantasyhub classpurchase %player% %{_class}%\"\n";
                text=text.replace("class-switch-fee: 100000","class-switch-fee: 3000");
                Files.copy(file,file.resolveSibling(file.getFileName()+".before-pricing-v2"),StandardCopyOption.REPLACE_EXISTING);
                Files.writeString(file,text);
            }
        }
    }
}
