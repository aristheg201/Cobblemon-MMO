package vn.svframe.fantasyhub.core;
import org.junit.jupiter.api.Test;import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;import java.io.*;import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class MailboxStoreTest {
 @TempDir Path root;
 @Test void largeBundleFitsLegacyPreferenceViaSmallDurableReference()throws Exception{
  var store=new MailboxStore(root);String contents="[{\"stack\":\""+"Native registered item components ".repeat(3000)+"\"}]";assertTrue(contents.length()>160);String ref=store.write(UUID.fromString("00000000-0000-0000-0000-000000000001"),contents);assertTrue(ref.length()<=160);assertEquals(contents,new MailboxStore(root).read(UUID.fromString("00000000-0000-0000-0000-000000000001"),ref));
 }
 @Test void rollbackReferenceKeepsOldMailAndRepeatedPreparationIsIdempotent()throws Exception{
  var store=new MailboxStore(root);var p=UUID.randomUUID();String old=store.write(p,"[1]"),prepared=store.write(p,"[1,2]");assertEquals(prepared,store.write(p,"[1,2]"));assertEquals("[1]",store.read(p,old));assertEquals("[1,2]",store.read(p,prepared));
 }
 @Test void inlineLegacyMailCanBeReadWithoutDroppingItsContents()throws Exception{
  var store=new MailboxStore(root);var p=UUID.randomUUID();String old="[{\"id\":\"old-paid-receipt\"}]";assertEquals(old,store.read(p,old));assertEquals(old,store.read(p,store.write(p,store.read(p,old))));
 }
 @Test void missingCorruptAndCrossPlayerReferencesFailClosed()throws Exception{
  var store=new MailboxStore(root);var p=UUID.randomUUID();String ref=store.write(p,"[1]");assertThrows(IOException.class,()->store.read(UUID.randomUUID(),ref));try(var files=Files.walk(root)){var file=files.filter(Files::isRegularFile).findFirst().orElseThrow();Files.writeString(file,"tampered");}assertThrows(IOException.class,()->store.read(p,ref));assertThrows(IOException.class,()->store.read(p,"mail:v1:../../escape"));
 }
}
