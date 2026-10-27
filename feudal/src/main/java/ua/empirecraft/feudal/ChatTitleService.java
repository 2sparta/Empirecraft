package ua.empirecraft.feudal;

import java.sql.SQLException;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import static ua.empirecraft.feudal.Database.*;

/** Read-only title lookup shared by /t title and the chat bridge; never opens another database. */
public final class ChatTitleService {
    private ChatTitleService() {}
    public static String resolve(FeudalService service, UUID person, String currentNation,
            java.util.function.Predicate<Model.Estate> currentAuthority,
            ToIntFunction<Model.Estate> rank, Function<Model.Estate, String> label) throws SQLException {
        String title = "";
        int best = -1;
        for (Model.Estate estate : service.estates()) {
            if (!estate.active() || !person.toString().equals(estate.lord()) || !currentAuthority.test(estate)) continue;
            int candidate = rank.applyAsInt(estate);
            if (candidate > best) { best = candidate; title = label.apply(estate); }
        }
        if (currentNation != null) for (var grant : service.db.query("SELECT * FROM grants WHERE person=? AND nation=?", person.toString(), currentNation)) {
            Model.Kind kind = Model.Kind.valueOf(string(grant, "rank"));
            if (kind.level > best) { best = kind.level; title = kind.title; }
        }
        return title;
    }
}
