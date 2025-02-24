/*
 * Copyright (c) The Trustees of Indiana University, Moi University
 * and Vanderbilt University Medical Center. All Rights Reserved.
 *
 * This version of the code is licensed under the MPL 2.0 Open Source license
 * with additional health care disclaimer.
 * If the user is an entity intending to commercialize any application that uses
 * this code in a for-profit venture, please contact the copyright holder.
 */

package com.muzima.view.initialwizard;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.PowerManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.TextInputEditText;
import com.muzima.MuzimaApplication;
import com.muzima.R;
import com.muzima.adapters.setupconfiguration.SetupConfigurationRecyclerViewAdapter;
import com.muzima.api.model.LastSyncTime;
import com.muzima.api.model.SetupConfiguration;
import com.muzima.api.service.LastSyncTimeService;
import com.muzima.service.MuzimaSyncService;
import com.muzima.service.SntpService;
import com.muzima.tasks.DownloadSetupConfigurationsTask;
import com.muzima.tasks.MuzimaAsyncTask;
import com.muzima.utils.KeyboardWatcher;
import com.muzima.utils.ThemeUtils;
import com.muzima.view.BroadcastListenerActivity;
import com.muzima.view.progressdialog.MuzimaProgressDialog;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.muzima.api.model.APIName.DOWNLOAD_SETUP_CONFIGURATIONS;
import static com.muzima.utils.Constants.DataSyncServiceConstants.SyncStatusConstants.SUCCESS;

public class SetupMethodPreferenceWizardActivity extends BroadcastListenerActivity implements KeyboardWatcher.OnKeyboardToggleListener,
        SetupConfigurationRecyclerViewAdapter.OnSetupConfigurationClickedListener {
    private Button activeNextButton;
    private View nextButtonLayout;
    private RecyclerView configsListView;
    private MuzimaProgressDialog progressDialog;
    private KeyboardWatcher keyboardWatcher;
    private TextInputEditText configSetupFilter;
    private ImageButton imageButton;
    private SetupConfigurationRecyclerViewAdapter setupConfigurationAdapter;
    private List<SetupConfiguration> setupConfigurationList = new ArrayList<>();
    private LinearLayout noDataLayout;
    private ScrollView scrollView;
    private TextView noDataMessage;
    private TextView noDataTip;


    public void onCreate(Bundle savedInstanceState) {
        ThemeUtils.getInstance().onCreate(this,false);
        super.onCreate(savedInstanceState);
        initializeResources();
        loadConfigList();
    }

    private void loadConfigList() {
        turnOnProgressDialog(getString(R.string.info_setup_config_load));
        ((MuzimaApplication) getApplicationContext()).getExecutorService()
                .execute(new DownloadSetupConfigurationsTask(getApplicationContext(), new DownloadSetupConfigurationsTask.SetupConfigurationCompletedCallback() {
                    @Override
                    public void setupConfigDownloadCompleted(final List<SetupConfiguration> configurationList) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                setupConfigurationList.addAll(configurationList);
                                if(setupConfigurationList.size()==0){
                                    noDataMessage.setText(R.string.info_program_unavailable);
                                    noDataTip.setText(R.string.info_program_unavailable_tip);
                                    noDataLayout.setVisibility(View.VISIBLE);
                                    scrollView.setVisibility(View.GONE);
                                    dismissProgressDialog();
                                }
                                else if(setupConfigurationList.size()==1){
                                    Intent intent = new Intent(getApplicationContext(), GuidedConfigurationWizardActivity.class);
                                    intent.putExtra(GuidedConfigurationWizardActivity.SETUP_CONFIG_UUID_INTENT_KEY, setupConfigurationList.get(0).getUuid());
                                    startActivity(intent);
                                }else {
                                    setupConfigurationAdapter.notifyDataSetChanged();
                                    setupConfigurationAdapter.setItemsCopy(configurationList);
                                    dismissProgressDialog();
                                }
                            }
                        });
                    }
                }));
    }

    private void initializeResources() {
        progressDialog = new MuzimaProgressDialog(this);
        setContentView(R.layout.activity_setup_method_wizard);
        configSetupFilter = findViewById(R.id.filter_configs_txt);
        setupConfigurationAdapter = new SetupConfigurationRecyclerViewAdapter(getApplicationContext(), setupConfigurationList, this);
        configSetupFilter.addTextChangedListener(textWatcherForFilterText(setupConfigurationAdapter));
        imageButton = findViewById(R.id.cancel_filter_txt);
        nextButtonLayout = findViewById(R.id.next_button_layout);
        configsListView = findViewById(R.id.configs_wizard_list);
        activeNextButton = findViewById(R.id.next);
        activeNextButton.setVisibility(View.GONE);
        activeNextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String setupConfigUuid = setupConfigurationAdapter.getSelectedConfigurationUuid();
                Intent intent = new Intent(getApplicationContext(), GuidedConfigurationWizardActivity.class);
                intent.putExtra(GuidedConfigurationWizardActivity.SETUP_CONFIG_UUID_INTENT_KEY, setupConfigUuid);
                startActivity(intent);
            }
        });
        imageButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                configSetupFilter.setText("");
            }
        });

        keyboardWatcher = new KeyboardWatcher(this);
        keyboardWatcher.setListener(this);
        configsListView.setAdapter(setupConfigurationAdapter);
        configsListView.setLayoutManager( new LinearLayoutManager(getApplicationContext()));
        noDataLayout = findViewById(R.id.no_data_layout);
        scrollView = findViewById(R.id.scroll_view);
        noDataMessage = findViewById(R.id.no_data_msg);
        noDataTip = findViewById(R.id.no_data_tip);
        hideKeyboard();
        logEvent("VIEW_SETUP_METHODS");
    }

    @Override
    public void onSetupConfigClicked(int position) {
        activeNextButton.setVisibility(View.VISIBLE);
        setupConfigurationAdapter.setSelectedConfigurationUuid(setupConfigurationList.get(position).getUuid());
        setupConfigurationAdapter.notifyDataSetChanged();
    }

    @Override
    protected void onDestroy() {
        keyboardWatcher.destroy();
        super.onDestroy();
    }

    @Override
    public void onKeyboardShown(int keyboardSize) {
        nextButtonLayout.setVisibility(View.GONE);

    }

    @Override
    public void onKeyboardClosed() {
        nextButtonLayout.setVisibility(View.VISIBLE);

    }

    private void hideKeyboard() {
        View view = this.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            view.clearFocus();
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private TextWatcher textWatcherForFilterText(final SetupConfigurationRecyclerViewAdapter setupConfigurationAdapter) {
        return new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {
                setupConfigurationAdapter.filterItems(charSequence.toString());
            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        };
    }

    private void turnOnProgressDialog(String message) {
        progressDialog.show(message);
    }

    private void dismissProgressDialog() {
        if (progressDialog != null) {
            progressDialog.dismiss();
        }
    }
}
