package ua.empirecraft.packfixes;

import java.lang.reflect.Method;
import java.util.Collection;

public final class JeiInvocations {
    public static Object invoke(Method method,Object receiver,Object[] arguments)throws ReflectiveOperationException {
        // JEI 15 rejects an empty removal set after another filter already hid it.
        if(method.getName().equals("removeIngredientsAtRuntime")&&arguments!=null&&arguments.length==2
                &&arguments[1] instanceof Collection<?> collection&&collection.isEmpty())return null;
        return method.invoke(receiver,arguments);
    }
}
