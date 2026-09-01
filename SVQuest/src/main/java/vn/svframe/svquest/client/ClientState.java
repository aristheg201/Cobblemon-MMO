package vn.svframe.svquest.client;

import vn.svframe.svquest.quest.QuestCatalog;

import java.util.HashMap;
import java.util.Map;

public final class ClientState {
    private int questIndex;
    private final Map<String, Integer> progress = new HashMap<>();
    private boolean serverAvailable;
    private String catalogSequence = "";
    private String[] catalogChunks = new String[0];
    private int catalogReceived;

    public int questIndex() { return questIndex; }
    public int progress(String key) { return Math.max(0, progress.getOrDefault(key, 0)); }
    public boolean serverAvailable() { return serverAvailable; }

    public void markServerUnavailable() { serverAvailable = false; }

    /** Reassembles bounded catalog chunks, then atomically installs the validated server catalog. */
    public void applyCatalogChunk(String encoded) {
        try {
            int a = encoded.indexOf('|');
            int b = a < 0 ? -1 : encoded.indexOf('|', a + 1);
            int c = b < 0 ? -1 : encoded.indexOf('|', b + 1);
            if (a <= 0 || b <= a || c <= b) return;
            String sequence = encoded.substring(0, a);
            int index = parse(encoded.substring(a + 1, b), -1);
            int total = parse(encoded.substring(b + 1, c), -1);
            if (index < 0 || total <= 0 || total > 1024 || index >= total) return;

            if (!sequence.equals(catalogSequence) || catalogChunks.length != total) {
                catalogSequence = sequence;
                catalogChunks = new String[total];
                catalogReceived = 0;
            }
            if (catalogChunks[index] == null) {
                catalogChunks[index] = encoded.substring(c + 1);
                catalogReceived++;
            }
            if (catalogReceived != total) return;

            StringBuilder token = new StringBuilder();
            for (String chunk : catalogChunks) {
                if (chunk == null) return;
                token.append(chunk);
            }
            QuestCatalog.installClientSnapshotToken(token.toString());
            catalogChunks = new String[0];
            catalogReceived = 0;
        } catch (Throwable ignored) {
        }
    }

    public void apply(String encoded) {
        try {
            int nextIndex = 0;
            String legacyCatalogToken = "";
            Map<String, Integer> next = new HashMap<>();
            for (String line : encoded.split("\\n")) {
                int eq = line.indexOf('=');
                if (eq <= 0) continue;
                String key = line.substring(0, eq);
                String value = line.substring(eq + 1);
                if (key.equals("questIndex")) nextIndex = parse(value, 0);
                else if (key.equals("catalog64")) legacyCatalogToken = value;
                else if (key.startsWith("p.")) next.put(key.substring(2), parse(value, 0));
            }
            if (!legacyCatalogToken.isBlank()) QuestCatalog.installClientSnapshotToken(legacyCatalogToken);
            questIndex = Math.max(0, nextIndex);
            progress.clear();
            progress.putAll(next);
            serverAvailable = true;
        } catch (Throwable ignored) {
        }
    }

    private static int parse(String v, int fallback) {
        try { return Integer.parseInt(v); }
        catch (Exception ignored) { return fallback; }
    }
}
