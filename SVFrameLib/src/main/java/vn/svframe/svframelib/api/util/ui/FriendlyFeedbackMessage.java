package vn.svframe.svframelib.api.util.ui;
public class FriendlyFeedbackMessage implements Cloneable {
 String message; boolean withPrefix; String withSubdivision;
 public FriendlyFeedbackMessage(String message){this.message=message;}
 public FriendlyFeedbackMessage(String message,String subdivision){this.message=message;if(subdivision!=null){withPrefix=true;withSubdivision=subdivision;}}
 public FriendlyFeedbackMessage(String message,boolean prefix){this.message=message;withPrefix=prefix;}
 public FriendlyFeedbackMessage(String message,boolean prefix,String subdivision){this.message=message;withPrefix=prefix;withSubdivision=subdivision;}
 @Override public FriendlyFeedbackMessage clone(){return new FriendlyFeedbackMessage(message,withPrefix,withSubdivision);} public void setMessage(String m){message=m;} public void togglePrefix(boolean p){withPrefix=p;} public void setSubdivision(String s){withSubdivision=s;} public String getMessage(){return message;} public boolean hasPrefix(){return withPrefix;} public String getSubdivision(){return withSubdivision;} @Override public String toString(){return message;}
 public String forPlayer(FriendlyFeedbackPalette p){return (withPrefix?p.parseForPlayer(p.getPrefix(withSubdivision)):"")+p.parseForPlayer(message);}
 public String forConsole(FriendlyFeedbackPalette p){return (withPrefix?p.parseForConsole(p.consolePrefix(withSubdivision)):"")+p.parseForConsole(message);}
}
