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
package com.layer.atlas.messenger.provider;

import android.util.Log;

import com.layer.atlas.messenger.AppIdCallback;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by steven on 5/24/15.
 */
public class QRIdentityProvider extends IdentityProvider {
    private final String TAG = QRIdentityProvider.class.getSimpleName();

    protected final AppIdCallback mAppIdCallback;
    
    public QRIdentityProvider(AppIdCallback appIdCallback) {
        this.mAppIdCallback = appIdCallback;
    }

    @Override
    public Result getIdentityToken(String nonce, String userName, String userPassword) {
        try {
            JSONObject rootObject = new JSONObject();
            rootObject.put("nonce", nonce);
            rootObject.put("name", userName);
            StringEntity entity = new StringEntity(rootObject.toString(), "UTF-8");
            entity.setContentType("application/json");

            HttpPost post = new HttpPost("https://layer-identity-provider.herokuapp.com/apps/" + mAppIdCallback.getAppId() + "/atlas_identities");
            post.setHeader("Content-Type", "application/json");
            post.setHeader("Accept", "application/json");
            post.setHeader("X_LAYER_APP_ID", mAppIdCallback.getAppId());
            post.setEntity(entity);
            HttpResponse response = (new DefaultHttpClient()).execute(post);
            if (HttpStatus.SC_OK != response.getStatusLine().getStatusCode() && HttpStatus.SC_CREATED != response.getStatusLine().getStatusCode()) {
                Log.e(TAG, String.format("Got status %d when logging in", response.getStatusLine().getStatusCode()));
                return null;
            }

            String responseString = EntityUtils.toString(response.getEntity());
            JSONObject jsonResp = new JSONObject(responseString);
            Result result = new Result();
            result.error = jsonResp.optString("error", null);
            result.identityToken = jsonResp.optString("identity_token");
            JSONArray atlasIdentities = jsonResp.getJSONArray("atlas_identities");
            List<Participant> participants = new ArrayList<Participant>(atlasIdentities.length());
            for (int i = 0; i < atlasIdentities.length(); i++) {
                JSONObject identity = atlasIdentities.getJSONObject(i);
                Participant participant = new Participant();
                participant.firstName = identity.getString("name");
                participant.userId = identity.getString("id");
                participants.add(participant);
            }
            result.participants = participants;
            return result;
        } catch (Exception e) {
            Log.e(TAG, "Error when fetching identity token", e);
        }
        return null;
    }

    @Override
    public boolean passwordRequired() {
        return false;
    }
}
