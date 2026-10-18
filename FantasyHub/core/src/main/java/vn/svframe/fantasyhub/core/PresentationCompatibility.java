package vn.svframe.fantasyhub.core;

/**
 * Release matching is derived from the running server, never a previous release's literal version.
 */
final class PresentationCompatibility {
  static boolean accepts(
      String runningVersion,
      String requiredAssets,
      int requiredProtocol,
      String clientVersion,
      String clientAssets,
      int clientProtocol,
      String diagnostic) {
    return runningVersion.equals(clientVersion)
        && requiredAssets.equals(clientAssets)
        && requiredProtocol == clientProtocol
        && diagnostic != null
        && diagnostic.isEmpty();
  }
}
