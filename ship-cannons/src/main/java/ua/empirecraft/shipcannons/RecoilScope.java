package ua.empirecraft.shipcannons;

/** No optional CBC classes here: VS calls this guard even in installations without CBC. */
public final class RecoilScope {
    public static final class Scope {
        private final Scope parent;
        private long ship=Long.MIN_VALUE;
        private Scope(Scope parent) {this.parent=parent;}
    }
    private static final ThreadLocal<Scope> CURRENT=new ThreadLocal<>();
    private static final StackWalker WALKER=StackWalker.getInstance();
    private RecoilScope() {}
    public static Scope begin() {Scope scope=new Scope(CURRENT.get());CURRENT.set(scope);return scope;}
    public static void handled(Scope scope,long id) {scope.ship=id;}
    public static boolean replacesNative(long id) {Scope scope=CURRENT.get();return scope!=null && scope.ship==id;}
    public static boolean nativeCbcCall() {
        return WALKER.walk(frames->frames.limit(24).anyMatch(frame->frame.getMethodName().contains("vs$handleRecoil") &&
            (frame.getClassName().equals("rbasamoyai.createbigcannons.cannon_control.contraption.MountedBigCannonContraption") ||
             frame.getClassName().equals("rbasamoyai.createbigcannons.cannon_control.contraption.MountedAutocannonContraption"))));
    }
    public static void end(Scope scope) {if(scope.parent==null)CURRENT.remove();else CURRENT.set(scope.parent);}
}
