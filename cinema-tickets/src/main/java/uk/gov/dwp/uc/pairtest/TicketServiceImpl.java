package uk.gov.dwp.uc.pairtest;

import thirdparty.paymentgateway.TicketPaymentService;
import thirdparty.paymentgateway.TicketPaymentServiceImpl;
import thirdparty.seatbooking.SeatReservationService;
import thirdparty.seatbooking.SeatReservationServiceImpl;
import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest;
import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest.Type;
import uk.gov.dwp.uc.pairtest.exception.InvalidPurchaseException;

import java.util.EnumMap;
import java.util.Map;

public class TicketServiceImpl implements TicketService {
    /**
     * Should only have private methods other than the one below.
     */

    private static final int MAX_TICKETS_PER_PURCHASE = 25;
    private static final int INFANT_PRICE = 0;
    private static final int CHILD_PRICE = 15;
    private static final int ADULT_PRICE = 25;

    private final TicketPaymentService paymentService;
    private final SeatReservationService seatReservationService;

    public TicketServiceImpl() {
        this(new TicketPaymentServiceImpl(), new SeatReservationServiceImpl());
    }

    public TicketServiceImpl(TicketPaymentService paymentService, SeatReservationService seatReservationService) {
        this.paymentService = paymentService;
        this.seatReservationService = seatReservationService;
    }

    @Override
    public void purchaseTickets(Long accountId, TicketTypeRequest... ticketTypeRequests) throws InvalidPurchaseException {
        validateAccount(accountId);
        Map<Type, Integer> ticketCounts = countTicketsByType(ticketTypeRequests);
        validatePurchase(ticketCounts);

        int amountToPay = calculateAmount(ticketCounts);
        int seatsToReserve = calculateSeats(ticketCounts);

        paymentService.makePayment(accountId, amountToPay);
        seatReservationService.reserveSeat(accountId, seatsToReserve);
    }

    private void validateAccount(Long accountId) {
        if (accountId == null || accountId <= 0) {
            throw new InvalidPurchaseException("Account id must be greater than zero");
        }
    }

    private Map<Type, Integer> countTicketsByType(TicketTypeRequest[] requests) {
        if (requests == null || requests.length == 0) {
            throw new InvalidPurchaseException("At least one ticket request is required");
        }

        Map<Type, Integer> counts = new EnumMap<>(Type.class);
        for (Type type : Type.values()) {
            counts.put(type, 0);
        }

        for (TicketTypeRequest request : requests) {
            if (request == null || request.getTicketType() == null) {
                throw new InvalidPurchaseException("Ticket request and ticket type must not be null");
            }
            if (request.getNoOfTickets() <= 0) {
                throw new InvalidPurchaseException("Number of tickets must be greater than zero");
            }
            if (request.getNoOfTickets() > MAX_TICKETS_PER_PURCHASE) {
                throw new InvalidPurchaseException(
                        "Cannot purchase more than " + MAX_TICKETS_PER_PURCHASE + " tickets at a time");
            }
            counts.merge(request.getTicketType(), request.getNoOfTickets(), Integer::sum);
        }
        return counts;
    }

    private void validatePurchase(Map<Type, Integer> counts) {
        int adults = counts.get(Type.ADULT);
        int children = counts.get(Type.CHILD);
        int infants = counts.get(Type.INFANT);

        if (adults + children + infants > MAX_TICKETS_PER_PURCHASE) {
            throw new InvalidPurchaseException(
                    "Cannot purchase more than " + MAX_TICKETS_PER_PURCHASE + " tickets at a time");
        }
        if (adults == 0) {
            throw new InvalidPurchaseException("Child and infant tickets require at least one adult ticket");
        }
        if (infants > adults) {
            throw new InvalidPurchaseException("Each infant must sit on an adult's lap, so infants cannot outnumber adults");
        }
    }

    private int calculateAmount(Map<Type, Integer> counts) {
        return counts.get(Type.ADULT) * ADULT_PRICE
                + counts.get(Type.CHILD) * CHILD_PRICE
                + counts.get(Type.INFANT) * INFANT_PRICE;
    }

    private int calculateSeats(Map<Type, Integer> counts) {
        // Infants sit on an adult's lap and are not allocated a seat.
        return counts.get(Type.ADULT) + counts.get(Type.CHILD);
    }

}
