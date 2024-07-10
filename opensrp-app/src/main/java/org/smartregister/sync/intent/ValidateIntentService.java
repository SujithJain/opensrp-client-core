package org.smartregister.sync.intent;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.smartregister.AllConstants;
import org.smartregister.CoreLibrary;
import org.smartregister.R;
import org.smartregister.domain.Response;
import org.smartregister.repository.EventClientRepository;
import org.smartregister.service.HTTPAgent;
import org.smartregister.util.Utils;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by keyman on 11/10/2017.
 */
public class ValidateIntentService extends BaseSyncIntentService {

    public static final String ACTION_VALIDATION = "validation_action";
    public static final String EXTRA_VALIDATION = "validation_extra";
    public static final String STATUS_FAILED = "failed";
    public static final String STATUS_NOTHING = "nothing";
    public static final String STATUS_SUCCESS = "success";
    private Context context;
    private HTTPAgent httpAgent;
    private static final int FETCH_LIMIT = 100;
    private static final String VALIDATE_SYNC_PATH = "rest/validate/sync";

    public ValidateIntentService() {
        super("ValidateIntentService");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        context = getBaseContext();
        httpAgent = CoreLibrary.getInstance().context().getHttpAgent();
        return super.onStartCommand(intent, flags, startId);

    }

    @Override
    protected void onHandleIntent(Intent intent) {

        try {
            super.onHandleIntent(intent);
            int fetchLimit = FETCH_LIMIT;
            EventClientRepository db = CoreLibrary.getInstance().context().getEventClientRepository();

            List<String> clientIds = db.getUnValidatedClientBaseEntityIds(fetchLimit);
            if (!clientIds.isEmpty()) {
                fetchLimit -= clientIds.size();
            }

            List<String> eventIds = new ArrayList<>();
            if (fetchLimit > 0) {
                eventIds = db.getUnValidatedEventFormSubmissionIds(fetchLimit);
            }
            Utils.appendLog("SYNC_URL","ValidateIntentService>clientIds.size():"+clientIds.size()+":event size:"+eventIds.size());


            JSONObject request = request(clientIds, eventIds);
            if (request == null) {
                broadcastStatus(STATUS_NOTHING);
                return;
            }
            Utils.appendLog("SYNC_URL","ValidateIntentService>request:"+request);

            String baseUrl = CoreLibrary.getInstance().context().configuration().dristhiBaseURL();
            if (baseUrl.endsWith(context.getString(R.string.url_separator))) {
                baseUrl = baseUrl.substring(0, baseUrl.lastIndexOf(context.getString(R.string.url_separator)));
            }

            String jsonPayload = request.toString();
            Response<String> response = httpAgent.postWithJsonResponse(
                    MessageFormat.format("{0}/{1}",
                            baseUrl,
                            VALIDATE_SYNC_PATH),
                    jsonPayload);
            if (response.isFailure() || response.isTimeoutError()) {
                broadcastStatus(STATUS_FAILED);
                return;
            }
            if(StringUtils.isBlank(response.payload())){
                broadcastStatus(STATUS_NOTHING);
                return;
            }
            Utils.appendLog("SYNC_URL","ValidateIntentService>response:"+response.payload());

            JSONObject results = new JSONObject(response.payload());

            if (results.has(AllConstants.KEY.CLIENTS)) {
                JSONArray inValidClients = results.getJSONArray(AllConstants.KEY.CLIENTS);

                for (int i = 0; i < inValidClients.length(); i++) {
                    String inValidClientId = inValidClients.getString(i);
                    clientIds.remove(inValidClientId);
                    db.markClientValidationStatus(inValidClientId, false);
                }

                for (String clientId : clientIds) {
                    db.markClientValidationStatus(clientId, true);
                }
            }

            if (results.has(AllConstants.KEY.EVENTS)) {
                JSONArray inValidEvents = results.getJSONArray(AllConstants.KEY.EVENTS);
                for (int i = 0; i < inValidEvents.length(); i++) {
                    String inValidEventId = inValidEvents.getString(i);
                    eventIds.remove(inValidEventId);
                    db.markEventValidationStatus(inValidEventId, false);
                }

                for (String eventId : eventIds) {
                    db.markEventValidationStatus(eventId, true);
                }
            }
            broadcastStatus(STATUS_SUCCESS);
        } catch (Exception e) {
            Log.e(getClass().getName(), "", e);
        }
    }

    private JSONObject request(List<String> clientIds, List<String> eventIds) {
        try {

            JSONArray clientIdArray = null;
            if (!clientIds.isEmpty()) {
                clientIdArray = new JSONArray(clientIds);
            }

            JSONArray eventIdArray = null;
            if (!eventIds.isEmpty()) {
                eventIdArray = new JSONArray(eventIds);
            }

            if (clientIdArray != null || eventIdArray != null) {
                JSONObject request = new JSONObject();
                if (clientIdArray != null) {
                    request.put(AllConstants.KEY.CLIENTS, clientIdArray);
                }

                if (eventIdArray != null) {
                    request.put(AllConstants.KEY.EVENTS, eventIdArray);
                }

                return request;

            }

        } catch (Exception e) {
            Log.e(getClass().getName(), "", e);
        }
        return null;
    }
    private void broadcastStatus(String message){
        Intent broadcastIntent = new Intent(ACTION_VALIDATION);
        broadcastIntent.putExtra(EXTRA_VALIDATION, message);
        sendBroadcast(broadcastIntent);
    }

}
