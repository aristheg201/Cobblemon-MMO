package vn.svframe.fantasyhub.client;

final class ClientSyncClock {
  static volatile long offset;

  static long now() {
    return System.currentTimeMillis() - offset;
  }
}
