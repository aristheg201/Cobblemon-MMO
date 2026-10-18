package vn.svframe.fantasyhub.core;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PresentationCompatibilityTest {
  @Test
  void matchingFutureReleasesDoNotRequireEditingHandshakeLogic() {
    for (String release : new String[] {"0.1.12", "0.2.0", "2.0.0-rc1"})
      assertTrue(PresentationCompatibility.accepts(release, "assets", 1, release, "assets", 1, ""));
  }

  @Test
  void oldClientsMissingAssetsAndBadProtocolAreRejected() {
    assertFalse(PresentationCompatibility.accepts("0.1.12", "v12", 1, "0.1.11", "v12", 1, ""));
    assertFalse(PresentationCompatibility.accepts("0.1.12", "v12", 1, "0.1.12", "v11", 1, ""));
    assertFalse(PresentationCompatibility.accepts("0.1.12", "v12", 1, "0.1.12", "v12", 2, ""));
    assertFalse(
        PresentationCompatibility.accepts(
            "0.1.12", "v12", 1, "0.1.12", "v12", 1, "missing texture"));
  }
}
