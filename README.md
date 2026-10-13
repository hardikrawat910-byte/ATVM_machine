# ATVM: Metro Ticket Vending Machine Simulator

A simulation of an Automatic Ticket Vending Machine (ATVM) for Navi Mumbai Metro Line 1 (Belapur Terminal to Pendhar, 11 stations). It comes in two versions: a Java console program, and a browser version that shows the machine's states as you move through a booking.

I made this while exploring ideas for my second-semester mini project. It was built with help from an AI assistant (Perplexity): I set the idea, the fare rules and the flow, and used the assistant to write and debug the code.

## What it does

- **Choose a ticket:** single journey, return journey or day pass (₹100)
- **Pick a destination:** fares are based on distance slabs (₹10 to ₹30)
- **Discounts:** student, senior citizen and smart-card holder
- **Pay:** cash with change, UPI/QR, card, or smart card, all simulated. Smart cards can be recharged.
- **Get a ticket:** a ticket number, source, destination, fare and timestamp are printed
- **Cancel:** press `C` at any prompt to cancel safely. Each booking is kept in a session history.

## Files

| File | What it is |
| --- | --- |
| `MetroTicket.java` | Console version (Java, single file, uses `Scanner`) |
| `ATVM_Pro_Combined.html` | Browser version with a touch-style interface. It shows the current machine state (IDLE → MENU → … → printing → GOODBYE). |

## How to run

**Java version** (JDK 17 or newer):

```bash
git clone https://github.com/hardikrawat910-byte/ATVM_machine.git
cd ATVM_machine
javac MetroTicket.java
java MetroTicket
```

**Browser version:** open `ATVM_Pro_Combined.html` in any browser. No setup is needed.

## What I learned

- Breaking a real machine's behaviour into **states** and the **transitions** between them (a finite state machine)
- Handling cancel and error paths at every step, not just the "happy path"
- Modelling fares, discounts and payment types with Java `enum`s and small classes

## Author

**Hardik Rawat**, B.Tech Electronics & Telecommunication, SIES Graduate School of Technology · [GitHub](https://github.com/hardikrawat910-byte)
