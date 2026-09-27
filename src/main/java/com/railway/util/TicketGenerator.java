package com.railway.util;

import com.railway.model.Booking;

/**
 * Formats a confirmed {@link Booking} into a plain-text e-ticket, shown in
 * the booking confirmation dialog and optionally saved to a .txt file.
 */
public final class TicketGenerator {

    private TicketGenerator() {
    }

    public static String generate(Booking booking) {
        String trainLine = booking.getTrainNumber() != null
                ? booking.getTrainNumber() + " - " + booking.getTrainName()
                : "Multiple trains (see itinerary)";

        StringBuilder sb = new StringBuilder();
        sb.append("================================================\n");
        sb.append("        SMART RAILWAY - E-TICKET\n");
        sb.append("================================================\n");
        sb.append(String.format("Booking ID     : %d%n", booking.getId()));
        sb.append(String.format("Passenger      : %s%n", booking.getPassengerName()));
        sb.append(String.format("From           : %s%n", booking.getFromStationLabel()));
        sb.append(String.format("To             : %s%n", booking.getToStationLabel()));
        sb.append(String.format("Train          : %s%n", trainLine));
        sb.append(String.format("Class          : %s%n", booking.getSeatClass().getDisplayName()));
        sb.append(String.format("Seats          : %d%n", booking.getSeatCount()));
        sb.append(String.format("Total Fare     : %.2f BDT%n", booking.getTotalFare()));
        sb.append(String.format("Booked At      : %s%n", booking.getBookedAt()));
        sb.append("------------------------------------------------\n");
        sb.append("Please arrive at least 30 minutes before departure.\n");
        sb.append("================================================\n");
        return sb.toString();
    }
}
