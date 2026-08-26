package io.lumine.mythic.lib.script.util;
public class VariableNotFoundException extends ScriptException {
    public VariableNotFoundException(String path){super("Could not find variable '"+path+"'");}
    public VariableNotFoundException(String path, Class<?> expected){super("Variable '"+path+"' is not of expected type "+(expected==null?"unknown":expected.getSimpleName()));}
    public VariableNotFoundException(String fullPath,String[] parts,int index){super("Could not resolve variable path '"+fullPath+"' at '"+(parts!=null&&index>=0&&index<parts.length?parts[index]:"?")+"'");}
}
