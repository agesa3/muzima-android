/*
 * Copyright (c) The Trustees of Indiana University, Moi University
 * and Vanderbilt University Medical Center. All Rights Reserved.
 *
 * This version of the code is licensed under the MPL 2.0 Open Source license
 * with additional health care disclaimer.
 * If the user is an entity intending to commercialize any application that uses
 * this code in a for-profit venture, please contact the copyright holder.
 */

package com.muzima.service;

import android.util.Log;
import com.muzima.MuzimaApplication;
import com.muzima.api.model.Concept;
import com.muzima.api.model.Encounter;
import com.muzima.api.model.Observation;
import com.muzima.api.model.Patient;
import com.muzima.api.model.Person;
import com.muzima.controller.ConceptController;
import com.muzima.controller.EncounterController;
import com.muzima.controller.ObservationController;
import com.muzima.controller.PatientController;

import com.muzima.controller.PersonController;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import static com.muzima.utils.DateUtils.parse;

public class HTMLFormObservationCreator {

    private final PatientController patientController;
    private final PersonController personController;
    private final ConceptController conceptController;
    private final EncounterController encounterController;
    private final ObservationController observationController;
    private final ObservationParserUtility observationParserUtility;

    private Person person;
    private Encounter encounter;
    private List<Observation> observations;
    private boolean createObservationsForConceptsNotAvailableLocally;
    private boolean parseAsObsForPerson;

    public HTMLFormObservationCreator(MuzimaApplication muzimaApplication, boolean createObservationsForConceptsNotAvailableLocally, boolean parseAsObsForPerson) {
        this.patientController = muzimaApplication.getPatientController();
        this.personController = muzimaApplication.getPersonController();
        this.conceptController = muzimaApplication.getConceptController();
        this.encounterController = muzimaApplication.getEncounterController();
        this.observationController = muzimaApplication.getObservationController();
        this.observationParserUtility = new ObservationParserUtility(muzimaApplication,createObservationsForConceptsNotAvailableLocally);
        this.createObservationsForConceptsNotAvailableLocally = createObservationsForConceptsNotAvailableLocally;
        this.parseAsObsForPerson = parseAsObsForPerson;
    }

    public void createAndPersistObservations(String jsonResponse,String formDataUuid) {
        parseJSONResponse(jsonResponse,formDataUuid);

        try {
            saveObservationsAndRelatedEntities();
        } catch (ConceptController.ConceptSaveException e) {
            Log.e(getClass().getSimpleName(), "Error while saving concept", e);
        } catch (EncounterController.SaveEncounterException e) {
            Log.e(getClass().getSimpleName(), "Error while saving Encounter", e);
        } catch (ObservationController.SaveObservationException e) {
            Log.e(getClass().getSimpleName(), "Error while saving Observation", e);
        } catch (Exception e) {
            Log.e(getClass().getSimpleName(), "Unexpected Exception occurred", e);
        }
    }

    public void createObservationsAndRelatedEntities(String jsonResponse,String formDataUuid) {
        parseJSONResponse(jsonResponse,formDataUuid);
    }

    public List<Observation> getObservations() {
        return observations != null ? observations : new ArrayList<Observation>();
    }
    public Encounter getEncounter() {
        return encounter;
    }
    public List<Concept> getNewConceptList() {
        return observationParserUtility.getNewConceptList();
    }

    private void parseJSONResponse(String jsonResponse, String formDataUuid) {
        try {
            JSONObject responseJSON = new JSONObject(jsonResponse);
            if(parseAsObsForPerson){
                person = getPerson(responseJSON.getJSONObject("patient"));
            } else {
                person = getPatient(responseJSON.getJSONObject("patient"));
            }
            encounter = createEncounter(responseJSON.getJSONObject("encounter"), formDataUuid);

            if (responseJSON.has("observation")) {
                observations = extractObservationFromJSONObject(responseJSON.getJSONObject("observation"));
            }
        } catch (PatientController.PatientLoadException e) {
            Log.e(getClass().getSimpleName(), "Error while fetching Patient", e);
        } catch (PersonController.PersonLoadException e) {
            Log.e(getClass().getSimpleName(), "Error while fetching Person", e);
        } catch (ConceptController.ConceptFetchException e) {
            Log.e(getClass().getSimpleName(), "Error while fetching Concept", e);
        }catch (JSONException | ParseException e) {
            Log.e(getClass().getSimpleName(), "Error while parsing response JSON", e);
        } catch (ConceptController.ConceptSaveException e) {
            Log.e(getClass().getSimpleName(), "Error while saving newly created concept", e);
        }
    }

    private void saveObservationsAndRelatedEntities() throws EncounterController.SaveEncounterException,
            ObservationController.SaveObservationException, ConceptController.ConceptSaveException {

        try {

            encounterController.saveEncounters(Collections.singletonList(encounter));

            if(createObservationsForConceptsNotAvailableLocally) {
                conceptController.saveConcepts(observationParserUtility.getNewConceptList());
            }

            if (observations != null && !observations.isEmpty()) {
                observationController.saveObservations(observations);
            }
        } catch (Exception e) {
            Log.e(getClass().getSimpleName(), "Error while parsing and storing Observations.", e);
        }
    }

    private List<Observation> extractObservationFromJSONObject(JSONObject jsonObject) throws JSONException,
            ConceptController.ConceptFetchException,ConceptController.ConceptSaveException{
        List<Observation> observations = new ArrayList<>();
        Iterator keys = jsonObject.keys();
        while (keys.hasNext()) {
            String key = (String) keys.next();
            observations.addAll(extractBasedOnType(jsonObject, key));
        }
        observations.removeAll(Collections.singleton(null));
        return observations;
    }

    private List<Observation> extractBasedOnType(JSONObject jsonObject, String key) throws JSONException,
            ConceptController.ConceptFetchException, ConceptController.ConceptSaveException{
        if (jsonObject.get(key) instanceof JSONArray) {
            return createMultipleObservation(key, jsonObject.getJSONArray(key));
        } else if (jsonObject.get(key) instanceof JSONObject) {
            return extractObservationFromJSONObject(jsonObject.getJSONObject(key));
        }
        ArrayList<Observation> observations = new ArrayList<>();
        observations.add(createObservation(key, jsonObject.getString(key)));
        return observations;
    }

    private List<Observation> createMultipleObservation(String conceptName, JSONArray jsonArray) throws JSONException,
            ConceptController.ConceptFetchException, ConceptController.ConceptSaveException{
        List<Observation> observations = new ArrayList<>();
        for (int i = 0; i < jsonArray.length(); i++) {
            if (jsonArray.get(i) instanceof JSONObject) {
                observations.addAll(extractObservationFromJSONObject(jsonArray.getJSONObject(i)));
            } else {
                observations.add(createObservation(conceptName, jsonArray.getString(i)));
            }
        }
        return observations;
    }

    private Observation createObservation(String conceptName, String value) throws
            ConceptController.ConceptFetchException {
        try {
            Concept concept = observationParserUtility.getConceptEntity(conceptName,
                    ObservationParserUtility.isFormattedAsConcept(value),
                    createObservationsForConceptsNotAvailableLocally);
            if(concept != null) {
                Observation observation = observationParserUtility.getObservationEntity(concept, value);
                observation.setPerson(person);
                observation.setEncounter(encounter);
                observation.setObservationDatetime(encounter.getEncounterDatetime());
                return observation;
            }
        } catch (ConceptController.ConceptParseException e) {
            Log.e(getClass().getSimpleName(), "Error while parsing Concept", e);
        } catch (ObservationController.ParseObservationException e) {
            Log.e(getClass().getSimpleName(), "Error while parsing Observation", e);
        }
        return null;
    }

    private Encounter createEncounter(JSONObject encounterJSON, String formDataUuid) throws JSONException, ParseException {
        Date encounterDate = parse(encounterJSON.getString("encounter.encounter_datetime"));
        return observationParserUtility.getEncounterEntity(encounterDate,
                encounterJSON.getString("encounter.form_uuid"), encounterJSON.getString("encounter.provider_id"),
                Integer.parseInt(encounterJSON.getString("encounter.location_id")),
                encounterJSON.getString("encounter.user_system_id"),formDataUuid, person, parseAsObsForPerson);
    }

    public Date getEncounterDateFromFormDate(String jsonResponse){
        try {
            JSONObject jsonObject = new JSONObject(jsonResponse);
            JSONObject jsonObjectInner = jsonObject.getJSONObject("encounter");
            DateFormat dateTimeFormat = new SimpleDateFormat("dd-MM-yyyy HH:mm");
            String dateTime = jsonObjectInner.getString("encounter.encounter_datetime");
            if(dateTime.length()<=10){
                 dateTime = dateTime.concat(" 00:10");
            }
            return  dateTimeFormat.parse(dateTime);
        } catch (JSONException | ParseException e) {
            Log.e(getClass().getSimpleName(), "Error while parsing response JSON", e);
        }
        return null;
    }

    private Patient getPatient(JSONObject patient) throws JSONException, PatientController.PatientLoadException {
        String uuid = patient.getString("patient.uuid");
        return patientController.getPatientByUuid(uuid);
    }

    private Person getPerson(JSONObject person) throws JSONException, PersonController.PersonLoadException {
        String uuid = person.getString("patient.uuid");
        return personController.getPersonByUuid(uuid);
    }
}
