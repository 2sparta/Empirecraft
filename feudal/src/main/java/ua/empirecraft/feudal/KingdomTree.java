package ua.empirecraft.feudal;
import java.sql.SQLException;
import java.util.*;
import static ua.empirecraft.feudal.Database.*;
/** Pure ledger traversal shared by native nation reconciliation and regression tests. */
final class KingdomTree {
    static String root(FeudalService s,String id)throws SQLException {
        Database db=s.db;
        Set<String> seen=new HashSet<>();
        while(seen.add(id)){
            String next=s.parent(id);
            if(db.one("SELECT table_name FROM information_schema.tables WHERE LOWER(table_name)='political_admin'")!=null){
                var province=db.one("SELECT center FROM political_admin WHERE province=?",id);
                var merged=db.one("SELECT merged_into FROM political_states WHERE estate=?",id);
                if(province!=null)next=string(province,"center");
                else if(merged!=null && string(merged,"merged_into")!=null)next=string(merged,"merged_into");
            }
            if(next==null)return id;id=next;
        }
        throw new IllegalArgumentException("Цикл підпорядкування: королівства не змінено.");
    }
}
