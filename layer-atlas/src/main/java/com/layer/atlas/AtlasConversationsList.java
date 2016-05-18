/*
 * Copyright (c) 2015 Layer. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.layer.atlas;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.support.v4.widget.SwipeRefreshLayout;
import android.util.AttributeSet;
import android.util.Log;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.daimajia.swipe.SwipeLayout;
import com.daimajia.swipe.adapters.BaseSwipeAdapter;
import com.layer.sdk.LayerClient;
import com.layer.sdk.changes.LayerChange;
import com.layer.sdk.changes.LayerChangeEvent;
import com.layer.sdk.exceptions.LayerException;
import com.layer.sdk.listeners.LayerAuthenticationListener;
import com.layer.sdk.listeners.LayerChangeEventListener;
import com.layer.sdk.messaging.Conversation;
import com.layer.sdk.messaging.LayerObject;
import com.layer.sdk.messaging.Message;
import com.layer.sdk.messaging.Message.RecipientStatus;
import com.layer.sdk.messaging.MessagePart;

import java.io.InputStream;
import java.net.URL;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Random;

/**
 * @author Oleg Orlov
 * @since 14 May 2015
 */
public class AtlasConversationsList extends FrameLayout implements LayerChangeEventListener.MainThread {


    private String[] treenameA = {"Red", "Green", "Blue", "Purple", "Orange", "Yellow", "Violet", "Pink", "Gray", "Brown", "Cyan", "Crimson" , "Gold" , "Silver" , "Teal" , "Azure", "Turquoise", "Lavender", "Maroon", "Tan", "Magenta" , "Indigo" , "Jade" , "Scarlet", "Amber"};
    private String[] treenameB = {"Acacia", "Aspen" , "Beech" , "Birch" , "Cedar" , "Cypress", "Ebony", "Elm" , "Eucalyptus", "Fir", "Grove" , "Hazel" ,  "Juniper" , "Maple", "Oak" , "Palm", "Poplar", "Pine" , "Sequoia" ,  "Spruce", "Sycamore", "Sylvan",  "Walnut", "Willow", "Yew"};


    private static final String TAG = AtlasConversationsList.class.getSimpleName();
    private static final boolean debug = false;

    private ListView conversationsList;
    private AtlasBaseSwipeAdapter conversationsAdapter;

    private ArrayList<Conversation> conversations = new ArrayList<Conversation>();

    private LayerClient layerClient;

    private ConversationClickListener clickListener;
    private ConversationLongClickListener longClickListener;

    public interface IMyEventListener {
        public void onConversationDeleted();
    }

    private IMyEventListener mEventListener;
    
    //styles
    private int titleTextColor;
    private int titleTextStyle;
    private Typeface titleTextTypeface;
    private int titleUnreadTextColor;
    private int titleUnreadTextStyle;
    private Typeface titleUnreadTextTypeface;
    private int subtitleTextColor;
    private int subtitleTextStyle;
    private Typeface subtitleTextTypeface;
    private int subtitleUnreadTextColor;
    private int subtitleUnreadTextStyle;
    private Typeface subtitleUnreadTextTypeface;
    private int cellBackgroundColor;
    private int cellUnreadBackgroundColor;
    private int dateTextColor;
    private int avatarTextColor;
    private int avatarBackgroundColor;

    SwipeRefreshLayout mSwipeRefreshLayout;



    // date
    private final DateFormat dateFormat;
    private final DateFormat timeFormat;


    //account type 1 is counselor
    //account type 0 is student
    //default set to 0
    private int accountType;

    //admin
    private String globalUserId;

    //Image Caching
    private LruCache<String, Bitmap> mMemoryCache;
    private DiskLruImageCache mDiskLruCache;
    private boolean mDiskCacheStarting = true;
    private final Object mDiskCacheLock = new Object();
    private final int DISK_CACHE_SIZE = 1024 * 1024 *10; // 10MB
    Context context;

    public AtlasConversationsList(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        parseStyle(context, attrs, defStyle);
        this.dateFormat = android.text.format.DateFormat.getDateFormat(context);
        this.timeFormat = android.text.format.DateFormat.getTimeFormat(context);
    }

    public AtlasConversationsList(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AtlasConversationsList(Context context) {
        super(context);
        this.dateFormat = android.text.format.DateFormat.getDateFormat(context);
        this.timeFormat = android.text.format.DateFormat.getTimeFormat(context);
    }

    public void setMyIEventListener(IMyEventListener mEventListener) {
        this.mEventListener = mEventListener;
    }

    public ArrayList<String> getCounselorsinConversationWith(){
       ArrayList<String> counselorsinConversationWith=new ArrayList<String>();
        for(Conversation convo:conversations){
            if(convo.getMetadata()!=null) {
                counselorsinConversationWith.add((String)convo.getMetadata().get("counselor.ID"));
            }

        }
        return counselorsinConversationWith;
    }

    public Conversation getConversationWithCounselorId(String counselorId){
       Conversation conversationWithCounselorId=null;
        int x=0;
        while(conversationWithCounselorId==null && x<conversations.size()){

            if(counselorId.equals(conversations.get(x).getMetadata().get("counselor.ID"))){
                conversationWithCounselorId=conversations.get(x);
            }
            x++;
        }
        return conversationWithCounselorId;
    }

    public ArrayList<Conversation> getConversations(){
        return conversations;
    }
    public AtlasBaseSwipeAdapter getAdapter(){
        return conversationsAdapter;
    }

    class InitDiskCacheTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void ... params) {
            synchronized (mDiskCacheLock) {
                mDiskLruCache= new DiskLruImageCache(context, "thumbnails", DISK_CACHE_SIZE, Bitmap.CompressFormat.PNG, 50);
                mDiskCacheStarting = false; // Finished initialization
                mDiskCacheLock.notifyAll(); // Wake any waiting threads
            }
            return null;
        }
    }

    public void init(final LayerClient layerClient, final ParticipantProvider participantProvider, int accountTypeLocal, Context contextLocal, String userId) {
        if (layerClient == null) throw new IllegalArgumentException("LayerClient cannot be null");
        if (participantProvider == null) throw new IllegalArgumentException("ParticipantProvider cannot be null");
        if (conversationsList != null) throw new IllegalStateException("AtlasConversationList is already initialized!");
        accountType=accountTypeLocal;
        globalUserId= layerClient.getAuthenticatedUserId();
        this.layerClient = layerClient;
        accountType = accountTypeLocal;
        context=contextLocal;
        final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);

        // Use 1/8th of the available memory for this memory cache.
        final int cacheSize = maxMemory;
        mMemoryCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                // The cache size will be measured in kilobytes rather than
                // number of items.
                return bitmap.getByteCount() / 1024;
            }
        };
        new InitDiskCacheTask().execute();


        // inflate children:
        LayoutInflater.from(getContext()).inflate(com.layer.atlas.R.layout.atlas_conversations_list, this);

        this.conversationsList = (ListView) findViewById(com.layer.atlas.R.id.atlas_conversations_view);
        conversationsAdapter = new AtlasBaseSwipeAdapter(participantProvider);
        this.conversationsList.setAdapter(conversationsAdapter);


        conversationsList.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Conversation conv = conversations.get(position);

                if (clickListener != null) clickListener.onItemClick(conv);
            }
        });
        conversationsList.setOnItemLongClickListener(new OnItemLongClickListener() {
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                Conversation conv = conversations.get(position);
                conversationsAdapter.openItem(position);
                if (longClickListener != null) longClickListener.onItemLongClick(conv);
                return true;
            }
        });
        
        // clean everything if deathenticated (client will explode on .getConversation())
        // and rebuilt everything back after successful authentication  
        layerClient.registerAuthenticationListener(new LayerAuthenticationListener() {
            public void onDeauthenticated(LayerClient client) {
                if (debug) Log.w(TAG, "onDeauthenticated() ");
                updateValues();
            }

            public void onAuthenticated(LayerClient client, String userId) {
                updateValues();
            }

            public void onAuthenticationError(LayerClient client, LayerException exception) {
            }

            public void onAuthenticationChallenge(LayerClient client, String nonce) {
            }
        });

        applyStyle();

        updateValues();

        mSwipeRefreshLayout=(SwipeRefreshLayout)findViewById(com.layer.atlas.R.id.conversation_list_swipe_refresh_layout);
        mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {

                refreshContent();
                updateValues();
            }
        });
    }



    private void refreshContent(){
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {

                mSwipeRefreshLayout.setRefreshing(false);

            }
        }, 1500);
        }



    public void updateValues() {
        if (conversationsAdapter == null) {                 // never initialized
            return;
        }

        conversations.clear();                              // always clean, rebuild if authenticated 
        conversationsAdapter.notifyDataSetChanged();

        if (layerClient.isAuthenticated()) {

            List<Conversation> convs = layerClient.getConversations();
            if (debug) Log.d(TAG, "updateValues() conv: " + convs.size());
            for (Conversation conv : convs) {
                // no participants means we are removed from conversation (disconnected conversation)
                if (conv.getParticipants().size() == 0) continue;




                // only ourselves in participant list is possible to happen, but there is nothing to do with it
                // behave like conversation is disconnected
                if (conv.getParticipants().size() == 1
                        && conv.getParticipants().contains(globalUserId)) continue;



                conversations.add(conv);
            }

            // the bigger .time the highest in the list
            Collections.sort(conversations, new Comparator<Conversation>() {
                public int compare(Conversation lhs, Conversation rhs) {
                    long leftSentAt = 0;
                    Message leftLastMessage = lhs.getLastMessage();
                    if (leftLastMessage != null && leftLastMessage.getSentAt() != null) {
                        leftSentAt = leftLastMessage.getSentAt().getTime();
                    }
                    long rightSentAt = 0;
                    Message rightLastMessage = rhs.getLastMessage();
                    if (rightLastMessage != null && rightLastMessage.getSentAt() != null) {
                        rightSentAt = rightLastMessage.getSentAt().getTime();
                    }
                    long result = rightSentAt - leftSentAt;
                    if (result == 0L) return 0;
                    return result < 0L ? -1 : 1;
                }
            });
            if(conversations.size()>0 && findViewById(R.id.no_conversation_description).getVisibility()==View.VISIBLE){
                findViewById(R.id.no_conversation_description).setVisibility(View.GONE);
                findViewById(R.id.atlas_conversations_view).setVisibility(View.VISIBLE);
            } else if (conversations.size()==0) {
                TextView noConversationDescription=(TextView)findViewById(R.id.no_conversation_description);
                noConversationDescription.setVisibility(VISIBLE);
                if(accountType==1){
                    noConversationDescription.setText(R.string.no_conversation_description_counselor);
                } else if (accountType==0) {
                    noConversationDescription.setText(R.string.no_conversation_description);
                } else if(accountType==2){
                    noConversationDescription.setText(R.string.no_reports_description);
                }
            }
        }

    }

    public String generateRandomTreeName(){
        Random randomGenerator=new Random();
        String randomName="";
        ArrayList<String> studentMetaDataNames=new ArrayList<String>();
        for(Conversation conv: conversations) {
            studentMetaDataNames.add((String)conv.getMetadata().get("student.name"));
        }
        while(studentMetaDataNames.contains(randomName)) {
            randomName = treenameA[randomGenerator.nextInt(treenameA.length)] + " " + treenameB[randomGenerator.nextInt(treenameB.length)];
        }
        return randomName;
    }

    private void parseStyle(Context context, AttributeSet attrs, int defStyle) {
        TypedArray ta = context.getTheme().obtainStyledAttributes(attrs, com.layer.atlas.R.styleable.AtlasConversationList, com.layer.atlas.R.attr.AtlasConversationList, defStyle);
        this.titleTextColor = ta.getColor(com.layer.atlas.R.styleable.AtlasConversationList_cellTitleTextColor, context.getResources().getColor(com.layer.atlas.R.color.atlas_text_black));
        this.titleTextStyle = ta.getInt(com.layer.atlas.R.styleable.AtlasConversationList_cellTitleTextStyle, Typeface.NORMAL);
        String titleTextTypefaceName = ta.getString(com.layer.atlas.R.styleable.AtlasConversationList_cellTitleTextTypeface);
        this.titleTextTypeface  = titleTextTypefaceName != null ? Typeface.create(titleTextTypefaceName, titleTextStyle) : null;
        
        this.titleUnreadTextColor = ta.getColor(com.layer.atlas.R.styleable.AtlasConversationList_cellTitleUnreadTextColor, context.getResources().getColor(com.layer.atlas.R.color.atlas_text_black));
        this.titleUnreadTextStyle = ta.getInt(com.layer.atlas.R.styleable.AtlasConversationList_cellTitleUnreadTextStyle, Typeface.BOLD);
        String titleUnreadTextTypefaceName = ta.getString(com.layer.atlas.R.styleable.AtlasConversationList_cellTitleUnreadTextTypeface);
        this.titleUnreadTextTypeface  = titleUnreadTextTypefaceName != null ? Typeface.create(titleUnreadTextTypefaceName, titleUnreadTextStyle) : null;
        
        this.subtitleTextColor = ta.getColor(com.layer.atlas.R.styleable.AtlasConversationList_cellSubtitleTextColor, context.getResources().getColor(com.layer.atlas.R.color.atlas_text_black));
        this.subtitleTextStyle = ta.getInt(com.layer.atlas.R.styleable.AtlasConversationList_cellSubtitleTextStyle, Typeface.NORMAL);
        String subtitleTextTypefaceName = ta.getString(com.layer.atlas.R.styleable.AtlasConversationList_cellSubtitleTextTypeface);
        this.subtitleTextTypeface  = subtitleTextTypefaceName != null ? Typeface.create(subtitleTextTypefaceName, subtitleTextStyle) : null;
        
        this.subtitleUnreadTextColor = ta.getColor(com.layer.atlas.R.styleable.AtlasConversationList_cellSubtitleUnreadTextColor, context.getResources().getColor(com.layer.atlas.R.color.atlas_text_black));
        this.subtitleUnreadTextStyle = ta.getInt(com.layer.atlas.R.styleable.AtlasConversationList_cellSubtitleUnreadTextStyle, Typeface.NORMAL);
        String subtitleUnreadTextTypefaceName = ta.getString(com.layer.atlas.R.styleable.AtlasConversationList_cellSubtitleUnreadTextTypeface);
        this.subtitleUnreadTextTypeface  = subtitleUnreadTextTypefaceName != null ? Typeface.create(subtitleUnreadTextTypefaceName, subtitleUnreadTextStyle) : null;
        
        this.cellBackgroundColor = ta.getColor(com.layer.atlas.R.styleable.AtlasConversationList_cellBackgroundColor, Color.TRANSPARENT);
        this.cellUnreadBackgroundColor = ta.getColor(com.layer.atlas.R.styleable.AtlasConversationList_cellUnreadBackgroundColor, Color.TRANSPARENT);
        this.dateTextColor = ta.getColor(com.layer.atlas.R.styleable.AtlasConversationList_dateTextColor, context.getResources().getColor(com.layer.atlas.R.color.atlas_text_black));
        this.avatarTextColor = ta.getColor(com.layer.atlas.R.styleable.AtlasConversationList_avatarTextColor, context.getResources().getColor(com.layer.atlas.R.color.atlas_text_black));
        this.avatarBackgroundColor = ta.getColor(com.layer.atlas.R.styleable.AtlasConversationList_avatarBackgroundColor, context.getResources().getColor(com.layer.atlas.R.color.atlas_shape_avatar_gray));
        ta.recycle();
    }
    
    private void applyStyle() {
        conversationsAdapter.notifyDataSetChanged();
    }
    
    public String formatTime(Date sentAt) {
        if (sentAt == null) sentAt = new Date();

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        long todayMidnight = cal.getTimeInMillis();
        long yesterMidnight = todayMidnight - Atlas.Tools.TIME_HOURS_24;
        long weekAgoMidnight = todayMidnight - Atlas.Tools.TIME_HOURS_24 * 7;
        
        String timeText = null;
        if (sentAt.getTime() > todayMidnight) {
            timeText = timeFormat.format(sentAt.getTime()); 
        } else if (sentAt.getTime() > yesterMidnight) {
            timeText = "Yesterday";
        } else if (sentAt.getTime() > weekAgoMidnight){
            cal.setTime(sentAt);
            timeText = Atlas.Tools.TIME_WEEKDAYS_NAMES[cal.get(Calendar.DAY_OF_WEEK) - 1];
        } else {
            timeText = dateFormat.format(sentAt);
        }
        return timeText;
    }

    @Override
    public void onEventMainThread(LayerChangeEvent event) {
        for (LayerChange change : event.getChanges()) {
            if (change.getObjectType() == LayerObject.Type.CONVERSATION
                    || change.getObjectType() == LayerObject.Type.MESSAGE) {
                updateValues();
                return;
            }
        }
    }
    
    public ConversationClickListener getClickListener() {
        return clickListener;
    }

    public void setClickListener(ConversationClickListener clickListener) {
        this.clickListener = clickListener;
    }

    public ConversationLongClickListener getLongClickListener() {
        return longClickListener;
    }

    public void setLongClickListener(ConversationLongClickListener conversationLongClickListener) {
        this.longClickListener = conversationLongClickListener;
    }

    
    public interface ConversationClickListener {
        void onItemClick(Conversation conversation);
    }
    
    public interface ConversationLongClickListener {
        void onItemLongClick(Conversation conversation);
    }



    //Swipe Adapter Documentation: https://github.com/daimajia/AndroidSwipeLayout/wiki
    public class AtlasBaseSwipeAdapter extends BaseSwipeAdapter {
        SwipeLayout swipeLayout;
        ParticipantProvider participantProvider;
        public AtlasBaseSwipeAdapter(ParticipantProvider provider){
            participantProvider=provider;

        }
        public int getSwipeLayoutResourceId(int position) {
            return com.layer.atlas.R.id.swipeconversationlistitem;
        }
        public View generateView(int position, ViewGroup parent) {
            return LayoutInflater.from(parent.getContext()).inflate(com.layer.atlas.R.layout.atlas_view_conversations_list_convert, parent, false);
        }
        public void notifyDataSetChanged() {
            super.notifyDataSetChanged();
        }
        public void fillValues(final int position, final View convertView) {

            Uri convId = conversations.get(position).getId();
            Conversation conv = layerClient.getConversation(convId);

            ArrayList<String> allButMe = new ArrayList<String>(conv.getParticipants());
            allButMe.remove(globalUserId);

            TextView textTitle = (TextView) convertView.findViewById(com.layer.atlas.R.id.atlas_conversation_view_convert_participant);
            String conversationTitle = Atlas.getTitle(conv, participantProvider, globalUserId);
            textTitle.setText(conversationTitle);


            // avatar icons...
            TextView textInitials = (TextView) convertView.findViewById(com.layer.atlas.R.id.atlas_view_conversations_list_convert_avatar_single_text);
            View avatarSingle = convertView.findViewById(com.layer.atlas.R.id.atlas_view_conversations_list_convert_avatar_single);
            View avatarMulti = convertView.findViewById(com.layer.atlas.R.id.atlas_view_conversations_list_convert_avatar_multi);
            ImageView imageView = (ImageView)convertView.findViewById(com.layer.atlas.R.id.atlas_view_conversations_list_convert_avatar_single_image);
        //    if (allButMe.size() < 3 && allButMe.contains("1")) {
                String conterpartyUserId = allButMe.get(0);
                Atlas.Participant participant = participantProvider.getParticipant(conterpartyUserId);

                //hide textInitials
                textInitials.setText(participant == null ? null : Atlas.getInitials(participant));
                textInitials.setTextColor(avatarTextColor);
                ((GradientDrawable) textInitials.getBackground()).setColor(avatarBackgroundColor);
                textInitials.setVisibility(View.GONE);



                if(conv.getMetadata().get("counselor")!=null && (accountType==0 || accountType==2)) {
                    String counselorIdMetadataUpper=(String)conv.getMetadata().get("counselor.ID");
                    Log.d("counselorIdMetadata", "counselorIdMetadataUpper" + counselorIdMetadataUpper);
                    if(getBitmapFromCache(counselorIdMetadataUpper.toLowerCase())==null){
                        Log.d("Loading Image", "Loading Image "+ "Loading Image");

                        new LoadImage(imageView, conv).execute((String)conv.getMetadata().get("counselor.avatarString"));
                    } else {
                        RoundImage roundImage=new RoundImage(getBitmapFromCache(counselorIdMetadataUpper.toLowerCase()));
                        imageView.setImageDrawable(roundImage);
                    }
                    if(accountType==2){
                        String titleText=conv.getMetadata().get("student.ID")+", "+conv.getMetadata().get("counselor.name");
                        textTitle.setText(titleText);
                    } else {
                        textTitle.setText((String) conv.getMetadata().get("counselor.name"));
                    }
                } else if (conv.getMetadata().get("student")!=null && accountType==1) {

                    if(conv.getMetadata().get("student.name").equals("")){
                        conv.putMetadataAtKeyPath("student.name",generateRandomTreeName());
                        Log.d("name set check","name set check"+conv.getMetadata().get("student.name"));
                    }
                    if(getBitmapFromCache(((String)conv.getMetadata().get("student.ID")).toLowerCase())==null){
                        Log.d("Loading Image", "Loading Image "+ "Loading Image");
                        new LoadImage(imageView, conv).execute((String)conv.getMetadata().get("student.avatarString"));
                    } else {
                        Log.d("cached","cachedConversationList");
                        RoundImage roundImage=new RoundImage(getBitmapFromCache(((String) conv.getMetadata().get("student.ID")).toLowerCase()));
                        imageView.setImageDrawable(roundImage);
                    }

                    textTitle.setText((String)conv.getMetadata().get("student.name"));
                }

                avatarSingle.setVisibility(View.VISIBLE);
                avatarMulti.setVisibility(View.GONE);

            //Multi Avatar-for later use
          /*  } else {
                Atlas.Participant leftParticipant = null;
                Atlas.Participant rightParticipant = null;
                for (Iterator<String> itUserId = allButMe.iterator(); itUserId.hasNext();) {
                    String userId = itUserId.next();
                    Atlas.Participant p = participantProvider.getParticipant(userId);
                    if (p == null) continue;

                    if (leftParticipant == null) {
                        leftParticipant = p;
                    } else {
                        rightParticipant = p;
                        break;
                    }
                }

                TextView textInitialsLeft = (TextView) convertView.findViewById(com.layer.atlas.R.id.atlas_view_conversations_list_convert_avatar_multi_left);
                textInitialsLeft.setText(leftParticipant == null ? "?" : Atlas.getInitials(leftParticipant));
                textInitialsLeft.setTextColor(avatarTextColor);
                ((GradientDrawable) textInitialsLeft.getBackground()).setColor(avatarBackgroundColor);

                TextView textInitialsRight = (TextView) convertView.findViewById(com.layer.atlas.R.id.atlas_view_conversations_list_convert_avatar_multi_right);
                textInitialsRight.setText(rightParticipant == null ? "?" : Atlas.getInitials(rightParticipant));
                textInitialsRight.setTextColor(avatarTextColor);
                ((GradientDrawable) textInitialsRight.getBackground()).setColor(avatarBackgroundColor);

                avatarSingle.setVisibility(View.GONE);
                avatarMulti.setVisibility(View.VISIBLE);
            }*/

            TextView textLastMessage = (TextView) convertView.findViewById(com.layer.atlas.R.id.atlas_conversation_view_last_message);
            TextView timeView = (TextView) convertView.findViewById(com.layer.atlas.R.id.atlas_conversation_view_convert_time);
            if (conv.getLastMessage() != null ) {

                Message last = conv.getLastMessage();
                boolean isReportMessageLast;
                do {
                    isReportMessageLast=false;
                    for (MessagePart part : last.getMessageParts()) {
                        String messageText = new String(part.getData());
                        if (messageText.equals("Conversation Reported")) {
                            isReportMessageLast = true;
                            last = layerClient.getMessages(conv).get((layerClient.getMessages(conv).indexOf(last) - 1));
                        }
                    }
                } while(isReportMessageLast);



                String lastMessageText = Atlas.Tools.toString(last);

                textLastMessage.setText(lastMessageText);

                Date sentAt = last.getSentAt();
                if (sentAt == null) timeView.setText("...");
                else                timeView.setText(formatTime(sentAt));

                String userId = last.getSender().getUserId();                   // could be null for system messages
                String myId = globalUserId;

                if ((userId != null) && !userId.equals(myId) && last.getRecipientStatus(myId) != RecipientStatus.READ) {
                    textTitle.setTextColor(titleUnreadTextColor);
                    textTitle.setTypeface(titleUnreadTextTypeface, titleUnreadTextStyle);
                    textLastMessage.setTypeface(subtitleUnreadTextTypeface, subtitleUnreadTextStyle);
                    textLastMessage.setTextColor(subtitleUnreadTextColor);
                    convertView.setBackgroundColor(cellUnreadBackgroundColor);
                } else {
                    textTitle.setTextColor(titleTextColor);
                    textTitle.setTypeface(titleTextTypeface, titleTextStyle);
                    textLastMessage.setTypeface(subtitleTextTypeface, subtitleTextStyle);
                    textLastMessage.setTextColor(subtitleTextColor);
                    convertView.setBackgroundColor(cellBackgroundColor);
                }
            } else {
                timeView.setText("...");
                textLastMessage.setText("");
                textTitle.setTextColor(titleTextColor);
                textTitle.setTypeface(titleTextTypeface, titleTextStyle);
                textLastMessage.setTypeface(subtitleTextTypeface, subtitleTextStyle);
                textLastMessage.setTextColor(subtitleTextColor);
                convertView.setBackgroundColor(cellBackgroundColor);
            }
            timeView.setTextColor(dateTextColor);
            swipeLayout =  (SwipeLayout)convertView.findViewById(com.layer.atlas.R.id.swipeconversationlistitem);
            swipeLayout.setShowMode(SwipeLayout.ShowMode.PullOut);
            swipeLayout.addDrag(SwipeLayout.DragEdge.Left, convertView.findViewById(com.layer.atlas.R.id.bottom_wrapper));
            ImageView trash= (ImageView)convertView.findViewById(com.layer.atlas.R.id.trash);
            trash.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    Conversation deleteConversation = (Conversation) getItem(position);
                    deleteConversation.delete(LayerClient.DeletionMode.ALL_PARTICIPANTS);
                    conversations.remove(getItem(position));
                    swipeLayout.close(false);
                    notifyDataSetChanged();

                    updateValues();

                    if (mEventListener != null) {
                        mEventListener.onConversationDeleted();
                    }


                }
            });
            ImageView reportButton= (ImageView)convertView.findViewById(R.id.report_student);
            if(accountType==0) {
                if (conv.getParticipants().size()>2){
                    trash.setVisibility(View.GONE);
                }
                reportButton.setVisibility(View.GONE);
            } else {
                if(conv.getParticipants().size()>2) {
                    reportButton.setImageResource(R.drawable.ic_undo_white_24dp);
                }
                reportButton.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Conversation conv=(Conversation)getItem(position);
                        if(conv.getParticipants().size()>2) {
                            getWarningAlertDialog(R.string.undo_warning, R.string.undo, conv, convertView).show();
                        } else {
                            getWarningAlertDialog(R.string.report_warning, R.string.report, conv, convertView).show();
                        }

                    }
                });
            }

        }

        public void changeStudentReportStatus(final Conversation conv, final View convertView) {

            Participant[] participants = participantProvider.getCustomParticipants();
            if(conv.getParticipants().size()<=2) {
                for (Participant p : participants) {
                    if (p.getCounselorType() == 0)
                        conv.addParticipants(p.getID());
                }
                Toast.makeText(context, "Conversation reported.", Toast.LENGTH_SHORT).show();
                updateValues();
                ImageView convertViewImage = (ImageView) convertView.findViewById(R.id.report_student);
                convertViewImage.setImageResource(R.drawable.ic_undo_white_24dp);
            } else {
                Toast.makeText(context, "Report Undone", Toast.LENGTH_SHORT).show();

                for(Participant p: participants){
                    if(p.getCounselorType()==0)
                        conv.removeParticipants(p.getID());
                }
                updateValues();
                ImageView convertViewImage=(ImageView)convertView.findViewById(R.id.report_student);
                convertViewImage.setImageResource(R.drawable.ic_report_problem_white_24dp);
            }
//            final HashMap<String, Object> params = new HashMap<String, Object>();
//            params.put("userID", conv.getMetadata().get("student.ID"));
           /* ParseCloud.callFunctionInBackground("changeStudentReportValue", params, new FunctionCallback<Boolean>() {
                public void done(Boolean studentReportValue, ParseException e) {
                    if (e == null) {
                        Participant[] participants=participantProvider.getCustomParticipants();
                        if (studentReportValue.getBoolean()) {
                            Toast.makeText(context, "User successfully reported.", Toast.LENGTH_SHORT).show();


                            for(Participant p: participants){
                                if(p.getCounselorType()==0)
                                    conv.addParticipants(p.getID());
                            }
                            updateValues();
                            ImageView convertViewImage=(ImageView)convertView.findViewById(R.id.report_student);
                            convertViewImage.setImageResource(R.drawable.ic_undo_white_24dp);
                        } else {
                            Toast.makeText(context, "User successfully un-reported.", Toast.LENGTH_SHORT).show();

                            for(Participant p: participants){
                                if(p.getCounselorType()==0)
                                    conv.removeParticipants(p.getID());
                            }
                            updateValues();
                            ImageView convertViewImage=(ImageView)convertView.findViewById(R.id.report_student);
                            convertViewImage.setImageResource(R.drawable.ic_report_problem_white_24dp);
                        }
                    } else {
                        Toast.makeText(context, e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
            });*/
        }

        private AlertDialog getWarningAlertDialog(int stringAddress, int acceptStringAddress, final Conversation conv, final View convertView){
            // Use the Builder class for convenient dialog construction
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setMessage(stringAddress)
                    .setPositiveButton(acceptStringAddress, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            changeStudentReportStatus( conv, convertView);
                        }
                    }).setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {

                }
            });
            // Create the AlertDialog object and return it
            return builder.create();
        }

        public long getItemId(int position) {
            return position;
        }
        public Object getItem(int position) {
            return conversations.get(position);
        }
        public int getCount() {
            return conversations.size();
        }


        public Bitmap getBitmapFromCache(String key) {
            Log.d("key", "keykey" + key);
            if (mMemoryCache.get(key)!=null) {
                return mMemoryCache.get(key);
            } else {
                synchronized (mDiskCacheLock) {
                    // Wait while disk cache is started from background thread
                    while (mDiskCacheStarting) {
                        try {
                            mDiskCacheLock.wait();
                        } catch (InterruptedException e) {
                        }
                    }
                    if (mDiskLruCache != null) {
                        if(mDiskLruCache.getBitmap(key)!=null) {
                            mMemoryCache.put(key, mDiskLruCache.getBitmap(key));
                            return mDiskLruCache.getBitmap(key);
                        } else {
                            return null;
                        }
                    } else {
                        return null;
                    }
                }
            }
        }

        public void addBitmapToCache(String key, Bitmap bitmap) {
            // Add to memory cache

            mMemoryCache.put(key, bitmap);

            // Also add to disk cache
            synchronized (mDiskCacheLock) {
                if (mDiskLruCache != null && mDiskLruCache.getBitmap(key) == null) {
                    mDiskLruCache.put(key, bitmap);
                }
            }
        }

        private class LoadImage extends AsyncTask<String, String, Bitmap> {
            ImageView imageView=null;
            Conversation conversation;
            //for passing image View
            public LoadImage(ImageView imageViewLocal, Conversation convparam) {
                super();
                imageView=imageViewLocal;
                conversation=convparam;
            }
            public int calculateInSampleSize(
                    BitmapFactory.Options options, int reqWidth, int reqHeight) {
                // Raw height and width of image
                final int height = options.outHeight;
                final int width = options.outWidth;
                int inSampleSize = 1;

                if (height > reqHeight || width > reqWidth) {

                    final int halfHeight = height / 2;
                    final int halfWidth = width / 2;

                    // Calculate the largest inSampleSize value that is a power of 2 and keeps both
                    // height and width larger than the requested height and width.
                    while ((halfHeight / inSampleSize) > reqHeight
                            && (halfWidth / inSampleSize) > reqWidth) {
                        inSampleSize *= 2;
                    }
                }

                return inSampleSize;
            }

            //convert image of link to bitmap
            protected Bitmap doInBackground(String... args) {
                Bitmap bitmap=null;
                try {
                    bitmap = BitmapFactory.decodeStream((InputStream) new URL(args[0]).getContent());
                    BitmapFactory.Options options= new BitmapFactory.Options();
                    options.inJustDecodeBounds = true;
                    Rect padding=new Rect();
                    padding.setEmpty();
                    BitmapFactory.decodeStream((InputStream) new URL(args[0]).getContent(), padding, options);

                    // Calculate inSampleSize
                    options.inSampleSize = calculateInSampleSize(options, 192, 192);
                    options.inJustDecodeBounds = false;
                    bitmap = BitmapFactory.decodeStream((InputStream) new URL(args[0]).getContent(), padding, options);

                } catch (Exception e) {
                    e.printStackTrace();
                    Log.d("failed to decode bitmap","failed to decode bitmap");
                }
                return bitmap;
            }

            //set image view to bitmap
            protected void onPostExecute(Bitmap image ) {

                if(image != null){
                    //Log.d("caching","caching");
                    String upperCaseData;
                    if(accountType==0 || accountType==2) {
                        upperCaseData = (String) conversation.getMetadata().get("counselor.ID");
                    }else {
                        upperCaseData = (String) conversation.getMetadata().get("student.ID");
                    }
                    addBitmapToCache(upperCaseData.toLowerCase(),image);
                    RoundImage roundImage=new RoundImage(image);
                    imageView.setImageDrawable(roundImage);

                }else{
                    Log.d("bitmap", "failed to set bitmap to image view");
                }
            }




        }

    }
}
