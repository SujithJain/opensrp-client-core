package org.smartregister.view.fragment;

import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.loader.app.LoaderManager;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.util.ReflectionHelpers;
import org.smartregister.AllConstants;
import org.smartregister.BaseUnitTest;
import org.smartregister.Context;
import org.smartregister.CoreLibrary;
import org.smartregister.R;
import org.smartregister.cursoradapter.RecyclerViewPaginatedAdapter;
import org.smartregister.domain.FetchStatus;
import org.smartregister.domain.ResponseErrorStatus;
import org.smartregister.util.AppProperties;
import org.smartregister.view.activity.SecuredNativeSmartRegisterActivity;
import org.smartregister.view.contract.BaseRegisterFragmentContract;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;

/**
 * Created by ndegwamartin on 2020-04-28.
 */

@PrepareForTest({CoreLibrary.class})
public class BaseRegisterFragmentTest extends BaseUnitTest {

    private BaseRegisterFragment baseRegisterFragment;

    @Mock
    private LayoutInflater layoutInflater;

    @Mock
    private ViewGroup container;

    @Mock
    private Bundle bundle;

    private AppCompatActivity activity;

    @Mock
    private EditText searchView;

    @Mock
    private Context opensrpContext;

    @Mock
    private TextWatcher textWatcher;

    @Mock
    private View.OnKeyListener hideKeyboard;

    @Mock
    private View searchCancelView;

    @Mock
    private BaseRegisterFragmentContract.Presenter presenter;

    @Mock
    private RecyclerViewPaginatedAdapter clientAdapter;

    @Mock
    private ActionBar actionBar;

    @Mock
    private TextView headerTextDisplay;

    @Mock
    private RelativeLayout filterRelativeLayout;

    @Captor
    private ArgumentCaptor<Boolean> qrCodeArgumentCaptor;

    @Captor
    private ArgumentCaptor<String> openSRPIdArgumentCaptor;

    @Captor
    private ArgumentCaptor<String> stringArgumentCaptor;

    @Captor
    private ArgumentCaptor<Integer> intArgumentCaptor;

    @Mock
    private Resources resources;

    @Mock
    private AppProperties appProperties;

    @Mock
    private LoaderManager loaderManager;

    @Mock
    private CoreLibrary coreLibrary;

    @Before
    public void setUp() {

        MockitoAnnotations.initMocks(this);
        baseRegisterFragment = Mockito.mock(BaseRegisterFragment.class, Mockito.CALLS_REAL_METHODS);

        ReflectionHelpers.setField(baseRegisterFragment, "presenter", presenter);
        ReflectionHelpers.setField(baseRegisterFragment, "clientAdapter", clientAdapter);
        ReflectionHelpers.setField(baseRegisterFragment, "headerTextDisplay", headerTextDisplay);
        ReflectionHelpers.setField(baseRegisterFragment, "filterRelativeLayout", filterRelativeLayout);

        Intent intent = new Intent();
        intent.putExtra(BaseRegisterFragment.TOOLBAR_TITLE, TEST_RANDOM_STRING);

        activity = Robolectric.buildActivity(AppCompatActivity.class, intent).get();

        AppCompatActivity activitySpy = Mockito.spy(activity);
        doReturn(activitySpy).when(baseRegisterFragment).getActivity();

        ReflectionHelpers.setStaticField(CoreLibrary.class, "instance", coreLibrary);
        doReturn(opensrpContext).when(coreLibrary).context();

        doReturn(appProperties).when(opensrpContext).getAppProperties();

        doReturn(opensrpContext).when(baseRegisterFragment).context();

        doNothing().when(baseRegisterFragment).showShortToast(ArgumentMatchers.eq(activitySpy), ArgumentMatchers.anyString());
    }

    @Test
    public void assertFragmentInstantiatesCorrectly() {

        Assert.assertNotNull(baseRegisterFragment);
    }

    @Test
    public void assertGetNavBarOptionsProviderNotNull() {
        SecuredNativeSmartRegisterActivity.NavBarOptionsProvider provider = baseRegisterFragment.getNavBarOptionsProvider();
        Assert.assertNotNull(provider);
    }

    @Test
    public void assertGetNavBarOptionsProviderReturnsCorrectValueFormSearchHint() {
        SecuredNativeSmartRegisterActivity.NavBarOptionsProvider provider = baseRegisterFragment.getNavBarOptionsProvider();

        doReturn(opensrpContext).when(baseRegisterFragment).context();
        doReturn(RuntimeEnvironment.application.getResources().getString(R.string.search_hint)).when(opensrpContext).getStringResource(R.string.search_hint);

        String hint = RuntimeEnvironment.application.getResources().getString(R.string.search_hint);
        Assert.assertEquals(hint, provider.searchHint());
    }

    @Test
    public void testOnCreateViewInitsToolbarConfigurationCorrectly() {

        View parentLayout = LayoutInflater.from(RuntimeEnvironment.application.getApplicationContext()).inflate(R.layout.fragment_base_register, null, false);
        doReturn(parentLayout).when(layoutInflater).inflate(R.layout.fragment_base_register, container, false);
        Toolbar toolbar = parentLayout.findViewById(R.id.register_toolbar);

        AppCompatActivity activitySpy = Mockito.spy(activity);
        doReturn(activitySpy).when(baseRegisterFragment).getActivity();

        doReturn(actionBar).when(activitySpy).getSupportActionBar();

        baseRegisterFragment.onCreateView(layoutInflater, container, bundle);

        Mockito.verify(activitySpy).setSupportActionBar(toolbar);
        Mockito.verify(actionBar).setTitle(TEST_RANDOM_STRING);
        Mockito.verify(actionBar).setDisplayHomeAsUpEnabled(false);

        Mockito.verify(actionBar).setLogo(R.drawable.round_white_background);
        Mockito.verify(actionBar).setDisplayUseLogoEnabled(false);
        Mockito.verify(actionBar).setDisplayShowTitleEnabled(false);
    }


    @Test
    public void testOnCreateViewInitsInvokesSetUpViewsWithCorrectParam() {

        View parentLayout = LayoutInflater.from(RuntimeEnvironment.application.getApplicationContext()).inflate(R.layout.fragment_base_register, null, false);
        doReturn(parentLayout).when(layoutInflater).inflate(R.layout.fragment_base_register, container, false);

        AppCompatActivity activitySpy = Mockito.spy(activity);
        doReturn(activitySpy).when(baseRegisterFragment).getActivity();

        doReturn(actionBar).when(activitySpy).getSupportActionBar();

        baseRegisterFragment.onCreateView(layoutInflater, container, bundle);

        Mockito.verify(baseRegisterFragment).setupViews(parentLayout);
    }

    @Test
    public void assertGetLayoutReturnsCorrectLayout() {

        Assert.assertEquals(R.layout.fragment_base_register, baseRegisterFragment.getLayout());
    }

    @Test
    public void assertUpdateSearchViewAddsCorrectListenersToSearchView() {

        doReturn(searchView).when(baseRegisterFragment).getSearchView();

        ReflectionHelpers.setField(baseRegisterFragment, "textWatcher", textWatcher);
        ReflectionHelpers.setField(baseRegisterFragment, "hideKeyboard", hideKeyboard);

        baseRegisterFragment.updateSearchView();

        Mockito.verify(searchView).removeTextChangedListener(textWatcher);
        Mockito.verify(searchView).addTextChangedListener(textWatcher);
        Mockito.verify(searchView).setOnKeyListener(hideKeyboard);
    }

    @Test
    public void assertUpdateSearchBarHintSetsCorrectValue() {

        doReturn(searchView).when(baseRegisterFragment).getSearchView();

        baseRegisterFragment.updateSearchBarHint(TEST_RANDOM_STRING);

        Mockito.verify(searchView).setHint(TEST_RANDOM_STRING);
    }

    @Test
    public void setSearchTermInitsCorrectValue() {

        doReturn(searchView).when(baseRegisterFragment).getSearchView();

        baseRegisterFragment.setSearchTerm(TEST_RANDOM_STRING);

        Mockito.verify(searchView).setText(TEST_RANDOM_STRING);
    }

    @Test
    public void assertOnQRCodeSucessfullyScannedInvokesFilterWithCorrectParams() {

        String OPENSRP_ID = "8232-372-8L";
        String OPENSRP_ID_NO_HYPHENS = "82323728L";

        baseRegisterFragment = Mockito.spy(baseRegisterFragment);

        doReturn(searchCancelView).when(baseRegisterFragment).getSearchCancelView();

        doNothing().when(baseRegisterFragment).filter(ArgumentMatchers.eq(OPENSRP_ID_NO_HYPHENS), ArgumentMatchers.eq(""), ArgumentMatchers.anyString(), ArgumentMatchers.eq(true));

        doReturn(activity).when(baseRegisterFragment).getActivity();

        doReturn(loaderManager).when(baseRegisterFragment).getLoaderManager();
        doReturn(null).when(loaderManager).restartLoader(anyInt(), any(), any());

        baseRegisterFragment.onQRCodeSucessfullyScanned(OPENSRP_ID);

        Mockito.verify(baseRegisterFragment).filter(openSRPIdArgumentCaptor.capture(), stringArgumentCaptor.capture(), stringArgumentCaptor.capture(), qrCodeArgumentCaptor.capture());

        String capturedIdFilterParam = openSRPIdArgumentCaptor.getValue();

        Assert.assertEquals(OPENSRP_ID_NO_HYPHENS, capturedIdFilterParam);

        Boolean isQRCodeParam = qrCodeArgumentCaptor.getValue();
        Assert.assertNotNull(isQRCodeParam);
        Assert.assertTrue(isQRCodeParam);

    }

    @Test
    public void assertOnQRCodeSucessfullyScannedInvokessetUniqueIDWithCorrectParams() {

        String OPENSRP_ID = "8232-372-8L";

        baseRegisterFragment = Mockito.spy(baseRegisterFragment);

        doReturn(searchCancelView).when(baseRegisterFragment).getSearchCancelView();

        doNothing().when(baseRegisterFragment).filter(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyBoolean());

        doReturn(activity).when(baseRegisterFragment).getActivity();

        doReturn(loaderManager).when(baseRegisterFragment).getLoaderManager();
        doReturn(null).when(loaderManager).restartLoader(anyInt(), any(), any());

        baseRegisterFragment.onQRCodeSucessfullyScanned(OPENSRP_ID);

        Mockito.verify(baseRegisterFragment).setUniqueID(openSRPIdArgumentCaptor.capture());

        String capturedIdFilterParam = openSRPIdArgumentCaptor.getValue();

        Assert.assertEquals(OPENSRP_ID, capturedIdFilterParam);
    }

    @Test
    public void testOnResumptionInvokesRenderView() {

        doReturn(opensrpContext).when(baseRegisterFragment).context();
        doReturn(false).when(opensrpContext).IsUserLoggedOut();
        doNothing().when(baseRegisterFragment).refreshSyncProgressSpinner();
        doReturn(activity).when(baseRegisterFragment).getActivity();

        baseRegisterFragment.onResumption();
        Mockito.verify(baseRegisterFragment).renderView();

    }

    @Test
    public void testSetTotalPatientsSetsCorrectHeaderTextForDisplay() {

        doReturn(activity).when(baseRegisterFragment).getActivity();

        doReturn(5).when(clientAdapter).getTotalcount();
        baseRegisterFragment.setTotalPatients();

        Mockito.verify(headerTextDisplay).setText(stringArgumentCaptor.capture());
        String capturedHeaderText = stringArgumentCaptor.getValue();
        Assert.assertEquals("5 Clients", capturedHeaderText);

        doReturn(1).when(clientAdapter).getTotalcount();
        baseRegisterFragment.setTotalPatients();
        Mockito.verify(headerTextDisplay, Mockito.times(2)).setText(stringArgumentCaptor.capture());
        capturedHeaderText = stringArgumentCaptor.getValue();
        Assert.assertEquals("1 Client", capturedHeaderText);

    }


    @Test
    public void testSetTotalPatientsHidesFilterRelativeLayoutView() {

        doReturn(activity).when(baseRegisterFragment).getActivity();

        doReturn(5).when(clientAdapter).getTotalcount();
        baseRegisterFragment.setTotalPatients();

        Mockito.verify(filterRelativeLayout).setVisibility(intArgumentCaptor.capture());
        int visibility = intArgumentCaptor.getValue();
        Assert.assertEquals(View.GONE, visibility);
    }

    @Test
    public void assertClientsProviderSetToNull() {
        Assert.assertNull(baseRegisterFragment.clientsProvider());
    }

    @Test
    public void testOnCreationInvokesPresenterStartSyncForRemoteLogin() {

        doReturn(activity).when(baseRegisterFragment).getActivity();

        Intent intent = new Intent();
        intent.putExtra(AllConstants.INTENT_KEY.IS_REMOTE_LOGIN, true);
        activity.setIntent(intent);

        baseRegisterFragment.onCreation();

        Mockito.verify(presenter).startSync();
    }

    @Test
    public void assertOnBackPressedReturnsFalse() {
        Assert.assertFalse(baseRegisterFragment.onBackPressed());
    }

    @Test
    public void testOnSyncInProgressRefreshSyncStatusViewsWithCorrectParam() {

        doNothing().when(baseRegisterFragment).refreshSyncStatusViews(FetchStatus.fetchStarted);
        baseRegisterFragment.onSyncInProgress(FetchStatus.fetchStarted);
        Mockito.verify(baseRegisterFragment).refreshSyncStatusViews(FetchStatus.fetchStarted);
    }

    @Test
    public void testOnSyncStartRefreshSyncStatusViewsWithCorrectParam() {
        doNothing().when(baseRegisterFragment).refreshSyncStatusViews(null);
        baseRegisterFragment.onSyncStart();
        Mockito.verify(baseRegisterFragment).refreshSyncStatusViews(null);
    }

    @Test
    public void testOnSyncCompleteRefreshSyncStatusViewsWithCorrectParam() {
        doNothing().when(baseRegisterFragment).refreshSyncStatusViews(FetchStatus.fetched);
        baseRegisterFragment.onSyncComplete(FetchStatus.fetched);
        Mockito.verify(baseRegisterFragment).refreshSyncStatusViews(FetchStatus.fetched);
    }

    @Test
    public void testUpdateFilterAndFilterStatus() {
        View parentLayout = LayoutInflater.from(RuntimeEnvironment.application.getApplicationContext()).inflate(R.layout.fragment_base_register, null, false);
        doReturn(parentLayout).when(layoutInflater).inflate(R.layout.fragment_base_register, container, false);

        TextView headerTextDisplay = parentLayout.findViewById(R.id.header_text_display);
        TextView filterStatus = parentLayout.findViewById(R.id.filter_status);
        RelativeLayout filterRelativeLayout = parentLayout.findViewById(R.id.filter_display_view);

        ReflectionHelpers.setField(baseRegisterFragment, "headerTextDisplay", headerTextDisplay);
        ReflectionHelpers.setField(baseRegisterFragment, "filterRelativeLayout", filterRelativeLayout);
        ReflectionHelpers.setField(baseRegisterFragment, "filterStatus", filterStatus);

        doReturn(5).when(clientAdapter).getTotalcount();
        baseRegisterFragment.updateFilterAndFilterStatus("benji", "ASC");

        Assert.assertEquals("benji", headerTextDisplay.getText().toString());
        Assert.assertEquals("5 patients ASC", filterStatus.getText().toString());
    }

    @Test
    public void testRefreshSyncStatusViewsWithSyncingTrue() {
        doReturn(true).when(baseRegisterFragment).isSyncing();
        AppCompatActivity activitySpy = Mockito.spy(activity);
        doReturn(activitySpy).when(baseRegisterFragment).getActivity();
        doReturn(resources).when(activitySpy).getResources();
        doReturn("Test").when(activitySpy).getString(anyInt());

        View parentLayout = LayoutInflater.from(RuntimeEnvironment.application.getApplicationContext()).inflate(R.layout.fragment_base_register, null, false);
        doReturn(parentLayout).when(layoutInflater).inflate(R.layout.fragment_base_register, container, false);

        ProgressBar syncProgressBar = parentLayout.findViewById(R.id.sync_progress_bar);
        ImageView syncButton = parentLayout.findViewById(R.id.sync_refresh);

        ReflectionHelpers.setField(baseRegisterFragment, "syncProgressBar", syncProgressBar);
        ReflectionHelpers.setField(baseRegisterFragment, "syncButton", syncButton);
        baseRegisterFragment.refreshSyncStatusViews(FetchStatus.fetchStarted);
        Mockito.verify(baseRegisterFragment).refreshSyncProgressSpinner();
        Assert.assertEquals(View.VISIBLE, syncProgressBar.getVisibility());
        Assert.assertEquals(View.GONE, syncButton.getVisibility());
    }

    @Test
    public void testRefreshSyncStatusViewsWithSyncingFalse() {
        doReturn(false).when(baseRegisterFragment).isSyncing();
        AppCompatActivity activitySpy = Mockito.spy(activity);
        doReturn(activitySpy).when(baseRegisterFragment).getActivity();
        doReturn(resources).when(activitySpy).getResources();
        doReturn("Test").when(activitySpy).getString(anyInt());
        baseRegisterFragment.refreshSyncStatusViews(FetchStatus.fetchStarted);
        Mockito.verify(baseRegisterFragment).refreshSyncProgressSpinner();
    }

    @Test
    public void testRefreshSyncStatusViewsWithSyncingFalseFetchStatusFailedMalformedUrl() {
        doReturn(false).when(baseRegisterFragment).isSyncing();
        AppCompatActivity activitySpy = Mockito.spy(activity);
        doReturn(activitySpy).when(baseRegisterFragment).getActivity();
        doReturn(resources).when(activitySpy).getResources();
        doReturn("Test").when(activitySpy).getString(anyInt());
        FetchStatus status = FetchStatus.fetchedFailed;
        status.setDisplayValue(ResponseErrorStatus.malformed_url.name());
        baseRegisterFragment.refreshSyncStatusViews(status);
        Mockito.verify(baseRegisterFragment).refreshSyncProgressSpinner();
    }

    @Test
    public void testRefreshSyncStatusViewsWithSyncingFalseFetchStatusFailed() {
        doReturn(false).when(baseRegisterFragment).isSyncing();
        AppCompatActivity activitySpy = Mockito.spy(activity);
        doReturn(activitySpy).when(baseRegisterFragment).getActivity();
        doReturn(resources).when(activitySpy).getResources();
        doReturn("Test").when(activitySpy).getString(anyInt());
        FetchStatus status = FetchStatus.fetchedFailed;
        status.setDisplayValue(ResponseErrorStatus.not_found.name());
        baseRegisterFragment.refreshSyncStatusViews(status);
        Mockito.verify(baseRegisterFragment).refreshSyncProgressSpinner();
    }

    @Test
    public void testRefreshSyncStatusViewsWithSyncingFalseFetchStatusFailedTimeout() {
        doReturn(false).when(baseRegisterFragment).isSyncing();
        AppCompatActivity activitySpy = Mockito.spy(activity);
        doReturn(activitySpy).when(baseRegisterFragment).getActivity();
        doReturn(resources).when(activitySpy).getResources();
        doReturn("Test").when(activitySpy).getString(anyInt());
        FetchStatus status = FetchStatus.fetchedFailed;
        status.setDisplayValue(ResponseErrorStatus.timeout.name());
        baseRegisterFragment.refreshSyncStatusViews(status);
        Mockito.verify(baseRegisterFragment).refreshSyncProgressSpinner();
    }

    @Test
    public void testRefreshSyncStatusViewsWithSyncingFalseFetchStatusFetched() {
        doReturn(false).when(baseRegisterFragment).isSyncing();
        AppCompatActivity activitySpy = Mockito.spy(activity);
        doReturn(activitySpy).when(baseRegisterFragment).getActivity();
        doReturn(resources).when(activitySpy).getResources();
        doReturn("Test").when(activitySpy).getString(anyInt());
        baseRegisterFragment.refreshSyncStatusViews(FetchStatus.fetched);
        Mockito.verify(baseRegisterFragment).refreshSyncProgressSpinner();
    }

    @Test
    public void testRefreshSyncStatusViewsWithSyncingFalseFetchStatusNoConnection() {
        doReturn(false).when(baseRegisterFragment).isSyncing();
        AppCompatActivity activitySpy = Mockito.spy(activity);
        doReturn(activitySpy).when(baseRegisterFragment).getActivity();
        doReturn(resources).when(activitySpy).getResources();
        doReturn("Test").when(activitySpy).getString(anyInt());

        View Layout = LayoutInflater.from(RuntimeEnvironment.application.getApplicationContext()).inflate(R.layout.fragment_base_register, null, false);
        doReturn(Layout).when(layoutInflater).inflate(R.layout.fragment_base_register, container, false);

        ProgressBar progressBar = Layout.findViewById(R.id.sync_progress_bar);
        ImageView imageView = Layout.findViewById(R.id.sync_refresh);

        ReflectionHelpers.setField(baseRegisterFragment, "syncProgressBar", progressBar);
        ReflectionHelpers.setField(baseRegisterFragment, "syncButton", imageView);

        baseRegisterFragment.refreshSyncStatusViews(FetchStatus.noConnection);
        Mockito.verify(baseRegisterFragment).refreshSyncProgressSpinner();
        Assert.assertEquals(View.GONE, progressBar.getVisibility());
        Assert.assertEquals(View.VISIBLE, imageView.getVisibility());
    }

    @Test
    public void testRefreshSyncStatusViewsWithSyncingFalseFetchStatusNull() {
        doReturn(false).when(baseRegisterFragment).isSyncing();
        AppCompatActivity activitySpy = Mockito.spy(activity);
        doReturn(activitySpy).when(baseRegisterFragment).getActivity();
        doReturn(resources).when(activitySpy).getResources();
        doReturn("Test").when(activitySpy).getString(anyInt());
        baseRegisterFragment.refreshSyncStatusViews(null);
        Mockito.verify(baseRegisterFragment).refreshSyncProgressSpinner();
    }
}