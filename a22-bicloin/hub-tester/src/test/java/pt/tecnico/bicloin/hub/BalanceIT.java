package pt.tecnico.bicloin.hub;

import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.Test;
import pt.tecnico.bicloin.hub.grpc.Hub;

import static io.grpc.Status.INVALID_ARGUMENT;
import static io.grpc.Status.NOT_FOUND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class BalanceIT extends BaseIT{
    @Test
    public void balanceOKTest() {
        Hub.BalanceRequest request = Hub.BalanceRequest.newBuilder().setUserID("alice").build();
        Hub.BalanceResponse response = frontend.balance(request);
        assertEquals(0, response.getUserBicloinBalance());
    }

    @Test
    public void emptyIDTest() {
        Hub.BalanceRequest request = Hub.BalanceRequest.newBuilder().setUserID("").build();
        assertEquals(
                INVALID_ARGUMENT.getCode(),
                assertThrows(
                        StatusRuntimeException.class, () -> frontend.balance(request))
                        .getStatus()
                        .getCode());
    }

    @Test
    public void unknownIDTest() {
        Hub.BalanceRequest request = Hub.BalanceRequest.newBuilder().setUserID("heyo").build();
        assertEquals(
                NOT_FOUND.getCode(),
                assertThrows(
                        StatusRuntimeException.class, () -> frontend.balance(request))
                        .getStatus()
                        .getCode());
    }
}
