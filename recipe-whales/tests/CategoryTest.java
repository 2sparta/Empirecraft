import java.util.*;
import com.simibubi.create.empirecraft.JeiCategories;
public class CategoryTest {
    public static void main(String[] args) {
        var categories=new ArrayList<Object>();
        for(String id:List.of("milling","pressing","sawing","block_cutting","item_application"))
            if(!JeiCategories.add(categories,id,id))throw new AssertionError("Missing category "+id);
        for(String id:List.of("fan_washing","fan_smoking","fan_blasting","fan_haunting","crushing","mixing",
                "automatic_shapeless","automatic_shaped","automatic_packing","automatic_brewing","mechanical_crafting",
                "sequenced_assembly","deploying","sandpaper_polishing","spout_filling","draining","mystery_conversion","packing"))
            if(JeiCategories.add(categories,id,id))throw new AssertionError("Disabled category "+id);
        if(categories.size()!=5)throw new AssertionError(categories);
        System.out.println("PASS JEI: five working categories retained; 18 disabled categories excluded before registration");
    }
}

