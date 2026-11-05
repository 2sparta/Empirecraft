package ua.empirecraft.townymap.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClientTextTest {
    @Test void nativeScreensUseTheServerPreferenceForEachReply(){
        assertEquals("Market",ClientText.tr("Ринок","en"));
        assertEquals("Рынок",ClientText.tr("Ринок","ru"));
        assertEquals("Ринок",ClientText.tr("Ринок","uk"));
        assertEquals("Market",ClientText.tr("Ринок","en"));
    }
    @Test void formNumbersCurrencyCodesAndDynamicDataStayLiteral(){
        assertEquals("Minimum to receive (blank: −0.5%)",ClientText.tr("Мінімум до отримання (порожньо: −0.5%)","en"));
        assertEquals("Минимум к получению (пусто: −0.5%)",ClientText.tr("Мінімум до отримання (порожньо: −0.5%)","ru"));
        assertEquals("Player KRN 123.45",ClientText.tr("Player KRN 123.45","ru"));
        assertEquals("Unknown custom text",ClientText.tr("Unknown custom text","en"));
    }
    @Test void unitsNavigationAndPolicyFieldsAreLocalized(){
        assertEquals("1 hour",ClientText.tr("1 година","en"));
        assertEquals("1 час",ClientText.tr("1 година","ru"));
        assertEquals("‹ Действия",ClientText.tr("‹ Дії","ru"));
        assertEquals("Purchase: fee %",ClientText.tr("Купівля: комісія %","en"));
    }
}
