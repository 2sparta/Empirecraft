package ua.empirecraft.townymap.client;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CivicReadStateTest {
    JsonObject response(String kind,String id){var data=new JsonObject();data.addProperty("readKind",kind);data.addProperty("readId",id);return data;}
    @Test void refreshingKeepsTheSelectedNoticeAndLeaderboard(){for(String kind:new String[]{"NOTICE","FISHING","EVENT"}){var data=response(kind,"12c62877-ce56-416c-a44a-fe532c7bead8");assertEquals(kind,CivicReadState.reading(data).orElseThrow().kind());assertTrue(CivicReadState.leaveForm("prepare",data));assertFalse(CivicReadState.leaveForm("view",data));}}
    @Test void returningToListAndServerRejectionStopReading(){assertTrue(CivicReadState.reading(new JsonObject()).isEmpty());var rejected=response("NOTICE","id");rejected.addProperty("error","Approach the board");assertTrue(CivicReadState.reading(rejected).isEmpty());assertFalse(CivicReadState.leaveForm("prepare",rejected));assertTrue(CivicReadState.reading(response("UNKNOWN","id")).isEmpty());}
    @Test void pageAndModerationViewsSurviveRefresh(){assertEquals("12",CivicReadState.reading(response("PAGE","12")).orElseThrow().id());assertEquals("REVIEW",CivicReadState.reading(response("REVIEW","queue")).orElseThrow().kind());}
    @Test void pagesStayWithinRowsWhenTheWindowOrListChanges(){assertEquals(new CivicReadState.Rows(7,3),CivicReadState.rows(10,100,63));assertEquals(new CivicReadState.Rows(0,3),CivicReadState.rows(2,7,63));assertEquals(new CivicReadState.Rows(0,1),CivicReadState.rows(0,-1,0));assertEquals(4,CivicReadState.rows(10,4,84).offset());}
}
