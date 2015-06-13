/*
 * Copyright (C) 2013 Google Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.glassware;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.repackaged.org.apache.commons.codec.binary.Base64;
import com.google.api.services.mirror.Mirror;
import com.google.api.services.mirror.model.Attachment;
import com.google.api.services.mirror.model.Location;
import com.google.api.services.mirror.model.MenuItem;
import com.google.api.services.mirror.model.Notification;
import com.google.api.services.mirror.model.NotificationConfig;
import com.google.api.services.mirror.model.TimelineItem;
import com.google.api.services.mirror.model.UserAction;
import com.google.common.collect.Lists;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.logging.Logger;
import java.util.Random;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONObject;



/**
 * Handles the notifications sent back from subscriptions
 *
 * @author Jenny Murphy - http://google.com/+JennyMurphy
 */
public class NotifyServlet extends HttpServlet {
  private static final Logger LOG = Logger.getLogger(NotifyServlet.class.getSimpleName());

  private static final String[] CAT_UTTERANCES = {
      "<em class='green'>Purr...</em>",
      "<em class='red'>Hisss... scratch...</em>",
      "<em class='yellow'>Meow...</em>"
  };

  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    // Respond with OK and status 200 in a timely fashion to prevent redelivery
    response.setContentType("text/html");
    Writer writer = response.getWriter();
    writer.append("OK");
    writer.close();

    // Get the notification object from the request body (into a string so we
    // can log it)
    BufferedReader notificationReader =
        new BufferedReader(new InputStreamReader(request.getInputStream()));
    String notificationString = "";
    
    String responseStringForFaceDetection = null ;
    // Count the lines as a very basic way to prevent Denial of Service attacks
    int lines = 0;
    String line;
    while ((line = notificationReader.readLine()) != null) {
      notificationString += line;
      lines++;

      // No notification would ever be this long. Something is very wrong.
      if (lines > 1000) {
        throw new IOException("Attempted to parse notification payload that was unexpectedly long.");
      }
    }
    notificationReader.close();

    LOG.info("got raw notification " + notificationString);

    JsonFactory jsonFactory = new JacksonFactory();

    // If logging the payload is not as important, use
    // jacksonFactory.fromInputStream instead.
    Notification notification = jsonFactory.fromString(notificationString, Notification.class);

    LOG.info("Got a notification with ID: " + notification.getItemId());

    // Figure out the impacted user and get their credentials for API calls
    String userId = notification.getUserToken();
    Credential credential = AuthUtil.getCredential(userId);
    Mirror mirrorClient = MirrorClient.getMirror(credential);


    if (notification.getCollection().equals("locations")) {
      LOG.info("Notification of updated location");
      Mirror glass = MirrorClient.getMirror(credential);
      // item id is usually 'latest'
      Location location = glass.locations().get(notification.getItemId()).execute();

      LOG.info("New location is " + location.getLatitude() + ", " + location.getLongitude());
      MirrorClient.insertTimelineItem(
          credential,
          new TimelineItem()
              .setText("Java Quick Start says you are now at " + location.getLatitude()
                  + " by " + location.getLongitude())
              .setNotification(new NotificationConfig().setLevel("DEFAULT")).setLocation(location)
              .setMenuItems(Lists.newArrayList(new MenuItem().setAction("NAVIGATE"))));

      // This is a location notification. Ping the device with a timeline item
      // telling them where they are.
    } else if (notification.getCollection().equals("timeline")) {
      // Get the impacted timeline item
      TimelineItem timelineItem = mirrorClient.timeline().get(notification.getItemId()).execute();
      LOG.info("Notification impacted timeline item with ID: " + timelineItem.getId());

      // If it was a share, and contains a photo, update the photo's caption to
      // acknowledge that we got it.
      if (notification.getUserActions().contains(new UserAction().setType("SHARE"))
          && timelineItem.getAttachments() != null && timelineItem.getAttachments().size() > 0) {
       String finalresponseForCard = null;
    	  
       String questionString = timelineItem.getText();
       if(!questionString.isEmpty()){
       String[] questionStringArray = questionString.split(" ");
      
       LOG.info( timelineItem.getText()+" is the questions asked by the user");
         LOG.info("A picture was taken");
         
         if(questionString.toLowerCase().contains("search")||questionString.toLowerCase().contains("tag")
             ||questionString.toLowerCase().contains("train")||questionString.toLowerCase().contains("mark")
             ||questionString.toLowerCase().contains("recognize")||questionString.toLowerCase().contains("what is")){
         
         //Fetching the image from the timeline
         InputStream inputStream = downloadAttachment(mirrorClient, notification.getItemId(), timelineItem.getAttachments().get(0));
         
         //converting the image to Base64
         Base64 base64Object = new Base64(false);
         String encodedImageToBase64 = base64Object.encodeToString(IOUtils.toByteArray(inputStream)); //byteArrayForOutputStream.toByteArray()
        // byteArrayForOutputStream.close();
         encodedImageToBase64 = java.net.URLEncoder.encode(encodedImageToBase64, "ISO-8859-1");
         
         
         //sending the API request
         LOG.info("Sending request to API");
         //For initial protoype we're calling the Alchemy API for detecting the number of Faces using web API call
         try{
            String urlParameters =""; 
            String tag ="";
           
              if(questionString.toLowerCase().contains("tag")||questionString.toLowerCase().contains("mark")){
                
                tag = extractTagFromQuestion(questionString);
                urlParameters  = "api_key=gE4P9Mze0ewOa976&api_secret=96JJ4G1bBLPaWLhf&jobs=object_add&name_space=recognizeObject&user_id=user1&tag="+tag+"&base64="+encodedImageToBase64;
                    
              }
              else if(questionString.toLowerCase().contains("train")){
                urlParameters = "api_key=gE4P9Mze0ewOa976&api_secret=96JJ4G1bBLPaWLhf&jobs=object_train&name_space=recognizeObject&user_id=user1";
              }else if(questionString.toLowerCase().contains("search")){
                urlParameters=  "api_key=gE4P9Mze0ewOa976&api_secret=96JJ4G1bBLPaWLhf&jobs=object_search&name_space=recognizeObject&user_id=user1&base64="+encodedImageToBase64;
              }else if(questionString.toLowerCase().contains("recognize")||questionString.toLowerCase().contains("what is")){
                urlParameters = "api_key=gE4P9Mze0ewOa976&api_secret=96JJ4G1bBLPaWLhf&jobs=object_recognize&name_space=recognizeObject&user_id=user1&base64="+encodedImageToBase64;
              }
              byte[] postData       = urlParameters.getBytes( Charset.forName( "UTF-8" ));
              int    postDataLength = postData.length;
              String newrequest        = "http://rekognition.com/func/api/";
              URL    url            = new URL( newrequest );
              HttpURLConnection connectionFaceDetection= (HttpURLConnection) url.openConnection();  
  
              // Increase the timeout for reading the response
              connectionFaceDetection.setReadTimeout(15000);
  
              connectionFaceDetection.setDoOutput( true );
              connectionFaceDetection.setDoInput ( true );
              connectionFaceDetection.setInstanceFollowRedirects( false );
              connectionFaceDetection.setRequestMethod( "POST" );
              connectionFaceDetection.setRequestProperty( "Content-Type", "application/x-www-form-urlencoded");
              connectionFaceDetection.setRequestProperty("X-Mashape-Key", "pzFbNRvNM4mshgWJvvdw0wpLp5N1p1X3AX9jsnOhjDUkn5Lvrp");
              connectionFaceDetection.setRequestProperty( "charset", "utf-8");
              connectionFaceDetection.setRequestProperty("Accept", "application/json");
              connectionFaceDetection.setRequestProperty( "Content-Length", Integer.toString( postDataLength ));
              connectionFaceDetection.setUseCaches( false );
               
              DataOutputStream outputStreamForFaceDetection = new DataOutputStream( connectionFaceDetection.getOutputStream()) ;
              outputStreamForFaceDetection.write( postData );
  
              BufferedReader inputStreamForFaceDetection = new BufferedReader(new InputStreamReader((connectionFaceDetection.getInputStream()))) ;
              
              StringBuilder responseForFaceDetection  = new StringBuilder(); 
             
              while((responseStringForFaceDetection = inputStreamForFaceDetection.readLine()) != null){
                responseForFaceDetection.append(responseStringForFaceDetection);
              }
  
              //closing all the connections
              inputStreamForFaceDetection.close();
              outputStreamForFaceDetection.close();
              connectionFaceDetection.disconnect(); 
              
              responseStringForFaceDetection = responseForFaceDetection.toString(); 
              LOG.info(responseStringForFaceDetection);
              
              JSONObject responseJSONObjectForFaceDetection = new JSONObject(responseStringForFaceDetection);
              if(questionString.toLowerCase().contains("train")||questionString.contains("tag")||questionString.toLowerCase().contains("mark")){
              JSONObject usageKeyFromResponse = responseJSONObjectForFaceDetection.getJSONObject("usage");
              finalresponseForCard = usageKeyFromResponse.getString("status");
              if(!tag.equals(""))
            	  finalresponseForCard="Object is tagged as "+tag;
              }
              else{
                JSONObject sceneUnderstandingObject = responseJSONObjectForFaceDetection.getJSONObject("scene_understanding");
                JSONArray matchesArray = sceneUnderstandingObject.getJSONArray("matches");
                JSONObject firstResultFromArray = matchesArray.getJSONObject(0);
               
                double percentSureOfObject ;
 				//If an score has value 1, then the value type is Integer else the value type is double
				 if(firstResultFromArray.get("score") instanceof Integer){
					 percentSureOfObject = (Integer) firstResultFromArray.get("score")*100;
				 } else
					 percentSureOfObject = (Double) firstResultFromArray.get("score")*100;
				
                finalresponseForCard = "The object is "+firstResultFromArray.getString("tag")+". Match score is"+percentSureOfObject;
              }
                
              //section where if it doesn't contain anything about tag or train
  
         } catch(Exception e){
          LOG.warning(e.getMessage());
        }
                  
         
       }
       
         else 
        	 finalresponseForCard ="Could not understand your words";
       }
       else 
      	 finalresponseForCard ="Could not understand your words";

        TimelineItem responseCardForSDKAlchemyAPI = new TimelineItem();
        
        responseCardForSDKAlchemyAPI.setText(finalresponseForCard);
        responseCardForSDKAlchemyAPI.setMenuItems(Lists.newArrayList(
                new MenuItem().setAction("READ_ALOUD")));
        responseCardForSDKAlchemyAPI.setSpeakableText(finalresponseForCard);
        responseCardForSDKAlchemyAPI.setSpeakableType("Results are as follows");
        responseCardForSDKAlchemyAPI.setNotification(new NotificationConfig().setLevel("DEFAULT"));
        mirrorClient.timeline().insert(responseCardForSDKAlchemyAPI).execute()  ;
        LOG.info("New card added to the timeline");

      } else if (notification.getUserActions().contains(new UserAction().setType("LAUNCH"))) {
        LOG.info("It was a note taken with the 'take a note' voice command. Processing it.");

        // Grab the spoken text from the timeline card and update the card with
        // an HTML response (deleting the text as well).
        String noteText = timelineItem.getText();
        String utterance = CAT_UTTERANCES[new Random().nextInt(CAT_UTTERANCES.length)];

        timelineItem.setText(null);
        timelineItem.setHtml(makeHtmlForCard("<p class='text-auto-size'>"
            + "Oh, did you say " + noteText + "? " + utterance + "</p>"));
        timelineItem.setMenuItems(Lists.newArrayList(
            new MenuItem().setAction("DELETE")));

        mirrorClient.timeline().update(timelineItem.getId(), timelineItem).execute();
      } else {
        LOG.warning("I don't know what to do with this notification, so I'm ignoring it.");
      }
    }
  }

  
  private static String extractTagFromQuestion(String questionString) {
      String objectTag =""; //result string
    String[] splitttedArray= questionString.split("as");//split the caption on 'as'
    if(splitttedArray.length>1){//considering there was as present in the caption
    String[] splitFurther= splitttedArray[1].split(" ");
    // if the tag is of more than one word also to eliminate all stop words
    for(int i=0;i<splitFurther.length;i++){
      if(splitFurther[i].length()>2&&!splitFurther[i].equalsIgnoreCase("the")){//condition for the stop words
        if(objectTag.equals(""))//if this is first word to be added into the result string
          objectTag = splitFurther[i];
        else  //if tag is bigger than one word we need to add underscore before the next word
          objectTag += "_"+splitFurther[i];
      }
      }
    }
    LOG.info("extracting tag from question string"+ objectTag);
    return objectTag;
  }

/**
   * Wraps some HTML content in article/section tags and adds a footer
   * identifying the card as originating from the Java Quick Start.
   *
   * @param content the HTML content to wrap
   * @return the wrapped HTML content
   */
  private static String makeHtmlForCard(String content) {
    return "<article class='auto-paginate'>" + content
        + "<footer><p>Java Quick Start</p></footer></article>";
  }
  

  public static InputStream downloadAttachment(Mirror service, String itemId, Attachment attachment) {
      try {
        HttpResponse resp =
            service.getRequestFactory().buildGetRequest(new GenericUrl(attachment.getContentUrl()))
            .execute();
       
        return resp.getContent();
      } catch (IOException e) {
        // An error occurred.
        LOG.warning(e.getMessage()+"This has failed");
        return null;
      }
    }
}
