package vn.svframe.skript.lang;

import java.util.*;
import vn.svframe.skript.lang.Platform.*;

public final class Program {
    public record Diagnostic(Syntax.Source source,String message){}
    public record Parameter(String name,String type){}
    public record Function(String name,List<Parameter> parameters,String returnType,List<Statement> body,Syntax.Source source){}
    public record Command(String name,List<Parameter> arguments,int required,List<String> aliases,String permission,String denied,String executableBy,List<Statement> body,Syntax.Source source){}
    public record Trigger(String event,long period,List<Statement> body,Syntax.Source source){}
    public record File(String name,List<Function> functions,List<Command> commands,List<Trigger> triggers,List<Diagnostic> diagnostics,Map<String,Expression> defaults,Map<String,String> options){
        public boolean valid(){return diagnostics.isEmpty();}
    }
    public sealed interface Statement permits Effect,Branch,Loop,While,Wait,Return,Stop,Exit,Continue {Syntax.Source source();}
    public record Effect(Action action,Syntax.Source source) implements Statement{}
    public record Branch(List<Arm> arms,Syntax.Source source) implements Statement{}
    public record Arm(Expression condition,List<Statement> body){}
    public record Loop(Expression values,Expression variable,String kind,List<Statement> body,Syntax.Source source) implements Statement{}
    public record While(Expression condition,List<Statement> body,Syntax.Source source) implements Statement{}
    public record Wait(Expression duration,Syntax.Source source) implements Statement{}
    public record Return(Expression value,Syntax.Source source) implements Statement{}
    public record Stop(Syntax.Source source) implements Statement{}
    public record Exit(Syntax.Source source) implements Statement{}
    public record Continue(Syntax.Source source) implements Statement{}
    public final Map<String,File> files;
    public final Map<String,Function> functions;
    public Program(Map<String,File> files){
        this.files=Collections.unmodifiableMap(new LinkedHashMap<>(files));
        Map<String,Function> functions=new LinkedHashMap<>();
        files.values().stream().filter(File::valid).forEach(file->file.functions.forEach(fn->functions.put(fn.name.toLowerCase(Locale.ROOT),fn)));
        this.functions=Collections.unmodifiableMap(functions);
    }
}
