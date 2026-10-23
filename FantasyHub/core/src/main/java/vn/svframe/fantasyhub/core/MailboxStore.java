package vn.svframe.fantasyhub.core;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/** Immutable mailbox blobs. Only their short reference participates in the player-data transaction. */
final class MailboxStore {
 private static final String PREFIX="mail:v1:";
 private static final int MAX_BYTES=4*1024*1024;
 private final Path root;
 MailboxStore(Path root){this.root=root;}
 String write(UUID player,String contents)throws IOException{
  byte[] bytes=contents.getBytes(StandardCharsets.UTF_8);
  if(bytes.length>MAX_BYTES)throw new IOException("Mailbox exceeds 4 MiB; existing rewards preserved");
  String hash=hash(bytes),ref=PREFIX+hash;Path directory=root.resolve(player.toString()),target=directory.resolve(hash+".json");
  Files.createDirectories(directory);
  if(Files.exists(target)){read(player,ref);return ref;}
  Path temporary=Files.createTempFile(directory,"pending-",".tmp");
  try{
   try(var channel=FileChannel.open(temporary,StandardOpenOption.WRITE)){var buffer=ByteBuffer.wrap(bytes);while(buffer.hasRemaining())channel.write(buffer);channel.force(true);}
   Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE);
   try(var channel=FileChannel.open(directory,StandardOpenOption.READ)){channel.force(true);}
   catch(UnsupportedOperationException|java.nio.file.AccessDeniedException unsupportedDirectorySync){/* Windows cannot open directory channels. The blob itself has been fsynced. */}
  }finally{Files.deleteIfExists(temporary);}
  return ref;
 }
 String read(UUID player,String ref)throws IOException{
  if(ref.startsWith("[")){if(ref.getBytes(StandardCharsets.UTF_8).length>MAX_BYTES)throw new IOException("Legacy mailbox exceeds size limit");return ref;}
  if(!ref.matches("mail:v1:[0-9a-f]{64}"))throw new IOException("Invalid mailbox reference for "+player);
  String hash=ref.substring(PREFIX.length());Path file=root.resolve(player.toString()).resolve(hash+".json");
  if(Files.size(file)>MAX_BYTES)throw new IOException("Mailbox exceeds size limit for "+player);
  byte[] bytes=Files.readAllBytes(file);if(!hash(bytes).equals(hash))throw new IOException("Mailbox integrity check failed for "+player);
  return new String(bytes,StandardCharsets.UTF_8);
 }
 private static String hash(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}}
}
