package ua.empirecraft.feudal;

import java.util.UUID;
import static ua.empirecraft.feudal.FeudalTests.*;

public final class ChatTitleTests {
    public static void main(String[] args) throws Exception {
        Class.forName("org.h2.Driver");
        try (var f = new Fixture()) {
            UUID person = UUID.fromString(D);
            eq(ChatTitleService.resolve(f.s,person,"n:A",e->true,e->e.kind().level,e->e.kind().title),"Барон");
            f.s.grant(a,"n:A",D,Model.Kind.COUNTY);
            eq(ChatTitleService.resolve(f.s,person,"n:A",e->true,e->e.kind().level,e->e.kind().title),"Граф");
            eq(ChatTitleService.resolve(f.s,person,null,e->false,e->e.kind().level,e->e.kind().title),"");
            eq(ChatTitleService.resolve(f.s,person,"n:B",e->false,e->e.kind().level,e->e.kind().title),"");
            eq(ChatTitleService.resolve(f.s,person,"n:A",e->true,e->5,e->"Імператор"),"Імператор");
            f.s.revoke(a,"n:A",D);
            eq(ChatTitleService.resolve(f.s,person,"n:A",e->true,e->e.kind().level,e->e.kind().title),"Барон");
            // A Towny authority change takes effect immediately, before the periodic estate sync.
            eq(ChatTitleService.resolve(f.s,person,"n:A",e->false,e->e.kind().level,e->e.kind().title),"");
            f.db.execute("UPDATE estates SET active=FALSE WHERE id='t:D'");
            eq(ChatTitleService.resolve(f.s,person,"n:A",e->true,e->e.kind().level,e->e.kind().title),"");
        }
        System.out.println("PASS current authority, political rank, grant/revoke, departed citizenship and inactive estates");
    }
}
