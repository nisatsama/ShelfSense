# Initial ERD

```mermaid
erDiagram

    PRODUCT ||--o{ INVENTORY : "has"

    LOCATION ||--o{ INVENTORY : "stores"

    PRODUCT {
        bigint id PK
        varchar sku UK
        varchar name
        varchar category
        decimal unit_price
        boolean active
        timestamp created_at
        timestamp updated_at
        bigint version
    }

    LOCATION {
        bigint id PK
        varchar code UK
        varchar name
        varchar type
        varchar address
        timestamp created_at
    }

    INVENTORY {
        bigint id PK
        bigint product_id FK
        bigint location_id FK
        integer on_hand
        integer reserved
        bigint version
        timestamp updated_at
    }
```

## Relationships

### Product → Inventory

One Product can have many Inventory records.

### Location → Inventory

One Location can have many Inventory records.

### Product + Location → Inventory

The combination of `product_id` and `location_id` must be unique.

This guarantees that a SKU has only one inventory state at a particular location.

## Inventory Invariants

```text
on_hand >= 0
reserved >= 0
reserved <= on_hand
UNIQUE(product_id, location_id)
```

Available inventory is derived:

```text
available = on_hand - reserved
```
