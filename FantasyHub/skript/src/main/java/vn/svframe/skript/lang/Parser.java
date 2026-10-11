package vn.svframe.skript.lang;

import java.util.*;
import java.util.regex.*;
import vn.svframe.skript.lang.Platform.*;
import vn.svframe.skript.lang.Program.*;
import static vn.svframe.skript.lang.Syntax.*;
import vn.svframe.skript.lang.Syntax.Error;

/** Indentation-aware source compiler for native Skript ASTs. A file activates only if every node compiles. */
public final class Parser {
    private record Node(Source source,int indent,List<Node> children){}
    private final Platform platform;
    private Set<String> functions=Set.of();
    public Parser(Platform platform){this.platform=platform;}
    public Program compile(Map<String,String> sources){
        Set<String> names=new HashSet<>();Map<String,List<Node>> trees=new LinkedHashMap<>();Map<String,List<Diagnostic>> diagnostics=new LinkedHashMap<>();
        sources.forEach((name,text)->{
            List<Diagnostic> errors=new ArrayList<>();List<Node> tree=tree(name,text,errors);trees.put(name,tree);diagnostics.put(name,errors);
            for(Node n:tree){Matcher m=Pattern.compile("(?i)^(?:local )?function ([\\w]+)\\(.*").matcher(n.source.text());if(m.matches())names.add(m.group(1).toLowerCase(Locale.ROOT));}
        });functions=Set.copyOf(names);
        Map<String,File> files=new LinkedHashMap<>();
        trees.forEach((name,nodes)->files.put(name,file(name,nodes,diagnostics.get(name))));
        // Reject callers of functions whose entire source file failed, including indirect dependencies.
        boolean changed;
        do {
            changed=false;Set<String> available=new HashSet<>();files.values().stream().filter(File::valid).forEach(f->f.functions().forEach(fn->available.add(fn.name().toLowerCase(Locale.ROOT))));
            for(var entry:new ArrayList<>(files.entrySet())){
                File f=entry.getValue();if(!f.valid())continue;List<Diagnostic> errors=new ArrayList<>();
                for(Node n:flatten(trees.get(entry.getKey()))){Matcher m=Pattern.compile("(?<![\\w.])([A-Za-z_][\\w]*)\\(").matcher(n.source.text());while(m.find()){String fn=m.group(1).toLowerCase(Locale.ROOT);if(functions.contains(fn)&&!available.contains(fn))errors.add(new Diagnostic(n.source,"Function unavailable because its source did not compile: "+fn));}}
                if(!errors.isEmpty()){files.put(entry.getKey(),new File(f.name(),f.functions(),f.commands(),f.triggers(),errors,f.defaults(),f.options()));changed=true;}
            }
        }while(changed);
        return new Program(files);
    }
    private static List<Node> flatten(List<Node> tree){List<Node> out=new ArrayList<>();for(Node n:tree){out.add(n);out.addAll(flatten(n.children));}return out;}
    private List<Node> tree(String file,String text,List<Diagnostic> errors){
        List<Node> roots=new ArrayList<>();List<Node> parents=new ArrayList<>();int line=0;
        for(String raw:text.split("\\R",-1)){
            line++;String clean=uncomment(raw);if(clean.isBlank())continue;int indent=0,index=0;
            while(index<clean.length()&&Character.isWhitespace(clean.charAt(index))){indent+=clean.charAt(index)=='\t'?4:1;index++;}
            Source source=new Source(file,line,clean.substring(index));Node n=new Node(source,indent,new ArrayList<>());
            while(!parents.isEmpty()&&parents.getLast().indent>=indent)parents.removeLast();
            if(indent==0)roots.add(n);else if(parents.isEmpty())errors.add(new Diagnostic(source,"Unexpected indentation"));else {
                Node parent=parents.getLast();if(!parent.source.text().endsWith(":"))errors.add(new Diagnostic(source,"Indented statement below a non-section"));parent.children.add(n);
            }
            parents.add(n);
        }
        return roots;
    }
    private File file(String name,List<Node> roots,List<Diagnostic> errors){
        Map<String,String> options=new LinkedHashMap<>();List<Function> fns=new ArrayList<>();List<Command> commands=new ArrayList<>();List<Trigger> triggers=new ArrayList<>();Map<String,Expression> defaults=new LinkedHashMap<>();
        for(Node n:roots)if(n.source.text().equalsIgnoreCase("options:"))for(Node child:n.children){int p=child.source.text().indexOf(':');if(p<1)errors.add(new Diagnostic(child.source,"Option requires name: value"));else options.put(child.source.text().substring(0,p).strip().toLowerCase(Locale.ROOT),child.source.text().substring(p+1).strip());}
        for(Node n:roots){
            String s=n.source.text();ExpressionCompiler ec=new ExpressionCompiler(platform,functions,options,n.source);
            try{
                if(s.equalsIgnoreCase("options:"))continue;
                if(s.equalsIgnoreCase("import:")){for(Node child:n.children)if(!platform.supportsImport(child.source.text()))errors.add(new Diagnostic(child.source,"No native adapter for import "+child.source.text()));continue;}
                if(s.equalsIgnoreCase("variables:")){for(Node child:n.children){int p=find(child.source.text()," = ",false);if(p<0)throw new Error(child.source,"Variable default requires =");String key=child.source.text().substring(0,p).strip();if(!enclosed(key,'{','}'))throw new Error(child.source,"Invalid variable default");defaults.put(key.substring(1,key.length()-1),new ExpressionCompiler(platform,functions,options,child.source).compile(child.source.text().substring(p+3)));}continue;}
                Matcher fn=Pattern.compile("(?i)^(?:local )?function ([\\w]+)\\((.*)\\)(?: :: ([\\w ]+))?:$").matcher(s);
                if(fn.matches()){
                    List<Parameter> args=new ArrayList<>();if(!fn.group(2).isBlank())for(String arg:split(fn.group(2),",")){int p=arg.indexOf(':');if(p<1)throw new Error(n.source,"Function parameter requires name: type");args.add(new Parameter(arg.substring(0,p).strip(),arg.substring(p+1).strip().toLowerCase(Locale.ROOT)));}
                    fns.add(new Function(fn.group(1),args,Objects.toString(fn.group(3),"void"),block(n.children,options,errors),n.source));continue;
                }
                Matcher cmd=Pattern.compile("(?i)^command /([^ \\[<:]+)(.*):$").matcher(s);
                if(cmd.matches()){
                    List<Parameter> args=new ArrayList<>();int required=0;Matcher a=Pattern.compile("(\\[)?<([^>]+)>(]?)").matcher(cmd.group(2));while(a.find()){args.add(new Parameter("arg-"+(args.size()+1),a.group(2)));if(a.group(1)==null)required++;}
                    List<String> aliases=new ArrayList<>();String permission="",denied="",executable="players and console";List<Statement> body=null;
                    for(Node child:n.children){String t=child.source.text();if(t.equalsIgnoreCase("trigger:")){body=block(child.children,options,errors);continue;}int p=t.indexOf(':');if(p<0)throw new Error(child.source,"Invalid command declaration");String k=normalize(t.substring(0,p)),v=ec.expand(t.substring(p+1).strip());switch(k){
                        case "aliases"->{for(String alias:split(v,","))aliases.add(alias.replaceFirst("^/",""));}
                        case "permission"->permission=v;case "permission message"->denied=v;case "executable by"->executable=v;
                        case "description","usage"->{} default->throw new Error(child.source,"Unsupported command property: "+k);
                    }}if(body==null)throw new Error(n.source,"Command is missing trigger:");commands.add(new Command(cmd.group(1),args,required,aliases,permission,denied,executable,body,n.source));continue;
                }
                if(s.toLowerCase(Locale.ROOT).startsWith("every ")&&s.endsWith(":")){
                    Object value=ec.compile(s.substring(6,s.length()-1)).evaluate(new Context(new Engine(platform),Map.of(),new HashMap<>(),100));
                    if(!(value instanceof Values.Span span)||span.milliseconds()<=0)throw new Error(n.source,"Periodic interval must be a positive fixed duration");
                    triggers.add(new Trigger("periodic",span.ticks(),block(n.children,options,errors),n.source));continue;
                }
                if(s.toLowerCase(Locale.ROOT).startsWith("on ")&&s.endsWith(":")){
                    String event=normalize(s.substring(3,s.length()-1));if(event.equals("script load"))event="load";if(event.equals("script unload"))event="unload";
                    if(!platform.supportsEvent(event))throw new Error(n.source,"Unsupported event: "+event);triggers.add(new Trigger(event,0,block(n.children,options,errors),n.source));continue;
                }
                throw new Error(n.source,"Unsupported top-level declaration: "+s);
            }catch(Error e){errors.add(new Diagnostic(e.source,e.getMessage()));}catch(IllegalArgumentException e){errors.add(new Diagnostic(n.source,e.getMessage()));}
        }
        return new File(name,List.copyOf(fns),List.copyOf(commands),List.copyOf(triggers),List.copyOf(errors),Map.copyOf(defaults),Map.copyOf(options));
    }
    private List<Statement> block(List<Node> nodes,Map<String,String> options,List<Diagnostic> errors){
        List<Statement> out=new ArrayList<>();
        for(int i=0;i<nodes.size();i++){
            Node n=nodes.get(i);ExpressionCompiler ec=new ExpressionCompiler(platform,functions,options,n.source);String s=n.source.text(),lower=normalize(s);
            try{
                if(lower.startsWith("if ")&&s.endsWith(":")){
                    List<Arm> arms=new ArrayList<>();arms.add(new Arm(ec.compile(s.substring(3,s.length()-1)),block(n.children,options,errors)));
                    while(i+1<nodes.size()&&normalize(nodes.get(i+1).source.text()).startsWith("else")){
                        Node branch=nodes.get(++i);String t=branch.source.text();Expression condition;
                        if(t.equalsIgnoreCase("else:"))condition=null;else if(t.toLowerCase(Locale.ROOT).startsWith("else if ")&&t.endsWith(":"))condition=new ExpressionCompiler(platform,functions,options,branch.source).compile(t.substring(8,t.length()-1));else throw new Error(branch.source,"Invalid else section");
                        arms.add(new Arm(condition,block(branch.children,options,errors)));if(condition==null)break;
                    }out.add(new Branch(arms,n.source));continue;
                }
                if(lower.startsWith("loop ")&&s.endsWith(":")){
                    String value=s.substring(5,s.length()-1);Expression count=null,values;String kind="value";
                    if(value.toLowerCase(Locale.ROOT).endsWith(" times")){count=ec.compile(value.substring(0,value.length()-6));Expression finalCount=count;values=c->{int size=(int)Values.number(finalCount.evaluate(c));if(size<0||size>10000)throw new IllegalArgumentException("Loop count exceeds 10000");List<Object> numbers=new ArrayList<>();for(int x=1;x<=size;x++)numbers.add((double)x);return numbers;};kind="number";}
                    else {values=ec.compile(value);if(value.equalsIgnoreCase("all players"))kind="player";if(enclosed(value,'{','}'))count=ec.template(value.substring(1,value.length()-1));}
                    out.add(new Loop(values,count,kind,block(n.children,options,errors),n.source));continue;
                }
                if(lower.startsWith("while ")&&s.endsWith(":")){out.add(new While(ec.compile(s.substring(6,s.length()-1)),block(n.children,options,errors),n.source));continue;}
                if(lower.startsWith("wait ")){out.add(new Wait(ec.compile(s.substring(5)),n.source));continue;}
                if(lower.equals("stop")||lower.equals("stop trigger")){out.add(new Stop(n.source));continue;}
                if(lower.equals("exit loop")||lower.equals("stop loop")){out.add(new Exit(n.source));continue;}
                if(lower.equals("continue")||lower.equals("continue loop")){out.add(new Continue(n.source));continue;}
                if(lower.equals("return")){out.add(new Return(c->null,n.source));continue;}
                if(lower.startsWith("return ")){out.add(new Return(ec.compile(s.substring(7)),n.source));continue;}
                if(!n.children.isEmpty())throw new Error(n.source,"Unsupported section: "+s);
                out.add(new Effect(effect(s,ec),n.source));
            }catch(Error failure){errors.add(new Diagnostic(failure.source,failure.getMessage()));}
        }
        return List.copyOf(out);
    }
    private Action effect(String raw,ExpressionCompiler ec){
        String s=ec.expand(raw),lower=normalize(s);
        if(lower.startsWith("subtract "))return effect("remove "+s.substring(9),ec);
        if(lower.startsWith("send action bar ")){Action action=platform.compileEffect(s,ec);if(action==null)throw new Error(ec.source(),"Action bar integration unavailable");return action;}
        for(String mode:List.of("set","add","remove")){
            String prefix=mode+" ";if(!lower.startsWith(prefix))continue;String token=mode.equals("remove")?" from ":" to ";int p=find(s,token,mode.equals("set")?false:true);
            if(p>0){String target=mode.equals("set")?s.substring(prefix.length(),p):s.substring(p+token.length());String value=mode.equals("set")?s.substring(p+token.length()):s.substring(prefix.length(),p);
                // Inventory-specific removal is delegated to the native platform.
                try{Reference ref=ec.writable(target);Expression v=ec.compile(value);return c->ref.change(c,v.evaluate(c),mode);}catch(Error failure){if(mode.equals("set"))throw failure;}
            }
        }
        if(lower.startsWith("delete ")||lower.startsWith("clear ")){Reference ref=ec.writable(s.substring(s.indexOf(' ')+1));return c->ref.change(c,null,"delete");}
        Matcher replace=Pattern.compile("(?i)^replace (all|first) (.+) in (.+) with (.+)$").matcher(s);
        if(replace.matches()){Expression from=ec.compile(replace.group(2)),to=ec.compile(replace.group(4));Reference ref=ec.writable(replace.group(3));boolean first=replace.group(1).equalsIgnoreCase("first");return c->{String old=c.text(ref.evaluate(c)),a=c.text(from.evaluate(c)),b=c.text(to.evaluate(c));ref.change(c,first?old.replaceFirst(Pattern.quote(a),Matcher.quoteReplacement(b)):old.replace(a,b),"set");};}
        Matcher alternateReplace=Pattern.compile("(?i)^replace (all|first) (.+) with (.+) in (.+)$").matcher(s);
        if(alternateReplace.matches()){Expression from=ec.compile(alternateReplace.group(2)),to=ec.compile(alternateReplace.group(3));Reference ref=ec.writable(alternateReplace.group(4));boolean first=alternateReplace.group(1).equalsIgnoreCase("first");return c->{String old=c.text(ref.evaluate(c)),a=c.text(from.evaluate(c)),b=c.text(to.evaluate(c));ref.change(c,first?old.replaceFirst(Pattern.quote(a),Matcher.quoteReplacement(b)):old.replace(a,b),"set");};}
        if(lower.equals("keep inventory")||lower.equals("cancel drops"))return c->{if(c.delayed||!c.event.containsKey("victim"))throw new IllegalStateException("Death effects require an active death event");c.eventState.put(lower,true);};
        if(lower.equals("cancel event"))return Context::cancel;
        if(lower.equals("uncancel event"))return c->{if(c.delayed)throw new IllegalStateException("Cannot uncancel after wait");c.eventState.put("cancelled",false);};
        if(lower.startsWith("send ")){
            int p=find(s," to ",true);Expression message=ec.compile(s.substring(5,p<0?s.length():p)),recipient=p<0?c->c.event("player"):ec.compile(s.substring(p+4));return c->platform.send(recipient.evaluate(c),c.text(message.evaluate(c)));
        }
        if(lower.startsWith("broadcast ")){Expression message=ec.compile(s.substring(10));return c->platform.broadcast(c.text(message.evaluate(c)));}
        for(String prefix:List.of("execute console command ","make console execute command ","execute player command ","make player execute command "))if(lower.startsWith(prefix)){Expression command=ec.compile(s.substring(prefix.length()));boolean console=prefix.contains("console");return c->platform.command(console?null:c.event("player"),c.text(command.evaluate(c)),c);}
        Matcher execute=Pattern.compile("(?i)^make (.+) execute command (.+)$").matcher(s);
        if(execute.matches()){Expression sender=ec.compile(execute.group(1)),command=ec.compile(execute.group(2));return c->platform.command(sender.evaluate(c),c.text(command.evaluate(c)),c);}
        if(lower.startsWith("give ")){int p=find(s," to ",true);if(p>0){Expression item=ec.compile(s.substring(5,p)),player=ec.compile(s.substring(p+4));return c->platform.give(player.evaluate(c),item.evaluate(c));}}
        if(lower.startsWith("teleport ")){int p=find(s," to ",true);if(p>0){Expression entity=ec.compile(s.substring(9,p)),location=ec.compile(s.substring(p+4));return c->platform.teleport(entity.evaluate(c),location.evaluate(c),c);}}
        Matcher potion=Pattern.compile("(?i)^apply ([a-z_ ]+) (\\d+) to (.+) for (.+)$").matcher(s);
        if(potion.matches()){String type=potion.group(1);int level=Integer.parseInt(potion.group(2));Expression entity=ec.compile(potion.group(3)),duration=ec.compile(potion.group(4));return c->platform.potion(entity.evaluate(c),type,level,(Values.Span)duration.evaluate(c),c);}
        Action nativeAction=platform.compileEffect(s,ec);if(nativeAction!=null)return nativeAction;
        if(lower.matches("[a-z_][\\w]*\\(.*\\)")){Expression call=ec.compile(s);return c->call.evaluate(c);}
        throw new Error(ec.source(),"Unsupported effect: "+s);
    }
}
