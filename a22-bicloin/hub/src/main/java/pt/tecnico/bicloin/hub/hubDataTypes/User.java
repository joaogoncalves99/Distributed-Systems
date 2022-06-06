package pt.tecnico.bicloin.hub.hubDataTypes;

public class User {
    private String userID;
    private String userName;
    private String userPhoneNumber;
    private String bicloinBalanceId = "user_nbicloins_";
    private String hasBikeID = "user_hasbike_";

    public User(String userID, String userName, String userPhoneNumber) {
        this.userID = userID;
        this.userName = userName;
        this.userPhoneNumber = userPhoneNumber;
        this.bicloinBalanceId += userID;
        this.hasBikeID += userID;
    }

    public String GetID() {
        return userID;
    }

    public String GetName() {
        return userName;
    }

    public String GetPhoneNumber() {
        return userPhoneNumber;
    }

    public String GetBicloinBalanceID() {
        return bicloinBalanceId;
    }

    public String GetHasBikeID() {
        return hasBikeID;
    }
}
