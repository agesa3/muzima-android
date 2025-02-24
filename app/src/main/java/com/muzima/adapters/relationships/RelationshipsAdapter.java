/*
 * Copyright (c) The Trustees of Indiana University, Moi University
 * and Vanderbilt University Medical Center. All Rights Reserved.
 *
 * This version of the code is licensed under the MPL 2.0 Open Source license
 * with additional health care disclaimer.
 * If the user is an entity intending to commercialize any application that uses
 * this code in a for-profit venture, please contact the copyright holder.
 */
package com.muzima.adapters.relationships;

import android.app.Activity;
import android.content.Context;
import androidx.annotation.NonNull;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.muzima.MuzimaApplication;
import com.muzima.R;
import com.muzima.adapters.ListAdapter;
import com.muzima.api.model.Concept;
import com.muzima.api.model.MuzimaSetting;
import com.muzima.api.model.Observation;
import com.muzima.api.model.Patient;
import com.muzima.api.model.Person;
import com.muzima.api.model.Relationship;
import com.muzima.controller.ConceptController;
import com.muzima.controller.MuzimaSettingController;
import com.muzima.controller.ObservationController;
import com.muzima.controller.PatientController;
import com.muzima.controller.RelationshipController;
import com.muzima.tasks.MuzimaAsyncTask;
import com.muzima.utils.DateUtils;
import com.muzima.utils.StringUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import static com.muzima.util.Constants.ServerSettings.SUPPORTED_RELATIONSHIP_TYPES;
import static com.muzima.util.Constants.ServerSettings.ALLOW_PATIENT_RELATIVES_DISPLAY;
import static com.muzima.utils.ConceptUtils.getConceptNameFromConceptNamesByLocale;
import static com.muzima.utils.DateUtils.SIMPLE_DAY_MONTH_YEAR_DATE_FORMAT;

import org.json.JSONException;

public class RelationshipsAdapter extends ListAdapter<Relationship> {
    private BackgroundListQueryTaskListener backgroundListQueryTaskListener;
    private final String patientUuid;
    private final RelationshipController relationshipController;
    private final PatientController patientController;
    private MuzimaApplication muzimaApplication;
    private ConceptController conceptController;
    private ObservationController observationController;


    public RelationshipsAdapter(Activity activity, int textViewResourceId, RelationshipController relationshipController,
                                String patientUuid, PatientController patientController) {
        super(activity, textViewResourceId);
        this.patientUuid = patientUuid;
        this.relationshipController = relationshipController;
        this.patientController = patientController;
        muzimaApplication = (MuzimaApplication) activity.getApplicationContext();
        conceptController = muzimaApplication.getConceptController();
        observationController = muzimaApplication.getObservationController();
    }

    @Override
    public void reloadData() {
        new BackgroundQueryTask().execute(patientUuid);
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        Relationship relationship=getItem(position);
        Context context = getContext();
        ViewHolder holder;
        if (convertView == null) {
            LayoutInflater layoutInflater = LayoutInflater.from(context);
            convertView = layoutInflater.inflate(R.layout.item_relationships_list_multi_checkable, parent, false);
            convertView.setClickable(false);
            convertView.setFocusable(false);
            holder = new ViewHolder();
            holder.relatedPerson = convertView.findViewById(R.id.name);
            holder.relationshipType = convertView.findViewById(R.id.relationshipType);
            holder.identifier = convertView.findViewById(R.id.identifier);
            holder.genderImg = convertView.findViewById(R.id.genderImg);
            holder.dateOfBirth = convertView.findViewById(R.id.dateOfBirth);
            holder.age = convertView.findViewById(R.id.age_text_label);
            holder.identifier = convertView.findViewById(R.id.identifier);
            holder.testDate = convertView.findViewById(R.id.hiv_test_date);
            holder.results = convertView.findViewById(R.id.hiv_results);
            holder.inHivCare = convertView.findViewById(R.id.in_hiv_care);
            holder.inCCR = convertView.findViewById(R.id.in_ccr);
            holder.hivTestDetails = convertView.findViewById(R.id.hiv_test_details);
            holder.hivCareDetails = convertView.findViewById(R.id.hiv_care_details);
            convertView.setTag(holder);
        }else {
            holder = (ViewHolder) convertView.getTag();
        }

        String relatedPersonUuid = "";

        if (StringUtils.equalsIgnoreCase(patientUuid, relationship.getPersonA().getUuid())) {
            relatedPersonUuid = relationship.getPersonB().getUuid();
            holder.relatedPerson.setText(relationship.getPersonB().getDisplayName());
            holder.relationshipType.setText(relationship.getRelationshipType().getBIsToA());

            Date dob = relationship.getPersonB().getBirthdate();
            if(dob != null) {
                holder.dateOfBirth.setText(context.getString(R.string.general_date_of_birth ,String.format(" %s", new SimpleDateFormat("dd-MM-yyyy",
                        Locale.getDefault()).format(dob))));

                holder.age.setText(context.getString(R.string.general_years ,String.format(Locale.getDefault(), "%d ", DateUtils.calculateAge(dob))));
            }else{
                holder.dateOfBirth.setText(String.format(""));
                holder.age.setText(String.format(""));
            }

            if(relationship.getPersonB().getGender() != null) {
                int genderDrawable = relationship.getPersonB().getGender().equalsIgnoreCase("M") ? R.drawable.gender_male : R.drawable.ic_female;
                holder.genderImg.setImageDrawable(getContext().getResources().getDrawable(genderDrawable));
            }
            try {
                Patient p = patientController.getPatientByUuid(relationship.getPersonB().getUuid());
                if (p != null){
                    holder.identifier.setVisibility(View.VISIBLE);
                    holder.identifier.setText(p.getIdentifier());
                } else
                    holder.identifier.setVisibility(View.GONE);
            } catch (PatientController.PatientLoadException e) {
                Log.e(this.getClass().getSimpleName(), "Error searching Patient");
            }
        } else {
            relatedPersonUuid = relationship.getPersonA().getUuid();
            holder.relatedPerson.setText(relationship.getPersonA().getDisplayName());
            holder.relationshipType.setText(relationship.getRelationshipType().getAIsToB());

            Date dob = relationship.getPersonA().getBirthdate();
            if(dob != null) {
                holder.dateOfBirth.setText(context.getString(R.string.general_date_of_birth ,String.format(" %s", new SimpleDateFormat("dd-MM-yyyy",
                        Locale.getDefault()).format(dob))));
                holder.age.setText(context.getString(R.string.general_years ,String.format(Locale.getDefault(), "%d ", DateUtils.calculateAge(dob))));
            }else{
                holder.dateOfBirth.setText(String.format(""));
                holder.age.setText(String.format(""));
            }

            if(relationship.getPersonA().getGender() != null) {
                int genderDrawable = relationship.getPersonA().getGender().equalsIgnoreCase("M") ? R.drawable.gender_male : R.drawable.ic_female;
                holder.genderImg.setImageDrawable(getContext().getResources().getDrawable(genderDrawable));
            }
            try {
                Patient p = patientController.getPatientByUuid(relationship.getPersonA().getUuid());
                if (p != null){
                    holder.identifier.setVisibility(View.VISIBLE);
                    holder.identifier.setText(p.getIdentifier());
                } else
                    holder.identifier.setVisibility(View.GONE);
            } catch (PatientController.PatientLoadException e) {
                Log.e(this.getClass().getSimpleName(), "Error searching Patient");
            }
        }

        if(!muzimaApplication.getMuzimaSettingController().isFGHCustomClientSummaryEnabled()){
            holder.hivTestDetails.setVisibility(View.GONE);
            holder.hivCareDetails.setVisibility(View.GONE);
        }else {
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getContext());
            String applicationLanguage = preferences.getString(getContext().getResources().getString(R.string.preference_app_language), getContext().getResources().getString(R.string.language_english));

            try {
                holder.testDate.setText(getObsDateTimeByPatientUuidAndConceptId(relatedPersonUuid, 23779, observationController, conceptController, applicationLanguage));
                holder.results.setText(getObsByPatientUuidAndConceptId(relatedPersonUuid, 23779, observationController, conceptController, applicationLanguage));
                holder.inHivCare.setText(getObsByPatientUuidAndConceptId(relatedPersonUuid, 23780, observationController, conceptController, applicationLanguage));
                holder.inCCR.setText(getObsByPatientUuidAndConceptId(relatedPersonUuid, 1885, observationController, conceptController, applicationLanguage));

            } catch (JSONException e) {
                Log.e(getClass().getSimpleName(),"Encountered JSONException ",e);
            } catch (ObservationController.LoadObservationException e) {
                Log.e(getClass().getSimpleName(),"Encountered LoadObservationException ",e);
            }
        }

        return convertView;
    }

    public String getObsByPatientUuidAndConceptId(String patientUuid, int conceptId, ObservationController observationController, ConceptController conceptController, String applicationLanguage) throws JSONException, ObservationController.LoadObservationException {
        List<Observation> observations = new ArrayList<>();
        try {
            observations = observationController.getObservationsByPatientuuidAndConceptId(patientUuid, conceptId);
            Concept concept = conceptController.getConceptById(conceptId);
            Collections.sort(observations, observationDateTimeComparator);
            if(observations.size()>0){
                Observation obs = observations.get(0);
                if(concept.isDatetime())
                    return DateUtils.getFormattedDate(obs.getValueDatetime(),SIMPLE_DAY_MONTH_YEAR_DATE_FORMAT);
                else if(concept.isCoded())
                    return getConceptNameFromConceptNamesByLocale(obs.getValueCoded().getConceptNames(),applicationLanguage);
                else if(concept.isNumeric())
                    return String.valueOf(obs.getValueNumeric());
                else
                    return obs.getValueText();
            }
        } catch (ObservationController.LoadObservationException | Exception | ConceptController.ConceptFetchException e) {
            Log.e(getClass().getSimpleName(), "Exception occurred while loading observations", e);
        }
        return StringUtils.EMPTY;
    }

    private String getObsDateTimeByPatientUuidAndConceptId(String patientUuid, int conceptId, ObservationController observationController, ConceptController conceptController, String applicationLanguage) throws JSONException, ObservationController.LoadObservationException {
        List<Observation> observations = new ArrayList<>();
        try {
            observations = observationController.getObservationsByPatientuuidAndConceptId(patientUuid, conceptId);
            Collections.sort(observations, observationDateTimeComparator);
            if(observations.size()>0){
                Observation obs = observations.get(0);
                return DateUtils.getFormattedDate(obs.getObservationDatetime(),SIMPLE_DAY_MONTH_YEAR_DATE_FORMAT);
            }
        } catch (ObservationController.LoadObservationException | Exception  e) {
            Log.e(getClass().getSimpleName(), "Exception occurred while loading observations", e);
        }
        return StringUtils.EMPTY;
    }

    private boolean isContactHivPositive(String patientUuid, int conceptId, ObservationController observationController, ConceptController conceptController) {
        List<Observation> observations = new ArrayList<>();
        try {
            Concept concept = conceptController.getConceptById(conceptId);
            observations = observationController.getObservationsByPatientuuidAndConceptId(patientUuid, conceptId);
            Collections.sort(observations, observationDateTimeComparator);
            if(observations.size()>0){
                Observation obs = observations.get(0);
                if(concept.isCoded()){
                    if(obs.getValueCoded().getId() == 703)
                        return true;
                    else
                        return false;

                }
            }
        } catch (ObservationController.LoadObservationException | Exception | ConceptController.ConceptFetchException e) {
            Log.e(getClass().getSimpleName(), "Exception occurred while loading observations", e);
        }
        return false;
    }

    private boolean isHivTestNegativeOrPositive(String patientUuid, int conceptId, ObservationController observationController, ConceptController conceptController) {
        List<Observation> observations = new ArrayList<>();
        try {
            Concept concept = conceptController.getConceptById(conceptId);
            observations = observationController.getObservationsByPatientuuidAndConceptId(patientUuid, conceptId);
            Collections.sort(observations, observationDateTimeComparator);
            if(observations.size()>0){
                Observation obs = observations.get(0);
                if(concept.isCoded()){
                    if(obs.getValueCoded().getId() == 664 || obs.getValueCoded().getId() == 703)
                        return true;
                    else
                        return false;
                }
            }
        } catch (ObservationController.LoadObservationException | Exception | ConceptController.ConceptFetchException e) {
            Log.e(getClass().getSimpleName(), "Exception occurred while loading observations", e);
        }
        return false;
    }

    private final Comparator<Observation> observationDateTimeComparator = new Comparator<Observation>() {
        @Override
        public int compare(Observation lhs, Observation rhs) {
            return -lhs.getObservationDatetime().compareTo(rhs.getObservationDatetime());
        }
    };

    public void setBackgroundListQueryTaskListener(BackgroundListQueryTaskListener backgroundListQueryTaskListener) {
        this.backgroundListQueryTaskListener = backgroundListQueryTaskListener;
    }

    public void removeRelationshipsForPatient(String patientUuid, List<Relationship> relationshipsToDelete) {
        try {
            List<Relationship> allRelationshipsForPatient = relationshipController.getRelationshipsForPerson(patientUuid);
            allRelationshipsForPatient.removeAll(relationshipsToDelete);
            try {
                relationshipController.deleteRelationships(relationshipsToDelete);

                //foreach relationship if related person is not synced and has no more relationship then, delete the person
                for (Relationship relationship : relationshipsToDelete) {
                    Person relatedPerson;
                    if (StringUtils.equals(relationship.getPersonA().getUuid(), patientUuid)) {
                        relatedPerson = relationship.getPersonB();
                    } else {
                        relatedPerson = relationship.getPersonA();
                    }

                    if (!relationship.getSynced() &&
                            relationshipController.getRelationshipsForPerson(relatedPerson.getUuid()).size() < 1 ){
                        try {
                            relationshipController.deletePerson(relatedPerson);
                        } catch (RelationshipController.DeletePersonException e) {
                            Log.e(getClass().getSimpleName(), "Error while deleting last person", e);
                        }
                    }
                }
            } catch (RelationshipController.DeleteRelationshipException e) {
                Log.e(getClass().getSimpleName(), "Error while deleting the relationships", e);
            }
            clear();
            addAll(allRelationshipsForPatient);
        } catch (RelationshipController.RetrieveRelationshipException e) {
            Log.e(getClass().getSimpleName(), "Error while fetching the relationships", e);
        }
    }

    class ViewHolder {
        ImageView genderImg;
        TextView dateOfBirth;
        TextView age;
        TextView identifier;

        TextView relatedPerson;
        TextView relationshipType;
        TextView testDate;
        TextView results;
        TextView inHivCare;
        TextView inCCR;
        RelativeLayout hivTestDetails;
        RelativeLayout hivCareDetails;

    }

    private class BackgroundQueryTask extends MuzimaAsyncTask<String, Void, List<Relationship>> {
        @Override
        protected void onPreExecute() {
            if (backgroundListQueryTaskListener != null) {
                backgroundListQueryTaskListener.onQueryTaskStarted();
            }
        }

        @Override
        protected List<Relationship> doInBackground(String... params) {
            List<Relationship> relationships = null;
            try {
                List<String> supportedRelationshipIdList = new ArrayList<>();
                MuzimaSetting setting = muzimaApplication.getMuzimaSettingController().getSettingByProperty(SUPPORTED_RELATIONSHIP_TYPES);
                if(setting != null && !StringUtils.isEmpty(setting.getValueString())) {
                    String supportedRelationshipIdString = setting.getValueString();
                    supportedRelationshipIdList = Arrays.asList(supportedRelationshipIdString.split(","));
                }
               relationships = relationshipController.getRelationshipsForPerson(patientUuid);

                if(!supportedRelationshipIdList.isEmpty()){
                    List<Relationship> filteredRelationships = new ArrayList<>();
                    for(Relationship relationship:relationships){
                        if(supportedRelationshipIdList.contains(String.valueOf(relationship.getRelationshipType().getId()))){
                            filteredRelationships.add(relationship);
                        }
                    }
                    relationships = filteredRelationships;
                }

                MuzimaSetting allowPatientRelativesDisplaySetting = muzimaApplication.getMuzimaSettingController().getSettingByProperty(ALLOW_PATIENT_RELATIVES_DISPLAY);
                if(!allowPatientRelativesDisplaySetting.getValueBoolean()){
                    List<Relationship> nonPatientRelationships = new ArrayList<>();
                    for(Relationship relationship:relationships){
                        Person relatedPerson = null;
                        boolean isRelatedPersonB = false;
                        if(StringUtils.equals(relationship.getPersonA().getUuid(),patientUuid)) {
                            relatedPerson = relationship.getPersonB();
                            isRelatedPersonB = true;
                        } else {
                            relatedPerson = relationship.getPersonA();
                        }

                        try {
                            if (patientController.getPatientByUuid(relatedPerson.getUuid()) == null) {
                                //remove hiv positive contacts
                               if(relationship.getRelationshipType().getUuid().equals("8d91a210-c2cc-11de-8d13-0010c6dffd0f") && isRelatedPersonB){
                                   boolean isEligible = false;
                                   if(relatedPerson.getBirthdate() == null){
                                       isEligible = true;
                                   } else {
                                       int age = DateUtils.calculateAge(relatedPerson.getBirthdate());
                                       if(age<15)
                                            isEligible = true;
                                   }

                                    boolean isHivTestNegativeOrPositive = isHivTestNegativeOrPositive(relatedPerson.getUuid(), 23779, observationController, conceptController);
                                    if(!isHivTestNegativeOrPositive && !relatedPerson.isVoided() && isEligible){
                                        nonPatientRelationships.add(relationship);
                                    }
                                }else {
                                   boolean isHivTestPositive = isContactHivPositive(relatedPerson.getUuid(), 23779, observationController, conceptController);
                                   if (!isHivTestPositive && !relatedPerson.isVoided()) {
                                        nonPatientRelationships.add(relationship);
                                    }
                                }
                            }
                        } catch (PatientController.PatientLoadException e) {
                            Log.e(this.getClass().getSimpleName(),"Could not get relationship patient",e);
                        }
                    }
                    relationships = nonPatientRelationships;
                }

            }catch(RelationshipController.RetrieveRelationshipException | MuzimaSettingController.MuzimaSettingFetchException e){
                Log.e(this.getClass().getSimpleName(),"Could not get relationship for patient",e);
            }

            return relationships;
        }

        @Override
        protected void onPostExecute(List<Relationship> relationships){
            if(relationships==null){
                Toast.makeText(getContext(),getContext().getString(R.string.error_relationship_load),Toast.LENGTH_SHORT).show();
                return;
            }
            clear();
            addAll(relationships);
            notifyDataSetChanged();
            backgroundListQueryTaskListener.onQueryTaskFinish();
        }

        @Override
        protected void onBackgroundError(Exception e) {

        }
    }
}
