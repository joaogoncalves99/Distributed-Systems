package pt.tecnico.bicloin.hub.hubDataTypes;

public class Station {
    private String stationID;

    private String stationName;
    private double latitureCoord;
    private double longitudeCoord;
    private int dockTotal;
    private int returnPrize;
    private String availableBikesId = "station_nbikes_";
    private String totalRequestsId = "station_nrequests_";
    private String totalReturnsId = "station_nreturns_";

    public Station(String stationID, String stationName, double latitureCoord, double longitudeCoord, int dockTotal, int returnPrize) {
        this.stationID = stationID;
        this.stationName = stationName;
        this.latitureCoord = latitureCoord;
        this.longitudeCoord = longitudeCoord;
        this.dockTotal = dockTotal;
        this.returnPrize = returnPrize;
        this.availableBikesId += stationID;
        this.totalRequestsId += stationID;
        this.totalReturnsId += stationID;
    }

    public String GetStationID() {
        return stationID;
    }

    public String GetStationName() {
        return stationName;
    }

    public double GetLatitureCoord() {
        return latitureCoord;
    }

    public double GetLongitudeCoord() {
        return longitudeCoord;
    }

    public int GetDockTotal() {
        return dockTotal;
    }

    public int GetReturnPrize() {
        return returnPrize;
    }

    public String GetAvailableBikesID() {
        return availableBikesId;
    }

    public String GetTotalRequestsID() {
        return totalRequestsId;
    }

    public String GetTotalReturnsID() {
        return totalReturnsId;
    }
}
