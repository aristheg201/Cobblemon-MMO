package vn.svframe.svframelib.skill.parameter.value;

import vn.svframe.svframelib.SVFrameLib;
import java.util.logging.Level;

public class FormulaFailsafeException extends RuntimeException {
    private final double failsafe;

    public FormulaFailsafeException(Exception cause, double failsafe) {
        super(cause);
        this.failsafe = failsafe;
    }
    public FormulaFailsafeException(String message) { super(message); this.failsafe = 0d; }
    public FormulaFailsafeException(String message, Throwable cause) { super(message, cause); this.failsafe = 0d; }
    public double getFailsafe() { return failsafe; }
    public void log(String format, Object... args) {
        String prefix = args == null || args.length == 0 ? format : String.format(format, args);
        Throwable cause = getCause();
        SVFrameLib.inst().getLogger().log(Level.WARNING, prefix + (cause == null || cause.getMessage() == null ? "" : ": " + cause.getMessage()));
    }
}
