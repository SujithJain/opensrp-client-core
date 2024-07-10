package org.smartregister.sync.intent;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.util.Pair;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.smartregister.AllConstants;
import org.smartregister.CoreLibrary;
import org.smartregister.R;
import org.smartregister.SyncConfiguration;
import org.smartregister.domain.FetchStatus;
import org.smartregister.domain.Response;
import org.smartregister.domain.db.EventClient;
import org.smartregister.receiver.SyncStatusBroadcastReceiver;
import org.smartregister.repository.EventClientRepository;
import org.smartregister.service.HTTPAgent;
import org.smartregister.sync.helper.ECSyncHelper;
import org.smartregister.util.NetworkUtils;
import org.smartregister.util.SyncUtils;
import org.smartregister.util.Utils;
import org.smartregister.view.activity.DrishtiApplication;

import java.text.MessageFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

import timber.log.Timber;

public class SyncIntentService extends BaseSyncIntentService {
    public static final String ADD_URL = "/rest/event/add";
    public static final String SYNC_URL = "/rest/event/sync";

    protected Context context;
    protected HTTPAgent httpAgent;
    protected SyncUtils syncUtils;

    protected static final int EVENT_PULL_LIMIT = 250;
    protected static final int EVENT_PUSH_LIMIT = 50;

    public SyncIntentService() {
        super("SyncIntentService");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        context = getBaseContext();
        httpAgent = CoreLibrary.getInstance().context().getHttpAgent();
        syncUtils = new SyncUtils(getBaseContext());
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        super.onHandleIntent(intent);
        handleSync();
    }

    protected void handleSync() {
        sendSyncStatusBroadcastMessage(FetchStatus.fetchStarted);

        doSync();
    }

    private void doSync() {
        if (!NetworkUtils.isNetworkAvailable()) {
            complete(FetchStatus.noConnection);
            return;
        }

        try {
            boolean hasValidAuthorization = syncUtils.verifyAuthorization();
            if (hasValidAuthorization || !CoreLibrary.getInstance().getSyncConfiguration().disableSyncToServerIfUserIsDisabled()) {
                pushToServer();
            }
            if (hasValidAuthorization) {
                pullECFromServer();
            } else {
                syncUtils.logoutUser();
            }

        } catch (Exception e) {
            Timber.e(e);
            complete(FetchStatus.fetchedFailed);
        }
    }


    private void pullECFromServer() {
        fetchRetry(0);
    }

    protected synchronized void fetchRetry(final int count) {
        try {
            SyncConfiguration configs = CoreLibrary.getInstance().getSyncConfiguration();
            if (configs.getSyncFilterParam() == null || StringUtils.isBlank(configs.getSyncFilterValue())) {
                complete(FetchStatus.fetchedFailed);
                return;
            }

            final ECSyncHelper ecSyncUpdater = ECSyncHelper.getInstance(context);
            String baseUrl = CoreLibrary.getInstance().context().
                    configuration().dristhiBaseURL();
            if (baseUrl.endsWith("/")) {
                baseUrl = baseUrl.substring(0, baseUrl.lastIndexOf("/"));
            }

            long lastSyncDatetime = ecSyncUpdater.getLastSyncTimeStamp();
            Timber.i("LAST SYNC DT %s", new DateTime(lastSyncDatetime));
            Utils.appendLog("SYNC_URL","from library:"+lastSyncDatetime);

            if (httpAgent == null) {
                complete(FetchStatus.fetchedFailed);
                return;
            }

            String url = baseUrl + SYNC_URL;
            Response resp;
            if (configs.isSyncUsingPost()) {
                JSONObject syncParams = new JSONObject();
                syncParams.put(configs.getSyncFilterParam().value(), configs.getSyncFilterValue());
                syncParams.put("serverVersion", lastSyncDatetime);
                syncParams.put("limit",  getEventPullLimit());
                resp = httpAgent.postWithJsonResponse(url,syncParams.toString());
            } else {
                url += "?" + configs.getSyncFilterParam().value() + "=" + configs.getSyncFilterValue() + "&serverVersion=" + lastSyncDatetime + "&limit=" + getEventPullLimit();
                Timber.i("URL: %s", url);
                resp = httpAgent.fetch(url);
            }
            Utils.appendLog("SYNC_URL","from library url:"+url);
            if (resp.isUrlError()) {
                FetchStatus.fetchedFailed.setDisplayValue(resp.status().displayValue());
                complete(FetchStatus.fetchedFailed);
                return;
            }

            if (resp.isTimeoutError()) {
                FetchStatus.fetchedFailed.setDisplayValue(resp.status().displayValue());
                complete(FetchStatus.fetchedFailed);
                return;
            }

            if (resp.isFailure() && !resp.isUrlError() && !resp.isTimeoutError()) {
                fetchFailed(count);
                return;
            }

            JSONObject jsonObject = new JSONObject((String) resp.payload());

            int eCount = fetchNumberOfEvents(jsonObject);
            Utils.appendLog("SYNC_URL","from library response come:"+eCount);

            if (eCount == 0) {
                complete(FetchStatus.nothingFetched);
            } else if (eCount < 0) {
                fetchFailed(count);
            } else if (eCount > 0) {
                final Pair<Long, Long> serverVersionPair = getMinMaxServerVersions(jsonObject);
                long lastServerVersion = serverVersionPair.second - 1;
                if (eCount < getEventPullLimit()) {
                    lastServerVersion = serverVersionPair.second;
                }

                boolean isSaved = ecSyncUpdater.saveAllClientsAndEvents(jsonObject);
                //update sync time if all event client is save.
                if(isSaved){
                    processClient(serverVersionPair);
                    ecSyncUpdater.updateLastSyncTimeStamp(lastServerVersion);
                }
                fetchRetry(0);
            }
        } catch (Exception e) {
            Timber.e(e, "Fetch Retry Exception:  %s", e.getMessage());
            fetchFailed(count);
        }
    }

    public void fetchFailed(int count) {
        if (count < CoreLibrary.getInstance().getSyncConfiguration().getSyncMaxRetries()) {
            int newCount = count + 1;
            fetchRetry(newCount);
        } else {
            complete(FetchStatus.fetchedFailed);
        }
    }

    protected void processClient(Pair<Long, Long> serverVersionPair) {
        try {
            ECSyncHelper ecUpdater = ECSyncHelper.getInstance(context);
            Utils.appendLog("SYNC_URL", "processClient:minimum"+(serverVersionPair.first - 1)+":maximum:"+serverVersionPair.second);

            List<EventClient> events = ecUpdater.allEventClients(serverVersionPair.first - 1, serverVersionPair.second);
            DrishtiApplication.getInstance().getClientProcessor().processClient(events);
            sendSyncStatusBroadcastMessage(FetchStatus.fetched);
        } catch (Exception e) {
            Timber.e(e, "Process Client Exception: %s", e.getMessage());
        }
    }

    // PUSH TO SERVER
    private void pushToServer() {
        pushECToServer();
    }

    protected void pushECToServer() {
        EventClientRepository db = CoreLibrary.getInstance().context().getEventClientRepository();
        boolean keepSyncing = true;

        while (keepSyncing) {
            try {
                Map<String, Object> pendingEvents = db.getUnSyncedEvents(EVENT_PUSH_LIMIT);

                if (pendingEvents.isEmpty()) {
                    return;
                }

                String baseUrl = CoreLibrary.getInstance().context().configuration().dristhiBaseURL();
                if (baseUrl.endsWith(context.getString(R.string.url_separator))) {
                    baseUrl = baseUrl.substring(0, baseUrl.lastIndexOf(context.getString(R.string.url_separator)));
                }
                // create request body
                JSONObject request = new JSONObject();
                if (pendingEvents.containsKey(AllConstants.KEY.CLIENTS)) {
                    request.put(AllConstants.KEY.CLIENTS, pendingEvents.get(AllConstants.KEY.CLIENTS));
                }
                if (pendingEvents.containsKey(AllConstants.KEY.EVENTS)) {
                    request.put(AllConstants.KEY.EVENTS, pendingEvents.get(AllConstants.KEY.EVENTS));
                }
                String jsonPayload = request.toString();
                Response<String> response = httpAgent.post(
                        MessageFormat.format("{0}/{1}",
                                baseUrl,
                                ADD_URL),
                        jsonPayload);
                if (response.isFailure() || response.isTimeoutError()) {
                    Timber.e("Events sync failed.");
                    return;
                }
                db.markEventsAsSynced(pendingEvents);
                Timber.i("Events synced successfully.");
            } catch (Exception e) {
                Timber.e(e);
            }
        }
    }

    protected void sendSyncStatusBroadcastMessage(FetchStatus fetchStatus) {
        Intent intent = new Intent();
        intent.setAction(SyncStatusBroadcastReceiver.ACTION_SYNC_STATUS);
        intent.putExtra(SyncStatusBroadcastReceiver.EXTRA_FETCH_STATUS, fetchStatus);
        sendBroadcast(intent);
    }

    protected void complete(FetchStatus fetchStatus) {
        Intent intent = new Intent();
        intent.setAction(SyncStatusBroadcastReceiver.ACTION_SYNC_STATUS);
        intent.putExtra(SyncStatusBroadcastReceiver.EXTRA_FETCH_STATUS, fetchStatus);
        intent.putExtra(SyncStatusBroadcastReceiver.EXTRA_COMPLETE_STATUS, true);

        sendBroadcast(intent);
        //sync time not update if sync is fail
        if (!fetchStatus.equals(FetchStatus.noConnection) && !fetchStatus.equals(FetchStatus.fetchedFailed)) {
            ECSyncHelper ecSyncUpdater = ECSyncHelper.getInstance(context);
            ecSyncUpdater.updateLastCheckTimeStamp(new Date().getTime());
        }

    }

    protected Pair<Long, Long> getMinMaxServerVersions(JSONObject jsonObject) {
        final String EVENTS = "events";
        final String SERVER_VERSION = "serverVersion";
        try {
            if (jsonObject != null && jsonObject.has(EVENTS)) {
                JSONArray events = jsonObject.getJSONArray(EVENTS);

                long maxServerVersion = Long.MIN_VALUE;
                long minServerVersion = Long.MAX_VALUE;

                for (int i = 0; i < events.length(); i++) {
                    Object o = events.get(i);
                    if (o instanceof JSONObject) {
                        JSONObject jo = (JSONObject) o;
                        if (jo.has(SERVER_VERSION)) {
                            long serverVersion = jo.getLong(SERVER_VERSION);
                            if (serverVersion > maxServerVersion) {
                                maxServerVersion = serverVersion;
                            }

                            if (serverVersion < minServerVersion) {
                                minServerVersion = serverVersion;
                            }
                        }
                    }
                }
                return Pair.create(minServerVersion, maxServerVersion);
            }
        } catch (Exception e) {
            Timber.e(e);
        }
        return Pair.create(0L, 0L);
    }

    protected int fetchNumberOfEvents(JSONObject jsonObject) {
        int count = -1;
        final String NO_OF_EVENTS = "no_of_events";
        try {
            if (jsonObject != null && jsonObject.has(NO_OF_EVENTS)) {
                count = jsonObject.getInt(NO_OF_EVENTS);
            }
        } catch (JSONException e) {
            Timber.e(e);
        }
        return count;
    }

    public int getEventPullLimit() {
        return EVENT_PULL_LIMIT;
    }
}
