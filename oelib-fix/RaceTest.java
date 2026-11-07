import java.lang.reflect.*;
import java.util.*;
import sun.misc.Unsafe;

/** Force modification after iterator creation, exactly where Set.copyOf used to race. */
public final class RaceTest {
 public static void main(String[] args)throws Exception {
  var type=Class.forName("cc.sighs.oelib.network.api.NetworkAutoRegistration");
  Field field=type.getDeclaredField("BASE_PACKAGES");field.setAccessible(true);
  Set<String> delegate=(Set<String>)field.get(null);
  var expectedOrder=new ArrayList<>(delegate);
  type.getMethod("registerBasePackage",String.class).invoke(null,(Object)null);
  type.getMethod("registerBasePackage",String.class).invoke(null,"");
  type.getMethod("registerBasePackage",String.class).invoke(null,expectedOrder.get(0));
  if(delegate.size()!=expectedOrder.size())throw new AssertionError("Null, empty or duplicate package changed registry");
  Field scannedField=type.getDeclaredField("SCANNED_PACKAGES");scannedField.setAccessible(true);
  ((Set<String>)scannedField.get(null)).addAll(delegate);
  Set<String> racing=new AbstractSet<>() {
   boolean once;
   public int size(){return delegate.size();}
   public Iterator<String> iterator(){var it=delegate.iterator();if(!once){once=true;delegate.add("test.concurrent.late");}return it;}
  };
  Field unsafeField=Unsafe.class.getDeclaredField("theUnsafe");unsafeField.setAccessible(true);Unsafe unsafe=(Unsafe)unsafeField.get(null);
  unsafe.putObject(unsafe.staticFieldBase(field),unsafe.staticFieldOffset(field),racing);
  try {
   var result=(Set<?>)type.getMethod("findAllAnnotatedPackets").invoke(null);
   if(args[0].equals("old"))throw new AssertionError("Old race did not reproduce");
   if(!(delegate instanceof java.util.concurrent.CopyOnWriteArraySet)||!result.isEmpty())throw new AssertionError("Unexpected fixed registry");
   expectedOrder.add("test.concurrent.late");
   if(!new ArrayList<>(delegate).equals(expectedOrder))throw new AssertionError("Package insertion order changed");
   System.out.println("OELIB_RACE_FIXED_PASS stable_snapshot=true insertion_order_preserved=true");
  } catch(InvocationTargetException e) {
   if(!args[0].equals("old")||!(e.getCause() instanceof ConcurrentModificationException))throw e;
   System.out.println("OELIB_ORIGINAL_RACE_REPRODUCED");
  }
 }
}
