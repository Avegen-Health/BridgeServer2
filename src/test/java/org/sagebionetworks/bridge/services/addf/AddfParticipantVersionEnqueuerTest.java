package org.sagebionetworks.bridge.services.addf;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.SendMessageResult;
import org.mockito.ArgumentCaptor;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.worker.AddfParticipantVersionRequest;
import org.sagebionetworks.bridge.models.worker.WorkerRequest;

/**
 * The participant-version fan-out (Phase 2b) carries the same two invariants as the upload fan-out — gated by the
 * shared {@code addf.export.enabled} kill-switch, and never able to fail the account-update path — plus one of its own:
 * the version number must survive the round-trip as an int, because it is half the {@code participant_versions} primary
 * key every activity row's FK resolves against (§3b.5).
 */
public class AddfParticipantVersionEnqueuerTest {
    private static final String ADDF_QUEUE_URL = "http://example.com/addf-queue";
    private static final String HEALTH_CODE = "test-health-code";
    private static final int PARTICIPANT_VERSION = 42;

    private BridgeConfig mockConfig;
    private AmazonSQS mockSqsClient;
    private AddfParticipantVersionEnqueuer enqueuer;

    @BeforeMethod
    public void before() {
        mockConfig = mock(BridgeConfig.class);
        mockSqsClient = mock(AmazonSQS.class);
        when(mockConfig.getProperty(BridgeConstants.CONFIG_KEY_ADDF_SQS_URL)).thenReturn(ADDF_QUEUE_URL);
        when(mockSqsClient.sendMessage(anyString(), anyString())).thenReturn(new SendMessageResult());

        enqueuer = new AddfParticipantVersionEnqueuer();
        enqueuer.setConfig(mockConfig);
        enqueuer.setSqsClient(mockSqsClient);
    }

    @Test
    public void enabledSendsAddfWorkerRequest() throws Exception {
        when(mockConfig.get(BridgeConstants.CONFIG_KEY_ADDF_ENABLED)).thenReturn("true");

        enqueuer.enqueue(TestConstants.TEST_APP_ID, HEALTH_CODE, PARTICIPANT_VERSION);

        ArgumentCaptor<String> requestJsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockSqsClient).sendMessage(eq(ADDF_QUEUE_URL), requestJsonCaptor.capture());

        WorkerRequest workerRequest = BridgeObjectMapper.get().readValue(requestJsonCaptor.getValue(),
                WorkerRequest.class);
        assertEquals(workerRequest.getService(),
                AddfParticipantVersionEnqueuer.WORKER_NAME_ADDF_PARTICIPANT_VERSION);

        AddfParticipantVersionRequest addfRequest = BridgeObjectMapper.get().convertValue(workerRequest.getBody(),
                AddfParticipantVersionRequest.class);
        assertEquals(addfRequest.getAppId(), TestConstants.TEST_APP_ID);
        assertEquals(addfRequest.getHealthCode(), HEALTH_CODE);
        assertEquals(addfRequest.getParticipantVersion(), PARTICIPANT_VERSION);
    }

    @Test
    public void killSwitchOffSendsNothing() {
        // One switch gates both fan-outs: turning ADDF off must stop versions as well as uploads, or the dimension
        // table would keep growing against an export store no longer receiving activity.
        when(mockConfig.get(BridgeConstants.CONFIG_KEY_ADDF_ENABLED)).thenReturn("false");

        enqueuer.enqueue(TestConstants.TEST_APP_ID, HEALTH_CODE, PARTICIPANT_VERSION);

        verify(mockSqsClient, never()).sendMessage(anyString(), anyString());
        verify(mockConfig, never()).getProperty(BridgeConstants.CONFIG_KEY_ADDF_SQS_URL);
    }

    @Test
    public void absentKillSwitchSendsNothing() {
        when(mockConfig.get(BridgeConstants.CONFIG_KEY_ADDF_ENABLED)).thenReturn(null);

        enqueuer.enqueue(TestConstants.TEST_APP_ID, HEALTH_CODE, PARTICIPANT_VERSION);

        verify(mockSqsClient, never()).sendMessage(anyString(), anyString());
    }

    @Test
    public void sqsFailureIsLoggedAndSwallowed() {
        when(mockConfig.get(BridgeConstants.CONFIG_KEY_ADDF_ENABLED)).thenReturn("true");
        doThrow(new AmazonServiceException("SQS is down")).when(mockSqsClient).sendMessage(anyString(), anyString());

        // Must return normally — an ADDF outage must not break account updates.
        enqueuer.enqueue(TestConstants.TEST_APP_ID, HEALTH_CODE, PARTICIPANT_VERSION);

        verify(mockSqsClient).sendMessage(eq(ADDF_QUEUE_URL), any(String.class));
    }
}
