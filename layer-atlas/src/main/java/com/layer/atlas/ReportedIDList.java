package com.layer.atlas;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
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
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.daimajia.swipe.SwipeLayout;
import com.daimajia.swipe.adapters.BaseSwipeAdapter;
import com.layer.sdk.LayerClient;
import com.layer.sdk.exceptions.LayerException;
import com.layer.sdk.listeners.LayerAuthenticationListener;
import com.layer.sdk.messaging.Conversation;
import com.layer.sdk.messaging.Message;
import com.layer.sdk.query.Predicate;
import com.layer.sdk.query.Query;
import com.layer.sdk.query.SortDescriptor;
import com.parse.FunctionCallback;
import com.parse.ParseCloud;
import com.parse.ParseException;
import com.parse.ParseObject;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

/**
 * Created by adityaaggarwal on 3/6/16.
 */
public class ReportedIDList extends FrameLayout {

    private static final String TAG = ReportedIDList.class.getSimpleName();

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
    private String schoolObjectId;
    private UserClickListener clickListener;
    private UserLongClickListener longClickListener;


    private ListView reportedIDsList;

    // date
    private final DateFormat dateFormat;
    private final DateFormat timeFormat;
    AtlasBaseSwipeAdapter reportedAdapter;

    private static final boolean debug = false;
    SwipeRefreshLayout mSwipeRefreshLayout;
    private LayerClient layerClient;


    //Image Caching
    private LruCache<String, Bitmap> mMemoryCache;
    private DiskLruImageCache mDiskLruCache;
    private boolean mDiskCacheStarting = true;
    private final Object mDiskCacheLock = new Object();
    private final int DISK_CACHE_SIZE = 1024 * 1024 *10; // 10MB
    Context context;
    ArrayList<String> userIds=new ArrayList<>();
    public ReportedIDList(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        parseStyle(context, attrs, defStyle);
        this.dateFormat = android.text.format.DateFormat.getDateFormat(context);
        this.timeFormat = android.text.format.DateFormat.getTimeFormat(context);
    }

    public ReportedIDList(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ReportedIDList(Context context) {
        super(context);
        this.dateFormat = android.text.format.DateFormat.getDateFormat(context);
        this.timeFormat = android.text.format.DateFormat.getTimeFormat(context);
    }
    public void init( LayerClient layerClient,  Context contextLocal, String schoolObjectId) {
        if (layerClient == null) throw new IllegalArgumentException("LayerClient cannot be null");
        if (reportedIDsList != null) throw new IllegalStateException("AtlasConversationList is already initialized!");

        this.schoolObjectId=schoolObjectId;
        this.layerClient = layerClient;
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

        this.reportedIDsList = (ListView) findViewById(com.layer.atlas.R.id.atlas_conversations_view);
        reportedAdapter = new AtlasBaseSwipeAdapter();
        this.reportedIDsList.setAdapter(reportedAdapter);


        reportedIDsList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                if (clickListener != null) clickListener.onItemClick(userIds.get(position));
            }
        });
        reportedIDsList.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {

                reportedAdapter.openItem(position);
                if (longClickListener != null) longClickListener.onItemLongClick(userIds.get(position));
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
        HashMap<String, Object> params = new HashMap<String, Object>();
        params.put("schoolID", schoolObjectId);
        ParseCloud.callFunctionInBackground("getReportedStudentIDs", params, new FunctionCallback<ArrayList<String>>() {
            public void done(ArrayList<String> returned, ParseException e) {
                if (e == null) {
                    for (String obj : returned) {
                        Log.d("MainActivity", "Returned string from cloud function is: " + obj);
                        try {
                            JSONObject j = new JSONObject(obj);
                            userIds.add(j.getString("userID"));
                            Log.d("MainActivity", "Successfully made JSON.");
                            Log.d("ObjectId", j.getString("userID") + "userID of User");
                        } catch (JSONException exception) {
                            exception.printStackTrace();
                            Log.d("MainActivity", "Couldn't convert string to JSON.");
                        }

                    }

                }

                updateValues();
            }
        });


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
        if (reportedAdapter == null) {                 // never initialized
            return;
        }

        reportedAdapter.notifyDataSetChanged();

        if(userIds.size()>0 && findViewById(R.id.no_conversation_description).getVisibility()==View.VISIBLE){
            findViewById(R.id.no_conversation_description).setVisibility(View.GONE);
            findViewById(R.id.atlas_conversations_view).setVisibility(View.VISIBLE);
        } else if (userIds.size()==0) {
            TextView noConversationDescription=(TextView)findViewById(R.id.no_conversation_description);
            noConversationDescription.setVisibility(VISIBLE);
            noConversationDescription.setText(R.string.no_reports_description);
        }
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


    public UserClickListener getClickListener() {
        return clickListener;
    }

    public void setClickListener(UserClickListener clickListener) {
        this.clickListener = clickListener;
    }

    public UserLongClickListener getLongClickListener() {
        return longClickListener;
    }

    public void setLongClickListener(UserLongClickListener longClickListener) {
        this.longClickListener = longClickListener;
    }


    public interface UserClickListener {
        void onItemClick(String userId);
    }

    public interface UserLongClickListener {
        void onItemLongClick(String userId);
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
    //Swipe Adapter Documentation: https://github.com/daimajia/AndroidSwipeLayout/wiki
    public class AtlasBaseSwipeAdapter extends BaseSwipeAdapter {
        SwipeLayout swipeLayout;

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


            TextView textTitle = (TextView) convertView.findViewById(com.layer.atlas.R.id.atlas_conversation_view_convert_participant);
            final String userId = userIds.get(position);
            textTitle.setText(userId);

            // avatar icons...
            TextView textInitials = (TextView) convertView.findViewById(com.layer.atlas.R.id.atlas_view_conversations_list_convert_avatar_single_text);
            View avatarSingle = convertView.findViewById(com.layer.atlas.R.id.atlas_view_conversations_list_convert_avatar_single);
            View avatarMulti = convertView.findViewById(com.layer.atlas.R.id.atlas_view_conversations_list_convert_avatar_multi);
            ImageView imageView = (ImageView)convertView.findViewById(com.layer.atlas.R.id.atlas_view_conversations_list_convert_avatar_single_image);
            ImageView trash= (ImageView)convertView.findViewById(com.layer.atlas.R.id.trash);

            //hide textInitials
            textInitials.setVisibility(View.GONE);
            trash.setVisibility(View.GONE);
            avatarMulti.setVisibility(View.GONE);

            avatarSingle.setVisibility(View.VISIBLE);



                if(getBitmapFromCache(userId.toLowerCase())==null){
                    Log.d("Loading Image", "Loading Image "+ "Loading Image");
                    new LoadImage(imageView, userId).execute(getVanilliconLink(userId));
                } else {
                    Log.d("cached","cachedConversationList");
                    RoundImage roundImage=new RoundImage(getBitmapFromCache(userId.toLowerCase()));
                    imageView.setImageDrawable(roundImage);
                }

            TextView textLastMessage = (TextView) convertView.findViewById(com.layer.atlas.R.id.atlas_conversation_view_last_message);
            TextView timeView = (TextView) convertView.findViewById(com.layer.atlas.R.id.atlas_conversation_view_convert_time);

                // the bigger .time the highest in the list
                Query query = Query.builder(Conversation.class)
                        .sortDescriptor(new SortDescriptor(Conversation.Property.LAST_MESSAGE_RECEIVED_AT, SortDescriptor.Order.DESCENDING))
                        .build();


                final List<Conversation> results = layerClient.executeQuery(query, Query.ResultType.OBJECTS);
            for(Conversation c: results) {
                if(c.getMetadata().get("schoolID")!=null) {
                    if (!(c.getMetadata().get("schoolID").equals(schoolObjectId))) {
                        results.remove(c);
                    } else {
                        if (c.getParticipants().size() != 0) {
                            if (!c.getParticipants().contains(userId)) {
                                results.remove(c);
                            }
                        }
                    }
                }
            }

            Log.d("check","check");
            Message last= results.get(0).getLastMessage();
                String lastMessageText = Atlas.Tools.toString(last);

                textLastMessage.setText(lastMessageText);

                Date sentAt = last.getSentAt();
                if (sentAt == null) timeView.setText("...");
                else                timeView.setText(formatTime(sentAt));


                String myId = layerClient.getAuthenticatedUserId();
                if (last.getRecipientStatus(myId) != Message.RecipientStatus.READ) {
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

            timeView.setTextColor(dateTextColor);
            swipeLayout =  (SwipeLayout)convertView.findViewById(com.layer.atlas.R.id.swipeconversationlistitem);
            swipeLayout.setShowMode(SwipeLayout.ShowMode.PullOut);
            swipeLayout.addDrag(SwipeLayout.DragEdge.Left, convertView.findViewById(com.layer.atlas.R.id.bottom_wrapper));

            ImageView reportButton= (ImageView)convertView.findViewById(R.id.report_student);
            reportButton.setImageResource(R.drawable.ic_undo_white_24dp);

                reportButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        changeReportStudentStatus(userId);

                    }
                });
            }


        public String getVanilliconLink(String userId) {
        //load Vanillicon
        byte[] bytesofTest = userId.getBytes();
        MessageDigest messageDigest = null;
        try {
            messageDigest = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        byte[] thedigest = messageDigest.digest(bytesofTest);

        StringBuffer sb = new StringBuffer();
        for (byte b : thedigest) {
            sb.append(String.format("%02x", b & 0xff));
        }
        String vanilliconLink = "http://vanillicon.com/" + sb.toString() + ".png";
        return vanilliconLink;
    }



        public void changeReportStudentStatus(final String userId) {

            final HashMap<String, Object> params = new HashMap<String, Object>();
            params.put("userID", userId);
            ParseCloud.callFunctionInBackground("changeStudentReportValue", params, new FunctionCallback<ParseObject>() {
                public void done(ParseObject reportedStudent, ParseException e) {
                    Toast.makeText(context, "Report Undone.", Toast.LENGTH_SHORT).show();


                    Query query = Query.builder(Conversation.class)
                            .predicate(new Predicate(Conversation.Property.PARTICIPANTS, Predicate.Operator.IN, reportedStudent.getString("userID")))
                            .sortDescriptor(new SortDescriptor(Conversation.Property.LAST_MESSAGE_RECEIVED_AT, SortDescriptor.Order.DESCENDING))
                            .build();
                    List<Conversation> results = layerClient.executeQuery(query, Query.ResultType.OBJECTS);
                    for(Conversation result:results){
                        result.putMetadataAtKeyPath("isReported", "false");
                    }
                    userIds.remove(reportedStudent.getString("userID"));
                    updateValues();
                }
            });
        }
        public long getItemId(int position) {
            return position;
        }
        public Object getItem(int position) {
            return userIds.get(position);
        }
        public int getCount() {
            return userIds.size();
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
            String userId=null;

            //for passing image View
            public LoadImage(ImageView imageViewLocal, String userId) {
                super();
                imageView=imageViewLocal;
                this.userId=userId;
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

                    addBitmapToCache(userId.toLowerCase(),image);
                    RoundImage roundImage=new RoundImage(image);
                    imageView.setImageDrawable(roundImage);
                }else{
                    Log.d("bitmap", "failed to set bitmap to image view");
                }
            }




        }

    }


}
