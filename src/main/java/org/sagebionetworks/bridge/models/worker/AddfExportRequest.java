package org.sagebionetworks.bridge.models.worker;

/**
 * Worker request for the ADDF export. Same shape as {@link Exporter3Request} ({appId, recordId}), but kept as a
 * distinct type so the ADDF export pipeline stays an independent sibling of Exporter 3.0 and the two contracts can
 * evolve separately.
 */
public class AddfExportRequest {
    private String appId;
    private String recordId;

    /** App ID of the record to be exported. */
    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    /** Record ID of the record to be exported. */
    public String getRecordId() {
        return recordId;
    }

    public void setRecordId(String recordId) {
        this.recordId = recordId;
    }
}
