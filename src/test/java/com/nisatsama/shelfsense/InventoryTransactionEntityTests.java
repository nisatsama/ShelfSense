package com.nisatsama.shelfsense;

import com.nisatsama.shelfsense.inventory.Inventory;
import com.nisatsama.shelfsense.inventory.InventoryTransaction;
import com.nisatsama.shelfsense.inventory.InventoryTransactionType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryTransactionEntityTests {

    @Test
    void acceptsEveryDefinedTransactionDeltaShape() {
        Inventory inventory = new Inventory();
        List<ValidShape> shapes = List.of(
                new ValidShape(InventoryTransactionType.INITIAL_STOCK, 0, 0, null),
                new ValidShape(InventoryTransactionType.INITIAL_STOCK, 8, 0, null),
                new ValidShape(InventoryTransactionType.RESERVATION_CREATED, 0, 2, 11L),
                new ValidShape(InventoryTransactionType.RESERVATION_RELEASED, 0, -2, 11L),
                new ValidShape(InventoryTransactionType.RESERVATION_CONFIRMED, -2, -2, 11L),
                new ValidShape(InventoryTransactionType.PURCHASE_RECEIVED, 2, 0, null),
                new ValidShape(InventoryTransactionType.SALE, -2, 0, null),
                new ValidShape(InventoryTransactionType.TRANSFER_OUT, -2, 0, null),
                new ValidShape(InventoryTransactionType.TRANSFER_IN, 2, 0, null),
                new ValidShape(InventoryTransactionType.RETURN, 2, 0, null),
                new ValidShape(InventoryTransactionType.ADJUSTMENT, -2, 0, null)
        );

        assertThatCode(() -> shapes.forEach(shape -> InventoryTransaction.create(
                inventory,
                shape.type(),
                shape.onHandDelta(),
                shape.reservedDelta(),
                shape.reservationId()
        ))).doesNotThrowAnyException();
    }

    @Test
    void rejectsInvalidDeltaShapeAndReservationReference() {
        Inventory inventory = new Inventory();

        assertThatThrownBy(() -> InventoryTransaction.create(
                inventory, InventoryTransactionType.RESERVATION_CREATED, 1, 2, 11L
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> InventoryTransaction.create(
                inventory, InventoryTransactionType.RESERVATION_CONFIRMED, -2, -1, 11L
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> InventoryTransaction.create(
                inventory, InventoryTransactionType.RESERVATION_RELEASED, 0, -2, null
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> InventoryTransaction.create(
                inventory, InventoryTransactionType.SALE, -2, 1, null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void exposesNoPublicMutationMethods() {
        assertThat(InventoryTransaction.class.getMethods())
                .noneMatch(method -> method.getName().startsWith("set"));
    }

    private record ValidShape(
            InventoryTransactionType type,
            int onHandDelta,
            int reservedDelta,
            Long reservationId
    ) {
    }
}
