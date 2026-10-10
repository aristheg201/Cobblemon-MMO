package vn.svframe.fantasyhub.client;

/** Observations of actual damage packets and hurt-camera render calls; no gameplay mutations. */
public final class ClientFeedbackQa {
    public static final boolean ENABLED="1".equals(System.getenv("SVFRAME_RUNTIME_QA"));
    public static long damagePackets,hurtCameraFrames;
    private ClientFeedbackQa(){}
}
