package vn.svframe.svframelib.util.configobject;

import java.util.LinkedHashMap;
import java.util.Map;

/** Fabric-side line-config wrapper. Accepts SVFrameLib's {key=value,...} and JSON-ish forms. */
public class JsonWrapper extends MapConfigObject {
    public JsonWrapper(String key,Map<String,?> object){super(key,object);}
    protected JsonWrapper(String line){this(parseKey(line),parseBody(line));}
    private static String parseKey(String line){if(line==null)return null;int open=line.indexOf('{');if(open<0)return null;String key=line.substring(0,open).trim();return key.isEmpty()?null:key;}
    private static Map<String,Object> parseBody(String line){Map<String,Object> result=new LinkedHashMap<>();if(line==null)return result;int open=line.indexOf('{'),close=line.lastIndexOf('}');if(open<0||close<open)return result;String body=line.substring(open+1,close).trim();if(body.isEmpty())return result;for(String part:splitTopLevel(body)){int split=findSeparator(part);if(split<0){String key=unquote(part.trim());if(!key.isEmpty())result.put(key,true);continue;}String key=unquote(part.substring(0,split).trim());String raw=part.substring(split+1).trim();result.put(key,parseScalar(raw));}return result;}
    private static int findSeparator(String s){int depth=0;char quote=0;for(int i=0;i<s.length();i++){char c=s.charAt(i);if(quote!=0){if(c==quote&&s.charAt(Math.max(0,i-1))!='\\')quote=0;continue;}if(c=='\''||c=='\"'){quote=c;continue;}if(c=='{'||c=='['||c=='(')depth++;else if(c=='}'||c==']'||c==')')depth--;else if(depth==0&&(c=='='||c==':'))return i;}return -1;}
    private static java.util.List<String> splitTopLevel(String s){java.util.List<String> out=new java.util.ArrayList<>();int start=0,depth=0;char quote=0;for(int i=0;i<s.length();i++){char c=s.charAt(i);if(quote!=0){if(c==quote&&(i==0||s.charAt(i-1)!='\\'))quote=0;continue;}if(c=='\''||c=='\"'){quote=c;continue;}if(c=='{'||c=='['||c=='(')depth++;else if(c=='}'||c==']'||c==')')depth--;else if((c==','||c==';')&&depth==0){String part=s.substring(start,i).trim();if(!part.isEmpty())out.add(part);start=i+1;}}String part=s.substring(start).trim();if(!part.isEmpty())out.add(part);return out;}
    private static Object parseScalar(String raw){String s=unquote(raw);if(s.equalsIgnoreCase("true"))return true;if(s.equalsIgnoreCase("false"))return false;try{if(s.matches("[-+]?\\d+"))return Integer.parseInt(s);if(s.matches("[-+]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][-+]?\\d+)?"))return Double.parseDouble(s);}catch(NumberFormatException ignored){}return s;}
    private static String unquote(String s){if(s.length()>=2&&((s.startsWith("\"")&&s.endsWith("\""))||(s.startsWith("'")&&s.endsWith("'"))))return s.substring(1,s.length()-1);return s;}
}
