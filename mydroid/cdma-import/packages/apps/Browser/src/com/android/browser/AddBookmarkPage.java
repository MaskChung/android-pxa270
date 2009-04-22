/*
 * Copyright (C) 2006 The Android Open Source Project
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

package com.android.browser;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.ParseException;
import android.net.WebAddress;
import android.os.Bundle;
import android.provider.Browser;
import android.view.View;
import android.view.Window;
import android.webkit.WebIconDatabase;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Date;

public class AddBookmarkPage extends Activity {

    private final String LOGTAG = "Bookmarks";

    private EditText    mTitle;
    private EditText    mAddress;
    private TextView    mButton;
    private View        mCancelButton;
    private boolean     mEditingExisting;
    private Bundle      mMap;
    
    private static final String[]   mProjection = 
        { "_id", "url", "bookmark", "created", "title" };
    private static final String     WHERE_CLAUSE = "url = ? AND bookmark = 0";
    private final String[]          SELECTION_ARGS = new String[1];

    private View.OnClickListener mSaveBookmark = new View.OnClickListener() {
        public void onClick(View v) {
            if (save()) {
                finish();
                Toast.makeText(AddBookmarkPage.this, R.string.bookmark_saved,
                        Toast.LENGTH_LONG).show();
            }
        }
    };

    private View.OnClickListener mCancel = new View.OnClickListener() {
        public void onClick(View v) {
            finish();
        }
    };

    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        requestWindowFeature(Window.FEATURE_LEFT_ICON);
        setContentView(R.layout.browser_add_bookmark);
        setTitle(R.string.save_to_bookmarks);
        getWindow().setFeatureDrawableResource(Window.FEATURE_LEFT_ICON, R.drawable.ic_dialog_bookmark);
        
        String title = null;
        String url = null;
        mMap = getIntent().getExtras();
        if (mMap != null) {
            Bundle b = mMap.getBundle("bookmark");
            if (b != null) {
                mMap = b;
                mEditingExisting = true;
                setTitle(R.string.edit_bookmark);
            }
            title = mMap.getString("title");
            url = mMap.getString("url");
        }

        mTitle = (EditText) findViewById(R.id.title);
        mTitle.setText(title);
        mAddress = (EditText) findViewById(R.id.address);
        mAddress.setText(url);


        View.OnClickListener accept = mSaveBookmark;
        mButton = (TextView) findViewById(R.id.OK);
        mButton.setOnClickListener(accept);

        mCancelButton = findViewById(R.id.cancel);
        mCancelButton.setOnClickListener(mCancel);
        
        if (!getWindow().getDecorView().isInTouchMode()) {
            mButton.requestFocus();
        }
    }
    
    /**
     *  Save the data to the database. 
     *  Also, change the view to dialog stating 
     *  that the webpage has been saved.
     */
    boolean save() {
        String title = mTitle.getText().toString().trim();
        String unfilteredUrl = mAddress.getText().toString();
        boolean emptyTitle = title.length() == 0;
        boolean emptyUrl = unfilteredUrl.trim().length() == 0;
        Resources r = getResources();
        if (emptyTitle) {
            if (emptyUrl) {
                setTitle(r.getText(R.string.empty_bookmark));
                return false;
            }    
            setTitle(r.getText(R.string.bookmark_needs_title));
            return false;
        }
        if (emptyUrl) {
            setTitle(r.getText(R.string.bookmark_needs_url));
            return false;
        }
        String url = unfilteredUrl;
        if (!(url.startsWith("about:") || url.startsWith("data:") || url
                .startsWith("file:"))) {
            WebAddress address;
            try {
                address = new WebAddress(unfilteredUrl);
            } catch (ParseException e) {
                setTitle(r.getText(R.string.bookmark_url_not_valid));
                return false;
            }
            if (address.mHost.length() == 0) {
                setTitle(r.getText(R.string.bookmark_url_not_valid));
                return false;
            }
            url = address.toString();
        }
        try {
            if (mEditingExisting) {
                mMap.putString("title", title);
                mMap.putString("url", url);
                setResult(RESULT_OK, (new Intent()).setAction(
                        getIntent().toString()).putExtras(mMap));
            } else {
                // Want to append to the beginning of the list
                long creationTime = new Date().getTime();
                SELECTION_ARGS[0] = url;
                ContentResolver cr = getContentResolver();
                Cursor c = cr.query(Browser.BOOKMARKS_URI,
                        mProjection,
                        WHERE_CLAUSE,
                        SELECTION_ARGS,
                        null);
                if (c.moveToFirst()) {
                    // This means we have been to this site, so convert the 
                    // history item to a bookmark.
                    ContentValues map = new ContentValues();
                    map.put(Browser.BookmarkColumns.CREATED, creationTime);
                    map.put(Browser.BookmarkColumns.TITLE, title);
                    map.put(Browser.BookmarkColumns.BOOKMARK, 1);
                    cr.update(Browser.BOOKMARKS_URI, map, 
                            "_id = " + c.getInt(0), null);
                } else {
                    // Adding a bookmark for a site the user has not been to.
                    ContentValues map = new ContentValues();
                    map.put(Browser.BookmarkColumns.TITLE, title);
                    map.put(Browser.BookmarkColumns.URL, url);
                    map.put(Browser.BookmarkColumns.CREATED, creationTime);
                    map.put(Browser.BookmarkColumns.BOOKMARK, 1);
                    map.put(Browser.BookmarkColumns.DATE, 0);
                    map.put(Browser.BookmarkColumns.VISITS, 0);
                    cr.insert(Browser.BOOKMARKS_URI, map);
                }
                WebIconDatabase.getInstance().retainIconForPageUrl(url);
                c.deactivate();
                setResult(RESULT_OK);
            }
        } catch (IllegalStateException e) {
            setTitle(r.getText(R.string.no_database));
            return false;
        }
        return true;
    }
}
