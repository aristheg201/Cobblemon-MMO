import java.nio.file.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import static org.objectweb.asm.Opcodes.*;

/** Retains the original engine. Reward claims must still fail closed if storage is unavailable. */
public final class PatchClasses {
    public static void main(String[] args) throws Exception {
        String kind = args[0]; Path input=Path.of(args[1]), output=Path.of(args[2]);
        ClassNode node = new ClassNode(); new ClassReader(Files.readAllBytes(input)).accept(node,0);
        if(kind.equals("currency")) {
            MethodNode constructor=node.methods.stream().filter(m->m.name.equals("<init>")&&m.desc.equals("(Ljava/lang/String;)V")).findFirst().orElseThrow();
            InsnList prefix=new InsnList();prefix.add(new VarInsnNode(ALOAD,1));
            prefix.add(new MethodInsnNode(INVOKESTATIC,"vn/svframe/compat/GuildCurrencyIds","resolve","(Ljava/lang/String;)Ljava/lang/String;",false));
            prefix.add(new VarInsnNode(ASTORE,1));constructor.instructions.insert(prefix);
            ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);Files.write(output,writer.toByteArray());return;
        }
        String name = kind.equals("quest") ? "save" : "matches";
        String desc = kind.equals("quest") ? "(Ljava/util/UUID;)V" : "(Lcom/google/gson/JsonObject;Ljava/util/Map;)Z";
        MethodNode target = node.methods.stream().filter(m->m.name.equals(name)&&m.desc.equals(desc)).findFirst().orElseThrow();
        MethodNode replacement = new MethodNode(target.access,target.name,target.desc,target.signature,null);
        MethodVisitor m = replacement; m.visitCode();
        if (kind.equals("quest")) {
            String owner="com/cobblemon/mdks/cobblepass/patch/QuestEngine", state=owner+"$State";
            Label done = new Label();
            m.visitFieldInsn(GETSTATIC,owner,"STATES","Ljava/util/Map;");m.visitVarInsn(ALOAD,0);
            m.visitMethodInsn(INVOKEINTERFACE,"java/util/Map","get","(Ljava/lang/Object;)Ljava/lang/Object;",true);
            m.visitTypeInsn(CHECKCAST,state);m.visitVarInsn(ASTORE,1);
            m.visitVarInsn(ALOAD,1);m.visitJumpInsn(IFNULL,done);
            m.visitVarInsn(ALOAD,1);m.visitFieldInsn(GETFIELD,state,"dirty","Z");m.visitJumpInsn(IFEQ,done);
            m.visitFieldInsn(GETSTATIC,owner,"GSON","Lcom/google/gson/Gson;");
            m.visitFieldInsn(GETSTATIC,owner,"ROOT","Ljava/nio/file/Path;");m.visitVarInsn(ALOAD,0);m.visitVarInsn(ALOAD,1);
            m.visitMethodInsn(INVOKESTATIC,"vn/svframe/compat/SafeQuestStore","save","(Lcom/google/gson/Gson;Ljava/nio/file/Path;Ljava/util/UUID;Ljava/lang/Object;)Z",false);
            m.visitJumpInsn(IFEQ,done);m.visitVarInsn(ALOAD,1);m.visitInsn(ICONST_0);m.visitFieldInsn(PUTFIELD,state,"dirty","Z");
            m.visitLabel(done);m.visitFrame(F_APPEND,1,new Object[]{state},0,null);m.visitInsn(RETURN);
        } else {
            m.visitVarInsn(ALOAD,0);m.visitVarInsn(ALOAD,1);
            m.visitMethodInsn(INVOKESTATIC,"vn/svframe/compat/GuildMiningFilters","matches",desc,false);m.visitInsn(IRETURN);
        }
        m.visitMaxs(0,0);m.visitEnd();node.methods.set(node.methods.indexOf(target),replacement);
        if (kind.equals("quest")) {
            String owner=node.name, state=owner+"$State";
            int claims=0;
            for (MethodNode method:node.methods) if(method.name.equals("claim"))
                for(AbstractInsnNode insn:method.instructions)
                    if(insn instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals("save")) {
                        call.name="saveRequired";claims++;
                    }
            if(claims!=2)throw new IllegalStateException("Unexpected quest reward transaction layout");
            MethodNode strict=new MethodNode(ACC_PRIVATE|ACC_STATIC,"saveRequired","(Ljava/util/UUID;)V",null,null);
            Label done=new Label();strict.visitCode();strict.visitVarInsn(ALOAD,0);
            strict.visitMethodInsn(INVOKESTATIC,owner,"save","(Ljava/util/UUID;)V",false);
            strict.visitFieldInsn(GETSTATIC,owner,"STATES","Ljava/util/Map;");strict.visitVarInsn(ALOAD,0);
            strict.visitMethodInsn(INVOKEINTERFACE,"java/util/Map","get","(Ljava/lang/Object;)Ljava/lang/Object;",true);
            strict.visitTypeInsn(CHECKCAST,state);strict.visitFieldInsn(GETFIELD,state,"dirty","Z");strict.visitJumpInsn(IFEQ,done);
            strict.visitTypeInsn(NEW,"java/lang/IllegalStateException");strict.visitInsn(DUP);
            strict.visitLdcInsn("Quest reward withheld: durable state save failed");
            strict.visitMethodInsn(INVOKESPECIAL,"java/lang/IllegalStateException","<init>","(Ljava/lang/String;)V",false);
            strict.visitInsn(ATHROW);strict.visitLabel(done);strict.visitFrame(F_SAME,0,null,0,null);
            strict.visitInsn(RETURN);strict.visitMaxs(0,0);strict.visitEnd();node.methods.add(strict);
        }
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);
        Files.createDirectories(output.getParent());Files.write(output,writer.toByteArray());
    }
}
