/*
 * Copyright 2014 Gleb Godonoga.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.andrada.sitracker.ui;

import android.annotation.SuppressLint;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.app.backup.BackupManager;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.widget.SlidingPaneLayout;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.andrada.sitracker.Constants;
import com.andrada.sitracker.R;
import com.andrada.sitracker.contracts.SIPrefs_;
import com.andrada.sitracker.events.AuthorMarkedAsReadEvent;
import com.andrada.sitracker.events.AuthorsExported;
import com.andrada.sitracker.events.ProgressBarToggleEvent;
import com.andrada.sitracker.events.PublicationMarkedAsReadEvent;
import com.andrada.sitracker.tasks.ExportAuthorsTask;
import com.andrada.sitracker.tasks.filters.UpdateStatusMessageFilter;
import com.andrada.sitracker.tasks.receivers.UpdateStatusReceiver;
import com.andrada.sitracker.ui.fragment.AboutDialog;
import com.andrada.sitracker.ui.fragment.AuthorsFragment;
import com.andrada.sitracker.ui.fragment.DirectoryChooserFragment;
import com.andrada.sitracker.ui.fragment.PublicationsFragment;
import com.andrada.sitracker.util.AnalyticsHelper;
import com.andrada.sitracker.util.UIUtils;
import com.andrada.sitracker.util.UpdateServiceHelper;
import com.github.amlcurran.showcaseview.ShowcaseView;
import com.github.amlcurran.showcaseview.targets.ActionItemTarget;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.Extra;
import org.androidannotations.annotations.FragmentById;
import org.androidannotations.annotations.OptionsItem;
import org.androidannotations.annotations.OptionsMenu;
import org.androidannotations.annotations.ViewById;
import org.androidannotations.annotations.res.StringRes;
import org.androidannotations.annotations.sharedpreferences.Pref;
import org.jetbrains.annotations.NotNull;

import java.util.Timer;
import java.util.TimerTask;

import de.greenrobot.event.EventBus;
import de.keyboardsurfer.android.widget.crouton.Configuration;
import de.keyboardsurfer.android.widget.crouton.Crouton;
import de.keyboardsurfer.android.widget.crouton.Style;


@SuppressLint("Registered")
@EActivity(R.layout.activity_main)
@OptionsMenu(R.menu.main_menu)
public class HomeActivity extends BaseActivity implements DirectoryChooserFragment.OnFragmentInteractionListener {

    public static final String AUTHORS_PROCESSED_EXTRA = "authors_total_processed";
    public static final String AUTHORS_SUCCESSFULLY_IMPORTED_EXTRA = "authors_successfully_imported";
    private static final long BACK_UP_DELAY = 30000L;

    /**
     * This global layout listener is used to fire an event after first layout
     * occurs and then it is removed. This gives us a chance to configure parts
     * of the UI that adapt based on available space after they have had the
     * opportunity to measure and layout.
     */
    final ViewTreeObserver.OnGlobalLayoutListener globalLayoutListener = new ViewTreeObserver.OnGlobalLayoutListener() {
        @SuppressLint("NewApi")
        @Override
        public void onGlobalLayout() {

            if (UIUtils.hasJellyBean()) {
                slidingPane.getViewTreeObserver().removeOnGlobalLayoutListener(this);
            } else {
                //noinspection deprecation
                slidingPane.getViewTreeObserver().removeGlobalOnLayoutListener(this);
            }

            if (slidingPane.isSlideable() && !slidingPane.isOpen()) {
                updateActionBarWithoutLandingNavigation();
            } else {
                updateActionBarWithHomeBackNavigation();
            }
        }
    };
    /**
     * This back stack listener is used to simulate standard fragment backstack behavior
     * for back button when panes are slid back and forth.
     */
    final FragmentManager.OnBackStackChangedListener backStackListener = new FragmentManager.OnBackStackChangedListener() {
        @Override
        public void onBackStackChanged() {
            if (slidingPane.isSlideable() &&
                    !slidingPane.isOpen() &&
                    getFragmentManager().getBackStackEntryCount() == 0) {
                slidingPane.openPane();
            }
        }
    };
    final SlidingPaneLayout.SimplePanelSlideListener slidingPaneListener = new SlidingPaneLayout.SimplePanelSlideListener() {

        public void onPanelOpened(View view) {
            AnalyticsHelper.getInstance().sendView(Constants.GA_SCREEN_AUTHORS);
            if (slidingPane.isSlideable()) {
                updateActionBarWithHomeBackNavigation();
                getFragmentManager().popBackStack();
            }
        }

        public void onPanelClosed(View view) {
            AnalyticsHelper.getInstance().sendView(Constants.GA_SCREEN_PUBLICATIONS);
            //This is called only on phones and 7 inch tablets in portrait
            updateActionBarWithoutLandingNavigation();
            getFragmentManager().beginTransaction().addToBackStack(null).commit();
        }
    };
    @NotNull
    private final Timer backUpTimer = new Timer();
    @Extra(AUTHORS_PROCESSED_EXTRA)
    int authorsProcessed = -1;
    @Extra(AUTHORS_SUCCESSFULLY_IMPORTED_EXTRA)
    int authorsSuccessfullyImported = -1;
    DirectoryChooserFragment mDialog;
    @FragmentById(R.id.fragment_publications)
    PublicationsFragment mPubFragment;
    @FragmentById(R.id.fragment_authors)
    AuthorsFragment mAuthorsFragment;
    @ViewById(R.id.fragment_container)
    SlidingPaneLayout slidingPane;
    @ViewById
    ProgressBar globalProgress;
    @Pref
    SIPrefs_ prefs;
    @StringRes(R.string.app_name)
    String mAppName;
    private TimerTask backUpTask;
    private BroadcastReceiver updateStatusReceiver;

    @AfterViews
    public void afterViews() {
        //Make sure the authors are opened
        slidingPane.openPane();
        slidingPane.setParallaxDistance(100);


        slidingPane.getViewTreeObserver().addOnGlobalLayoutListener(globalLayoutListener);
        ensureUpdatesAreRunningOnSchedule();

        new ShowcaseView.Builder(this)
                //TODO switch to X, Y position as toolbar is not suppoerted yet
                .setTarget(new ActionItemTarget(this, R.id.action_search))
                .setContentTitle(getString(R.string.showcase_getting_started_title))
                .setContentText(getString(R.string.showcase_getting_started_detail))
                .setStyle(R.style.ShowcaseView_Base)
                .singleShot(Constants.SHOWCASE_START_SEARCH_SHOT_ID)
                .build();
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        //Do not show menu in actionbar if authors are updating
        return mAuthorsFragment == null || !mAuthorsFragment.isUpdating();
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean onOptionsItemSelected(@NotNull MenuItem item) {
        /*
         * The action bar up action should open the slider if it is currently
         * closed, as the left pane contains content one level up in the
         * navigation hierarchy.
         */
        if (item.getItemId() == android.R.id.home && !slidingPane.isOpen()) {
            slidingPane.openPane();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EventBus.getDefault().register(this);
        if (isFinishing()) {
            return;
        }
        UIUtils.enableDisableActivitiesByFormFactor(this);
        mDialog = DirectoryChooserFragment.newInstance(getResources().getString(R.string.export_folder_name), null, true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
        Crouton.cancelAllCroutons();
    }

    @Override
    protected void onPause() {
        super.onPause();
        slidingPane.setPanelSlideListener(null);
        unregisterReceiver(updateStatusReceiver);
        getFragmentManager().removeOnBackStackChangedListener(backStackListener);
    }

    @Override
    protected void onNewIntent(@NotNull Intent intent) {
        super.onNewIntent(intent);
        Bundle extras = intent.getExtras();
        if (extras != null) {
            if (extras.containsKey(AUTHORS_PROCESSED_EXTRA)) {
                authorsProcessed = extras.getInt(AUTHORS_PROCESSED_EXTRA);
            }
            if (extras.containsKey(AUTHORS_SUCCESSFULLY_IMPORTED_EXTRA)) {
                authorsSuccessfullyImported = extras.getInt(AUTHORS_SUCCESSFULLY_IMPORTED_EXTRA);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (updateStatusReceiver == null) {
            //AuthorsFragment is the callback here
            updateStatusReceiver = new UpdateStatusReceiver(mAuthorsFragment);
            updateStatusReceiver.setOrderedHint(true);
        }
        mAuthorsFragment.getAdapter().reloadAuthors();
        slidingPane.setPanelSlideListener(slidingPaneListener);
        getFragmentManager().addOnBackStackChangedListener(backStackListener);

        if (UpdateServiceHelper.isServiceCurrentlyRunning(getApplicationContext())) {
            globalProgress.setVisibility(View.VISIBLE);
        } else {
            globalProgress.setVisibility(View.GONE);
        }

        UpdateStatusMessageFilter filter = new UpdateStatusMessageFilter();
        filter.setPriority(1);
        registerReceiver(updateStatusReceiver, filter);
        attemptToShowImportProgress();
    }

    public void ensureUpdatesAreRunningOnSchedule() {
        boolean isSyncing = prefs.updatesEnabled().get();

        boolean updateServiceUp = UpdateServiceHelper.isServiceScheduled(this);
        if (isSyncing && !updateServiceUp) {
            UpdateServiceHelper.scheduleUpdates(this);
        } else if (!isSyncing && updateServiceUp) {
            UpdateServiceHelper.cancelUpdates(this);
        }
    }

    @OptionsItem(R.id.action_import)
    void menuImportSelected() {
        startActivity(com.andrada.sitracker.ui.ImportAuthorsActivity_.intent(this).get());
    }

    @OptionsItem(R.id.action_export)
    void menuExportSelected() {
        AnalyticsHelper.getInstance().sendView(Constants.GA_SCREEN_EXPORT_DIALOG);
        mDialog.show(getFragmentManager(), null);
    }

    @OptionsItem(R.id.action_about)
    void menuAboutSelected() {
        AnalyticsHelper.getInstance().sendView(Constants.GA_SCREEN_ABOUT_DIALOG);
        FragmentManager fm = this.getFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();
        Fragment prev = fm.findFragmentByTag(AboutDialog.FRAGMENT_TAG);
        if (prev != null) {
            ft.remove(prev);
        }
        ft.addToBackStack(null);
        new AboutDialog().show(ft, AboutDialog.FRAGMENT_TAG);
    }

    private void attemptToShowImportProgress() {

        if (authorsProcessed != -1 && authorsSuccessfullyImported != -1) {
            View view = getLayoutInflater().inflate(R.layout.crouton_import_result, null);
            TextView totalTextV = (TextView) view.findViewById(R.id.totalAuthorsText);
            totalTextV.setText(getResources().getString(R.string.author_import_total_crouton_message,
                    authorsProcessed));
            TextView successTextV = (TextView) view.findViewById(R.id.successAuthorsText);
            successTextV.setText(getResources().getString(R.string.author_import_processed_crouton_message,
                    authorsSuccessfullyImported));
            TextView failedTextV = (TextView) view.findViewById(R.id.failedAuthorsText);
            failedTextV.setText(getResources().getString(R.string.author_import_failed_crouton_message,
                    authorsProcessed - authorsSuccessfullyImported));
            Configuration croutonConfiguration = new Configuration.Builder()
                    .setDuration(Configuration.DURATION_LONG).build();
            Crouton mNoNetworkCrouton = Crouton.make(this, view);
            mNoNetworkCrouton.setConfiguration(croutonConfiguration);
            mNoNetworkCrouton.show();

            //Remove extras to avoid reinitialization on config change
            getIntent().removeExtra(AUTHORS_PROCESSED_EXTRA);
            getIntent().removeExtra(AUTHORS_SUCCESSFULLY_IMPORTED_EXTRA);
            authorsSuccessfullyImported = -1;
            authorsProcessed = -1;
        }
    }

    private void updateActionBarWithoutLandingNavigation() {
        //TODO replace with toolbar
        //getActionBar().setDisplayHomeAsUpEnabled(true);
        //getActionBar().setHomeButtonEnabled(true);
        mAuthorsFragment.setHasOptionsMenu(false);
        String authorTitle = mAuthorsFragment.getCurrentSelectedAuthorName();
        //getActionBar().setTitle(authorTitle.equals("") ? mAppName : authorTitle);
    }

    private void updateActionBarWithHomeBackNavigation() {
        //TODO replace with toolbar
        //getActionBar().setDisplayHomeAsUpEnabled(false);
        //getActionBar().setHomeButtonEnabled(false);
        mAuthorsFragment.setHasOptionsMenu(true);
        //getActionBar().setTitle(mAppName);
    }

    public void onEventMainThread(@NotNull ProgressBarToggleEvent event) {
        if (event.showProgress) {
            this.globalProgress.setVisibility(View.VISIBLE);
        } else {
            this.globalProgress.setVisibility(View.GONE);
        }
    }

    public void onEvent(@NotNull AuthorsExported event) {
        String message = event.getMessage();

        Style.Builder alertStyle = new Style.Builder()
                .setTextAppearance(android.R.attr.textAppearanceLarge)
                .setPaddingInPixels(25);

        if (message.length() == 0) {
            //This is success
            alertStyle.setBackgroundColorValue(Style.holoGreenLight);
            message = getResources().getString(R.string.author_export_success_crouton_message);
        } else {
            alertStyle.setBackgroundColorValue(Style.holoRedLight);
        }
        Crouton.makeText(this, message, alertStyle.build()).show();

    }

    public void onEvent(AuthorMarkedAsReadEvent event) {
        this.scheduleBackup();
    }

    public void onEvent(PublicationMarkedAsReadEvent event) {
        this.scheduleBackup();
    }

    private void scheduleBackup() {
        if (this.backUpTask != null) {
            this.backUpTask.cancel();
        }
        this.backUpTask = new TimerTask() {
            @Override
            public void run() {
                BackupManager bm = new BackupManager(getApplicationContext());
                bm.dataChanged();
            }
        };
        backUpTimer.schedule(this.backUpTask, BACK_UP_DELAY);
    }

    public AuthorsFragment getAuthorsFragment() {
        return mAuthorsFragment;
    }

    public PublicationsFragment getPubFragment() {
        return mPubFragment;
    }

    @Override
    public void onSelectDirectory(String path) {
        ExportAuthorsTask task = new ExportAuthorsTask(getApplicationContext());
        task.execute(path);
        mDialog.dismiss();
    }

    @Override
    public void onCancelChooser() {
        mDialog.dismiss();
    }
}
