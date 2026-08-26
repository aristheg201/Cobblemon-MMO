package io.lumine.mythic.lib.api.util;
public class Ref<E> {
    private E value;
    public Ref(E value){this.value=value;} public Ref(){}
    public E get(){return value;} public E getValue(){return value;} public E getValue(E fallback){return value==null?fallback:value;}
    public void set(E value){this.value=value;} public void setValue(E value){this.value=value;}
    public boolean isPresent(){return value!=null;}
    public static <S> void setValue(Ref<S> ref,S value){if(ref!=null)ref.setValue(value);}
}
