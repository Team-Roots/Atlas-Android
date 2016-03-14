package com.layer.atlas;

import android.util.Log;

public class Participant implements Atlas.Participant {

    private String name;
    private String ID;
    private String avatarString;
    private String bio;
    private boolean isAvailable;
    private int counselorType;

    public Participant(String localName, String localID, String localAvatarString, String localBio, boolean localIsAvailable, int localCounselorType) {
        name = localName;
        ID=localID;
        Log.d("Participant","Participant created with an id of "+ID);
        avatarString=localAvatarString;
        bio=localBio;
        isAvailable = localIsAvailable;
        counselorType=localCounselorType;
    }

    public String getFirstName() { return name.split(" ")[0]; }
    public String getLastName() { return name.split(" ")[1]; }
    public String getName() {return name; }
    public String getID(){ return ID;}
    public String getBio() {return bio; }
    public String getAvatarString(){ return avatarString;}
    public int getCounselorType(){ return counselorType;}

    public boolean getIsAvailable() {
        return isAvailable;
    }

    public void setAvailable(boolean available){
        isAvailable=available;
    }
}

