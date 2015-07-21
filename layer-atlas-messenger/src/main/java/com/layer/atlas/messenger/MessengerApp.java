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
package com.layer.atlas.messenger;

import java.lang.reflect.Method;
import java.util.Iterator;

import android.app.Application;
import android.os.Bundle;
import android.util.Log;
import android.view.Window;

import com.layer.atlas.Atlas;
import com.layer.atlas.messenger.provider.HerokuIdentityProvider;
import com.layer.atlas.messenger.provider.IdentityProvider;
import com.layer.atlas.messenger.provider.ParticipantProvider;
import com.layer.atlas.messenger.provider.QRIdentityProvider;
import com.layer.atlas.messenger.provider.QRParticipantProviderCallback;
import com.layer.sdk.LayerClient;
import com.layer.sdk.LayerClient.Options;

/**
 * @author Oleg Orlov
 * @since March 3, 2015
 */
public class MessengerApp extends Application implements AppIdCallback {

    //==============================================================================================
    // LAYER CONFIGURATION
    //==============================================================================================

    // 1. Set your Layer App ID from the Developer Console to bypass the QR code flow
    private static final String LAYER_APP_ID = null;

    // 2. Optionally replace the Google Cloud Messaging Sender ID (in the Developer Console too)
    private static final String GCM_SENDER_ID = "748607264448"; // Set your GCM Sender ID

    //==============================================================================================

    
    private static final String TAG = MessengerApp.class.getSimpleName();
    private static final boolean DEBUG = true;

    private LayerClient layerClient;
    private IdentityProvider identityProvider;
    private Atlas.ParticipantProvider participantProvider;
    private String appId = LAYER_APP_ID;

    public interface keys {
        String CONVERSATION_URI = "conversation.uri";
    }

    @Override
    public void onCreate() {
        super.onCreate();
        LayerClient.enableLogging();
        LayerClient.applicationCreated(this);
        if (appId == null) {
            this.appId = loadAppId(); 
            this.identityProvider = new QRIdentityProvider(this);
            this.participantProvider = new ParticipantProvider(this, new QRParticipantProviderCallback(this));
        } else {
            HerokuIdentityProvider herokuBasedProvider = new HerokuIdentityProvider(this, appId);
            this.identityProvider = herokuBasedProvider;
            this.participantProvider = herokuBasedProvider;
            herokuBasedProvider.load();
        }
    }

    public LayerClient getLayerClient() {
        return layerClient;
    }

    /**
     * Initializes a new LayerClient if needed, or returns the already-initialized LayerClient.
     * 
     * @param localAppId Layer App ID to initialize a LayerClient with
     * @return The newly initialized LayerClient, or the existing LayerClient
     */
    public LayerClient initLayerClient(final String localAppId) {
        if (layerClient != null) return layerClient;
        final LayerClient client = LayerClient.newInstance(this, localAppId, new Options()
                .broadcastPushInForeground(false)
                .googleCloudMessagingSenderId(GCM_SENDER_ID));
        if (DEBUG) Log.w(TAG, "onCreate() client created");

        setAppId(localAppId);
        layerClient = client;

        if (!client.isAuthenticated()) client.authenticate();
        else if (!client.isConnected()) client.connect();
        if (DEBUG) Log.w(TAG, "onCreate() Layer launched");

        if (getParticipantProvider() instanceof ParticipantProvider) {
            if (DEBUG) Log.d(TAG, "onCreate() Refreshing Contacts");
            ((ParticipantProvider)getParticipantProvider()).refresh();
        }
        return layerClient;
    }

    public Atlas.ParticipantProvider getParticipantProvider() {
        return participantProvider;
    }

    public IdentityProvider getIdentityProvider() {
        return identityProvider;
    }

    public void setAppId(String appId) {
        this.appId = appId;
        getSharedPreferences("app", MODE_PRIVATE).edit().putString("appId", appId).commit();
    }

    private String loadAppId() {
        return getSharedPreferences("app", MODE_PRIVATE).getString("appId", null);
    }

    @Override
    public String getAppId() {
        return appId;
    }

    /**
     * Converts a Bundle to the human readable string.
     *
     * @param bundle the collection for example, {@link java.util.ArrayList}, {@link java.util.HashSet} etc.
     * @return the converted string
     */
    public static String toString(Bundle bundle) {
        return toString(bundle, ", ", "");
    }

    public static String toString(Bundle bundle, String separator, String firstSeparator) {
        if (bundle == null) return "null";
        StringBuilder sb = new StringBuilder("[");
        int i = 0;
        for (Iterator<String> itKey = bundle.keySet().iterator(); itKey.hasNext(); i++) {
            String key = itKey.next();
            sb.append(i == 0 ? firstSeparator : separator).append(i).append(": ");
            sb.append(key).append(" : ").append(bundle.get(key));
        }
        sb.append("]");
        return sb.toString();
    }
    
    /** Window flags for translucency are available on Android 5.0+ */
    public static final int FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS = 0x80000000;
    public static final int FLAG_TRANSLUCENT_STATUS = 0x04000000;
    
    /** Changes Status Bar color On Android 5.0+ devices. Do nothing on devices without translucency support */
    public static void setStatusBarColor(Window wnd, int color) {
        try {
            final Method mthd_setStatusBarColor = wnd.getClass().getMethod("setStatusBarColor", int.class);
            if (mthd_setStatusBarColor != null) {
                mthd_setStatusBarColor.invoke(wnd, color);
                wnd.addFlags(FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
                wnd.clearFlags(FLAG_TRANSLUCENT_STATUS);
            }
        } catch (Exception ignored) {}
    }
}
