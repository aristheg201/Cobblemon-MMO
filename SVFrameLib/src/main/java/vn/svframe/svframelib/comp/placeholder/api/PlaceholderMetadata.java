package vn.svframe.svframelib.comp.placeholder.api;
public final class PlaceholderMetadata<T> {
    public final T playerData; public final int argIndex; public final String placeholderInput;
    public PlaceholderMetadata(T data,String input,int index){playerData=data;placeholderInput=input==null?"":input;argIndex=index;}
    public String params(){int i=placeholderInput.indexOf('_',Math.max(0,argIndex));return i<0?"":placeholderInput.substring(i+1);}
}
