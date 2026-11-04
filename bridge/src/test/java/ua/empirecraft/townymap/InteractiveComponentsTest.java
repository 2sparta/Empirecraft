package ua.empirecraft.townymap;

import org.junit.jupiter.api.Test;
import net.minecraft.network.chat.*;
import ua.empirecraft.townymap.client.InteractiveComponents;
import static org.junit.jupiter.api.Assertions.*;

final class InteractiveComponentsTest {
    @Test void runAndSuggestPreserveExactArgumentsAndHover(){
        for(var action:new ClickEvent.Action[]{ClickEvent.Action.RUN_COMMAND,ClickEvent.Action.SUGGEST_COMMAND}){
            String command="/market treaty accept 3a5c 7 5b46-token";
            var message=Component.literal("Договір ").append(Component.literal("Прийняти").withStyle(s->s.withClickEvent(new ClickEvent(action,command)).withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,Component.literal("Точні механічні умови")))));
            var decoded=Component.Serializer.fromJson(Component.Serializer.toJson(message));
            assertTrue(InteractiveComponents.actionable(decoded));
            var style=decoded.getSiblings().get(0).getStyle();assertEquals(action,style.getClickEvent().getAction());assertEquals(command,style.getClickEvent().getValue());assertNotNull(style.getHoverEvent());
        }
    }
    @Test void PlayerTextNeverBecomesAnAction(){assertFalse(InteractiveComponents.actionable(Component.literal("{clickEvent:{action:run_command,value:/op somebody}}")));}
}
