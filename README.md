# ATVM Metro Ticket Vending Machine Simulation

A Finite State Machine–based simulation of an Automatic Ticket Vending Machine (ATVM) for metro ticket booking, implemented in Java with an HTML‑based frontend.

## What this project does
- Models the ATVM as a **state machine** (e.g., `idle`, `select_station`, `payment`, `ticket_issue`, `error`).
- Handles user input such as station selection, fare calculation, and payment confirmation.
- Prints a simulated ticket with details like source, destination, fare, and timestamp.

## Tech stack
- **Core logic:** Java (Swing / console‑style interaction)
- **Frontend / UI:** HTML page (`ATVM_Pro_Combined.html`) for user instructions and flow overview
- **Pattern:** Finite State Machine (FSM) design for state transitions

## How to run
1. Clone the repo:
   ```bash
   git clone https://github.com/hardikrawat910-byte/atvm-metro-ticket.git
   ```
2. Compile the Java file:
   ```bash
   javac MetroTicket.java
   ```
3. Run the program:
   ```bash
   java MetroTicket
   ```
4. Open `ATVM_Pro_Combined.html` in the browser to follow the ATVM flow visually.

## What I learned
- How to design and implement a **state machine** for real‑world systems.
- Basic UI‑like flow design using HTML to guide the user through the ATVM steps.
- Structuring a console‑based Java program for modular, readable code.

## Notes
- This is a **first‑year academic project** created as part of my engineering coursework.
- The idea and structure of the FSM and ticket‑issuing logic were designed by me.
