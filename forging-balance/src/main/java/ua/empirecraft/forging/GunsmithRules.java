package ua.empirecraft.forging;
/** Engine-independent workshop timing and damage policy. Fractions apply before vanilla armor. */
public final class GunsmithRules {
    private GunsmithRules() {}
    public static int steps(int recipe,boolean blueprint){return recipe==3?(blueprint?6:24):(recipe==4||recipe==5)?18:recipe==0?12:6;}
    public static int operation(int progress,long now,long next){
        if(now<next)return progress;
        if(now>next+40&&progress>0)return progress-1;
        return progress+1;
    }
    public static String profile(String id){
        if(id.contains("cuirassier"))return "proof";
        if(id.contains("hauberk")||id.contains("coif")||id.contains("haubergeon")||id.contains("mail")||id.contains("chain"))return "mail";
        if(id.contains("quilted")||id.contains("arming_")||id.contains("gambeson")||id.contains("padded")||id.contains("leather"))return "padded";
        return "plate";
    }
    public static float modifier(String profile,int kind){return switch(profile){
        case "proof"->new float[]{-.50f,-.25f,.25f}[kind];
        case "mail"->new float[]{.20f,-.35f,.15f}[kind];
        case "padded"->new float[]{.30f,-.10f,-.20f}[kind];
        default->new float[]{-.15f,-.30f,.20f}[kind];
    };}
    public static float tier(String entity){return entity.startsWith("medieval_boomsticks:")?.60f:entity.startsWith("thirtyyearswar:")?1.35f:1f;}
    public static boolean bullet(String entity,String damage){
        return (entity.startsWith("musketmod:")||entity.startsWith("medieval_boomsticks:")||entity.startsWith("thirtyyearswar:"))
            &&(entity.contains("bullet")||entity.contains("shot")||entity.contains("round_ball")||entity.contains("roundball"))
            ||damage.contains("bullet")||damage.contains("musket")||damage.contains("gunshot");
    }
}
