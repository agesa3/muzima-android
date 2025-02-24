/*
 * Copyright (c) The Trustees of Indiana University, Moi University
 * and Vanderbilt University Medical Center. All Rights Reserved.
 *
 * This version of the code is licensed under the MPL 2.0 Open Source license
 * with additional health care disclaimer.
 * If the user is an entity intending to commercialize any application that uses
 * this code in a for-profit venture, please contact the copyright holder.
 */

package com.muzima.controller;

import android.content.Context;
import android.util.Log;

import com.muzima.MuzimaApplication;
import com.muzima.api.model.APIName;
import com.muzima.api.model.Encounter;
import com.muzima.api.model.Form;
import com.muzima.api.model.FormData;
import com.muzima.api.model.FormDataStatus;
import com.muzima.api.model.FormTemplate;
import com.muzima.api.model.LastSyncTime;
import com.muzima.api.model.Observation;
import com.muzima.api.model.Patient;
import com.muzima.api.model.Person;
import com.muzima.api.model.PersonName;
import com.muzima.api.model.SetupConfigurationTemplate;
import com.muzima.api.model.Tag;
import com.muzima.api.service.EncounterService;
import com.muzima.api.service.FormService;
import com.muzima.api.service.LastSyncTimeService;
import com.muzima.api.service.ObservationService;
import com.muzima.api.service.PatientService;
import com.muzima.api.service.PersonService;
import com.muzima.api.service.SetupConfigurationService;
import com.muzima.model.AvailableForm;
import com.muzima.model.CompleteFormWithPatientData;
import com.muzima.model.builders.AvailableFormBuilder;
import com.muzima.model.builders.CompleteFormBuilder;
import com.muzima.model.builders.CompleteFormWithPatientDataBuilder;
import com.muzima.model.builders.DownloadedFormBuilder;
import com.muzima.model.builders.IncompleteFormBuilder;
import com.muzima.model.builders.IncompleteFormWithPatientDataBuilder;
import com.muzima.model.collections.AvailableForms;
import com.muzima.model.collections.CompleteForms;
import com.muzima.model.collections.CompleteFormsWithPatientData;
import com.muzima.model.collections.DownloadedForms;
import com.muzima.model.collections.IncompleteForms;
import com.muzima.model.collections.IncompleteFormsWithPatientData;
import com.muzima.service.MuzimaLoggerService;
import com.muzima.service.SntpService;
import com.muzima.util.JsonUtils;
import com.muzima.utils.Constants;
import com.muzima.utils.CustomColor;
import com.muzima.utils.EnDeCrypt;
import com.muzima.utils.MediaUtils;
import com.muzima.utils.PersonRegistrationUtils;
import com.muzima.utils.StringUtils;
import com.muzima.view.forms.GenericPatientRegistrationJSONMapper;
import com.muzima.view.forms.HTMLPatientJSONMapper;
import com.muzima.view.forms.PatientJSONMapper;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import net.minidev.json.parser.JSONParser;
import net.minidev.json.parser.ParseException;
import org.json.JSONException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.muzima.utils.Constants.FORM_DISCRIMINATOR_REGISTRATION;
import static com.muzima.utils.Constants.FORM_JSON_DISCRIMINATOR_CONSULTATION;
import static com.muzima.utils.Constants.FORM_JSON_DISCRIMINATOR_DEMOGRAPHICS_UPDATE;
import static com.muzima.utils.Constants.FORM_JSON_DISCRIMINATOR_GENERIC_REGISTRATION;
import static com.muzima.utils.Constants.FORM_JSON_DISCRIMINATOR_PERSON_UPDATE;
import static com.muzima.utils.Constants.FORM_JSON_DISCRIMINATOR_REGISTRATION;
import static com.muzima.utils.Constants.FORM_JSON_DISCRIMINATOR_RELATIONSHIP;
import static com.muzima.utils.Constants.FORM_JSON_DISCRIMINATOR_SHR_REGISTRATION;
import static com.muzima.utils.Constants.STATUS_INCOMPLETE;
import static com.muzima.utils.Constants.STATUS_UPLOADED;

public class FormController {

    private final FormService formService;
    private final PatientService patientService;
    private final PersonService personService;
    private final LastSyncTimeService lastSyncTimeService;
    private final SntpService sntpService;
    private final ObservationService observationService;
    private final EncounterService encounterService;
    private final SetupConfigurationService setupConfigurationService;
    private final Map<String, Integer> tagColors;
    private List<Tag> selectedTags;
    private String jsonPayload;
    private MuzimaApplication muzimaApplication;

    public FormController(MuzimaApplication muzimaApplication) throws IOException {
        this.formService = muzimaApplication.getMuzimaContext().getFormService();
        this.patientService = muzimaApplication.getMuzimaContext().getPatientService();
        this.personService = muzimaApplication.getMuzimaContext().getPersonService();
        this.lastSyncTimeService = muzimaApplication.getMuzimaContext().getLastSyncTimeService();
        this.sntpService = muzimaApplication.getSntpService();
        this.observationService = muzimaApplication.getMuzimaContext().getObservationService();
        this.encounterService = muzimaApplication.getMuzimaContext().getEncounterService();
        this.setupConfigurationService = muzimaApplication.getMuzimaContext().getSetupConfigurationService();
        tagColors = new HashMap<>();
        selectedTags = new ArrayList<>();
        this.muzimaApplication = muzimaApplication;
    }

    public int getTotalFormCount() throws FormFetchException {
        try {
            return formService.countAllForms();
        } catch (IOException e) {
            throw new FormFetchException(e);
        }
    }

    public Form getFormByUuid(String formId) throws FormFetchException {
        try {
            return formService.getFormByUuid(formId);
        } catch (IOException e) {
            throw new FormFetchException(e);
        }
    }

    public FormTemplate getFormTemplateByUuid(String formId) throws FormFetchException {
        try {
            return formService.getFormTemplateByUuid(formId);
        } catch (IOException e) {
            throw new FormFetchException(e);
        }
    }

    public AvailableForms getAvailableFormByTags(List<String> tagsUuid) throws FormFetchException {
        return getAvailableFormByTags(tagsUuid, false);
    }

    public List<Form> getAllAvailableForms() throws FormFetchException {
        List<Form> allAvailableForms = new ArrayList<>();
        try {
            List<String> formsInConfig = getFormListAsPerConfigOrder();
            boolean isDisplayOnlyFormsInSetupConfig= muzimaApplication.getMuzimaSettingController().isDisplayOnlyFormsInConfigEnabled();
            if(isDisplayOnlyFormsInSetupConfig) {
                List<Form> allForms = formService.getAllForms();
                for(Form form: allForms){
                    if(formsInConfig.contains(form.getUuid())){
                        allAvailableForms.add(form);
                    }
                }
            }else{
                allAvailableForms = formService.getAllForms();
            }

            return allAvailableForms;

        } catch (IOException e) {
            throw new FormFetchException(e);
        }
    }

    public AvailableForms getAvailableFormByTags(List<String> tagsUuid, boolean alwaysIncludeRegistrationForms) throws FormFetchException {
        try {
            List<Form> allForms = formService.getAllForms();
            List<Form> filteredForms = filterFormsByTags(allForms, tagsUuid, alwaysIncludeRegistrationForms);
            AvailableForms availableForms = new AvailableForms();
            availableForms = getAvailableForms(filteredForms);
            AvailableForms availableFormsInConfig = new AvailableForms();

            List<String> formsInConfig = getFormListAsPerConfigOrder();
            boolean isDisplayOnlyFormsInSetupConfig= muzimaApplication.getMuzimaSettingController().isDisplayOnlyFormsInConfigEnabled();
            if(isDisplayOnlyFormsInSetupConfig) {
                for(AvailableForm form: availableForms){
                    if(formsInConfig.contains(form.getFormUuid())){
                        availableFormsInConfig.add(form);
                    }
                }
                return availableFormsInConfig;
            }else{
                return availableForms;
            }
        } catch (IOException e) {
            throw new FormFetchException(e);
        }
    }

    private List<Form> filterFormsByTags(List<Form> forms, List<String> tagsUuid, boolean alwaysIncludeRegistrationForms) {
        if (tagsUuid == null || tagsUuid.isEmpty()) {
            return forms;
        }
        List<Form> filteredForms = new ArrayList<>();
        for (Form form : forms) {
            Tag[] formTags = form.getTags();
            for (Tag formTag : formTags) {
                if (tagsUuid.contains(formTag.getUuid()) ||
                        (alwaysIncludeRegistrationForms && isRegistrationTag(formTag))) {
                    filteredForms.add(form);
                    break;
                }
            }
        }
        return filteredForms;
    }

    public List<Tag> getAllTags() throws FormFetchException {
        List<Tag> allTags = new ArrayList<>();
        List<Form> allForms = null;
        try {
            allForms = formService.getAllForms();
        } catch (IOException e) {
            throw new FormFetchException(e);
        }
        for (Form form : allForms) {
            for (Tag tag : form.getTags()) {
                if (!allTags.contains(tag)) {
                    allTags.add(tag);
                }
            }
        }
        return allTags;
    }

    public List<Tag> getAllTagsExcludingRegistrationTag() throws FormFetchException {
        List<Tag> allTags = new ArrayList<>();
        for (Tag tag : getAllTags()) {
            if (!isRegistrationTag(tag)) {
                allTags.add(tag);
            }
        }
        return allTags;
    }


    public DownloadedForms getAllDownloadedForms() throws FormFetchException {
        DownloadedForms downloadedFormsByTags = new DownloadedForms();
        try {
            List<Form> allForms = formService.getAllForms();
            for (Form form : allForms) {
                if (formService.isFormTemplateDownloaded(form.getUuid())) {
                    downloadedFormsByTags.add(new DownloadedFormBuilder().withDownloadedForm(form).build());
                }
            }
        } catch (IOException e) {
            throw new FormFetchException(e);
        }
        return downloadedFormsByTags;
    }

    public List<Form> downloadAllForms() throws FormFetchException {
        try {
            Date lastSyncDate = lastSyncTimeService.getLastSyncTimeFor(APIName.DOWNLOAD_FORMS);
            List<Form> forms = formService.downloadFormsByName(StringUtils.EMPTY, lastSyncDate);
            LastSyncTime lastSyncTime = new LastSyncTime(APIName.DOWNLOAD_FORMS, sntpService.getTimePerDeviceTimeZone());
            lastSyncTimeService.saveLastSyncTime(lastSyncTime);
            return forms;
        } catch (IOException e) {
            throw new FormFetchException(e);
        }
    }

    public List<FormTemplate> downloadFormTemplates(String[] formUuids) throws FormFetchException {
        ArrayList<FormTemplate> formTemplates = new ArrayList<>();
        for (String uuid : formUuids) {
            formTemplates.add(downloadFormTemplateByUuid(uuid));
        }
        return formTemplates;
    }

    public FormTemplate downloadFormTemplateByUuid(String uuid) throws FormFetchException {
        try {
            return formService.downloadFormTemplateByUuid(uuid);
        } catch (IOException e) {
            throw new FormFetchException(e);
        }
    }

    public void saveAllForms(List<Form> forms) throws FormSaveException {
        try {
            formService.saveForms(forms);
        } catch (IOException e) {
            throw new FormSaveException(e);
        }
    }

    public void updateAllForms(List<Form> forms) throws FormSaveException {
        try {
            formService.updateForms(forms);
        } catch (IOException e) {
            throw new FormSaveException(e);
        }
    }

    public void deleteAllForms() throws FormDeleteException {
        try {
            formService.deleteForms(formService.getAllForms());
        } catch (IOException e) {
            throw new FormDeleteException(e);
        }
    }

    public void deleteAllFormTemplates() throws FormDeleteException {
        try {
            formService.deleteFormTemplates(formService.getAllFormTemplates());
        } catch (IOException e) {
            throw new FormDeleteException(e);
        }
    }

    public void deleteForms(List<Form> forms) throws FormDeleteException {
        try {
            formService.deleteForms(forms);
        } catch (IOException e) {
            throw new FormDeleteException(e);
        }
    }

    public void deleteFormTemplatesByUUID(List<String> formTemplateUUIDs) throws FormDeleteException {
        try {
            formService.deleteFormTemplateByUUIDs(formTemplateUUIDs);
        } catch (IOException e) {
            throw new FormDeleteException(e);
        }
    }

    public void replaceFormTemplates(List<FormTemplate> formTemplates) throws FormSaveException {
        for (FormTemplate formTemplate : formTemplates) {
            FormTemplate existingFormTemplate = null;
            try {
                existingFormTemplate = formService.getFormTemplateByUuid(formTemplate.getUuid());
                if (existingFormTemplate != null) {
                    formService.deleteFormTemplate(existingFormTemplate);
                }
                formService.saveFormTemplate(formTemplate);
            } catch (IOException e) {
                throw new FormSaveException(e);
            }
        }
    }

    public void saveFormTemplates(List<FormTemplate> formTemplates) throws FormSaveException {

        try {
            formService.saveFormTemplates(formTemplates);
        } catch (IOException e) {
            throw new FormSaveException(e);
        }
    }

    public boolean isFormDownloaded(Form form) throws FormFetchException {
        boolean downloaded;
        try {
            downloaded = formService.isFormTemplateDownloaded(form.getUuid());
        } catch (IOException e) {
            throw new FormFetchException(e);
        }
        return downloaded;
    }

    public int getTagColor(String uuid) {
        if (!tagColors.containsKey(uuid)) {
            tagColors.put(uuid, CustomColor.getRandomColor());
        }
        return tagColors.get(uuid);
    }

    public void resetTagColors() {
        tagColors.clear();
    }

    public List<Tag> getSelectedTags() {
        return selectedTags;
    }

    public void setSelectedTags(List<Tag> selectedTags) {
        this.selectedTags = selectedTags;
    }

    public FormData getFormDataByUuid(String formDataUuid) throws FormDataFetchException {
        try {
            return formService.getFormDataByUuid(formDataUuid);
        } catch (IOException e) {
            throw new FormDataFetchException(e);
        }
    }

    public List<FormData> getFormDataByUuids(List<String> formDataUuids) throws FormDataFetchException {
        try {
            return formService.getFormDataByUuids(formDataUuids);
        } catch (IOException e) {
            throw new FormDataFetchException(e);
        }
    }

    public CompleteFormWithPatientData getCompleteFormDataByUuid(String formDataUuid) throws FormFetchException {
        CompleteFormWithPatientData completeForm = null;

        try {
            FormData formData = formService.getFormDataByUuid(formDataUuid);
            if (formData != null && (StringUtils.equals(formData.getStatus(), Constants.STATUS_UPLOADED)) ||
                    StringUtils.equals(formData.getStatus(), Constants.STATUS_COMPLETE)) {
                Patient patient = patientService.getPatientByUuid(formData.getPatientUuid());
                completeForm = new CompleteFormWithPatientDataBuilder()
                        .withForm(formService.getFormByUuid(formData.getTemplateUuid()))
                        .withFormDataUuid(formData.getUuid())
                        .withPatient(patient)
                        .withLastModifiedDate(formData.getSaveTime())
                        .withEncounterDate(formData.getEncounterDate())
                        .build();
            }

        } catch (IOException e) {
            throw new FormFetchException(e);
        }
        return completeForm;
    }

    public void saveFormData(FormData formData) throws FormDataSaveException {
        try {
            formData.setSaveTime(new Date());
            formService.saveFormData(formData);
        } catch (IOException e) {
            throw new FormDataSaveException(e);
        }
    }

    public List<FormData> getAllFormData(String status) throws FormDataFetchException {
        try {
            return formService.getAllFormData(status);
        } catch (Exception e) {
            throw new FormDataFetchException(e);
        }
    }

    public List<FormData> getAllFormDataByPatientUuid(String patientUuid, String status) throws FormDataFetchException {
        try {
            return formService.getFormDataByPatient(patientUuid, status);
        } catch (IOException e) {
            throw new FormDataFetchException(e);
        }
    }

    private int countAllFormDataByPatientUuid(String patientUuid, String status) throws FormDataFetchException {
        try {
            return formService.countFormDataByPatient(patientUuid, status);
        } catch (IOException e) {
            throw new FormDataFetchException(e);
        }
    }

    public IncompleteFormsWithPatientData getAllIncompleteFormsWithPatientData(String filterPatientUuid) throws FormFetchException {
        IncompleteFormsWithPatientData incompleteForms = new IncompleteFormsWithPatientData();

        try {
            List<FormData> allFormData;
            if(!StringUtils.isEmpty(filterPatientUuid)) {
                allFormData = formService.getFormDataByPatient(filterPatientUuid, Constants.STATUS_INCOMPLETE);
            } else {
                allFormData = formService.getAllFormData(Constants.STATUS_INCOMPLETE);
            }

            for (FormData formData : allFormData) {
                String patientUuid = formData.getPatientUuid();
                Patient patient = null;
                if (patientUuid != null) {
                    patient = patientService.getPatientByUuid(patientUuid);
                }
                if(patient == null){
                    try {
                        patient = new Patient();
                        org.json.JSONObject payloadObject = new org.json.JSONObject(formData.getJsonPayload());
                        org.json.JSONObject patientJSON = payloadObject.getJSONObject("patient");
                        final PersonName personName = PersonRegistrationUtils.createPersonName(patientJSON);
                        patient.setNames(new ArrayList<PersonName>() {{
                            add(personName);
                        }});
                    } catch (JSONException e) {
                        patient = null;
                        Log.e(getClass().getSimpleName(),"Could not create temporary patient",e);
                    }
                }
                incompleteForms.add(new IncompleteFormWithPatientDataBuilder()
                        .withForm(formService.getFormByUuid(formData.getTemplateUuid()))
                        .withFormDataUuid(formData.getUuid())
                        .withPatient(patient)
                        .withLastModifiedDate(formData.getSaveTime())
                        .withEncounterDate(formData.getEncounterDate())
                        .build());
            }
        } catch (IOException e) {
            throw new FormFetchException(e);
        }
        return incompleteForms;
    }

    public CompleteFormsWithPatientData getAllCompleteFormsWithPatientData(Context context, String filterPatientUuid) throws FormFetchException {
        CompleteFormsWithPatientData completeForms = new CompleteFormsWithPatientData();

        try {
            List<FormData> allFormData;
            if(!StringUtils.isEmpty(filterPatientUuid)) {
                allFormData = formService.getFormDataByPatient(filterPatientUuid, Constants.STATUS_COMPLETE);
            } else {
                allFormData = formService.getAllFormData(Constants.STATUS_COMPLETE);
            }

            for (FormData formData : allFormData) {
                Patient patient = patientService.getPatientByUuid(formData.getPatientUuid());
                Form form = formService.getFormByUuid(formData.getTemplateUuid());
                if (form != null) {
                    CompleteFormWithPatientData completeForm = new CompleteFormWithPatientDataBuilder()
                            .withForm(form)
                            .withFormDataUuid(formData.getUuid())
                            .withPatient(patient)
                            .withLastModifiedDate(formData.getSaveTime())
                            .withEncounterDate(formData.getEncounterDate())
                            .build();
                    completeForms.add(completeForm);
                } else {
                    if (formData.getDiscriminator() != null) {
                        if (formData.getDiscriminator().equals(Constants.FORM_JSON_DISCRIMINATOR_INDIVIDUAL_OBS)) {
                            CompleteFormWithPatientData completeForm = new CompleteFormWithPatientDataBuilder()
                                    .withIndividualObsForm(form, context)
                                    .withFormDataUuid(formData.getUuid())
                                    .withPatient(patient)
                                    .withLastModifiedDate(formData.getSaveTime())
                                    .withEncounterDate(formData.getEncounterDate())
                                    .build();
                            completeForms.add(completeForm);
                        }else if (formData.getDiscriminator().equals(Constants.FORM_JSON_DISCRIMINATOR_SHR_REGISTRATION)) {
                            CompleteFormWithPatientData completeForm = new CompleteFormWithPatientDataBuilder()
                                    .withSHRRegistrationForm(form, context)
                                    .withFormDataUuid(formData.getUuid())
                                    .withPatient(patient)
                                    .withLastModifiedDate(formData.getSaveTime())
                                    .withEncounterDate(formData.getEncounterDate())
                                    .build();
                            completeForms.add(completeForm);
                        }else if (formData.getDiscriminator().equals(FORM_JSON_DISCRIMINATOR_RELATIONSHIP)) {
                            CompleteFormWithPatientData completeForm = new CompleteFormWithPatientDataBuilder()
                                    .withRelationshipForm(form, context)
                                    .withFormDataUuid(formData.getUuid())
                                    .withPatient(patient)
                                    .withLastModifiedDate(formData.getSaveTime())
                                    .withEncounterDate(formData.getEncounterDate())
                                    .build();
                            completeForms.add(completeForm);
                        }else if (formData.getDiscriminator().equals(Constants.FORM_JSON_DISCRIMINATOR_DEMOGRAPHICS_UPDATE)) {
                            CompleteFormWithPatientData completeForm = new CompleteFormWithPatientDataBuilder()
                                    .withDemographicsUpdateForm(form, context)
                                    .withFormDataUuid(formData.getUuid())
                                    .withPatient(patient)
                                    .withLastModifiedDate(formData.getSaveTime())
                                    .withEncounterDate(formData.getEncounterDate())
                                    .build();
                            completeForms.add(completeForm);
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new FormFetchException(e);
        }
        return completeForms;
    }

    public IncompleteForms getAllIncompleteFormsForPatientUuid(String patientUuid) throws FormFetchException {
        IncompleteForms incompleteForms = new IncompleteForms();
        try {
            List<FormData> allFormData = formService.getFormDataByPatient(patientUuid, Constants.STATUS_INCOMPLETE);
            for (FormData formData : allFormData) {
                incompleteForms.add(new IncompleteFormBuilder().withForm(formService.getFormByUuid(formData.getTemplateUuid()))
                        .withFormDataUuid(formData.getUuid())
                        .withLastModifiedDate(formData.getSaveTime())
                        .withEncounterDate(formData.getEncounterDate())
                        .build());
            }
        } catch (IOException e) {
            throw new FormFetchException(e);
        }
        return incompleteForms;
    }

    public CompleteForms getAllCompleteFormsForPatientUuid(String patientUuid) throws FormFetchException {
        CompleteForms completePatientForms = new CompleteForms();
        try {
            List<FormData> allFormData = formService.getFormDataByPatient(patientUuid, Constants.STATUS_COMPLETE);
            for (FormData formData : allFormData) {
                Form form = formService.getFormByUuid(formData.getTemplateUuid());
                if (form != null) {
                    completePatientForms.add(new CompleteFormBuilder()
                            .withForm(form)
                            .withFormDataUuid(formData.getUuid())
                            .withLastModifiedDate(formData.getSaveTime())
                            .withEncounterDate(formData.getEncounterDate())
                            .build());
                }
            }
        } catch (IOException e) {
            throw new FormFetchException(e);
        }
        return completePatientForms;
    }

    public boolean isFormTemplateUpdatesAvailable() throws FormFetchException {
        try {
            List<Form> forms = formService.getAllForms();
            for (Form form : forms) {
                if (form.isUpdateAvailable() && formService.isFormTemplateDownloaded(form.getUuid())) {
                    return true;
                }
            }
            return false;
        } catch (IOException e){
            throw new FormFetchException(e);
        }
    }

    public int countAllIncompleteForms() throws FormFetchException {
        return getAllIncompleteFormsWithPatientData(StringUtils.EMPTY).size();
    }

    public int countAllCompleteForms() throws FormFetchException {
        return getAllCompleteFormsWithPatientData(null,StringUtils.EMPTY).size();
    }

    public int countCompleteFormsForPatient(String patientuuid) throws FormFetchException {
        return getAllCompleteFormsWithPatientData(null,patientuuid).size();
    }

    public int countIncompleteFormsForPatient(String patientuuid) throws FormFetchException {
        return getAllIncompleteFormsWithPatientData(patientuuid).size();
    }

    public AvailableForms getDownloadedRegistrationForms() throws FormFetchException {
        AvailableForms result = new AvailableForms();

        for (AvailableForm form : getAvailableFormByTags(null)) {
            if (form.isDownloaded() && form.isRegistrationForm()) {
                result.add(form);
            }
        }
        return result;
    }

    public Patient createNewPatient(MuzimaApplication muzimaApplication, FormData formData) throws FormDataProcessException {
        try {
            Patient patient;
            if (isGenericRegistrationHTMLFormData(formData)) {
                patient = new GenericPatientRegistrationJSONMapper().getPatient(muzimaApplication, formData.getJsonPayload());
            } else if (isRegistrationHTMLFormData(formData)) {
                patient = new HTMLPatientJSONMapper().getPatient(muzimaApplication, formData.getJsonPayload());
            } else if (isRegistrationXMLFormData(formData)) {
                patient = new PatientJSONMapper(formData.getJsonPayload()).getPatient();
            } else {
                throw new Exception("Could not determine type of registration form: ["+formData.getDiscriminator()+"]. Patient not created.");
            }
            patientService.savePatient(patient);
            return patient;
        } catch (Exception e) {
            throw new FormDataProcessException(e);
        }
    }

    public Patient updatePatient(MuzimaApplication muzimaApplication, FormData formData) throws FormDataProcessException {
        try {
            Patient patient = patientService.getPatientByUuid(formData.getPatientUuid());
            if(patient == null){
                throw new Exception("Could not find patient for demographics update.");
            }
            if (isDemographicsUpdateFormData(formData)) {
                new GenericPatientRegistrationJSONMapper().processDemographicsUpdateForPatient(muzimaApplication, formData.getJsonPayload(), patient);
            } else {
                throw new Exception("Could not determine type of demographics update form: ["+formData.getDiscriminator()+"]. Patient not updated.");
            }
            patientService.updatePatient(patient);
            return patient;
        } catch (Exception e) {
            throw new FormDataProcessException(e);
        }
    }

    public Person updatePerson(MuzimaApplication muzimaApplication, FormData formData) throws FormDataProcessException {
        try {
            Person person = personService.getPersonByUuid(formData.getPatientUuid());
            if(person == null){
                throw new Exception("Could not find person for demographics update.");
            }
            if (isPersonDemographicsUpdateFormData(formData)) {
                new GenericPatientRegistrationJSONMapper().processDemographicsUpdateForPerson(muzimaApplication, formData.getJsonPayload(), person);
            } else {
                throw new Exception("Could not determine type of person demographics update form: ["+
                        formData.getDiscriminator()+"]. Person not updated.");
            }
            personService.updatePerson(person);

            person = personService.getPersonByUuid(person.getUuid());
            return person;
        } catch (Exception e) {
            throw new FormDataProcessException(e);
        }
    }

    public boolean uploadAllCompletedForms() throws UploadFormDataException {
        try {
            boolean result = true;
            List<FormData> allFormData = formService.getAllFormData(Constants.STATUS_COMPLETE);

            result = uploadFormDataToServer(getFormsWithDiscriminator(allFormData, FORM_DISCRIMINATOR_REGISTRATION), result);
            result = uploadFormDataToServer(getFormsWithDiscriminator(allFormData, Constants.FORM_JSON_DISCRIMINATOR_INDIVIDUAL_OBS), result);
            result = uploadFormDataToServer(getFormsWithDiscriminator(allFormData, Constants.FORM_JSON_DISCRIMINATOR_RELATIONSHIP), result);
            result = uploadFormDataToServer(getFormsWithDiscriminator(allFormData, FORM_JSON_DISCRIMINATOR_REGISTRATION), result);
            result = uploadFormDataToServer(getFormsWithDiscriminator(allFormData, FORM_JSON_DISCRIMINATOR_SHR_REGISTRATION), result);
            result = uploadFormDataToServer(getFormsWithDiscriminator(allFormData, FORM_JSON_DISCRIMINATOR_GENERIC_REGISTRATION), result);
            result = uploadFormDataToServer(getFormsWithDiscriminator(allFormData, Constants.FORM_JSON_DISCRIMINATOR_DEMOGRAPHICS_UPDATE), result);
            result = uploadFormDataToServer(getFormsWithDiscriminator(allFormData, Constants.FORM_JSON_DISCRIMINATOR_SHR_DEMOGRAPHICS_UPDATE), result);
            result = uploadFormDataToServer(getFormsWithDiscriminator(allFormData, Constants.FORM_JSON_DISCRIMINATOR_CONSULTATION), result);
            result = uploadFormDataToServer(getFormsWithDiscriminator(allFormData, Constants.FORM_XML_DISCRIMINATOR_ENCOUNTER), result);
            result = uploadFormDataToServer(getFormsWithDiscriminator(allFormData, Constants.FORM_JSON_DISCRIMINATOR_ENCOUNTER), result);
            result = uploadFormDataToServer(getFormsWithDiscriminator(allFormData, Constants.FORM_JSON_DISCRIMINATOR_SHR_ENCOUNTER), result);
            return uploadFormDataToServer(getFormsWithDiscriminator(allFormData, FORM_JSON_DISCRIMINATOR_PERSON_UPDATE), result);
        } catch (IOException e) {
            throw new UploadFormDataException(e);
        }
    }

    public AvailableForms getRecommendedForms() throws FormFetchException {
        AvailableForms result = new AvailableForms();
        for (AvailableForm form : getRecommendedFormsByTagsSortedByConfigOrder(null, false)) {
            if (form.isDownloaded() && !form.isRegistrationForm() && !form.isProviderReport()
                    && !form.isProviderPerformanceReport()
                    && !form.isRelationshipForm() && !form.isPersonUpdateForm()) {
                result.add(form);
            }
        }
        return result;
    }

    public AvailableForms getProviderReports() throws FormFetchException {
        AvailableForms result = new AvailableForms();
        for (AvailableForm form : getAvailableFormByTags(null)) {
            if (form.isDownloaded() && (form.isProviderReport() || form.isProviderPerformanceReport())) {
                result.add(form);
            }
        }
        return result;
    }

    public int getRecommendedFormsCount() throws FormFetchException {
        return getRecommendedForms().size();
    }

    private boolean isRegistrationTag(Tag formTag) {
        String REGISTRATION = "registration";
        return REGISTRATION.equalsIgnoreCase(formTag.getName());
    }

    public void deleteCompleteAndIncompleteEncounterFormData(List<String> formDataUuids) throws FormDataDeleteException {
        try {
            List<FormData> formDataList = getFormDataByUuids(formDataUuids);
            deleteEncounterFormDataAndRelatedPatientData(formDataList);
        } catch (FormDataFetchException e) {
            throw new FormDataDeleteException(e);
        }
    }

    public void deleteCompleteAndIncompleteFormData(List<FormData> formDataList) throws FormDataDeleteException {
        deleteEncounterFormDataAndRelatedPatientData(formDataList);
    }

    public boolean isFormWithPatientDataAvailable(Context context) throws FormFetchException {
        return !(getAllIncompleteFormsWithPatientData(StringUtils.EMPTY).isEmpty() &&
                getAllCompleteFormsWithPatientData(context,StringUtils.EMPTY).isEmpty());
    }

    public static class FormDataProcessException extends Throwable {
        FormDataProcessException(Throwable throwable) {
            super(throwable);
        }
    }

    public static class UploadFormDataException extends Throwable {
        UploadFormDataException(Throwable throwable) {
            super(throwable);
        }
    }

    public static class FormFetchException extends Throwable {
        public FormFetchException(Throwable throwable) {
            super(throwable);
        }
    }

    public static class FormSaveException extends Throwable {
        public FormSaveException(Throwable throwable) {
            super(throwable);
        }
    }

    public static class FormDeleteException extends Throwable {
        public FormDeleteException(Throwable throwable) {
            super(throwable);
        }
    }

    public static class FormDataFetchException extends Throwable {
        public FormDataFetchException(Throwable throwable) {
            super(throwable);
        }
    }

    public static class FormDataDeleteException extends Throwable {
        public FormDataDeleteException(Throwable throwable) {
            super(throwable);
        }
    }

    public static class FormDataSaveException extends Throwable {
        public FormDataSaveException(Throwable throwable) {
            super(throwable);
        }
    }

    public static class FormDataStatusDownloadException extends Throwable {
        public FormDataStatusDownloadException(Throwable throwable) {
            super(throwable);
        }
    }

    public List<FormData> getNonUploadedFormData(String templateUUID) throws FormDataFetchException {
        List<FormData> incompleteFormData = new ArrayList<>();
        try {
            List<FormData> formDataByTemplateUUID = formService.getFormDataByTemplateUUID(templateUUID);
            for (FormData formData : formDataByTemplateUUID) {
                if (!formData.getStatus().equals(Constants.STATUS_UPLOADED)) {
                    incompleteFormData.add(formData);
                }
            }
            return incompleteFormData;
        } catch (IOException e) {
            throw new FormDataFetchException(e);
        }
    }

    private List<FormData> getFormsWithDiscriminator(List<FormData> allFormData, String discriminator) {
        List<FormData> requiredForms = new ArrayList<>();
        for (FormData formData : allFormData) {
            if (formData.getDiscriminator().equals(discriminator)) {
                requiredForms.add(formData);
            }
        }
        return requiredForms;
    }

    boolean uploadFormDataToServer(List<FormData> allFormData, boolean result) throws IOException {
        for (FormData formData : allFormData) {
            String rawPayload = formData.getJsonPayload();
            // inject consultation.sourceUuid
            formData = injectUuidToPayload(formData);
            // replace media paths with base64 string
            formData = replaceMediaPathWithBase64String(formData);
            if (formService.syncFormData(formData)) {
                formData.setStatus(STATUS_UPLOADED);

                //DO NOT save base64 string in DB
                formData.setJsonPayload(rawPayload);
                formService.saveFormData(formData);
                MuzimaLoggerService.log(muzimaApplication, "SYNCED_FORM_DATA", "{\"formDataUuid\":\"" + formData.getUuid() + "\"}");

            } else {
                result = false;
            }
        }
        return result;
    }

    public void markFormDataAsIncompleteAndDeleteRelatedEncountersAndObs(final FormData formData) throws FormDataSaveException, FormDataDeleteException {
        deletePatientDataRelatedToFormData(new ArrayList<FormData>() {{
            add(formData);
        }});
        formData.setStatus(STATUS_INCOMPLETE);
        saveFormData(formData);
    }

    public void deleteFormDataAndRelatedEncountersAndObs(List<FormData> formData) throws FormDataDeleteException {
        try {
            deleteEncounterFormDataAndRelatedPatientData(formData);
            formService.deleteFormData(formData);
        } catch (IOException e) {
            throw new FormDataDeleteException(e);
        }
    }

    public List<FormData> getArchivedFormData() throws FormDataFetchException {
        try {
            return formService.getAllFormData(STATUS_UPLOADED);
        } catch (IOException e) {
            throw new FormDataFetchException(e);
        }
    }

    public FormDataStatus downloadFormDataStatus(FormData formData) throws FormDataStatusDownloadException {
        try {
            return formService.downloadFormDataStatusByFormDataUuid(formData.getUuid());
        } catch (IOException e) {
            throw new FormDataStatusDownloadException(e);
        }
    }

    private static FormData injectUuidToPayload(FormData formData) {

        if (StringUtils.equals(formData.getDiscriminator(), FORM_JSON_DISCRIMINATOR_CONSULTATION)) {
            try {
                String base = "consultation";
                JSONParser jp = new JSONParser(JSONParser.MODE_PERMISSIVE);
                JSONObject obj = (JSONObject) jp.parse(formData.getJsonPayload());
                JsonUtils.replaceAsString(obj, base, "consultation.sourceUuid", formData.getUuid());
                formData.setJsonPayload(obj.toJSONString());
            } catch (ParseException e) {
                Log.e(FormController.class.getSimpleName(), e.getMessage(), e);
            }
        }
        return formData;
    }

    private void traverseJson(JSONObject json) {
        for (String key : json.keySet()) {
            String val = null;
            try {
                Object obj = JsonUtils.readAsObject(json.toJSONString(), "$['" + key + "']");
                if (obj instanceof JSONArray) {
                    JSONArray arr = (JSONArray) obj;
                    for (Object object : arr) {
                        traverseJson((JSONObject) object);
                    }
                } else {
                    traverseJson((JSONObject) obj);
                }
            } catch (Exception e) {
                val = json.get(key).toString();
            }

            if (val != null) {
                replaceMediaPathWithMedia(key, val);
            }
        }
    }

    private void replaceMediaPathWithMedia(String key, String value) {
        if (value.contains("/muzima/media/")) {
            String newKeyValPair = "\"" + key + "\":\"" + getStringMedia(value) + "\"";
            if (!jsonPayload.contains(value)) {
                value = value.replace("/", "\\/");
            }
            String keyValPair = "\"" + key + "\":\"" + value + "\"";

            jsonPayload = jsonPayload.replace(keyValPair, newKeyValPair);
        }
    }

    private FormData replaceMediaPathWithBase64String(FormData formData) {
        try {
            jsonPayload = formData.getJsonPayload();
            JSONParser jp = new JSONParser(JSONParser.MODE_PERMISSIVE);
            traverseJson((JSONObject) jp.parse(jsonPayload));
            formData.setJsonPayload(jsonPayload);
        } catch (ParseException e) {
            Log.e("FormController", e.getMessage(), e);
        }
        return formData;
    }

    private static String getStringMedia(String mediaUri) {
        String mediaString = null;
        if (!StringUtils.isEmpty(mediaUri)) {
            //fetch the media and convert it to @Base64 encoded string.
            File f = new File(mediaUri);
            if (f.exists()) {

                // here the media file is encrypted so we decrypt it
                EnDeCrypt.decrypt(f, "this-is-supposed-to-be-a-secure-key");

                try {
                    FileInputStream fis = new FileInputStream(f);
                    byte[] fileBytes = new byte[(int) f.length()];
                    int read = fis.read(fileBytes);
                    if (read != f.length()) {
                        Log.e("FormController", "File read is not equal to the length of the file itself.");
                    }

                    //convert the decrypted media to Base64 string
                    mediaString = MediaUtils.toBase64(fileBytes);
                } catch (Exception e) {
                    Log.e("FormController", e.getMessage(), e);
                }

                // and encrypt again
                EnDeCrypt.encrypt(f, "this-is-supposed-to-be-a-secure-key");
            }
        }
        return mediaString != null ? mediaString : mediaUri;
    }

    public boolean isFormAlreadyExist(String jsonPayload, FormData formData) throws IOException, JSONException {
        org.json.JSONObject temp = new org.json.JSONObject(jsonPayload);
        String checkEncounterDate = ((org.json.JSONObject) temp.get("encounter")).get("encounter.encounter_datetime").toString();
        String checkPatientUuid = ((org.json.JSONObject) temp.get("patient")).get("patient.uuid").toString();
        String checkFormUuid = ((org.json.JSONObject) temp.get("encounter")).get("encounter.form_uuid").toString();

        List<FormData> allFormData = formService.getAllFormData(Constants.STATUS_INCOMPLETE);
        allFormData.addAll(formService.getAllFormData(Constants.STATUS_COMPLETE));
        for (FormData formData1 : allFormData) {
            if (!isRegistrationFormData(formData1) && !formData1.getUuid().equals(formData.getUuid())) {
                org.json.JSONObject object = new org.json.JSONObject(formData1.getJsonPayload());
                String encounterDate = ((org.json.JSONObject) object.get("encounter")).get("encounter.encounter_datetime").toString();
                String patientUuid = ((org.json.JSONObject) object.get("patient")).get("patient.uuid").toString();
                String formUuid = ((org.json.JSONObject) object.get("encounter")).get("encounter.form_uuid").toString();
                if (encounterDate.equals(checkEncounterDate)
                        && patientUuid.equals(checkPatientUuid)
                        && formUuid.equals(checkFormUuid)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isRegistrationFormDataWithEncounterForm(String formUuid) throws FormDataFetchException {
        FormData formData = getFormDataByUuid(formUuid);
        return isRegistrationFormData(formData) && hasEncounterForm(formData);
    }

    private boolean hasEncounterForm(FormData registrationFormData) throws FormDataFetchException {
        return countAllFormDataByPatientUuid(registrationFormData.getPatientUuid(), null) > 1;
    }

    public boolean isRegistrationFormData(FormData formData) {
        return formData.getDiscriminator().equalsIgnoreCase(FORM_DISCRIMINATOR_REGISTRATION)
                || formData.getDiscriminator().equalsIgnoreCase(FORM_JSON_DISCRIMINATOR_REGISTRATION)
                || formData.getDiscriminator().equalsIgnoreCase(FORM_JSON_DISCRIMINATOR_GENERIC_REGISTRATION);
    }

    public boolean isGenericRegistrationHTMLFormData(FormData formData) {
        return (formData.getDiscriminator() != null)
                && formData.getDiscriminator().equalsIgnoreCase(FORM_JSON_DISCRIMINATOR_GENERIC_REGISTRATION);
    }

    public boolean isPersonRegistrationHTMLFormData(FormData formData) {
        return (formData.getDiscriminator() != null)
                && formData.getDiscriminator().equalsIgnoreCase(FORM_JSON_DISCRIMINATOR_RELATIONSHIP);
    }

    public boolean isPersonUpdateHTMLFormData(FormData formData) {
        return (formData.getDiscriminator() != null)
                && formData.getDiscriminator().equalsIgnoreCase(FORM_JSON_DISCRIMINATOR_PERSON_UPDATE);
    }

    private boolean isRegistrationHTMLFormData(FormData formData) {
        return (formData.getDiscriminator() != null)
                && formData.getDiscriminator().equalsIgnoreCase(FORM_JSON_DISCRIMINATOR_REGISTRATION);
    }

    private boolean isRegistrationXMLFormData(FormData formData) {
        return (formData.getDiscriminator() != null)
                && formData.getDiscriminator().equalsIgnoreCase(FORM_DISCRIMINATOR_REGISTRATION);
    }

    public boolean isDemographicsUpdateFormData(FormData formData) {
        return (formData.getDiscriminator() != null)
                && formData.getDiscriminator().equalsIgnoreCase(FORM_JSON_DISCRIMINATOR_DEMOGRAPHICS_UPDATE);
    }

    private boolean isPersonDemographicsUpdateFormData(FormData formData) {
        return (formData.getDiscriminator() != null)
                && formData.getDiscriminator().equalsIgnoreCase(FORM_JSON_DISCRIMINATOR_PERSON_UPDATE);
    }

    public boolean isEncounterFormData(FormData formData) {
        return (formData.getDiscriminator() != null) && !isRegistrationFormData(formData);
    }

    private boolean isCompleteFormData(FormData formData) {
        return StringUtils.equals(formData.getStatus(), Constants.STATUS_COMPLETE);
    }

    private boolean isArchivedFormData(FormData formData) {
        return StringUtils.equals(formData.getStatus(), STATUS_UPLOADED);
    }

    public Map<String, List<FormData>> getFormDataGroupedByPatient(List<String> uuids) throws FormDataFetchException {
        Map<String, List<FormData>> formDataMap = new HashMap<>();
        List<FormData> formDataList = getFormDataByUuids(uuids);
        if (!formDataList.isEmpty()) {
            for (FormData formData : formDataList) {
                String patientUuid = formData.getPatientUuid();
                if (formDataMap.containsKey(patientUuid)) {
                    formDataMap.get(patientUuid).add(formData);
                } else {
                    List patientFormData = new ArrayList<FormData>();
                    patientFormData.add(formData);
                    formDataMap.put(patientUuid, patientFormData);
                }
            }
        }
        return formDataMap;
    }

    public Map<String, List<FormData>> deleteFormDataWithNoRelatedCompleteRegistrationFormDataInGroup(
            Map<String, List<FormData>> groupedFormData) throws FormDataDeleteException {
        Map<String, List<FormData>> remnantData = new HashMap<>();
        Log.e(getClass().getSimpleName(),"deleting individual Obs");
        for (String patientUuid : groupedFormData.keySet()) {
            List<FormData> formDataList = groupedFormData.get(patientUuid);
            boolean hasRegistration = false;
            for (FormData data : formDataList) {
                if (isRegistrationFormData(data) && isCompleteFormData(data)) {
                    hasRegistration = true;
                    break;
                }
            }
            if (hasRegistration) {
                remnantData.put(patientUuid, formDataList);
            } else {
                deleteEncounterFormDataAndRelatedPatientData(formDataList);
            }
        }
        return remnantData;
    }

    public void deleteEncounterFormDataAndRelatedPatientData(List<FormData> formDataList) throws FormDataDeleteException {
        try {
            for (FormData formData : formDataList) {
                boolean parseAsFormData = false;
                if(formData.getDiscriminator().equals(Constants.FORM_JSON_DISCRIMINATOR_INDIVIDUAL_OBS)){
                    String jsonPayload = formData.getJsonPayload();
                    org.json.JSONObject responseJSON = new org.json.JSONObject(jsonPayload);
                    org.json.JSONObject encounterObject = responseJSON.getJSONObject("encounter");
                    if(encounterObject.has("encounter.encounter_uuid")) {
                        String encounterUuid = encounterObject.getString("encounter.encounter_uuid");
                        List<Observation> observations = observationService.getObservationsByEncounter(encounterUuid);
                        observationService.deleteObservations(observations);
                        formService.deleteFormData(formData);
                    } else {
                        parseAsFormData = true;
                    }
                }

                if(parseAsFormData || !formData.getDiscriminator().equals(Constants.FORM_JSON_DISCRIMINATOR_INDIVIDUAL_OBS)){
                    if (isCompleteFormData(formData) || isArchivedFormData(formData)) {
                        List<Encounter> encounters = encounterService.getEncountersByFormDataUuid(formData.getUuid());
                        for (Encounter encounter : encounters) {
                            List<Observation> observations = observationService.getObservationsByEncounter(encounter.getUuid());
                            observationService.deleteObservations(observations);
                        }
                        encounterService.deleteEncounters(encounters);
                    }
                    formService.deleteFormData(formData);
                }
            }
            muzimaApplication.getPatientController().deletePatientsPendingDeletion();
        } catch (IOException e) {
            throw new FormDataDeleteException(e);
        } catch (JSONException e) {
            Log.e(getClass().getSimpleName(),"Encounter an JSON exception",e);
        }
    }

    private void deletePatientDataRelatedToFormData(List<FormData> formDataList) throws FormDataDeleteException {
        try {
            for (FormData formData : formDataList) {
                if (isCompleteFormData(formData) || isArchivedFormData(formData)) {
                    List<Encounter> encounters = encounterService.getEncountersByFormDataUuid(formData.getUuid());
                    for (Encounter encounter : encounters) {
                        List<Observation> observations = observationService.getObservationsByEncounter(encounter.getUuid());
                        observationService.deleteObservations(observations);
                    }
                    encounterService.deleteEncounters(encounters);
                }
            }
        } catch (IOException e) {
            throw new FormDataDeleteException(e);
        }
    }

    public Map<String, List<FormData>> deleteRegistrationFormDataWithAllRelatedEncountersInGroup(Map<String, List<FormData>> groupedFormData) throws FormDeleteException, FormDataFetchException {
        try {
            Map<String, List<FormData>> remnantData = new HashMap<>();
            for (String patientUuid : groupedFormData.keySet()) {
                List<FormData> formDataList = groupedFormData.get(patientUuid);
                int actualPatientFormDataCount = countAllFormDataByPatientUuid(patientUuid, null);
                if (actualPatientFormDataCount == formDataList.size()) {
                    formService.deleteFormData(formDataList);
                    Patient patient = patientService.getPatientByUuid(patientUuid);
                    if (patient != null) {
                        patientService.deletePatient(patient);
                    }
                } else {
                    remnantData.put(patientUuid, formDataList);
                }
            }
            return remnantData;
        } catch (IOException e) {
            throw new FormDeleteException(e);
        }
    }

    public AvailableForms getRecommendedFormsByTagsSortedByConfigOrder(List<String> tagsUuid, boolean alwaysIncludeRegistrationForms) throws FormFetchException {
        try {
            List<Form> allForms = formService.getAllForms();
            List<Form> filteredForms = filterFormsByTags(allForms, tagsUuid, alwaysIncludeRegistrationForms);
            ArrayList<String> formUuids = getFormListAsPerConfigOrder();
            AvailableForms availableForms = getRecommendedFormsByConfigOrder(formUuids, filteredForms);
            return availableForms;
        } catch (IOException e) {
            throw new FormFetchException(e);
        }
    }

    public AvailableForms getDownloadedRelationshipForms() throws FormFetchException {
        AvailableForms result = new AvailableForms();

        for (AvailableForm form : getAvailableFormByTags(null)) {
            if (form.isDownloaded() && form.isRelationshipForm()) {
                result.add(form);
            }
        }
        return result;
    }

    public AvailableForms getDownloadedPersonUpdateForms() throws FormFetchException {
        AvailableForms result = new AvailableForms();

        for (AvailableForm form : getAvailableFormByTags(null)) {
            if (form.isDownloaded() && form.isPersonUpdateForm()) {
                result.add(form);
            }
        }
        return result;
    }

    public AvailableForm getAvailableFormByFormUuid(String uuid) throws FormFetchException {
        AvailableForm availableForm = new AvailableForm();
        try {
            Form form = formService.getFormByUuid(uuid);
            boolean downloadStatus = formService.isFormTemplateDownloaded(uuid);
            availableForm = new AvailableFormBuilder()
                    .withAvailableForm(form)
                    .withDownloadStatus(downloadStatus)
                    .withUpdateAvailableStatus(form.isUpdateAvailable()).build();
        } catch (IOException e) {
            throw new FormFetchException(e);
        }
        return availableForm;
    }

    public ArrayList<String> getFormListAsPerConfigOrder() throws IOException {
        List<SetupConfigurationTemplate> setupConfigurationTemplates = setupConfigurationService.getSetupConfigurationTemplates();
        ArrayList<String> formUuids = new ArrayList<>();
        for (SetupConfigurationTemplate setupConfigurationTemplate : setupConfigurationTemplates) {
            org.json.JSONObject object = null;
            try {
                object = new org.json.JSONObject(setupConfigurationTemplate.getConfigJson());
                org.json.JSONObject forms = object.getJSONObject("config");
                org.json.JSONArray formsArray = forms.getJSONArray("forms");
                for (int i = 0; i < formsArray.length(); i++) {
                    org.json.JSONObject formObject = formsArray.getJSONObject(i);
                    Form form = formService.getFormByUuid(formObject.get("uuid").toString());
                    if (form != null) {
                        formUuids.add(formObject.get("uuid").toString());
                    } else {
                        Log.d(getClass().getSimpleName(), "Could not find form with uuid = " + formObject.get("uuid").toString() +
                                " specified in setup config with uuid = " + setupConfigurationTemplate.getUuid());
                    }
                }
            } catch (JSONException e) {
                Log.e(getClass().getSimpleName(), "Encountered JsonException while sorting forms");
            }
        }
        return formUuids;
    }

    public AvailableForms getRecommendedFormsByConfigOrder(ArrayList<String> formUuids, List<Form> filteredForms) throws IOException{
        AvailableForms availableForms = new AvailableForms();
        if (formUuids.size() > 0) {
            for (String formUuid : formUuids) {
                Form form = formService.getFormByUuid(formUuid);
                boolean downloadStatus = formService.isFormTemplateDownloaded(form.getUuid());
                AvailableForm availableForm = new AvailableFormBuilder().withAvailableForm(form)
                        .withDownloadStatus(downloadStatus)
                        .withUpdateAvailableStatus(form.isUpdateAvailable()).build();
                availableForms.add(availableForm);
            }
        }

        //add forms not in the setup config
        boolean isDisplayOnlyFormsInSetupConfig= muzimaApplication.getMuzimaSettingController().isDisplayOnlyFormsInConfigEnabled();
        if(!isDisplayOnlyFormsInSetupConfig) {
            for (Form filteredForm : filteredForms) {
                if (!formUuids.contains(filteredForm.getUuid())) {
                    boolean downloadStatus = formService.isFormTemplateDownloaded(filteredForm.getUuid());
                    AvailableForm availableForm = new AvailableFormBuilder()
                            .withAvailableForm(filteredForm)
                            .withDownloadStatus(downloadStatus)
                            .withUpdateAvailableStatus(filteredForm.isUpdateAvailable()).build();
                    availableForms.add(availableForm);
                }
            }
        }

        return availableForms;
    }

    public AvailableForms getAvailableForms(List<Form> filteredForms) throws IOException {
        AvailableForms availableFormsDownloaded = new AvailableForms();
        AvailableForms availableFormsNotDownloaded = new AvailableForms();
        AvailableForms availableForms = new AvailableForms();
        for (Form filteredForm : filteredForms) {
            boolean downloadStatus = formService.isFormTemplateDownloaded(filteredForm.getUuid());
            AvailableForm availableForm = new AvailableFormBuilder().withAvailableForm(filteredForm)
                    .withDownloadStatus(downloadStatus)
                    .withUpdateAvailableStatus(filteredForm.isUpdateAvailable()).build();
            if(downloadStatus){
                availableFormsDownloaded.add(availableForm);
            }else{
                availableFormsNotDownloaded.add(availableForm);

            }
        }
        if(availableFormsDownloaded.size()>0)
            availableForms.addAll(availableFormsDownloaded);
        if(availableFormsNotDownloaded.size()>0)
            availableForms.addAll(availableFormsNotDownloaded);

        return availableForms;
    }
}
