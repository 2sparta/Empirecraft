package ua.empirecraft.feudal;

import java.sql.SQLException;
import java.util.*;
import static ua.empirecraft.feudal.Model.require;
import static ua.empirecraft.feudal.PoliticalModel.*;
import ua.empirecraft.feudal.FeudalService.Actor;

/** Explicit debug authority, never called by normal promotion or scheduled reconciliation. */
final class PoliticsDebug {
    static final String PERMISSION="empirecraft.feudal.debug";
    private final PoliticsService p;
    PoliticsDebug(PoliticsService politics){p=politics;}

    List<Stage> nextStages(String id) throws SQLException {
        Stage current=p.state(id).stage();
        return Arrays.stream(Stage.values()).filter(target->p.rules.transition(current,target)).toList();
    }
    private void allowed(Actor actor,String id) throws SQLException {
        require(actor.admin(),LocalText.tr("Потрібен дозвіл ")+PERMISSION+".");
        p.live(id);
        require(p.db.one("SELECT id FROM political_elections WHERE state=? AND status='PREPARED'",id)==null,
            LocalText.tr("Спочатку завершіть або скасуйте підготовлену передачу влади: /t state election resume|abort <ID>."));
    }
    private void changed(Actor actor,String id,String event,String detail) throws SQLException {
        p.db.execute("DELETE FROM political_ready WHERE state=?",id);
        p.db.execute("UPDATE political_decisions SET status='STALE' WHERE state=? AND status='OPEN'",id);
        p.db.execute("UPDATE political_elections SET status='STALE' WHERE state=? AND status='OPEN'",id);
        p.changed(actor,id,event,detail+LocalText.tr("; debug: вимоги розвитку пропущено"));
    }
    Stage next(Actor actor,String id,Stage requested){return p.db.transaction(()->{
        allowed(actor,id);var choices=nextStages(id);
        require(!choices.isEmpty(),LocalText.tr("Вищих статусів цієї гілки немає."));
        require(requested!=null || choices.size()==1,LocalText.tr("Оберіть наступний статус: ")+choices);
        Stage target=requested==null?choices.get(0):requested;
        require(choices.contains(target),LocalText.tr("Доступні наступні статуси: ")+choices);
        setStage(actor,id,target,"DEBUG_NEXT");return target;
    });}
    void stage(Actor actor,String id,Stage target){p.db.transaction(()->{
        allowed(actor,id);setStage(actor,id,Objects.requireNonNull(target),"DEBUG_STAGE");return null;
    });}
    private void setStage(Actor actor,String id,Stage target,String event) throws SQLException {
        Stage before=p.state(id).stage();require(before!=target,LocalText.tr("Цей статус уже встановлено."));
        p.applyStage(id,target);changed(actor,id,event,before+" → "+target);
    }
    void government(Actor actor,String id,Government target){p.db.transaction(()->{
        allowed(actor,id);Government before=p.state(id).government();require(before!=target,LocalText.tr("Ця форма правління вже встановлена."));
        p.db.execute("UPDATE political_states SET government=?,term_end=? WHERE estate=?",target.name(),
            target==Government.REPUBLIC?p.s.now()+p.rules.days("term-days",30):0,id);
        changed(actor,id,"DEBUG_GOVERNMENT",before+" → "+target);return null;
    });}
    void territory(Actor actor,String id,Territory target){p.db.transaction(()->{
        allowed(actor,id);Territory before=p.state(id).territory();require(before!=target,LocalText.tr("Цей устрій уже встановлено."));
        p.db.execute("UPDATE political_states SET territory=? WHERE estate=?",target.name(),id);
        changed(actor,id,"DEBUG_TERRITORY",before+" → "+target);return null;
    });}
    void ready(Actor actor,String id,Stage target){p.db.transaction(()->{
            allowed(actor,id);
            require(p.progressRecord(id,target).met(),LocalText.tr("Спочатку виконайте вимоги розвитку; ready пропускає лише строк стабільності."));
            p.db.execute("MERGE INTO political_ready KEY(state,target) VALUES(?,?,?,?)",id,target.name(),
                Math.max(1,Math.subtractExact(p.s.now(),p.rules.hold())),p.fingerprint(id));
            p.db.execute("DELETE FROM political_advances WHERE state=?",id);
            p.history(actor,id,"DEBUG_READY",target+LocalText.tr("; строк стабільності пропущено, потрібна звичайна заявка правителя"));return null;
    });}
}
