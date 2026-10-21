# Car Rental - Facade over a Three-Stage Chain (Assignment 4)

One client operation - **rent a car** - drives three independent subsystems:

| Step | Subsystem | Classes | Persisted in |
|------|-----------|---------|--------------|
| A | `fleet` - reserve a car and price it | `FleetAvailabilityTracker`, `PricingEngine` (logic), `Reservation` (entity), `Car`, `ReservationRepository` (gateway) | `data/fleet.db` |
| B | `billing` - insurance + deposit | `DepositHandler`, `InsuranceCalculator` (logic), `Deposit`, `InsurancePolicy` (entities), `BillingRepository` (gateway) | `data/billing.db` |
| C | `handover` - inspection + GPS | `DamageInspector`, `GpsTracker`, `DamageRules` (logic), `Inspection` (entity), `HandoverRepository` (gateway) | `data/handover.db` |

Facades: `RentalFacade` (client workflow) and `RentalAdminFacade` (inspect / manual compensation / restart recovery).
Chain progress is stored in `data/orders.db` (`STARTED -> PENDING_B -> PENDING_C -> COMPLETED | COMPENSATED | FAILED`).

## Build, run, test (one command each)

```
mvn -q compile exec:java      # run the console demo
mvn test                      # run tests + JaCoCo coverage -> target/site/jacoco/index.html
```

**Reset to a clean state:** stop the app and delete the `data/` folder (`rm -rf data`).

## Realistic failures

| Scenario | How it happens | What the facade undoes |
|---|---|---|
| **B fails after A** | Card balance < deposit + insurance (`DepositDeclinedException`) | **A only** - reservation `RELEASED` |
| **C fails after A and B** | Inspector finds damage (`DamageFoundException`) **or** GPS pool is empty (`GpsUnavailableException`) | **B, then A** - deposit `REFUNDED`, policy `CANCELLED`, reservation `RELEASED` (+ inspection `VOIDED` if it had passed) |
| A fails (no commit) | No free car of that type (`NoCarAvailableException`) | nothing; order `FAILED` |
| Invalid input | Bad dates, age < 18, blank name... (`InvalidRentalRequestException`) | nothing is even persisted |

Console menu options 7, 8, 9, 10 trigger these on demand; option 11 simulates a crash after step B.
Restart the app: on startup it reads `orders.db`, sees `PENDING_C`, and undoes C leftovers, B, then A.
All compensation methods are **idempotent** (they act on what is on disk, keyed by `orderRef`).

## Principle of Least Knowledge - before / after

**Before (no facade): the client has many friends and knows the internal protocol and undo order.**

```java
Car-related:   Reservation r = fleetTracker.reserve(orderId, type, from, to);
               double price = r.getPrice();
Billing:       long days = PricingEngine.days(from, to);
               depositHandler.authorize(orderId, price, days, age, coverage, balance);   // may throw
Handover:      damageInspector.inspect(orderId, r.getCarId(), score);                    // may throw
               gpsTracker.activate(orderId);                                             // may throw
// on failure the client must remember: void inspection, release GPS, refund deposit, release reservation - in that order
fleetTracker.confirm(orderId);  depositHandler.activate(orderId);
```
The client talks to 4 collaborators of 3 subsystems and must know the compensation rules.

**After (with facade): one immediate friend.**

```java
RentalOrder order = rentalFacade.rentCar(request);
```
The client knows only `RentalFacade` (and the request/response data objects).

## Facade vs. Adapter

An **Adapter** exists to make *one* incompatible interface look like the interface a client already expects; its intent is *translation*.
Our problem is different: the client has no expected interface to match. Three unrelated subsystems (`fleet`, `billing`, `handover`) each have a fine interface of their own, but together they form one business transaction that needs ordering, persisted progress and partial rollback (A alone, or B then A). The intent here is *simplification and coordination of a group of subsystems behind one entry point*, which is exactly a **Facade**. Wrapping each subsystem in an adapter would still leave the client to sequence the calls and undo failures itself, so the Least-Knowledge problem would remain.

## Tests (F.I.R.S.T.)

* Fast / Independent / Repeatable: every test uses its own `@TempDir` SQLite files; no shared state or order dependence.
* Self-validating: all assertions are programmatic.
* Domain-specific language: `RentalDsl`, e.g. `rental.aRental().withCardBalance(10).bookExpectingFailure(DepositDeclinedException.class); rental.thenLatestOrderIs(COMPENSATED).andReservationIs(RELEASED).andNoDepositExists();`
* Both compensation depths: `whenBFailsAfterAOnlyAIsCompensated`, `whenCFailsBecauseOfDamageBThenAAreCompensated`, `whenCFailsBecauseGpsPoolIsEmpty...`
* Persistence reload: `restartWhileChainIsPendingCUndoesBThenA` (reopens the stores via `restartApplication()` and recovers from persisted status).
* Coverage: JaCoCo (`mvn test`, report in `target/site/jacoco/index.html`).

## Standalone subsystems

`rental.fleet.FleetMain`, `rental.billing.BillingMain`, `rental.handover.HandoverMain` each use one subsystem without the facade or the other two.
# Assignment 5: Strategy Pattern

## Class Responsibilities
* **InsuranceStrategy**: Defines the contract for premium calculation.
* **BasicInsuranceStrategy**: Computes standard premium with young driver surcharge.
* **FullInsuranceStrategy**: Computes comprehensive premium applying long duration discount.
* **DynamicWeatherInsuranceStrategy**: Computes premium multiplied by external weather warnings.
* **InsuranceContext**: Delegates premium calculation to the injected strategy.
* **InsuranceSelector**: Chooses the correct premium algorithm matching policy coverage.
* **WeatherService**: Simulates external weather data retrieval.
* **PricingStrategy**: Defines the contract for rental price calculation.
* **StandardPricingStrategy**: Computes total price multiplying daily rate with days.
* **LongTermPricingStrategy**: Computes total price applying duration discounts.
* **HighDemandPricingStrategy**: Computes total price multiplied by external demand factor.
* **PricingContext**: Delegates price calculation to the injected strategy.
* **PricingSelector**: Chooses the correct pricing algorithm matching car type.
* **FleetDemandService**: Simulates external market demand retrieval.

## Strategy vs State vs Template Method
The Strategy pattern was selected over State and Template Method because the algorithms representing pricing models need to be entirely interchangeable at runtime via composition without depending on object state transitions. The State pattern is inappropriate here because it encapsulates behavior triggered by transitioning through discrete internal states. Our calculation models are stateless operations returning a computed numeric value. The Template Method relies heavily on inheritance to redefine specific steps of a hardcoded skeletal algorithm. Strategy relies on composition, allowing the Context to remain completely oblivious to the calculation specifics, ensuring seamless integration of future calculation models without modifying existing base classes.

## Refactoring Commits
* [Before Refactoring (if-else statements)]77d54d9e05b3d327a8532d90f8ca28fb3798f152
* [After Refactoring (Strategy Pattern applied)]d1d7a13fff1b6bdb90d3ffbc78b742a58563b2cf
