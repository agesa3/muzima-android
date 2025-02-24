/*
 * Copyright (c) The Trustees of Indiana University, Moi University
 * and Vanderbilt University Medical Center. All Rights Reserved.
 *
 * This version of the code is licensed under the MPL 2.0 Open Source license
 * with additional health care disclaimer.
 * If the user is an entity intending to commercialize any application that uses
 * this code in a for-profit venture, please contact the copyright holder.
 */

package com.muzima.adapters.patients;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;
import com.muzima.MuzimaApplication;
import com.muzima.adapters.RecyclerAdapter;
import com.muzima.api.model.Patient;
import com.muzima.controller.PatientController;
import com.muzima.domain.Credentials;
import com.muzima.utils.Constants.SERVER_CONNECTIVITY_STATUS;
import com.muzima.utils.NetworkUtils;

import java.util.ArrayList;
import java.util.List;

import static com.muzima.utils.Constants.DataSyncServiceConstants.SyncStatusConstants;

public class PatientsRemoteSearchAdapter extends PatientAdapterHelper {
    private final PatientController patientController;
    private final String searchString;

    public PatientsRemoteSearchAdapter(Context context, PatientController patientController,
                                       String searchString) {
        super(context,patientController);
        this.patientController = patientController;
        this.searchString = searchString;
    }

    @Override
    public void reloadData() {
        new ServerSearchBackgroundTask().execute(searchString);
    }

    public void onAuthenticationError(int searchResutStatus, RecyclerAdapter.BackgroundListQueryTaskListener backgroundListQueryTaskListener){
        backgroundListQueryTaskListener.onQueryTaskCancelled(searchResutStatus);
    }

    public void onNetworkError(SERVER_CONNECTIVITY_STATUS networkStatus, RecyclerAdapter.BackgroundListQueryTaskListener backgroundListQueryTaskListener){
        if (backgroundListQueryTaskListener != null) {
            backgroundListQueryTaskListener.onQueryTaskCancelled(networkStatus);
        }
    }

    private class ServerSearchBackgroundTask extends AsyncTask<String, Void, Object> {
        @Override
        protected void onPreExecute() {
            onPreExecuteUpdate();
        }

        @Override
        protected void onPostExecute(Object patientsObject) {
            List<Patient> patients = (List<Patient>)patientsObject;
            onPostExecuteUpdate(patients);
        }

        @Override
        protected void onCancelled(Object result){
            if(result instanceof SERVER_CONNECTIVITY_STATUS){
                onNetworkError((SERVER_CONNECTIVITY_STATUS)result,getBackgroundListQueryTaskListener());
            } else {
                int authenticateResult = (int) result;
                onAuthenticationError(authenticateResult, getBackgroundListQueryTaskListener());
            }
        }

        @Override
        protected Object doInBackground(String... strings) {
            MuzimaApplication applicationContext = (MuzimaApplication) getContext().getApplicationContext();

            Credentials credentials = new Credentials(applicationContext);
            try {
                SERVER_CONNECTIVITY_STATUS serverStatus = NetworkUtils.getServerStatus(applicationContext, credentials.getServerUrl());
                if(serverStatus == SERVER_CONNECTIVITY_STATUS.SERVER_ONLINE) {
                    int authenticateResult = applicationContext.getMuzimaSyncService().authenticate(credentials.getCredentialsArray());
                    if (authenticateResult == SyncStatusConstants.AUTHENTICATION_SUCCESS) {
                        return patientController.searchPatientOnServer(strings[0]);
                    } else {
                        cancel(true);
                        return authenticateResult;
                    }
                }else {
                        cancel(true);
                        return serverStatus;
                }

            } catch (Throwable t) {
                Log.e(getClass().getSimpleName(), "Error while searching for patient in the server.", t);
            } finally {
                applicationContext.getMuzimaContext().closeSession();
            }
            Log.e(getClass().getSimpleName(), "Authentication failure !! Returning empty patient list");
            return new ArrayList<Patient>();
        }
    }
}
