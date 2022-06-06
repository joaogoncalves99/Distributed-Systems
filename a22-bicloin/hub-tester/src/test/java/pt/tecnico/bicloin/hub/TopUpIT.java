package pt.tecnico.bicloin.hub;

import com.google.type.Money;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.Test;
import pt.tecnico.bicloin.hub.grpc.Hub;

import static io.grpc.Status.INVALID_ARGUMENT;
import static io.grpc.Status.NOT_FOUND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TopUpIT extends BaseIT{
    @Test
    public void topUpOKTest() {
        Hub.TopUpRequest request = Hub.TopUpRequest.newBuilder()
                .setUserID("alice")
                .setUserPhoneNumber("+35191102030")
                .setUserChargeAmount(Money.newBuilder().setUnits(15).setCurrencyCode("EUR")).build();

        Hub.TopUpResponse response = frontend.topUp(request);
        assertEquals(150, response.getUserBicloinBalance());
    }

    @Test
    public void emptyIDTest() {
        Hub.TopUpRequest request = Hub.TopUpRequest.newBuilder()
                .setUserID("")
                .setUserPhoneNumber("+35191102030")
                .setUserChargeAmount(Money.newBuilder().setUnits(15).setCurrencyCode("EUR")).build();

        assertEquals(
                INVALID_ARGUMENT.getCode(),
                assertThrows(
                        StatusRuntimeException.class, () -> frontend.topUp(request))
                        .getStatus()
                        .getCode());
    }

    @Test
    public void unknownIDTest() {
        Hub.TopUpRequest request = Hub.TopUpRequest.newBuilder()
                .setUserID("heyo")
                .setUserPhoneNumber("+35191102030")
                .setUserChargeAmount(Money.newBuilder().setUnits(15).setCurrencyCode("EUR")).build();

        assertEquals(
                NOT_FOUND.getCode(),
                assertThrows(
                        StatusRuntimeException.class, () -> frontend.topUp(request))
                        .getStatus()
                        .getCode());
    }

    @Test
    public void invalidMoneyAmountTest() {
        Hub.TopUpRequest request = Hub.TopUpRequest.newBuilder()
                .setUserID("alice")
                .setUserPhoneNumber("+35191102030")
                .setUserChargeAmount(Money.newBuilder().setUnits(50).setCurrencyCode("EUR")).build();

        assertEquals(
                INVALID_ARGUMENT.getCode(),
                assertThrows(
                        StatusRuntimeException.class, () -> frontend.topUp(request))
                        .getStatus()
                        .getCode());
    }
}
