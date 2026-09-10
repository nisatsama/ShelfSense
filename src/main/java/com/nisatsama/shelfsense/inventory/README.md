# Inventory Domain

## Purpose

The Inventory domain tracks the quantity of each product available at each physical location.

A location can be:

* STORE
* WAREHOUSE
* DISTRIBUTION_CENTER

## Inventory State

For each `(product, location)` pair, ShelfSense maintains:

* `onHand` — physical quantity currently held at the location
* `reserved` — quantity temporarily committed to active reservations

Available inventory is derived as:

`available = onHand - reserved`

`available` is not independently maintained as a source of truth.

## Core Invariants

1. `onHand >= 0`
2. `reserved >= 0`
3. `reserved <= onHand`
4. A product can have only one inventory record per location.
5. Inventory mutations must occur inside a database transaction.
6. Every inventory mutation must create an immutable inventory transaction record.
7. Concurrent reservations must never allow available inventory to become negative.

## Example

Product:

`SHAMPOO-250`

Location:

`STORE-KOL-01`

```text
onHand   = 100
reserved = 17
available = 83
```

If a customer reserves 5 units:

```text
onHand   = 100
reserved = 22
available = 78
```

If the reservation is cancelled:

```text
onHand   = 100
reserved = 17
available = 83
```

## Inventory Mutation Types

The system will eventually support:

* PURCHASE_RECEIVED
* SALE
* RESERVATION_CREATED
* RESERVATION_RELEASED
* RESERVATION_CONFIRMED
* TRANSFER_OUT
* TRANSFER_IN
* ADJUSTMENT
* RETURN
