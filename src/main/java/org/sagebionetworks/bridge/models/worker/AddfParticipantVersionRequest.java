package org.sagebionetworks.bridge.models.worker;

/**
 * Worker request to export a participant version to the ADDF pipeline. Same shape as
 * {@link Ex3ParticipantVersionRequest} ({appId, healthCode, participantVersion}), but kept as a distinct type so the
 * ADDF export pipeline stays an independent sibling of Exporter 3.0.
 */
public class AddfParticipantVersionRequest {
    private String appId;
    private String healthCode;
    private int participantVersion;

    /** App ID of the participant version to export. */
    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    /** Health code of the participant version to export. */
    public String getHealthCode() {
        return healthCode;
    }

    public void setHealthCode(String healthCode) {
        this.healthCode = healthCode;
    }

    /** Version number of the participant version to export. */
    public int getParticipantVersion() {
        return participantVersion;
    }

    public void setParticipantVersion(int participantVersion) {
        this.participantVersion = participantVersion;
    }
}
