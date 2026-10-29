package ua.empirecraft.townymap.client;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CurrencyViewStateTest {
    @Test void periodicRefreshNeverDisablesControlsOrOverlapsAnotherRefresh(){
        var state=new CurrencyViewState();assertTrue(state.begin(true,100));assertTrue(state.waiting());assertFalse(state.busy());
        assertFalse(state.begin(true,101));state.complete();assertFalse(state.busy());assertTrue(state.begin(true,200));assertFalse(state.busy());
    }
    @Test void userActionSupersedesAnInflightPollAndChangesTheAcceptedReplyRevision(){
        var state=new CurrencyViewState();state.begin(true,100);long old=state.revision();assertTrue(state.begin(false,101));
        assertNotEquals(old,state.revision());assertTrue(state.busy());assertFalse(state.begin(true,102));assertFalse(state.begin(false,103));state.complete();assertFalse(state.busy());
    }
    @Test void bothKindsOfRequestCanTimeOutAndRecover(){
        var state=new CurrencyViewState();state.begin(true,100);assertFalse(state.timedOut(299));assertTrue(state.timedOut(300));state.complete();
        assertTrue(state.begin(false,301));assertTrue(state.busy());assertTrue(state.timedOut(501));state.complete();assertTrue(state.begin(true,502));
    }
    @Test void comparisonsExcludeGoldTheOtherSelectedCurrencyAndDuplicates(){
        assertEquals(List.of("SOL"),CurrencyViewState.choices(List.of("@GOLD","KRN","SOL","SOL"),"KRN"));
        assertEquals(List.of(),CurrencyViewState.choices(List.of("KRN"),"KRN"));assertEquals(List.of(),CurrencyViewState.choices(List.of(),""));
    }
    @Test void thePrintTabExistsOnlyWithServerGrantedPrintingAuthority(){
        assertFalse(CurrencyViewState.tabs(false).contains("print"));assertEquals(List.of("market","policy","log","redeem","reserve"),CurrencyViewState.tabs(false));
        assertEquals(List.of("market","print","policy","log","redeem","reserve"),CurrencyViewState.tabs(true));
    }
}

