package pt.tecnico.bicloin.hub;

import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.Test;
import pt.tecnico.bicloin.hub.grpc.Hub;

import static io.grpc.Status.INVALID_ARGUMENT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class LocateStationsIT extends BaseIT{
    final private double latitude = 38.7380;
    final private double longitude = -9.3000;
    final private int amount = 3;

    @Test
    public void locateStationsTest() {
        Hub.LocateStationRequest request = Hub.LocateStationRequest.newBuilder()
                .setUserLatitude(latitude)
                .setUserLongitude(longitude)
                .setAmountToDisplay(amount).build();
        Hub.LocateStationResponse response = frontend.locateStation(request);

        assertEquals(response.getStationID(0), "istt");
        assertEquals(response.getStationID(1), "stao");
        assertEquals(response.getStationID(2), "jero");
    }

    @Test
    public void invalidCoordinatesTest() {
        Hub.LocateStationRequest request = Hub.LocateStationRequest.newBuilder()
                .setUserLatitude(latitude + 90)
                .setUserLongitude(longitude)
                .setAmountToDisplay(amount).build();
        assertEquals(
                INVALID_ARGUMENT.getCode(),
                assertThrows(
                        StatusRuntimeException.class, () -> frontend.locateStation(request))
                        .getStatus()
                        .getCode());
    }

    @Test
    public void invalidScanAmountTest() {
        Hub.LocateStationRequest request = Hub.LocateStationRequest.newBuilder()
                .setUserLatitude(latitude)
                .setUserLongitude(longitude)
                .setAmountToDisplay(-1).build();
        assertEquals(
                INVALID_ARGUMENT.getCode(),
                assertThrows(
                        StatusRuntimeException.class, () -> frontend.locateStation(request))
                        .getStatus()
                        .getCode());
    }
}
