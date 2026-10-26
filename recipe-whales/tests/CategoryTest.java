import java.util.*;
import ua.empirecraft.nativepatch.JeiCategories;
public class CategoryTest {
    public static void main(String[] args) {
        var categories=new ArrayList<Object>();
        for(String id:List.of("milling","pressing","sawing","block_cutting"))
            if(!JeiCategories.add(categories,id,id))throw new AssertionError("Missing category "+id);
        for(String id:List.of("fan_washing","fan_smoking","fan_blasting","fan_haunting","crushing","mixing",
                "automatic_shapeless","automatic_shaped","automatic_packing","automatic_brewing","mechanical_crafting",
                "sequenced_assembly","deploying","sandpaper_polishing","spout_filling","draining","mystery_conversion"))
            if(JeiCategories.add(categories,id,id))throw new AssertionError("Disabled category "+id);
        if(categories.size()!=4)throw new AssertionError(categories);
        System.out.println("PASS JEI: four working categories retained; 17 disabled categories excluded before registration");
    }
}
