package vn.svframe.svframelib.skill.handler;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Exact SVFrameLib 1.7.1 built-in skill metadata annotation. */
@Retention(RetentionPolicy.RUNTIME)
public @interface BuiltinSkillHandler {
    String[] mods() default {};
    boolean triggerable() default true;
}
