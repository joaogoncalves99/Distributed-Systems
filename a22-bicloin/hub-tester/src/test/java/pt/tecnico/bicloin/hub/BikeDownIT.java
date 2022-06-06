package pt.tecnico.bicloin.hub;

import com.google.type.Money;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pt.tecnico.bicloin.hub.grpc.Hub;

import static io.grpc.Status.*;
import static org.junit.jupiter.api.Assertions.*;

public class BikeDownIT extends BaseIT {

    public void returnBike() {
        Hub.BikeDownRequest request = Hub.BikeDownRequest.newBuilder()
                .setUserID("alice")
                .setUserLatitude(38.7376)
                .setUserLongitude(-9.3031)
                .setStationID("istt").build();
        frontend.bikeDown(request);
    }

    @BeforeEach
    public void setupBike() {
        Hub.TopUpRequest topUpRequest = Hub.TopUpRequest.newBuilder()
                .setUserID("alice")
                .setUserPhoneNumber("+35191102030")
                .setUserChargeAmount(Money.newBuilder().setUnits(15).setCurrencyCode("EUR")).build();
        frontend.topUp(topUpRequest);

        Hub.BikeUpRequest request = Hub.BikeUpRequest.newBuilder()
                .setUserID("alice")
                .setUserLatitude(38.7376)
                .setUserLongitude(-9.3031)
                .setStationID("istt").build();
        frontend.bikeUp(request);
    }

    @AfterEach
    public void resetUser() {
        Hub.ClearUserDataRequest clearRequest = Hub.ClearUserDataRequest.newBuilder()
                .setUserID("alice").build();
        Hub.ClearUserDataResponse response = frontend.clearUserData(clearRequest);
    }

    @Test
    public void bikeDownOKTest(){
        Hub.BikeDownRequest request = Hub.BikeDownRequest.newBuilder()
                .setUserID("alice")
                .setUserLatitude(38.7376)
                .setUserLongitude(-9.3031)
                .setStationID("istt").build();
        Hub.BikeDownResponse response = frontend.bikeDown(request);

        assertTrue(response.getRequestAccepted());
    }

    @Test
    public void emptyIDTest(){
        Hub.BikeDownRequest request = Hub.BikeDownRequest.newBuilder()
                .setUserID("")
                .setUserLatitude(38.7376)
                .setUserLongitude(-9.3031)
                .setStationID("istt").build();

        assertEquals(
                INVALID_ARGUMENT.getCode(),
                assertThrows(
                        StatusRuntimeException.class, () -> frontend.bikeDown(request))
                        .getStatus()
                        .getCode());

        bikeDownOKTest();
    }

    @Test
    public void invalidCoordinatesTest(){
        Hub.BikeDownRequest request = Hub.BikeDownRequest.newBuilder()
                .setUserID("alice")
                .setUserLatitude(138.7376)
                .setUserLongitude(-9.3031)
                .setStationID("istt").build();

        assertEquals(
                INVALID_ARGUMENT.getCode(),
                assertThrows(
                        StatusRuntimeException.class, () -> frontend.bikeDown(request))
                        .getStatus()
                        .getCode());

        bikeDownOKTest();
    }

    @Test
    public void invalidStationTest(){
        Hub.BikeDownRequest request = Hub.BikeDownRequest.newBuilder()
                .setUserID("alice")
                .setUserLatitude(38.7376)
                .setUserLongitude(-9.3031)
                .setStationID("aeio").build();

        assertEquals(
                NOT_FOUND.getCode(),
                assertThrows(
                        StatusRuntimeException.class, () -> frontend.bikeDown(request))
                        .getStatus()
                        .getCode());

        bikeDownOKTest();
    }

    @Test
    public void highDistanceFailTest(){
        Hub.BikeDownRequest request = Hub.BikeDownRequest.newBuilder()
                .setUserID("alice")
                .setUserLatitude(38.7376)
                .setUserLongitude(-9.3031)
                .setStationID("ista").build();

        assertEquals(
                PERMISSION_DENIED.getCode(),
                assertThrows(
                        StatusRuntimeException.class, () -> frontend.bikeDown(request))
                        .getStatus()
                        .getCode());

        bikeDownOKTest();
    }

    @Test
    public void consecutiveBikeDownTest(){
        Hub.BikeDownRequest request = Hub.BikeDownRequest.newBuilder()
                .setUserID("alice")
                .setUserLatitude(38.7376)
                .setUserLongitude(-9.3031)
                .setStationID("istt").build();
        frontend.bikeDown(request);

        Hub.BikeDownRequest request2 = Hub.BikeDownRequest.newBuilder()
                .setUserID("alice")
                .setUserLatitude(38.7376)
                .setUserLongitude(-9.3031)
                .setStationID("istt").build();

        assertEquals(
                PERMISSION_DENIED.getCode(),
                assertThrows(
                        StatusRuntimeException.class, () -> frontend.bikeDown(request2))
                        .getStatus()
                        .getCode());
    }
}
