package pt.tecnico.bicloin.hub;

import com.google.type.Money;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import pt.tecnico.bicloin.hub.grpc.Hub;

import static io.grpc.Status.*;
import static org.junit.jupiter.api.Assertions.*;

public class BikeUpIT extends BaseIT{
    private void moneySetup(int value) {
        Hub.TopUpRequest topUpRequest = Hub.TopUpRequest.newBuilder()
                .setUserID("alice")
                .setUserPhoneNumber("+35191102030")
                .setUserChargeAmount(Money.newBuilder().setUnits(value).setCurrencyCode("EUR")).build();
        frontend.topUp(topUpRequest);
    }

    public void returnBike() {
        Hub.BikeDownRequest request = Hub.BikeDownRequest.newBuilder()
                .setUserID("alice")
                .setUserLatitude(38.7376)
                .setUserLongitude(-9.3031)
                .setStationID("istt").build();
        Hub.BikeDownResponse response = frontend.bikeDown(request);
    }

    @AfterEach
    public void resetUserBalance() {
        Hub.ClearUserDataRequest clearRequest = Hub.ClearUserDataRequest.newBuilder()
                .setUserID("alice").build();
        Hub.ClearUserDataResponse response = frontend.clearUserData(clearRequest);
    }

    @Test
    public void bikeUpOKTest(){
        moneySetup(15);
        Hub.BikeUpRequest request = Hub.BikeUpRequest.newBuilder()
                .setUserID("alice")
                .setUserLatitude(38.7376)
                .setUserLongitude(-9.3031)
                .setStationID("istt").build();
        Hub.BikeUpResponse response = frontend.bikeUp(request);

        assertTrue(response.getRequestAccepted());

        returnBike();
    }

    @Test
    public void emptyIDTest(){
        moneySetup(15);
        Hub.BikeUpRequest request = Hub.BikeUpRequest.newBuilder()
                .setUserID("")
                .setUserLatitude(38.7376)
                .setUserLongitude(-9.3031)
                .setStationID("istt").build();

        assertEquals(
                INVALID_ARGUMENT.getCode(),
                assertThrows(
                        StatusRuntimeException.class, () -> frontend.bikeUp(request))
                        .getStatus()
                        .getCode());
    }

    @Test
    public void invalidCoordinatesTest(){
        moneySetup(15);
        Hub.BikeUpRequest request = Hub.BikeUpRequest.newBuilder()
                .setUserID("alice")
                .setUserLatitude(138.7376)
                .setUserLongitude(-9.3031)
                .setStationID("istt").build();

        assertEquals(
                INVALID_ARGUMENT.getCode(),
                assertThrows(
                        StatusRuntimeException.class, () -> frontend.bikeUp(request))
                        .getStatus()
                        .getCode());
    }

    @Test
    public void invalidStationTest(){
        moneySetup(15);
        Hub.BikeUpRequest request = Hub.BikeUpRequest.newBuilder()
                .setUserID("alice")
                .setUserLatitude(38.7376)
                .setUserLongitude(-9.3031)
                .setStationID("aeio").build();

        assertEquals(
                NOT_FOUND.getCode(),
                assertThrows(
                        StatusRuntimeException.class, () -> frontend.bikeUp(request))
                        .getStatus()
                        .getCode());

    }

    @Test
    public void insufficientBalanceTest(){
        Hub.BikeUpRequest request = Hub.BikeUpRequest.newBuilder()
                .setUserID("alice")
                .setUserLatitude(38.7376)
                .setUserLongitude(-9.3031)
                .setStationID("istt").build();

        assertEquals(
                PERMISSION_DENIED.getCode(),
                assertThrows(
                        StatusRuntimeException.class, () -> frontend.bikeUp(request))
                        .getStatus()
                        .getCode());
    }

    @Test
    public void highDistanceFailTest(){
        moneySetup(15);
        Hub.BikeUpRequest request = Hub.BikeUpRequest.newBuilder()
                .setUserID("alice")
                .setUserLatitude(38.7376)
                .setUserLongitude(-9.3031)
                .setStationID("ista").build();

        assertEquals(
                PERMISSION_DENIED.getCode(),
                assertThrows(
                        StatusRuntimeException.class, () -> frontend.bikeUp(request))
                        .getStatus()
                        .getCode());
    }

    @Test
    public void consecutiveBikeUpTest(){
        moneySetup(15);
        Hub.BikeUpRequest request = Hub.BikeUpRequest.newBuilder()
                .setUserID("alice")
                .setUserLatitude(38.7376)
                .setUserLongitude(-9.3031)
                .setStationID("istt").build();
        frontend.bikeUp(request);

        Hub.BikeUpRequest request2 = Hub.BikeUpRequest.newBuilder()
                .setUserID("alice")
                .setUserLatitude(38.7376)
                .setUserLongitude(-9.3031)
                .setStationID("istt").build();

        assertEquals(
                PERMISSION_DENIED.getCode(),
                assertThrows(
                        StatusRuntimeException.class, () -> frontend.bikeUp(request2))
                        .getStatus()
                        .getCode());

        returnBike();
    }
}
