package uk.gov.dwp.uc.pairtest;
 
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import thirdparty.paymentgateway.TicketPaymentService;
import thirdparty.seatbooking.SeatReservationService;
import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest;
import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest.Type;
import uk.gov.dwp.uc.pairtest.exception.InvalidPurchaseException;
 
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
 
class TicketServiceImplTest {
 
    private static final long ACCOUNT_ID = 1L;
 
    private TicketPaymentService paymentService;
 
    private SeatReservationService seatReservationService;
 
    private TicketService ticketService;
 
    @BeforeEach
    void setUp() {
        paymentService = mock(TicketPaymentService.class);
        seatReservationService = mock(SeatReservationService.class);
        ticketService = new TicketServiceImpl(paymentService, seatReservationService);
    }
 
    private static TicketTypeRequest request(Type type, int count) {
        return new TicketTypeRequest(type, count);
    }
 
    private void assertInvalid(Long accountId, TicketTypeRequest... requests) {
        assertThrows(InvalidPurchaseException.class, () -> ticketService.purchaseTickets(accountId, requests));
        verifyNoInteractions(paymentService, seatReservationService);
    }
 
    @Nested
    @DisplayName("valid purchases")
    class ValidPurchases {
 
        @Test
        void singleAdultPaysFullPriceAndGetsASeat() {
            ticketService.purchaseTickets(ACCOUNT_ID, request(Type.ADULT, 1));
 
            verify(paymentService).makePayment(ACCOUNT_ID, 25);
            verify(seatReservationService).reserveSeat(ACCOUNT_ID, 1);
        }
 
        @Test
        void adultAndChildPayForBothAndGetTwoSeats() {
            ticketService.purchaseTickets(ACCOUNT_ID, request(Type.ADULT, 1), request(Type.CHILD, 1));
 
            verify(paymentService).makePayment(ACCOUNT_ID, 40);
            verify(seatReservationService).reserveSeat(ACCOUNT_ID, 2);
        }
 
        @Test
        void infantsPayNothingAndAreNotAllocatedSeats() {
            ticketService.purchaseTickets(ACCOUNT_ID, request(Type.ADULT, 2), request(Type.INFANT, 2));
 
            verify(paymentService).makePayment(ACCOUNT_ID, 50);
            verify(seatReservationService).reserveSeat(ACCOUNT_ID, 2);
        }
 
        @Test
        void mixedPurchaseCalculatesAmountAndSeats() {
            ticketService.purchaseTickets(ACCOUNT_ID,
                    request(Type.ADULT, 3), request(Type.CHILD, 2), request(Type.INFANT, 1));
 
            verify(paymentService).makePayment(ACCOUNT_ID, 3 * 25 + 2 * 15);
            verify(seatReservationService).reserveSeat(ACCOUNT_ID, 5);
        }
 
        @Test
        void multipleRequestsOfTheSameTypeAreCombined() {
            ticketService.purchaseTickets(ACCOUNT_ID,
                    request(Type.ADULT, 1), request(Type.ADULT, 2), request(Type.CHILD, 1), request(Type.CHILD, 1));
 
            verify(paymentService).makePayment(ACCOUNT_ID, 3 * 25 + 2 * 15);
            verify(seatReservationService).reserveSeat(ACCOUNT_ID, 5);
        }
 
        @Test
        void exactlyTwentyFiveTicketsIsAllowed() {
            ticketService.purchaseTickets(ACCOUNT_ID,
                    request(Type.ADULT, 10), request(Type.CHILD, 10), request(Type.INFANT, 5));
 
            verify(paymentService).makePayment(ACCOUNT_ID, 10 * 25 + 10 * 15);
            verify(seatReservationService).reserveSeat(ACCOUNT_ID, 20);
        }
 
        @Test
        void infantsEqualToAdultsIsAllowed() {
            ticketService.purchaseTickets(ACCOUNT_ID, request(Type.ADULT, 3), request(Type.INFANT, 3));
 
            verify(paymentService).makePayment(ACCOUNT_ID, 75);
            verify(seatReservationService).reserveSeat(ACCOUNT_ID, 3);
        }
    }
 
    @Nested
    @DisplayName("invalid account")
    class InvalidAccount {
 
        @Test
        void nullAccountIdIsRejected() {
            assertInvalid(null, request(Type.ADULT, 1));
        }
 
        @Test
        void zeroAccountIdIsRejected() {
            assertInvalid(0L, request(Type.ADULT, 1));
        }
 
        @Test
        void negativeAccountIdIsRejected() {
            assertInvalid(-5L, request(Type.ADULT, 1));
        }
    }
 
    @Nested
    @DisplayName("invalid requests")
    class InvalidRequests {
 
        @Test
        void noRequestsIsRejected() {
            assertInvalid(ACCOUNT_ID);
        }
 
        @Test
        void nullRequestArrayIsRejected() {
            assertThrows(InvalidPurchaseException.class,
                    () -> ticketService.purchaseTickets(ACCOUNT_ID, (TicketTypeRequest[]) null));
            verifyNoInteractions(paymentService, seatReservationService);
        }
 
        @Test
        void nullRequestElementIsRejected() {
            assertInvalid(ACCOUNT_ID, request(Type.ADULT, 1), null);
        }
 
        @Test
        void nullTicketTypeIsRejected() {
            assertInvalid(ACCOUNT_ID, request(Type.ADULT, 1), request(null, 1));
        }
 
        @Test
        void zeroTicketsInARequestIsRejected() {
            assertInvalid(ACCOUNT_ID, request(Type.ADULT, 0));
        }
 
        @Test
        void negativeTicketsInARequestIsRejected() {
            assertInvalid(ACCOUNT_ID, request(Type.ADULT, 2), request(Type.CHILD, -1));
        }
 
        @Test
        void hugeSingleRequestIsRejectedWithoutOverflow() {
            assertInvalid(ACCOUNT_ID, request(Type.ADULT, Integer.MAX_VALUE), request(Type.ADULT, Integer.MAX_VALUE));
        }
    }
 
    @Nested
    @DisplayName("business rule violations")
    class BusinessRules {
 
        @Test
        void moreThanTwentyFiveTicketsInTotalIsRejected() {
            assertInvalid(ACCOUNT_ID, request(Type.ADULT, 20), request(Type.CHILD, 6));
        }
 
        @Test
        void infantsCountTowardsTheTwentyFiveTicketLimit() {
            assertInvalid(ACCOUNT_ID, request(Type.ADULT, 20), request(Type.INFANT, 6));
        }
 
        @Test
        void twentySixAdultsInOneRequestIsRejected() {
            assertInvalid(ACCOUNT_ID, request(Type.ADULT, 26));
        }
 
        @Test
        void childWithoutAdultIsRejected() {
            assertInvalid(ACCOUNT_ID, request(Type.CHILD, 1));
        }
 
        @Test
        void infantWithoutAdultIsRejected() {
            assertInvalid(ACCOUNT_ID, request(Type.INFANT, 1));
        }
 
        @Test
        void childAndInfantWithoutAdultIsRejected() {
            assertInvalid(ACCOUNT_ID, request(Type.CHILD, 2), request(Type.INFANT, 1));
        }
 
        @Test
        void moreInfantsThanAdultsIsRejected() {
            assertInvalid(ACCOUNT_ID, request(Type.ADULT, 1), request(Type.INFANT, 2));
        }
 
        @Test
        void rejectedPurchaseNeverTakesPaymentOrReservesSeats() {
            assertThrows(InvalidPurchaseException.class,
                    () -> ticketService.purchaseTickets(ACCOUNT_ID, request(Type.CHILD, 1)));
 
            verify(paymentService, never()).makePayment(anyLong(), anyInt());
            verify(seatReservationService, never()).reserveSeat(anyLong(), anyInt());
        }
    }
}