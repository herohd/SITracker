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

import android.app.ActionBar;
import android.app.FragmentManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;

import com.andrada.sitracker.R;
import com.andrada.sitracker.ui.fragment.DirectoryChooserFragment;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Let's the user choose a directory on the storage device. The selected folder
 * will be sent back to the starting activity as an activity result.
 */
public class DirectoryChooserActivity extends BaseActivity implements
        DirectoryChooserFragment.OnFragmentInteractionListener {
    public static final String EXTRA_NEW_DIR_NAME = "directory_name";
    public static final String EXTRA_IS_DIRECTORY_CHOOSER = "is_directory_chooser";

    /**
     * Extra to define the path of the directory that will be shown first.
     * If it is not sent or if path denotes a non readable/writable directory
     * or it is not a directory, it defaults to
     * {@link android.os.Environment#getExternalStorageDirectory()}
     */
    public static final String EXTRA_INITIAL_DIRECTORY = "initial_directory";

    public static final String RESULT_SELECTED_DIR = "selected_dir";
    public static final int RESULT_CODE_DIR_SELECTED = 1;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupActionBar();

        setContentView(R.layout.directory_chooser_activity);

        final String newDirName = getIntent().getStringExtra(EXTRA_NEW_DIR_NAME);
        final String initialDir = getIntent().getStringExtra(EXTRA_INITIAL_DIRECTORY);
        final Boolean isDirectoryChooser = getIntent().getBooleanExtra(EXTRA_IS_DIRECTORY_CHOOSER, true);

        if (newDirName == null) {
            throw new IllegalArgumentException(
                    "You must provide EXTRA_NEW_DIR_NAME when starting the DirectoryChooserActivity.");
        }

        if (savedInstanceState == null) {
            final FragmentManager fragmentManager = getFragmentManager();
            final DirectoryChooserFragment fragment = DirectoryChooserFragment.newInstance(newDirName, initialDir, isDirectoryChooser);
            fragmentManager.beginTransaction().add(R.id.fp_main, fragment)
                    .commit();
        }
    }

    /* package */
    void setupActionBar() {
        // there might not be an ActionBar, for example when started in Theme.Holo.Dialog.NoActionBar theme
        final ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NotNull MenuItem item) {
        final int itemId = item.getItemId();

        if (itemId == android.R.id.home) {
            setResult(RESULT_CANCELED);
            finish();

            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onSelectDirectory(String path) {
        final Intent intent = new Intent();
        intent.putExtra(RESULT_SELECTED_DIR, path);
        setResult(RESULT_CODE_DIR_SELECTED, intent);
        finish();
    }

    @Override
    public void onCancelChooser() {
        setResult(RESULT_CANCELED);
        finish();
    }
}
