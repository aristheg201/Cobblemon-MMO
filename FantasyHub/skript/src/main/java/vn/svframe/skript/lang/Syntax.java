package vn.svframe.skript.lang;

import java.util.*;

/** Lexical operations shared by the source parser and natural-language expression compiler. */
public final class Syntax {
    private Syntax() {}
    public record Source(String file,int line,String text) {
        @Override public String toString(){return file+":"+line;}
    }
    public static final class Error extends IllegalArgumentException {
        public final Source source;
        public Error(Source source,String message){super(source+": "+message);this.source=source;}
    }
    public static String uncomment(String s) {
        boolean quote=false;
        for(int i=0;i<s.length();i++){
            char c=s.charAt(i);
            if(c=='"'){
                if(quote&&i+1<s.length()&&s.charAt(i+1)=='"'){i++;continue;}
                quote=!quote;
            }
            if(c=='#'&&!quote)return s.substring(0,i).stripTrailing();
        }
        return s.stripTrailing();
    }
    /** Delimiters only match outside quoted strings, variables and parentheses. */
    public static int find(String text,String token,boolean last) {
        boolean quote=false;int braces=0,parens=0,square=0,found=-1;
        for(int i=0;i<=text.length()-token.length();i++){
            char c=text.charAt(i);
            if(c=='"'){
                if(quote&&i+1<text.length()&&text.charAt(i+1)=='"'){i++;continue;}
                quote=!quote;continue;
            }
            if(quote)continue;
            if(braces==0&&parens==0&&square==0&&text.regionMatches(true,i,token,0,token.length())){
                found=i;if(!last)return i;
            }
            if(c=='{')braces++;else if(c=='}')braces--;
            else if(braces==0){if(c=='(')parens++;else if(c==')')parens--;else if(c=='[')square++;else if(c==']')square--;}
        }
        return found;
    }
    public static List<String> split(String text,String token) {
        List<String> out=new ArrayList<>();int p;
        while((p=find(text,token,false))>=0){out.add(text.substring(0,p).strip());text=text.substring(p+token.length());}
        out.add(text.strip());return out;
    }
    public static boolean enclosed(String s,char open,char close) {
        if(s.length()<2||s.charAt(0)!=open||s.charAt(s.length()-1)!=close)return false;
        boolean quote=false;int depth=0;
        for(int i=0;i<s.length();i++){
            char c=s.charAt(i);if(c=='"'){if(quote&&i+1<s.length()&&s.charAt(i+1)=='"'){i++;continue;}quote=!quote;}
            if(quote)continue;
            if(c==open)depth++;if(c==close&&--depth==0&&i<s.length()-1)return false;
        }
        return depth==0;
    }
    public static String normalize(String s){return s.strip().toLowerCase(Locale.ROOT);}
    public static String formatNumber(double n){return n==(long)n?Long.toString((long)n):Double.toString(n);}
}
