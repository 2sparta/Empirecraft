package ua.posters;
import java.util.*;import org.objectweb.asm.*;import org.objectweb.asm.tree.*;import org.joml.Matrix4d;
/** A small, non-executing reader for straight-line Minecraft model-builder definitions. */
public final class JavaModelReader {
    public record Pose(double x,double y,double z,double rx,double ry,double rz) {public static final Pose ZERO=new Pose(0,0,0,0,0,0);}
    private record Cube(double x,double y,double z,double dx,double dy,double dz,double inflate){}
    public static final class Node {
        public final String name;public final Pose pose;private final List<Cube> cubes;public final Map<String,Node> children=new LinkedHashMap<>();
        private Node(String name,Pose pose,List<Cube> cubes){this.name=name;this.pose=pose;this.cubes=List.copyOf(cubes);}
        public List<Mesh.Triangle> geometry(Matrix4d parent,Map<String,Pose> overrides,int color){
            Pose p=overrides.getOrDefault(name,pose);Matrix4d m=new Matrix4d(parent).translate(p.x/16,p.y/16,p.z/16).rotateZ(p.rz).rotateY(p.ry).rotateX(p.rx);List<Mesh.Triangle> out=new ArrayList<>();
            for(Cube c:cubes)out.addAll(Mesh.transform(Mesh.box((c.x-c.inflate)/16,(c.y-c.inflate)/16,(c.z-c.inflate)/16,(c.x+c.dx+c.inflate)/16,(c.y+c.dy+c.inflate)/16,(c.z+c.dz+c.inflate)/16,0,color),m,0,Integer.MIN_VALUE));
            for(Node child:children.values())out.addAll(child.geometry(m,overrides,color));return out;
        }
        public Node find(String name){if(this.name.equals(name))return this;for(Node n:children.values()){Node r=n.find(name);if(r!=null)return r;}return null;}
        public int cubeCount(){int n=cubes.size();for(Node c:children.values())n+=c.cubeCount();return n;}
    }
    private static final class Builder {final List<Cube> cubes=new ArrayList<>();}
    private static final class Reference {final String kind;double inflate;final Node root=new Node("root",Pose.ZERO,List.of());Reference(String kind){this.kind=kind;}}
    public static Node read(byte[] data,String name){
        if(data==null||data.length>2*1024*1024)throw new IllegalArgumentException("missing model builder");
        ClassNode cls=new ClassNode();new ClassReader(data).accept(cls,ClassReader.SKIP_FRAMES|ClassReader.SKIP_DEBUG);
        MethodNode method=cls.methods.stream().filter(m->m.name.equals(name)&&(m.access&Opcodes.ACC_STATIC)!=0).findFirst().orElseThrow(()->new IllegalArgumentException("model factory"));
        if(method.instructions.size()>4096)throw new IllegalArgumentException("model factory size");List<Object> stack=new ArrayList<>();Object[] locals=new Object[64];
        for(AbstractInsnNode insn:method.instructions){int op=insn.getOpcode();if(op<0||op==Opcodes.NOP)continue;
            switch(op){
                case Opcodes.NEW->{String type=((TypeInsnNode)insn).desc;if(!type.endsWith("/MeshDefinition")&&!type.endsWith("/CubeDeformation"))throw new IllegalArgumentException("model object");stack.add(new Reference(type));}
                case Opcodes.DUP->stack.add(stack.get(stack.size()-1));case Opcodes.POP->pop(stack);
                case Opcodes.ASTORE,Opcodes.FSTORE,Opcodes.ISTORE->locals[((VarInsnNode)insn).var]=pop(stack);
                case Opcodes.ALOAD,Opcodes.FLOAD,Opcodes.ILOAD->stack.add(locals[((VarInsnNode)insn).var]);
                case Opcodes.LDC->stack.add(((LdcInsnNode)insn).cst);
                case Opcodes.BIPUSH,Opcodes.SIPUSH->stack.add(((IntInsnNode)insn).operand);
                case Opcodes.ICONST_M1,Opcodes.ICONST_0,Opcodes.ICONST_1,Opcodes.ICONST_2,Opcodes.ICONST_3,Opcodes.ICONST_4,Opcodes.ICONST_5->stack.add(op-Opcodes.ICONST_0);
                case Opcodes.FCONST_0,Opcodes.FCONST_1,Opcodes.FCONST_2->stack.add((float)(op-Opcodes.FCONST_0));
                case Opcodes.FNEG->stack.add(-num(pop(stack)));case Opcodes.I2F->stack.add((float)num(pop(stack)));
                case Opcodes.FADD,Opcodes.FSUB,Opcodes.FMUL,Opcodes.FDIV->{double b=num(pop(stack)),a=num(pop(stack));stack.add(op==Opcodes.FADD?a+b:op==Opcodes.FSUB?a-b:op==Opcodes.FMUL?a*b:a/b);}
                case Opcodes.GETSTATIC->{var f=(FieldInsnNode)insn;if(!f.owner.endsWith("/PartPose")||!f.name.equals("f_171404_"))throw new IllegalArgumentException("model field");stack.add(Pose.ZERO);}
                case Opcodes.INVOKESTATIC,Opcodes.INVOKEVIRTUAL,Opcodes.INVOKESPECIAL->{
                    var call=(MethodInsnNode)insn;int count=Type.getArgumentTypes(call.desc).length;Object[] args=new Object[count];for(int i=count-1;i>=0;i--)args[i]=pop(stack);Object receiver=op==Opcodes.INVOKESTATIC?null:pop(stack);Object result=call(call,receiver,args);
                    if(Type.getReturnType(call.desc).getSort()!=Type.VOID)stack.add(result);
                }
                case Opcodes.ARETURN->{Object value=pop(stack);if(value instanceof Reference r&&r.kind.endsWith("/MeshDefinition"))return r.root;throw new IllegalArgumentException("model return");}
                default->throw new IllegalArgumentException("unsupported model instruction "+op);
            }
            if(stack.size()>128)throw new IllegalArgumentException("model stack");
        }throw new IllegalArgumentException("no model return");
    }
    private static Object call(MethodInsnNode m,Object receiver,Object[] a){
        if(m.name.equals("<init>")){if(!(receiver instanceof Reference r))throw new IllegalArgumentException("model constructor");if(r.kind.endsWith("/CubeDeformation")){if(a.length!=1)throw new IllegalArgumentException("deformation");r.inflate=num(a[0]);}return null;}
        if(m.owner.endsWith("/MeshDefinition")&&receiver instanceof Reference r&&a.length==0)return r.root;
        if(m.owner.endsWith("/CubeListBuilder")){
            if(receiver==null&&a.length==0)return new Builder();if(!(receiver instanceof Builder b))throw new IllegalArgumentException("cube builder");
            if(a.length>=6&&a[0] instanceof Number){double inflate=a.length==7&&a[6] instanceof Reference r?r.inflate:0;b.cubes.add(new Cube(num(a[0]),num(a[1]),num(a[2]),num(a[3]),num(a[4]),num(a[5]),inflate));}
            else if(a.length!=2&&a.length!=0&&a.length!=1)throw new IllegalArgumentException("cube builder arguments");return b;
        }
        if(m.owner.endsWith("/PartPose")){if(a.length==6)return new Pose(num(a[0]),num(a[1]),num(a[2]),num(a[3]),num(a[4]),num(a[5]));throw new IllegalArgumentException("model pose");}
        if(m.owner.endsWith("/PartDefinition")&&receiver instanceof Node parent&&a.length==3&&a[0] instanceof String name&&a[1] instanceof Builder b&&a[2] instanceof Pose pose){Node child=new Node(name,pose,b.cubes);parent.children.put(name,child);return child;}
        if(m.owner.endsWith("/LayerDefinition")&&a.length==3&&a[0] instanceof Reference r)return r;
        throw new IllegalArgumentException("unsupported builder call "+m.owner+"."+m.name);
    }
    private static Object pop(List<Object> values){if(values.isEmpty())throw new IllegalArgumentException("model stack");return values.remove(values.size()-1);}
    private static double num(Object o){if(!(o instanceof Number n)||!Double.isFinite(n.doubleValue()))throw new IllegalArgumentException("model number");return n.doubleValue();}
}
