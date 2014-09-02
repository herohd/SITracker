/*
 * Copyright 2013 Gleb Godonoga.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.andrada.sitracker.tasks;

import android.annotation.SuppressLint;
import android.app.IntentService;
import android.app.backup.BackupManager;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import com.andrada.sitracker.Constants;
import com.andrada.sitracker.contracts.SIPrefs_;
import com.andrada.sitracker.db.beans.Author;
import com.andrada.sitracker.db.manager.SiDBHelper;
import com.andrada.sitracker.reader.SiteDetector;
import com.andrada.sitracker.reader.SiteStrategy;
import com.andrada.sitracker.tasks.messages.UpdateFailedIntentMessage;
import com.andrada.sitracker.tasks.messages.UpdateSuccessfulIntentMessage;
import com.google.analytics.tracking.android.EasyTracker;
import com.j256.ormlite.android.apptools.OpenHelperManager;

import org.androidannotations.annotations.EService;
import org.androidannotations.annotations.SystemService;
import org.androidannotations.annotations.sharedpreferences.Pref;

import java.sql.SQLException;
import java.util.List;

@SuppressLint("Registered")
@EService
public class UpdateAuthorsTask extends IntentService {

    @Pref
    SIPrefs_ prefs;
    @SystemService
    ConnectivityManager connectivityManager;
    private SiDBHelper siDBHelper;
    private int updatedAuthors;

    public UpdateAuthorsTask() {
        super(UpdateAuthorsTask.class.getSimpleName());
    }

    @Override
    public void onCreate() {
        super.onCreate();
        siDBHelper = OpenHelperManager.getHelper(this, SiDBHelper.class);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        OpenHelperManager.releaseHelper();
    }

    /**
     * The IntentService calls this method from the default worker thread with
     * the intent that started the service. When this method returns, IntentService
     * stops the service, as appropriate.
     */
    @Override
    protected void onHandleIntent(Intent intent) {

        boolean isNetworkIgnore = intent.getBooleanExtra(Constants.UPDATE_IGNORES_NETWORK, false);

        EasyTracker.getInstance().setContext(this.getApplicationContext());

        //Check for updates
        this.updatedAuthors = 0;
        try {
            List<Author> authors = siDBHelper.getAuthorDao().queryForAll();
            for (Author author : authors) {
                boolean useWiFiOnly = prefs.updateOnlyWiFi().get();
                if (this.isConnected() &&
                        (isNetworkIgnore ||
                                (!useWiFiOnly || this.isConnectedToWiFi()))) {
                    SiteStrategy strategy = SiteDetector.chooseStrategy(author.getUrl(), siDBHelper);
                    if (strategy.updateAuthor(author)) {
                        this.updatedAuthors++;
                    }
                }
                //Sleep for 5 seconds to avoid ban
                Thread.sleep(5000);
            }

            //Success
            //Do a broadcast
            broadCastResult(true);

        } catch (SQLException e) {
            //Error
            //Do a broadcast
            broadCastResult(false);
            trackException(e.getMessage());
        } catch (InterruptedException e) {
            //Ignore
            trackException(e.getMessage());
        }
    }

    private void broadCastResult(boolean success) {
        Intent broadcastIntent = new Intent();
        if (success) {
            broadcastIntent.setAction(UpdateSuccessfulIntentMessage.SUCCESS_MESSAGE);
            broadcastIntent.putExtra(Constants.NUMBER_OF_UPDATED_AUTHORS, this.updatedAuthors);
            BackupManager bm = new BackupManager(this.getApplicationContext());
            bm.dataChanged();
        } else {
            broadcastIntent = broadcastIntent.setAction(UpdateFailedIntentMessage.FAILED_MESSAGE);
        }
        sendOrderedBroadcast(broadcastIntent, null);
    }

    private boolean isConnected() {
        final NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
        return (activeNetwork != null && activeNetwork.isConnected());
    }

    private boolean isConnectedToWiFi() {
        final NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
        return (activeNetwork != null &&
                activeNetwork.isConnected() &&
                activeNetwork.getType() == ConnectivityManager.TYPE_WIFI);
    }

    private void trackException(String message) {
        EasyTracker.getTracker().sendException(message, false);
    }

}
