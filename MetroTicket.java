import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class MetroTicket {

    private static final String APP_NAME  = "Navi Mumbai Metro";
    private static final String VERSION   = "2.1.1";
    private static final String SOURCE    = "Belapur Terminal";
    private static final String HELPLINE  = "1800-123-CIDCO";
    private static final double DAY_PASS  = 100.0;
    private static final int[]  SLABS     = {3, 6, 9, 11};
    private static final int[]  FARES     = {10, 15, 20, 25, 30};
    private static final int    STUDENT_MAX = 24;
    private static final int    SENIOR_MIN  = 60;
    private static final String TICKET_PFX  = "NMM";
    private static final long   DELAY_MS    = 3000;

    private static final String[] STATIONS = {
        "Belapur Terminal", "RBI Colony", "Belpada", "Utsav Chowk",
        "Kendriya Vihar", "Kharghar Village", "Central Park", "Pethpada",
        "Amandoot", "Pethali-Taloja", "Pendhar"
    };

    private static final int[] DIST = {0, 1, 2, 4, 5, 6, 7, 8, 9, 10, 11};

    // -----------------------------------------------------------------------
    // Enums
    // -----------------------------------------------------------------------

    enum TicketType {
        SINGLE("Single Journey"),
        RETURN("Return Journey"),
        DAY_PASS("Day Pass");

        final String label;
        TicketType(String label) { this.label = label; }
    }

    enum Payment {
        CASH("Cash"),
        UPI("UPI / QR Code"),
        CARD("Debit / Credit Card"),
        SMART("Smart Card");

        final String label;
        Payment(String label) { this.label = label; }
    }

    enum Category {
        STUDENT("Student",        0.25),
        SENIOR ("Senior Citizen", 0.60),
        REGULAR("Regular",        1.00);

        final String label;
        final double factor;
        Category(String label, double factor) { this.label = label; this.factor = factor; }
    }

    // -----------------------------------------------------------------------
    // Domain classes
    // -----------------------------------------------------------------------

    static class Passenger {
        final int     age;
        final boolean cardHolder;

        Passenger(int age, boolean cardHolder) {
            // BUG-FIX: tighten range check message to match guard
            if (age < 1 || age > 120)
                throw new IllegalArgumentException("Age must be between 1 and 120.");
            this.age        = age;
            this.cardHolder = cardHolder;
        }

        Category getCategory() {
            if (!cardHolder)      return Category.REGULAR;
            if (age <= STUDENT_MAX) return Category.STUDENT;
            if (age >= SENIOR_MIN)  return Category.SENIOR;
            return Category.REGULAR;
        }

        double getFactor()   { return getCategory().factor; }
        String getCatName()  { return getCategory().label;  }
    }

    static class Fare {
        final int    base;
        final double total;
        final double discount;   // total discount saved (Rs.)

        Fare(int base, double total, double discount) {
            this.base     = base;
            this.total    = total;
            this.discount = discount;
        }
    }

    static class Booking {
        private final List<Passenger> pax = new ArrayList<>();
        private TicketType type;
        private int    destIdx  = -1;
        private String destName;
        private Payment payment;
        private boolean done;
        private String  ticketNo;
        private final LocalDate date = LocalDate.now();

        void addPax(Passenger p)   { pax.add(p); }
        void clearPax()            { pax.clear(); }
        List<Passenger> getPax()   { return new ArrayList<>(pax); }
        int  paxCount()            { return pax.size(); }

        // BUG-FIX: hasCard() no longer relies on a dummy passenger being pre-loaded;
        //          it now checks whether the first real passenger has a card.
        boolean hasCard() {
            return !pax.isEmpty() && pax.get(0).cardHolder;
        }

        void setDest(int idx, String name) { destIdx = idx; destName = name; }
        String getDestName() { return destName != null ? destName : "All Stations"; }

        int getDist() {
            return (destIdx > 0 && destIdx < DIST.length) ? DIST[destIdx] : 0;
        }

        void reset() {
            pax.clear();
            type     = null;
            destIdx  = -1;
            destName = null;
            payment  = null;
            done     = false;
            ticketNo = null;
        }
    }

    static class History {
        private static final List<String[]> log = new ArrayList<>();

        static void add(Booking b, Fare f, boolean ok) {
            String ts  = java.time.LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
            String tkt = (b.ticketNo != null) ? b.ticketNo : "CANCELLED";
            String typ = (b.type     != null) ? b.type.label : "N/A";
            String pay = (b.payment  != null) ? b.payment.label : "N/A";
            double amt = (f          != null) ? f.total : 0;

            log.add(new String[]{
                ts,
                tkt,
                typ,
                b.getDestName(),
                String.valueOf(b.paxCount()),
                String.format("%.2f", amt),
                pay,
                ok ? "OK" : "FAIL"
            });
        }

        static void print() {
            System.out.println("\n==========================================");
            System.out.println("         TODAY'S SUMMARY                  ");
            System.out.println("==========================================");
            System.out.printf("  Total Transactions : %d%n", log.size());

            long   ok  = log.stream().filter(r -> "OK".equals(r[7])).count();
            double rev = log.stream().filter(r -> "OK".equals(r[7]))
                    .mapToDouble(r -> Double.parseDouble(r[5])).sum();

            System.out.printf("  Successful         : %d%n", ok);
            System.out.printf("  Cancelled/Failed   : %d%n", log.size() - ok);
            System.out.printf("  Total Revenue      : Rs. %.2f%n", rev);

            if (!log.isEmpty()) {
                System.out.println("\n  Recent Transactions:");
                System.out.printf("  %-20s %-18s %-8s %s%n",
                        "Ticket No", "Type", "Amount", "Status");
                System.out.println("  --------------------------------------------------------");
                int start = Math.max(0, log.size() - 5);
                for (int i = start; i < log.size(); i++) {
                    String[] r = log.get(i);
                    System.out.printf("  %-20s %-18s Rs.%-5s %s%n",
                            r[1], r[2], r[5], r[7]);
                }
            }
            System.out.println("==========================================\n");
        }
    }

    // -----------------------------------------------------------------------
    // Fare calculation
    // -----------------------------------------------------------------------

    /**
     * Returns the flat fare for a given distance in km.
     * BUG-FIX: km == 0 (source station) returns FARES[0] — this is intentional for
     *          Day Pass; for single/return the destination can never be 0.
     */
    private static int fareForDist(int km) {
        if (km <= 0) return FARES[0];
        for (int i = 0; i < SLABS.length; i++) {
            if (km <= SLABS[i]) return FARES[i];
        }
        return FARES[FARES.length - 1];
    }

    private static Fare calcFare(Booking b) {
        int    base     = fareForDist(b.getDist());
        double total    = 0;
        double discount = 0;

        if (b.type == TicketType.DAY_PASS) {
            total = DAY_PASS * b.paxCount();
            // no individual discounts on Day Pass
        } else {
            for (Passenger p : b.getPax()) {
                double discounted = base * p.getFactor();
                total    += discounted;
                discount += (base - discounted);
            }
            if (b.type == TicketType.RETURN) {
                total    *= 2;
                discount *= 2;
            }
        }

        return new Fare(base, total, discount);
    }

    // -----------------------------------------------------------------------
    // Ticket-number and utility helpers
    // -----------------------------------------------------------------------

    private static String newTicketNo() {
        return String.format("%s-%d-%05d",
                TICKET_PFX,
                System.currentTimeMillis() % 1_000_000,
                (int)(Math.random() * 100_000));
    }

    private static void pause(long ms) {
        try { Thread.sleep(ms); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    /** Normalise a date string to dd/MM/yyyy; returns null on failure. */
    private static String normalizeDate(String s) {
        if (s == null) return null;
        s = s.trim().replaceAll("\\s+", "");

        // Already well-formed?
        try {
            LocalDate.parse(s, DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            return s;
        } catch (DateTimeParseException ignored) {}

        // Strip non-digits and reconstruct
        String d = s.replaceAll("[^0-9]", "");
        if (d.length() != 8) return null;

        try {
            String f = d.substring(0, 2) + "/" + d.substring(2, 4) + "/" + d.substring(4, 8);
            LocalDate.parse(f, DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            return f;
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** Returns true only when the parsed date is today or in the future. */
    private static boolean validDate(String s) {
        if (s == null) return false;
        try {
            return !LocalDate.parse(s, DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                    .isBefore(LocalDate.now());
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private static String  ask(Scanner sc, String prompt) { System.out.print(prompt); return sc.nextLine().trim(); }
    private static boolean isCancelled(String s)          { return s.equalsIgnoreCase("C"); }
    private static void    err(String msg)                { System.out.println("  ERROR : " + msg); }

    private static void header(String title) {
        System.out.println("==========================================");
        System.out.println("   " + title);
        System.out.println("==========================================");
    }

    private static void cancelScreen(String reason) {
        System.out.println("\n==================================");
        System.out.println("  Status  : " + reason);
        System.out.println("  Thank you! Restarting in 3 seconds...");
        System.out.println("==================================");
    }

    // -----------------------------------------------------------------------
    // Booking steps
    // -----------------------------------------------------------------------

    /**
     * Prompts the user to choose Smart Card or Normal.
     * BUG-FIX: No dummy passenger is added here.  The real Passenger is only
     *          created after card details are validated in stepCardDetails().
     */
    private static boolean stepUserType(Scanner sc, Booking b) {
        System.out.println("  1. Smart Card User");
        System.out.println("  2. Normal User (Cash/UPI)");
        while (true) {
            String in = ask(sc, "  Enter [1-2] : ");
            if (isCancelled(in)) return false;
            if ("1".equals(in)) return true;   // card details collected next
            if ("2".equals(in)) return true;
            err("Select 1 or 2!");
        }
    }

    /**
     * BUG-FIX: Returns the user type so runBooking knows whether to call
     *          stepCardDetails.  Using a separate flag instead of inspecting
     *          the pax list avoids the dummy-passenger anti-pattern.
     */
    private static int promptUserType(Scanner sc) {
        System.out.println("  1. Smart Card User");
        System.out.println("  2. Normal User (Cash/UPI)");
        while (true) {
            String in = ask(sc, "  Enter [1-2] : ");
            if (isCancelled(in)) return 0;
            if ("1".equals(in))  return 1;
            if ("2".equals(in))  return 2;
            err("Select 1 or 2!");
        }
    }

    private static boolean stepCardDetails(Scanner sc, Booking b) {
        System.out.println("\n  Accepted formats: 12/04/2026 | 12-04-2026 | 12042026");
        System.out.println("  Type BACK to cancel\n");

        // -- Expiry date --
        while (true) {
            String in = ask(sc, "  Card Expiry Date : ");
            if (in.equalsIgnoreCase("BACK")) return false;
            if (in.isEmpty()) { err("Cannot be empty!"); continue; }

            String norm = normalizeDate(in);
            if (norm == null) { err("Invalid date format!"); continue; }

            boolean v = validDate(norm);
            System.out.println("  Card Status : " + (v ? "VALID" : "EXPIRED"));
            if (!v) {
                System.out.println("  Please use a valid / non-expired card.");
                return false;
            }
            break;
        }

        // -- Card holder age (determines discount category) --
        while (true) {
            String in = ask(sc, "  Card Holder Age  : ");
            if (in.equalsIgnoreCase("BACK")) return false;
            try {
                int age = Integer.parseInt(in);
                if (age < 1 || age > 120) { err("Age must be 1-120!"); continue; }

                b.clearPax();
                b.addPax(new Passenger(age, true));

                Passenger p = b.getPax().get(0);
                System.out.printf("  Category : %-18s | Discount : %.0f%% off%n",
                        p.getCatName(), (1 - p.getFactor()) * 100);
                return true;

            } catch (NumberFormatException e) {
                err("Enter a valid number!");
            }
        }
    }

    private static boolean stepTicketType(Scanner sc, Booking b) {
        System.out.println("  1. Single Journey");
        System.out.println("  2. Return Journey");
        System.out.println("  3. Day Pass  (Rs.100/person, All Stations)");

        while (true) {
            String in = ask(sc, "  Enter [1-3] : ");
            if (isCancelled(in)) return false;
            switch (in) {
                case "1": b.type = TicketType.SINGLE;   return true;
                case "2": b.type = TicketType.RETURN;   return true;
                case "3": b.type = TicketType.DAY_PASS; return true;
                default:  err("Invalid selection!");
            }
        }
    }

    private static boolean stepDestination(Scanner sc, Booking b) {
        if (b.type == TicketType.DAY_PASS) {
            b.setDest(0, "All Stations");
            System.out.println("\n  Day Pass - Valid at ALL stations today.");
            return true;
        }

        while (true) {
            System.out.printf("\n  %-4s %-22s %-10s %s%n", "No.", "Station", "Dist", "Fare");
            System.out.println("  --------------------------------------------");
            for (int i = 1; i < STATIONS.length; i++) {
                System.out.printf("  %2d. %-22s (%d km)     Rs.%d%n",
                        i, STATIONS[i], DIST[i], fareForDist(DIST[i]));
            }

            String in = ask(sc, "\n  Choose destination [1-" + (STATIONS.length - 1) + "] : ");
            if (isCancelled(in)) return false;

            try {
                int ch = Integer.parseInt(in);
                if (ch < 1 || ch >= STATIONS.length) { err("Invalid station!"); continue; }
                b.setDest(ch, STATIONS[ch]);
                return true;
            } catch (NumberFormatException e) {
                err("Enter a valid number!");
            }
        }
    }

    /**
     * BUG-FIX: Now returns boolean instead of void.
     * Previously the method called b.reset() internally and relied on the
     * caller checking paxCount() == 0, which was fragile and hid intent.
     * Returning false on cancel gives the caller clear, explicit control.
     */
    private static boolean stepPassengerCount(Scanner sc, Booking b) {
        if (b.hasCard()) {
            System.out.println("  Card Holder counted as 1 passenger.");
            while (true) {
                String in = ask(sc, "  Additional passengers [0-9] : ");
                if (isCancelled(in)) return false;
                try {
                    int n = Integer.parseInt(in);
                    if (n < 0 || n > 9) { err("Must be 0-9!"); continue; }
                    for (int i = 0; i < n; i++) b.addPax(new Passenger(30, false));
                    return true;
                } catch (NumberFormatException e) {
                    err("Enter a valid number!");
                }
            }
        } else {
            while (true) {
                String in = ask(sc, "  Number of passengers [1-10] : ");
                if (isCancelled(in)) return false;
                try {
                    int n = Integer.parseInt(in);
                    if (n < 1 || n > 10) { err("Must be 1-10!"); continue; }
                    b.clearPax();
                    for (int i = 0; i < n; i++) b.addPax(new Passenger(30, false));
                    return true;
                } catch (NumberFormatException e) {
                    err("Enter a valid number!");
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Fare display
    // -----------------------------------------------------------------------

    private static void printFareSummary(Booking b, Fare f) {
        System.out.println("\n  ---------- Fare Summary ----------");
        System.out.println("  Ticket    : " + b.type.label);
        System.out.println("  From      : " + SOURCE);
        System.out.println("  To        : " + b.getDestName());

        if (b.type == TicketType.DAY_PASS) {
            System.out.println("  Rate      : Rs.100 flat/person");
        } else {
            System.out.printf("  Slab      : Rs.%d/person%n", f.base);

            Passenger first = b.getPax().get(0);
            if (first.getFactor() < 1.0) {
                System.out.printf("  Discount  : %.0f%% off (%s)%n",
                        (1 - first.getFactor()) * 100, first.getCatName());
                // IMPROVEMENT: also show the actual rupee saving
                System.out.printf("  You save  : Rs. %.2f%n", f.discount);
            } else {
                System.out.println("  Discount  : None");
            }

            if (b.type == TicketType.RETURN) {
                System.out.println("  Multiplier: x2 (Return)");
            }
        }

        System.out.println("  Passengers: " + b.paxCount());
        System.out.println("  ----------------------------------");
        System.out.printf("  TOTAL     : Rs. %.2f%n", f.total);
        System.out.println("  ----------------------------------");
    }

    // -----------------------------------------------------------------------
    // Payment
    // -----------------------------------------------------------------------

    private static boolean doPayment(Scanner sc, Booking b, double amount) {
        System.out.printf("\n  Amount Due : Rs. %.2f%n%n", amount);
        System.out.println("  Payment Mode:");
        System.out.println("  1. Cash");
        System.out.println("  2. UPI / QR Code");
        System.out.println("  3. Debit / Credit Card");
        System.out.println("  4. Smart Card Balance");

        while (true) {
            String in = ask(sc, "  Enter [1-4] or C to cancel : ");
            if (isCancelled(in)) { b.reset(); return false; }

            Payment method;
            switch (in) {
                case "1": method = Payment.CASH;  break;
                case "2": method = Payment.UPI;   break;
                case "3": method = Payment.CARD;  break;
                case "4": method = Payment.SMART; break;
                default:  err("Invalid choice!"); continue;
            }

            boolean ok = (method == Payment.CASH)
                    ? doCash(sc, amount)
                    : doSimulate(sc, method.label, amount);

            if (ok) { b.payment = method; return true; }
            // Payment failed/retried — loop to let user pick another method
        }
    }

    private static boolean doCash(Scanner sc, double amount) {
        while (true) {
            System.out.printf("\n  Amount Due : Rs. %.2f%n", amount);
            String in = ask(sc, "  Insert Cash : Rs. ");
            if (isCancelled(in)) return false;
            try {
                double paid = Double.parseDouble(in);
                // BUG-FIX: reject negative / zero cash amounts
                if (paid <= 0) { err("Amount must be greater than 0!"); continue; }

                if (paid < amount) {
                    System.out.printf("  Short by Rs.%.2f | 1. Add more  2. Cancel%n", amount - paid);
                    if ("2".equals(ask(sc, "  Enter [1/2] : "))) return false;
                    continue;
                }

                System.out.println("\n  Cash accepted!");
                if (paid > amount) {
                    System.out.printf("  Change : Rs. %.2f%n", paid - amount);
                }
                return true;

            } catch (NumberFormatException e) {
                err("Enter a valid amount!");
            }
        }
    }

    private static boolean doSimulate(Scanner sc, String method, double amount) {
        System.out.printf("\n  [SIM] %s — Rs. %.2f%n", method, amount);
        System.out.println("  1. Success   2. Retry   3. Cancel");
        String ch = ask(sc, "  Enter [1-3] : ");
        if ("1".equals(ch)) { System.out.println("  " + method + " — Payment OK!"); return true; }
        if ("2".equals(ch)) return doSimulate(sc, method, amount);
        return false;
    }

    // -----------------------------------------------------------------------
    // Smart Card recharge
    // -----------------------------------------------------------------------

    private static boolean doRechargeSimulation(Scanner sc, String method, double amount) {
        while (true) {
            System.out.printf("\n  [SIM] %s Recharge Payment — Rs. %.2f%n", method, amount);
            System.out.println("  1. Success");
            System.out.println("  2. Retry");
            System.out.println("  3. Cancel");
            String ch = ask(sc, "  Enter [1-3] : ");
            if ("1".equals(ch)) { System.out.println("  " + method + " payment successful!"); return true; }
            if ("2".equals(ch)) { System.out.println("  Retrying " + method + " payment..."); continue; }
            if ("3".equals(ch) || isCancelled(ch)) return false;
            err("Invalid choice!");
        }
    }

    private static boolean addMoneyToSmartCard(Scanner sc) {
        System.out.println("\n==================================");
        System.out.println("   SMART CARD RECHARGE            ");
        System.out.println("==================================");
        System.out.println("  1. Add Rs.100");
        System.out.println("  2. Add Rs.200");
        System.out.println("  3. Add Rs.500");
        System.out.println("  4. Enter Custom Amount");
        System.out.println("  5. Cancel");

        while (true) {
            String choice = ask(sc, "  Enter [1-5] : ");
            if (isCancelled(choice) || "5".equals(choice)) return false;

            double amount;
            switch (choice) {
                case "1": amount = 100; break;
                case "2": amount = 200; break;
                case "3": amount = 500; break;
                case "4":
                    // BUG-FIX: 'amount' must be effectively-final for use inside lambda,
                    //          so we collect it via a local variable and use a flag.
                    double customAmt = 0;
                    boolean got = false;
                    while (!got) {
                        String in = ask(sc, "  Enter amount to add : Rs. ");
                        if (isCancelled(in)) return false;
                        try {
                            customAmt = Double.parseDouble(in);
                            // IMPROVEMENT: enforce a minimum recharge of Rs.10
                            if (customAmt < 10) { err("Minimum recharge is Rs.10!"); continue; }
                            got = true;
                        } catch (NumberFormatException e) {
                            err("Enter a valid amount!");
                        }
                    }
                    amount = customAmt;
                    break;
                default: err("Invalid choice!"); continue;
            }

            // Payment for the recharge
            while (true) {
                System.out.printf("\n  Recharge Amount : Rs. %.2f%n", amount);
                System.out.println("  Choose payment method:");
                System.out.println("  1. Cash");
                System.out.println("  2. UPI");
                System.out.println("  3. Card");
                System.out.println("  4. Cancel");

                String payChoice = ask(sc, "  Enter [1-4] : ");
                boolean recharged = false;

                switch (payChoice) {
                    case "1": recharged = doCash(sc, amount);                    break;
                    case "2": recharged = doRechargeSimulation(sc, "UPI",  amount); break;
                    case "3": recharged = doRechargeSimulation(sc, "Card", amount); break;
                    case "4":
                    default:
                        if ("4".equals(payChoice) || isCancelled(payChoice)) return false;
                        err("Invalid choice!"); continue;
                }

                if (recharged) {
                    System.out.printf("\n  Smart Card recharged successfully with Rs. %.2f%n", amount);
                    return true;
                }
                // Payment failed: loop back to let user retry or choose another method
            }
        }
    }

    // -----------------------------------------------------------------------
    // Print ticket
    // -----------------------------------------------------------------------

    /**
     * BUG-FIX: This method now records History itself on success/failure so
     *          that the history entry accurately reflects whether the ticket
     *          was physically printed (previously success was logged before
     *          printing, so a print failure was still recorded as "OK").
     */
    private static boolean doPrint(Scanner sc, Booking b, Fare f) {
        System.out.println("\n  [SIM] Printer check...");
        System.out.println("  1. Print OK   2. Print Failed");
        String sim = ask(sc, "  Simulate [1/2] : ");

        if ("1".equals(sim)) {
            b.ticketNo = newTicketNo();
            b.done     = true;
            printTicket(b, f);
            History.add(b, f, true);    // BUG-FIX: log success only after confirmed print
            return true;
        }

        System.out.println("\n  Printer error!");
        System.out.println("  1. Retry   2. Call Staff   3. Cancel");
        String ch = ask(sc, "  Enter [1-3] : ");

        if ("1".equals(ch)) return doPrint(sc, b, f);
        if ("2".equals(ch)) {
            System.out.println("  Helpline: " + HELPLINE);
            System.out.println("  NOTE: Payment was collected. Please collect your refund from staff.");
            pause(5000);
        }

        History.add(b, f, false);       // log failure when print ultimately not completed
        return false;
    }

    private static void printTicket(Booking b, Fare f) {
        System.out.println("\n==========================================");
        System.out.println("      NAVI MUMBAI METRO -- CIDCO         ");
        System.out.println("==========================================");
        System.out.printf("  Ticket No  : %s%n", b.ticketNo);
        System.out.printf("  Date       : %s%n", b.date);
        System.out.printf("  Time       : %s%n",
                LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        System.out.println("  ----------------------------------------");
        System.out.println("  From       : " + SOURCE);
        System.out.printf("  To         : %s%n", b.getDestName());
        if (b.type != TicketType.DAY_PASS) {
            System.out.printf("  Distance   : %d km%n", b.getDist());
        }
        System.out.println("  ----------------------------------------");
        System.out.printf("  Ticket     : %s%n", b.type.label);
        System.out.printf("  Passengers : %d%n", b.paxCount());

        int i = 1;
        for (Passenger p : b.getPax()) {
            System.out.printf("    #%d : %s (Age %d)%n", i++, p.getCatName(), p.age);
        }

        System.out.println("  ----------------------------------------");
        if (b.type == TicketType.DAY_PASS) {
            System.out.println("  Rate       : Rs.100/person (Day Pass)");
        } else {
            System.out.printf("  Base Fare  : Rs.%d/person%n", f.base);
            // IMPROVEMENT: show saved amount on ticket when applicable
            if (f.discount > 0) {
                System.out.printf("  Discount   : Rs. %.2f saved%n", f.discount);
            }
        }
        System.out.printf("  TOTAL PAID : Rs. %.2f%n", f.total);
        System.out.printf("  Paid Via   : %s%n", b.payment.label);
        System.out.println("==========================================");
        System.out.println("  Have a safe journey!");
        System.out.println("==========================================\n");
    }

    // -----------------------------------------------------------------------
    // Station info screen
    // -----------------------------------------------------------------------

    private static void stationInfo(Scanner sc) {
        while (true) {
            System.out.println("\n  1. All Stations");
            System.out.println("  2. Fare Chart");
            System.out.println("  3. Back");
            String in = ask(sc, "  Enter [1-3] : ");

            if ("1".equals(in)) {
                System.out.println("\n==========================================");
                System.out.printf("  %-4s %-22s %-10s %s%n", "No.", "Station", "Dist", "Fare");
                System.out.println("  --------------------------------------------");
                for (int i = 0; i < STATIONS.length; i++) {
                    // BUG-FIX: source station (i==0) showed Rs.10 due to fareForDist(0).
                    //          It should show "--" as there is no fare from source to source.
                    String dist = (i == 0) ? "(Source)" : "(" + DIST[i] + " km)";
                    String fare = (i == 0) ? "--"       : "Rs." + fareForDist(DIST[i]);
                    System.out.printf("  %-4d %-22s %-10s %s%n", i, STATIONS[i], dist, fare);
                }
                System.out.println("==========================================");

            } else if ("2".equals(in)) {
                System.out.println("\n==========================================");
                System.out.println("              FARE CHART                  ");
                System.out.println("==========================================");
                System.out.println("  Distance      | Fare");
                System.out.println("  --------------|-------");
                System.out.println("  Up to 3 km    | Rs.10");
                System.out.println("  3 - 6 km      | Rs.15");
                System.out.println("  6 - 9 km      | Rs.20");
                System.out.println("  9 - 11 km     | Rs.25");
                System.out.println("  Above 11 km   | Rs.30");
                System.out.println("  Day Pass      | Rs.100/person");
                System.out.println("  --------------|-------");
                System.out.println("  Card Discounts:");
                // BUG-FIX: was "Student (<25)" — STUDENT_MAX is 24, so correct label is "<=24"
                System.out.println("    Student (age <=24) : 75% off");
                System.out.println("    Senior  (age >=60) : 40% off");
                System.out.println("==========================================");

            } else if ("3".equals(in)) {
                return;
            } else {
                err("Invalid choice!");
            }
        }
    }

    // -----------------------------------------------------------------------
    // Main booking flow
    // -----------------------------------------------------------------------

    private static void runBooking(Scanner sc) {
        Booking b    = new Booking();
        Fare    fare = null;

        try {
            header("Metro Ticket -- New Booking");
            System.out.println("  Press C at any prompt to cancel.\n");

            // BUG-FIX: Replaced stepUserType (which pre-loaded a dummy passenger)
            //          with promptUserType that returns an int flag (0=cancel,1=card,2=normal).
            int userType = promptUserType(sc);
            if (userType == 0) {
                cancelScreen("Cancelled");
                pause(DELAY_MS);
                History.add(b, null, false);
                return;
            }

            boolean isCardUser = (userType == 1);

            if (isCardUser) {
                if (!stepCardDetails(sc, b)) {
                    cancelScreen("Card Error");
                    pause(DELAY_MS);
                    History.add(b, null, false);
                    return;
                }
                System.out.println("  Discount applies to card holder only.");
            }

            header("Metro Ticket -- Route");
            System.out.println("  Press C at any prompt to cancel.\n");

            if (!stepTicketType(sc, b)) {
                cancelScreen("Cancelled");
                pause(DELAY_MS);
                History.add(b, null, false);
                return;
            }

            if (!stepDestination(sc, b)) {
                cancelScreen("Cancelled");
                pause(DELAY_MS);
                History.add(b, null, false);
                return;
            }

            // BUG-FIX: stepPassengerCount now returns boolean; no more silent b.reset() inside.
            if (!stepPassengerCount(sc, b)) {
                cancelScreen("Cancelled");
                pause(DELAY_MS);
                History.add(b, null, false);
                return;
            }

            fare = calcFare(b);
            printFareSummary(b, fare);

            String confirm = ask(sc, "  Confirm and proceed to payment? [Y/N] : ");
            if (!confirm.equalsIgnoreCase("Y")) {
                cancelScreen("Cancelled");
                pause(DELAY_MS);
                History.add(b, fare, false);
                return;
            }

            if (!doPayment(sc, b, fare.total)) {
                cancelScreen("Payment Cancelled");
                pause(DELAY_MS);
                History.add(b, fare, false);
                return;
            }

            // BUG-FIX: History is now recorded inside doPrint(), AFTER the outcome is
            //          known (success or failure).  We no longer log "OK" here before
            //          knowing whether the ticket was successfully printed.
            if (!doPrint(sc, b, fare)) {
                cancelScreen("Print Failed");
                pause(DELAY_MS);
            }

        } catch (IllegalArgumentException e) {
            System.out.println("  ERROR : " + e.getMessage());
            History.add(b, fare, false);
        }
    }

    // -----------------------------------------------------------------------
    // Entry point
    // -----------------------------------------------------------------------

    public static void main(String[] args) {
        System.out.println("\n==========================================");
        System.out.println("        NAVI MUMBAI METRO SYSTEM          ");
        System.out.println("==========================================");
        System.out.println("  Source Station : " + SOURCE);
        System.out.println("  Version        : " + VERSION);
        System.out.println("==========================================\n");

        try (Scanner sc = new Scanner(System.in)) {
            while (true) {
                System.out.println("==========================================");
                System.out.println("              MAIN MENU                   ");
                System.out.println("==========================================");
                System.out.println("  1. Book New Ticket");
                System.out.println("  2. Add Money to Smart Card");
                System.out.println("  3. Station Information");
                System.out.println("  4. Today's Summary");
                System.out.println("  5. Exit");

                String in = ask(sc, "  Enter [1-5] : ");

                if (in.equalsIgnoreCase("EXIT") || "5".equals(in)) {
                    System.out.println("\n  Thank you for travelling with " + APP_NAME + ". Goodbye!\n");
                    break;
                }

                switch (in) {
                    case "1": runBooking(sc);          break;
                    case "2": addMoneyToSmartCard(sc); break;
                    case "3": stationInfo(sc);         break;
                    case "4": History.print();         break;
                    default:  err("Select 1-5!");
                }

                pause(400);
            }
        }
    }
}