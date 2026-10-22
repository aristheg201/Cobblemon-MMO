package vn.svframe.worldboss;
import java.util.function.Consumer;
/** Acquired actors must be discarded if any subsequent configuration step fails. */
final class ActorCreation {
 private ActorCreation(){}
 static <T>T finish(T actor,Consumer<T> configure,Consumer<T> discard){
  try{configure.accept(actor);return actor;}
  catch(RuntimeException|Error failure){try{discard.accept(actor);}catch(RuntimeException|Error cleanup){failure.addSuppressed(cleanup);}throw failure;}
 }
}
