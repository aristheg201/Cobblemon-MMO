package vn.svframe.skript.lang;

import java.util.*;
import java.util.regex.*;
import vn.svframe.skript.lang.Platform.*;
import static vn.svframe.skript.lang.Syntax.*;
import vn.svframe.skript.lang.Syntax.Error;

/** Compiles Skript expressions; unknown syntax is rejected at load time, never silently evaluated as text. */
public final class ExpressionCompiler {
    private final Platform platform;
    private final Set<String> functions;
    private final Map<String,String> options;
    private final Source source;
    private int nesting;
    public ExpressionCompiler(Platform platform,Set<String> functions,Map<String,String> options,Source source){this.platform=platform;this.functions=functions;this.options=options;this.source=source;}
    public Source source(){return source;}
    public String expand(String text){
        Matcher m=Pattern.compile("\\{@([^}]+)}").matcher(text);StringBuffer out=new StringBuffer();
        while(m.find()){String v=options.get(m.group(1).toLowerCase(Locale.ROOT));if(v==null)throw new Error(source,"Undefined option "+m.group(1));m.appendReplacement(out,Matcher.quoteReplacement(v));}
        m.appendTail(out);return out.toString();
    }
    public Expression compile(String input){
        if(++nesting>64){nesting--;throw new Error(source,"Expression nesting exceeds 64");}
        try{return parse(expand(input.strip()));}finally{nesting--;}
    }
    private Expression parse(String s){
        while(enclosed(s,'(',')'))s=s.substring(1,s.length()-1).strip();
        if(s.isEmpty())throw new Error(source,"Empty expression");
        for(String op:(s.toLowerCase(Locale.ROOT).startsWith("difference between ")||s.toLowerCase(Locale.ROOT).startsWith("distance between "))?List.<String>of():List.of(" or "," and ")){
            int p=find(s,op,false);if(p>=0){Expression a=compile(s.substring(0,p)),b=compile(s.substring(p+op.length()));
                return op.equals(" or ")?c->Values.truth(a.evaluate(c))||Values.truth(b.evaluate(c)):c->Values.truth(a.evaluate(c))&&Values.truth(b.evaluate(c));}
        }
        if(s.startsWith("not ")){Expression e=compile(s.substring(4));return c->!Values.truth(e.evaluate(c));}
        Matcher entityType=Pattern.compile("(?i)^(.+) is (not )?(?:an? )?(player|living entity|armor stand)$").matcher(s);
        if(entityType.matches()){Expression subject=compile(entityType.group(1));String type=entityType.group(3).toLowerCase(Locale.ROOT);boolean negate=entityType.group(2)!=null;return c->negate!=platform.isType(subject.evaluate(c),type);}
        List<String> pieces=s.toLowerCase(Locale.ROOT).startsWith("location at ")?List.of(s):split(s,",");if(pieces.size()>1){List<Expression> expr=pieces.stream().map(this::compile).toList();return c->{List<Object> out=new ArrayList<>();for(Expression e:expr)out.addAll(Values.list(e.evaluate(c)));return out;};}
        for(String suffix:List.of(" is not set"," is set"," is not a player"," is a player"," is not online"," is online"," is not op"," is op")){
            if(s.toLowerCase(Locale.ROOT).endsWith(suffix)){
                Expression e=compile(s.substring(0,s.length()-suffix.length()));boolean negate=suffix.contains(" not ");
                return c->{Object v=e.evaluate(c);boolean result=suffix.endsWith("set")?v!=null&&(!(v instanceof Collection<?> list)||!list.isEmpty()):suffix.endsWith("player")?platform.isPlayer(v):Values.truth(platform.property(suffix.endsWith("online")?"online":"op",v,c));return negate?!result:result;};
            }
        }
        for(String op:List.of(" does not have permission "," has permission "," does not contain "," contains "," does not start with "," starts with "," does not end with "," ends with "," is not "," is "," != "," >= "," <= "," > "," < "," = ")){
            int p=find(s,op,false);if(p<0)continue;Expression a=compile(s.substring(0,p)),b=compile(s.substring(p+op.length()));
            return c->{Object x=a.evaluate(c),y=b.evaluate(c);return switch(op){
                case " does not have permission "->!platform.hasPermission(x,c.text(y));case " has permission "->platform.hasPermission(x,c.text(y));
                case " does not contain "->!contains(x,y,c);case " contains "->contains(x,y,c);
                case " does not start with "->!c.text(x).toLowerCase(Locale.ROOT).startsWith(c.text(y).toLowerCase(Locale.ROOT));
                case " starts with "->c.text(x).toLowerCase(Locale.ROOT).startsWith(c.text(y).toLowerCase(Locale.ROOT));
                case " does not end with "->!c.text(x).toLowerCase(Locale.ROOT).endsWith(c.text(y).toLowerCase(Locale.ROOT));
                case " ends with "->c.text(x).toLowerCase(Locale.ROOT).endsWith(c.text(y).toLowerCase(Locale.ROOT));
                case " is not "," != "->!Values.equal(x,y);case " is "," = "->Values.equal(x,y);
                case " >= "->x!=null&&y!=null&&Values.compare(x,y)>=0;case " <= "->x!=null&&y!=null&&Values.compare(x,y)<=0;
                case " > "->x!=null&&y!=null&&Values.compare(x,y)>0;case " < "->x!=null&&y!=null&&Values.compare(x,y)<0;
                default->false;};};
        }
        // A possessive property binds before arithmetic, while parentheses remain explicit.
        for(String[] group:new String[][]{{" + "," - "},{" * "," / "," mod "},{" ^ "}}){
            int position=-1;String operator=null;for(String op:group){int p=find(s,op,true);if(p>position){position=p;operator=op;}}
            if(position>=0){Expression a=compile(s.substring(0,position)),b=compile(s.substring(position+operator.length()));String op=operator;
                return c->{Object x=a.evaluate(c),y=b.evaluate(c);if(x==null||y==null)return null;if(x instanceof Values.Date d&&y instanceof Values.Span t&&(op.equals(" + ")||op.equals(" - ")))return new Values.Date(Math.addExact(d.milliseconds(),(long)(op.equals(" + ")?t.milliseconds():-t.milliseconds())));double l=Values.number(x),r=Values.number(y);double v=switch(op){case " + "->l+r;case " - "->l-r;case " * "->l*r;case " / "->r==0?Double.NaN:l/r;case " mod "->l%r;default->Math.pow(l,r);};if(!Double.isFinite(v))return null;return x instanceof Values.Span?new Values.Span(v):v;};}
        }
        if(s.startsWith("\"")&&s.endsWith("\""))return template(s.substring(1,s.length()-1).replace("\"\"","\""));
        if(enclosed(s,'{','}'))return variable(s);
        String lower=s.toLowerCase(Locale.ROOT);
        if(lower.equals("true")||lower.equals("false")){boolean value=Boolean.parseBoolean(lower);return c->value;}
        if(lower.equals("none")||lower.equals("nothing"))return c->null;
        if(lower.matches("[-+]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)")){double value=Double.parseDouble(lower);return c->value;}
        Matcher duration=Pattern.compile("(?i)^(.+) (ticks?|seconds?|minutes?|hours?|days?)$").matcher(s);
        if(duration.matches()){
            Expression e=compile(duration.group(1));String unit=duration.group(2).toLowerCase(Locale.ROOT);double multiplier=unit.startsWith("tick")?50:unit.startsWith("second")?1000:unit.startsWith("minute")?60000:unit.startsWith("hour")?3600000:86400000;
            return c->{Object v=e.evaluate(c);return v==null?null:new Values.Span(Values.number(v)*multiplier);};
        }
        Matcher call=Pattern.compile("^([A-Za-z_][\\w]*)\\((.*)\\)$").matcher(s);
        if(call.matches()){
            String fn=call.group(1).toLowerCase(Locale.ROOT);List<Expression> args=call.group(2).isBlank()?List.of():split(call.group(2),",").stream().map(this::compile).toList();
            if(Set.of("round","floor","ceil","abs","sin","cos","sqrt","min","max").contains(fn))return c->{List<Object> a=args.stream().map(e->e.evaluate(c)).toList();if(a.isEmpty()||a.stream().anyMatch(Objects::isNull))return null;double v=Values.number(a.getFirst());return switch(fn){case "round"->(double)Math.round(v);case "floor"->Math.floor(v);case "ceil"->Math.ceil(v);case "abs"->Math.abs(v);case "sin"->Math.sin(Math.toRadians(v));case "cos"->Math.cos(Math.toRadians(v));case "sqrt"->Math.sqrt(v);case "min"->a.stream().mapToDouble(Values::number).min().orElse(v);default->a.stream().mapToDouble(Values::number).max().orElse(v);};};
            if(!functions.contains(fn)){Expression nativeCall=platform.compileExpression(s,this);if(nativeCall!=null)return nativeCall;throw new Error(source,"Unknown function "+fn);}
            return c->c.engine.call(fn,args.stream().map(e->e.evaluate(c)).toList(),c);
        }
        int split=find(s," split at ",false);if(split>=0){Expression e=compile(s.substring(0,split)),sep=compile(s.substring(split+10));return c->Arrays.asList(c.text(e.evaluate(c)).split(Pattern.quote(c.text(sep.evaluate(c))),-1));}
        int parsed=find(s," parsed as ",false);if(parsed>=0){Expression e=compile(s.substring(0,parsed));String type=s.substring(parsed+11).toLowerCase(Locale.ROOT);if(!Set.of("number","integer","boolean","text","player","offline player").contains(type))throw new Error(source,"Unsupported parsed type "+type);return c->cast(e.evaluate(c),type,c);}
        if(lower.startsWith("difference between ")){
            int p=find(s.substring(19)," and ",false);if(p>=0){p+=19;Expression a=compile(s.substring(19,p)),b=compile(s.substring(p+5));return c->{Object x=a.evaluate(c),y=b.evaluate(c);return x instanceof Values.Date d&&y instanceof Values.Date t?new Values.Span(Math.abs(d.milliseconds()-t.milliseconds())):null;};}
        }
        for(String prefix:List.of("lowercase ","uppercase ","uncolored ","colored ","size of ","length of ","rounded down ","rounded up ")){
            if(lower.startsWith(prefix)){Expression e=compile(s.substring(prefix.length()));return c->{Object v=e.evaluate(c);return switch(prefix){case "lowercase "->c.text(v).toLowerCase(Locale.ROOT);case "uppercase "->c.text(v).toUpperCase(Locale.ROOT);case "uncolored "->c.text(v).replaceAll("(?i)[&§][0-9a-fk-orx]","");case "colored "->c.text(v).replace('&','§');case "size of "->(double)Values.list(v).size();case "length of "->(double)c.text(v).length();case "rounded down "->v==null?null:Math.floor(Values.number(v));default->v==null?null:Math.ceil(Values.number(v));};};}
        }
        int format=find(s," formatted as ",false);
        if(format>0){Expression value=compile(s.substring(0,format)),pattern=compile(s.substring(format+14));return c->{Object date=value.evaluate(c);if(!(date instanceof Values.Date d))return null;java.text.SimpleDateFormat formatter=new java.text.SimpleDateFormat(c.text(pattern.evaluate(c)),Locale.ROOT);return formatter.format(new java.util.Date(d.milliseconds()));};}
        if(lower.equals("now"))return c->new Values.Date(c.engine.now());
        if(lower.startsWith("loop-"))return c->c.loops.get(lower);
        if(lower.matches("arg(?:ument)?-?\\d+")){String n=lower.replaceAll("\\D","");return c->c.event("arg-"+n);}
        if(lower.equals("death message"))return new Reference(){public Object evaluate(Context c){return c.eventState.getOrDefault("death message",c.event("death message"));}public void change(Context c,Object value,String mode){if(c.delayed)throw new IllegalStateException("Cannot change a death event after wait");c.eventState.put("death message",value==null?null:c.text(value));}};
        if(lower.equals("damage"))return new Reference(){public Object evaluate(Context c){return c.eventState.getOrDefault("damage",c.event("damage"));}public void change(Context c,Object value,String mode){if(c.delayed||!c.event.containsKey("damage"))throw new IllegalStateException("Damage can only change during the damage event");double old=Values.number(evaluate(c)),amount=Values.number(value);double next=switch(mode){case "set"->amount;case "add"->old+amount;case "remove"->old-amount;default->throw new IllegalArgumentException(mode);};if(!Double.isFinite(next)||next<0)throw new IllegalArgumentException("Invalid event damage");c.eventState.put("damage",next);}};
        if(Set.of("player","sender","command sender","victim","attacker","entity","event-entity","event-player","event-item","event-inventory","event-block","block","command","full command","inventory","clicked inventory","clicked slot","damage","death message").contains(lower))return c->c.event(lower.equals("command sender")?"sender":lower);
        if(lower.equals("all players")||lower.equals("players"))return c->platform.property("all players",null,c);
        Expression nativeExpr=platform.compileExpression(s,this);if(nativeExpr!=null)return nativeExpr;
        // Natural properties are explicit and platform-validated during loading.
        int of=find(s," of ",false);
        if(of>0&&platform.supportsProperty(s.substring(0,of).toLowerCase(Locale.ROOT),false)){
            String property=s.substring(0,of).toLowerCase(Locale.ROOT);Expression subject=compile(s.substring(of+4));return reference(property,subject);
        }
        int possessive=find(s,"'s ",true);
        if(possessive>0){String property=s.substring(possessive+3).toLowerCase(Locale.ROOT);if(platform.supportsProperty(property,false))return reference(property,compile(s.substring(0,possessive)));}
        Object literal=platform.literal(lower);if(literal!=null)return c->Values.copy(literal);
        throw new Error(source,"Unsupported expression: "+s);
    }
    private boolean contains(Object x,Object y,Context c){return x instanceof Collection<?> values?values.stream().anyMatch(v->Values.equal(v,y)):x!=null&&y!=null&&c.text(x).toLowerCase(Locale.ROOT).contains(c.text(y).toLowerCase(Locale.ROOT));}
    private Object cast(Object v,String type,Context c){if(v==null)return null;try{return switch(type){case "number"->Double.parseDouble(c.text(v));case "integer"->(double)Long.parseLong(c.text(v));case "text"->c.text(v);case "boolean"->Set.of("true","false").contains(c.text(v).toLowerCase(Locale.ROOT))?Boolean.parseBoolean(c.text(v)):null;default->platform.property(type.equals("offline player")?"offline player lookup":"player lookup",v,c);};}catch(NumberFormatException failure){return null;}}
    public Reference variable(String s){
        if(!enclosed(s,'{','}'))throw new Error(source,"Expected variable: "+s);
        Expression name=template(s.substring(1,s.length()-1));
        return new Reference(){public Object evaluate(Context c){return c.engine.variables().get(c.text(name.evaluate(c)),c.locals);}public void change(Context c,Object v,String mode){String key=c.text(name.evaluate(c));if(!key.startsWith("_")){Map<String,Object> before=c.engine.variables().capture(key);c.rollbackWith(()->c.engine.variables().restore(key,before));}switch(mode){case "set"->c.engine.variables().set(key,v,c.locals);case "delete"->c.engine.variables().set(key,null,c.locals);case "add"->c.engine.variables().add(key,v,c.locals);case "remove"->c.engine.variables().remove(key,v,c.locals);default->throw new IllegalStateException(mode);}}};
    }
    public Reference writable(String s){Expression e=compile(s);if(!(e instanceof Reference r)||!r.writable())throw new Error(source,"Expression cannot be changed: "+s);return r;}
    private Reference reference(String property,Expression subject){
        return new Reference(){public boolean writable(){return platform.supportsProperty(property,true);}public Object evaluate(Context c){return platform.property(property,subject.evaluate(c),c);}public void change(Context c,Object v,String mode){if(!platform.supportsProperty(property,true))throw new IllegalArgumentException("Read-only property "+property);Object object=subject.evaluate(c);if(object instanceof Values.Location l&&subject instanceof Reference r&&Set.of("x-coordinate","y-coordinate","z-coordinate","yaw","pitch").contains(property)){double old=Values.number(evaluate(c)),amount=Values.number(v),next=switch(mode){case "set"->amount;case "add"->old+amount;case "remove"->old-amount;default->throw new IllegalArgumentException(mode);};if(!Double.isFinite(next))throw new IllegalArgumentException("Invalid location coordinate");r.change(c,new Values.Location(l.world(),property.equals("x-coordinate")?next:l.x(),property.equals("y-coordinate")?next:l.y(),property.equals("z-coordinate")?next:l.z(),property.equals("yaw")?(float)next:l.yaw(),property.equals("pitch")?(float)next:l.pitch()),"set");return;}platform.changeProperty(property,object,v,mode,c);}};
    }
    public Expression template(String s){
        List<Object> pieces=new ArrayList<>();StringBuilder literal=new StringBuilder();
        for(int i=0;i<s.length();){
            if(s.charAt(i)!='%'){literal.append(s.charAt(i++));continue;}
            if(i+1<s.length()&&s.charAt(i+1)=='%'){literal.append('%');i+=2;continue;}
            int start=++i,braces=0,parens=0;boolean quote=false;
            while(i<s.length()){
                char ch=s.charAt(i);if(ch=='"')quote=!quote;
                if(!quote){if(ch=='{')braces++;else if(ch=='}')braces--;else if(ch=='(')parens++;else if(ch==')')parens--;if(ch=='%'&&braces==0&&parens==0)break;}i++;
            }
            if(i==s.length())throw new Error(source,"Unclosed %expression% in text");
            pieces.add(literal.toString());literal.setLength(0);pieces.add(compile(s.substring(start,i)));i++;
        }
        pieces.add(literal.toString());return c->{StringBuilder out=new StringBuilder();for(Object p:pieces)out.append(p instanceof Expression e?c.text(e.evaluate(c)):p);return out.toString();};
    }
}
