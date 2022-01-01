package com.google.mediapipe.apps.wearableai.nlp;

import java.util.Arrays;

import android.util.Log;

import com.google.mediapipe.apps.wearableai.comms.MessageTypes;
import android.content.Context;

//rxjava internal comms
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import org.json.JSONObject;
import org.json.JSONException;

import java.util.ArrayList;

//CSV
import com.opencsv.CSVReader;
import java.io.IOException;
import java.io.FileReader;
import java.util.List;
import java.io.InputStream;
import java.io.InputStreamReader;

public class WearableReferencerAutocite {
    private static final String TAG = "WearableIntelligenceSystem_WearableReferencer";

    private final String referencesFileName = "wearable_referencer_references.csv";
    private List<String []> myReferences;
    private Context context;


    //voice command fuzzy search threshold
    private final double referenceThreshold = 0.90;
    private final int keywordIndex = 5;
    private final int authorsIndex = 3;
    private final int titleIndex = 1;
    private final int titleMinWordLen = 5;
    private final int refCountThresh = 2; //how many reference hits before we suggest the reference

    private NlpUtils nlpUtils;

    //receive/send data stream
    PublishSubject<JSONObject> dataObservable;

    public WearableReferencerAutocite(Context context){
        this.context = context;
        //open our references file so we can search for references
        openReferencesFile();

        nlpUtils = NlpUtils.getInstance(context);
    }

    private void openReferencesFile(){
//        CSVReader reader = new CSVReader(new FileReader("yourfile.csv"));
//        CSVReader reader = new CSVReader(new FileReader("yourfile.csv"));
//      
        try{
            InputStream csvFileStream = context.getAssets().open(referencesFileName);
            InputStreamReader csvStreamReader = new InputStreamReader(csvFileStream);
            CSVReader reader = new CSVReader(csvStreamReader);
            myReferences = reader.readAll();
            for (int i = 0; i < myReferences.size(); i++){
                Log.d(TAG, "Printing references at index: " + i);
                for (int j = 0; j < myReferences.get(i).length; j++){
                    Log.d(TAG, "--- " + myReferences.get(i)[j]);
                }
            }
        } catch (IOException e){
            Log.d(TAG, "openReferenecsFile failed:");
            e.printStackTrace();
        }
    }

    //receive observable to send and receive data
    public void setObservable(PublishSubject<JSONObject> observable){
        dataObservable = observable;
        Disposable dataSub = dataObservable.subscribe(i -> handleDataStream(i));
    }

    //this receives data from the data observable. This function decides what to parse
    private void handleDataStream(JSONObject data){
        //first check if it's a type we should handle
        try{
            String type = data.getString(MessageTypes.MESSAGE_TYPE_LOCAL);
            if (type.equals(MessageTypes.INTERMEDIATE_TRANSCRIPT)){
                Log.d(TAG, "WearableReferencer got INTERMEDIATE_TRANSCRIPT");
            } else if (type.equals(MessageTypes.FINAL_TRANSCRIPT)){
                Log.d(TAG, "WearableReferencer got FINAL_TRANSCRIPT, parsing");
                try{
                    String transcript = data.getString(MessageTypes.TRANSCRIPT_TEXT);
                    parseString(transcript);
                } catch (JSONException e){
                    e.printStackTrace();
                }
            }
        } catch (JSONException e){
            e.printStackTrace();
        }
    }

    //searches incoming transcript to see if it contains any keywords
    private void parseString(String toReference){
        List<Integer> potentialReferences = new ArrayList();
        for (int i = 1; i < myReferences.size(); i++){ //start at 1 to skip title line of CSV
            int matchCount = 0;
            String [] keywords = myReferences.get(i)[keywordIndex].toLowerCase().split(";");
            String [] authors = myReferences.get(i)[authorsIndex].toLowerCase().split(";");
            String [] title = myReferences.get(i)[titleIndex].toLowerCase().split(" ");
            title = removeElementsLessThanN(titleMinWordLen, title); //title shouldn't match "the", "for", "is", etc.
            matchCount = matchCount + nlpUtils.findNumMatches(toReference, keywords, referenceThreshold);
            matchCount = matchCount + nlpUtils.findNumMatches(toReference, authors, referenceThreshold);
            matchCount = matchCount + nlpUtils.findNumMatches(toReference, title, referenceThreshold);
            Log.d(TAG, "Got matchCount: " + matchCount + "; for index: " + i);
            if (matchCount >= refCountThresh){
                potentialReferences.add(i);
            }
        }
        for (Integer i : potentialReferences){
            Log.d(TAG, "Possible reference: " + myReferences.get(i)[titleIndex]);
        }
    }

    private String [] removeElementsLessThanN(int n, String [] myStringArray){
        List<String> myList = new ArrayList();

        for (String l : myStringArray) {
            if (!(l.length() < n)) {
                myList.add(l);
            }
        }

        String[] newStringArray = new String[myList.size()];
        myStringArray = myList.toArray(newStringArray);
        return myStringArray;
    }

}