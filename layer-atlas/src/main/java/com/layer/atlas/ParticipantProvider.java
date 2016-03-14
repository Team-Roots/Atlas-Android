package com.layer.atlas;

import android.util.Log;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class ParticipantProvider implements Atlas.ParticipantProvider {

    private final Map<String, Participant> participantMap =
            new HashMap<String, Participant>();



    public void refresh(List<Participant> participants) {
        Log.d("ParticipantProvider", "refresh called.");
                    //Populate counselors with counselors from parse
                    for (Participant participant:participants) {
                        participantMap.put(participant.getID(), participant);
                        Log.d("ParticipantProvider", "Participant with id of " + participant.getID() + " added to map.");
                    }
    }




    public Map<String, Participant> getCustomParticipants(String filter, Map<String, Participant> result) {
        if (result == null) {
            result = new HashMap<String, Participant>();
        }

        if (filter == null) {
            for (Participant p : participantMap.values()) {
                result.put(p.getID(), p);
            }
            return result;
        }

        for (Participant p : participantMap.values()) {
            if (p.getFirstName() != null && !p.getFirstName().toLowerCase().contains(filter)) {
                result.remove(p.getID());
                continue;
            }

            result.put(p.getID(), p);
        }

        return result;
    }

    public Map<String, Atlas.Participant> getParticipants(String filter, Map<String,
            Atlas.Participant> result) {
        if (result == null) {
            result = new HashMap<String, Atlas.Participant>();
        }

        if (filter == null) {
            for (Participant p : participantMap.values()) {
                result.put(p.getID(), p);
            }
            return result;
        }

        for (Participant p : participantMap.values()) {
            if (p.getFirstName() != null && !p.getFirstName().toLowerCase().contains(filter)) {
                result.remove(p.getID());
                continue;
            }

            result.put(p.getID(), p);
        }

        return result;
    }

    @Override
    public Participant getParticipant(String userId) {
        Log.d("ParticipantProvider","getParticipant called");
        Log.d("ParticipantProvider","requested userID=="+userId);
        Log.d("ParticipantProvider","map is "+participantMap.toString());
        Log.d("ParticipantProvider","participantMap.get(userId)=="+participantMap.get(userId));
        return participantMap.get(userId);
    }

    // Returns an array of type Participant
    public Participant[] getCustomParticipants() {
        return participantMap.values().toArray(new Participant[]{});
    }
}
