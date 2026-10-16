package vn.svframe.skript.lang;

import java.util.*;
import vn.svframe.skript.lang.Platform.*;

public final class Program {
    public record Diagnostic(Syntax.Source source,String message){}
    public record Parameter(String name,String type){}
    public record Function(String name,List<Parameter> parameters,String returnType,List<Statement> body,Syntax.Source source,boolean local){}
    public record Command(String name,List<Parameter> arguments,int required,List<String> aliases,String permission,String denied,String executableBy,List<Statement> body,Syntax.Source source){}
    public record Trigger(String event,long period,List<Statement> body,Syntax.Source source,int atTime){
        public Trigger(String event,long period,List<Statement> body,Syntax.Source source){this(event,period,body,source,-1);}
    }
    public record File(String name,List<Function> functions,List<Command> commands,List<Trigger> triggers,List<Diagnostic> diagnostics,Map<String,Expression> defaults,Map<String,String> options){
        public boolean valid(){return diagnostics.isEmpty();}
    }
    public sealed interface Statement permits Effect,Condition,Branch,Loop,While,Wait,Return,Stop,Exit,Continue {Syntax.Source source();}
    public record Effect(Action action,Syntax.Source source) implements Statement{}
    public record Condition(Expression condition,Syntax.Source source) implements Statement{}
    public record Branch(List<Arm> arms,Syntax.Source source) implements Statement{}
    public record Arm(Expression condition,List<Statement> body){}
    public record Loop(Expression values,Expression variable,String kind,List<Statement> body,Syntax.Source source) implements Statement{}
    public record While(Expression condition,List<Statement> body,Syntax.Source source,boolean doFirst) implements Statement{
        public While(Expression condition,List<Statement> body,Syntax.Source source){this(condition,body,source,false);}
    }
    public record Wait(Expression duration,Syntax.Source source) implements Statement{}
    public record Return(Expression value,Syntax.Source source) implements Statement{}
    public record Stop(Syntax.Source source) implements Statement{}
    public record Exit(Syntax.Source source) implements Statement{}
    public record Continue(Syntax.Source source) implements Statement{}
    public final Map<String,File> files;
    public final Map<String,Function> functions;
    private final Map<String,Map<String,Function>> localFunctions;
    public Program(Map<String,File> files){
        this.files=Collections.unmodifiableMap(new LinkedHashMap<>(files));
        Map<String,Function> functions=new LinkedHashMap<>();
        Map<String,Map<String,Function>> local=new LinkedHashMap<>();
        files.values().stream().filter(File::valid).forEach(file->file.functions.forEach(fn->{
            if(fn.local())local.computeIfAbsent(file.name(),ignored->new LinkedHashMap<>()).put(fn.name().toLowerCase(Locale.ROOT),fn);
            else functions.put(fn.name().toLowerCase(Locale.ROOT),fn);
        }));
        this.functions=Collections.unmodifiableMap(functions);
        local.replaceAll((file,entries)->Map.copyOf(entries));localFunctions=Map.copyOf(local);
    }
    public Function function(String name,String sourceFile){
        String key=name.toLowerCase(Locale.ROOT);
        Function scoped=localFunctions.getOrDefault(sourceFile,Map.of()).get(key);
        return scoped==null?functions.get(key):scoped;
    }
}
