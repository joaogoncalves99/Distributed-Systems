package pt.tecnico.bicloin.hub;

import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.Test;
import pt.tecnico.bicloin.hub.grpc.Hub;

import static io.grpc.Status.INVALID_ARGUMENT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class StationInfoIT extends BaseIT{
    @Test
    public void stationInfoTest() {
        Hub.StationInfoRequest request = Hub.StationInfoRequest.newBuilder()
                .setStationID("istt").build();

        Hub.StationInfoResponse response = frontend.infoStation(request);

        assertEquals("IST Taguspark", response.getStationName());
        assertEquals(38.7372, response.getStationLatitude());
        assertEquals(-9.3023, response.getStationLongitude());
        assertEquals(20, response.getStationDockCapacity());
        assertEquals(4, response.getStationPrize());
        assertEquals(12, response.getStationAvailableBikes());
    }

    @Test
    public void emptyIDTest() {
        Hub.StationInfoRequest request = Hub.StationInfoRequest.newBuilder()
                .setStationID("").build();
        assertEquals(
                INVALID_ARGUMENT.getCode(),
                assertThrows(
                        StatusRuntimeException.class, () -> frontend.infoStation(request))
                        .getStatus()
                        .getCode());
    }
}
