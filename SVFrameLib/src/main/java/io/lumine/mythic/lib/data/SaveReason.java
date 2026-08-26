package io.lumine.mythic.lib.data;
import io.lumine.mythic.lib.profile.SessionUpdateReason;
@Deprecated public enum SaveReason { AUTOSAVE,LOG_OUT,QUIT_PROFILE;
    public SessionUpdateReason adapt(){return switch(this){case AUTOSAVE->SessionUpdateReason.AUTOSAVE;case LOG_OUT->SessionUpdateReason.LOG_OUT;case QUIT_PROFILE->SessionUpdateReason.QUIT_PROFILE;};}
}
