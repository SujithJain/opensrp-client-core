package org.smartregister.sync.helper;

import android.content.Context;
import android.support.annotation.VisibleForTesting;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import org.joda.time.DateTime;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.smartregister.CoreLibrary;
import org.smartregister.domain.Response;
import org.smartregister.domain.Task;
import org.smartregister.domain.TaskUpdate;
import org.smartregister.exception.NoHttpResponseException;
import org.smartregister.repository.AllSharedPreferences;
import org.smartregister.repository.BaseRepository;
import org.smartregister.repository.TaskRepository;
import org.smartregister.service.HTTPAgent;
import org.smartregister.util.DateTimeTypeConverter;
import org.smartregister.util.Utils;

import java.text.MessageFormat;
import java.util.List;
import java.util.Set;

import timber.log.Timber;

public class TaskServiceHelper {

    private AllSharedPreferences allSharedPreferences = CoreLibrary.getInstance().context().allSharedPreferences();

    protected final Context context;
    private TaskRepository taskRepository;
    public static final String TASK_LAST_SYNC_DATE = "TASK_LAST_SYNC_DATE";
    public static final String UPDATE_STATUS_URL = "/rest/task/update_status";
    public static final String ADD_TASK_URL = "/rest/task/add";
    public static final String SYNC_TASK_URL = "/rest/task/sync";

    public static final Gson taskGson = new GsonBuilder().registerTypeAdapter(DateTime.class, new DateTimeTypeConverter("yyyy-MM-dd'T'HHmm")).create();

    protected static TaskServiceHelper instance;

    public static TaskServiceHelper getInstance() {
        if (instance == null) {
            instance = new TaskServiceHelper(CoreLibrary.getInstance().context().getTaskRepository());
        }
        return instance;
    }

    @VisibleForTesting
    public TaskServiceHelper(TaskRepository taskRepository) {
        this.context = CoreLibrary.getInstance().context().applicationContext();
        this.taskRepository = taskRepository;
    }

    public List<Task> syncTasks() {
        syncCreatedTaskToServer();
        syncTaskStatusToServer();
        return fetchTasksFromServer();
    }

    protected List<String> getLocationIds() {
        return CoreLibrary.getInstance().context().getLocationRepository().getAllLocationIds();
    }

    protected Set<String> getPlanDefinitionIds() {
        return CoreLibrary.getInstance().context().getPlanDefinitionRepository().findAllPlanDefinitionIds();
    }

    public List<Task> fetchTasksFromServer() {
        Set<String> planDefinitions = getPlanDefinitionIds();
        List<String> groups = getLocationIds();
        long serverVersion = 0;
        try {
            serverVersion = Long.parseLong(allSharedPreferences.getPreference(TASK_LAST_SYNC_DATE));
        } catch (NumberFormatException e) {
            Timber.e(e, "EXCEPTION %s", e.toString());
        }
        if (serverVersion > 0) serverVersion += 1;
        try {
            Long maxServerVersion = 0l;
            String tasksResponse = fetchTasks(planDefinitions, groups, serverVersion);
            List<Task> tasks = taskGson.fromJson(tasksResponse, new TypeToken<List<Task>>() {
            }.getType());
            if (tasks != null && tasks.size() > 0) {
                for (Task task : tasks) {
                    try {
                        task.setSyncStatus(BaseRepository.TYPE_Synced);
                        taskRepository.addOrUpdate(task);
                    } catch (Exception e) {
                        Timber.e(e, "Error saving task " + task.getIdentifier());
                    }
                }
            }
            if (!Utils.isEmptyCollection(tasks)) {
                allSharedPreferences.savePreference(TASK_LAST_SYNC_DATE, String.valueOf(getTaskMaxServerVersion(tasks, maxServerVersion)));
            }
            return tasks;
        } catch (Exception e) {
            Timber.e(e, "Error fetching tasks from server");
        }
        return null;
    }

    private String fetchTasks(Set<String> plan, List<String> group, Long serverVersion) throws Exception {
        HTTPAgent httpAgent = CoreLibrary.getInstance().context().getHttpAgent();
        String baseUrl = CoreLibrary.getInstance().context().
                configuration().dristhiBaseURL();
        String endString = "/";
        if (baseUrl.endsWith(endString)) {
            baseUrl = baseUrl.substring(0, baseUrl.lastIndexOf(endString));
        }

        JSONObject request = new JSONObject();
        request.put("plan", new JSONArray(plan));
        request.put("group", new JSONArray(group));
        request.put("serverVersion", serverVersion);

        if (httpAgent == null) {
            throw new IllegalArgumentException(SYNC_TASK_URL + " http agent is null");
        }

        Response resp = httpAgent.post(
                MessageFormat.format("{0}{1}",
                        baseUrl,
                        SYNC_TASK_URL),
                request.toString());
        if (resp.isFailure()) {
            throw new NoHttpResponseException(SYNC_TASK_URL + " not returned data");
        }

        return resp.payload().toString();
    }

    private long getTaskMaxServerVersion(List<Task> tasks, long maxServerVersion) {
        for (Task task : tasks) {
            long serverVersion = task.getServerVersion();
            if (serverVersion > maxServerVersion) {
                maxServerVersion = serverVersion;
            }
        }

        return maxServerVersion;
    }

    public void syncTaskStatusToServer() {
        HTTPAgent httpAgent = CoreLibrary.getInstance().context().getHttpAgent();
        List<TaskUpdate> updates = taskRepository.getUnSyncedTaskStatus();
        if (!updates.isEmpty()) {
            String jsonPayload = new Gson().toJson(updates);

            String baseUrl = CoreLibrary.getInstance().context().configuration().dristhiBaseURL();
            Response<String> response = httpAgent.postWithJsonResponse(
                    MessageFormat.format("{0}/{1}",
                            baseUrl,
                            UPDATE_STATUS_URL),
                    jsonPayload);

            if (response.isFailure()) {
                Timber.e("Update Status failedd: %s", response.payload());
                return;
            }

            if (response.payload() != null) {
                try {
                    JSONObject idObject = new JSONObject(response.payload());
                    JSONArray updatedIds = idObject.optJSONArray("task_ids");
                    if (updatedIds != null) {
                        for (int i = 0; i < updatedIds.length(); i++) {
                            taskRepository.markTaskAsSynced(updatedIds.get(i).toString());
                        }
                    }
                } catch (JSONException e) {
                    Timber.e(e, "Error processing the tasks payload: %s", response.payload());
                }
            }
        }
    }

    public void syncCreatedTaskToServer() {
        HTTPAgent httpAgent = CoreLibrary.getInstance().context().getHttpAgent();
        List<Task> tasks = taskRepository.getAllUnsynchedCreatedTasks();
        if (!tasks.isEmpty()) {
            String jsonPayload = taskGson.toJson(tasks);
            String baseUrl = CoreLibrary.getInstance().context().configuration().dristhiBaseURL();
            Response<String> response = httpAgent.post(
                    MessageFormat.format("{0}/{1}",
                            baseUrl,
                            ADD_TASK_URL),
                    jsonPayload);
            if (response.isFailure()) {
                Timber.e("Failed to create new tasks on server.: %s", response.payload());
                return;
            }

            for (Task task : tasks) {
                taskRepository.markTaskAsSynced(task.getIdentifier());
            }
        }
    }


}

