package uk.gov.dwp.uc.pairtest.domain;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TicketTypeRequestTest {

    @Test
    void exposesTypeAndNumberOfTickets() {
        TicketTypeRequest request = new TicketTypeRequest(TicketTypeRequest.Type.CHILD, 3);

        assertEquals(TicketTypeRequest.Type.CHILD, request.getTicketType());
        assertEquals(3, request.getNoOfTickets());
    }

    @Test
    void isImmutable() {
        assertTrue(Modifier.isFinal(TicketTypeRequest.class.getModifiers()), "class should be final");
        Arrays.stream(TicketTypeRequest.class.getDeclaredFields())
                .filter(field -> !field.isSynthetic())
                .forEach(field -> assertTrue(Modifier.isFinal(field.getModifiers()),
                        field.getName() + " should be final"));
    }
}
