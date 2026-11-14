import java.util.*;
import ua.empirecraft.packfixes.JeiInvocations;

public final class JeiInvocationsTest {
    public static final class Manager {
        int removals,queries;
        public void removeIngredientsAtRuntime(Object type,Collection<?> ingredients) {
            if(ingredients.isEmpty())throw new IllegalArgumentException("ingredients must not be empty.");removals+=ingredients.size();
        }
        public Collection<?> getAllIngredients(Object type){queries++;return List.of();}
    }
    public static void main(String[] args)throws Exception {
        var manager=new Manager();var method=Manager.class.getMethod("removeIngredientsAtRuntime",Object.class,Collection.class);
        JeiInvocations.invoke(method,manager,new Object[]{new Object(),List.of()});
        if(manager.removals!=0)throw new AssertionError("Empty removal reached JEI");
        JeiInvocations.invoke(method,manager,new Object[]{new Object(),List.of("one","two")});
        if(manager.removals!=2)throw new AssertionError("Nonempty removal changed");
        var query=Manager.class.getMethod("getAllIngredients",Object.class);
        if(!((Collection<?>)JeiInvocations.invoke(query,manager,new Object[]{new Object()})).isEmpty()||manager.queries!=1)throw new AssertionError("Normal call changed");
        System.out.println("JEI_EMPTY_REMOVAL_PASS empty=skipped nonempty=preserved queries=preserved");
    }
}
