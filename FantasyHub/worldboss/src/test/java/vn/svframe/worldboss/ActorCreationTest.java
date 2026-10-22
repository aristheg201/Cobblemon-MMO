package vn.svframe.worldboss;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ActorCreationTest {
 private static final class Actor {boolean discarded,configured;}
 @Test void configurationFailureDiscardsTheAlreadyCreatedActor(){var actor=new Actor();var error=new IllegalArgumentException("Invalid skill");var thrown=assertThrows(IllegalArgumentException.class,()->ActorCreation.finish(actor,a->{throw error;},a->a.discarded=true));assertSame(error,thrown);assertTrue(actor.discarded);}
 @Test void successfulConfigurationKeepsActorAlive(){var actor=new Actor();assertSame(actor,ActorCreation.finish(actor,a->a.configured=true,a->a.discarded=true));assertTrue(actor.configured);assertFalse(actor.discarded);}
 @Test void cleanupFailureCannotHideOriginalConfigurationError(){var actor=new Actor();var error=new IllegalArgumentException("Invalid skill");var cleanup=new IllegalStateException("Cleanup failed");var thrown=assertThrows(IllegalArgumentException.class,()->ActorCreation.finish(actor,a->{throw error;},a->{throw cleanup;}));assertSame(error,thrown);assertArrayEquals(new Throwable[]{cleanup},thrown.getSuppressed());}
}
