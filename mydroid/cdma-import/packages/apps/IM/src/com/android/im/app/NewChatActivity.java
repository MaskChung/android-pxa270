/*
 * Copyright (C) 2008 Esmertec AG.
 * Copyright (C) 2008 The Android Open Source Project
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
package com.android.im.app;

import com.android.im.IChatSession;
import com.android.im.R;
import com.android.im.app.adapter.ChatListenerAdapter;
import com.android.im.plugin.BrandingResourceIDs;
import com.android.im.service.ImServiceConstants;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentUris;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.provider.Im;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.SimpleAdapter;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NewChatActivity extends Activity {
    private static final String[] CHAT_SWITCHER_PROJECTION = {
            Im.Contacts._ID,
            Im.Contacts.PROVIDER,
            Im.Contacts.ACCOUNT,
            Im.Contacts.USERNAME,
            Im.Chats.GROUP_CHAT,
    };

    private static final int CHAT_SWITCHER_ID_COLUMN = 0;

    private static final int REQUEST_PICK_CONTACTS = RESULT_FIRST_USER + 1;

    ImApp mApp;

    ChatView mChatView;
    SimpleAlertHandler mHandler;

    private AlertDialog mSmileyDialog;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        requestWindowFeature(Window.FEATURE_NO_TITLE);

        setContentView(R.layout.chat_view);

        mChatView = (ChatView) findViewById(R.id.chatView);
        mHandler = mChatView.mHandler;

        final Handler handler = new Handler();
        mApp= ImApp.getApplication(this);
        mApp.callWhenServiceConnected(handler, new Runnable() {
            public void run() {
                resolveIntent(getIntent());
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        mChatView.onResume();
    }

    @Override
    protected void onPause() {
        mChatView.onPause();
        super.onPause();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        resolveIntent(intent);
    }

    void resolveIntent(Intent intent) {
        if (ImServiceConstants.ACTION_MANAGE_SUBSCRIPTION.equals(intent.getAction())) {
            long providerId = intent.getLongExtra(ImServiceConstants.EXTRA_INTENT_PROVIDER_ID, -1);
            String from = intent.getStringExtra(ImServiceConstants.EXTRA_INTENT_FROM_ADDRESS);
            if ((providerId == -1) || (from == null)) {
                finish();
            } else {
                mChatView.bindSubscription(providerId, from);
            }
        } else {
            Uri data = intent.getData();
            String type = getContentResolver().getType(data);
            if (Im.Chats.CONTENT_ITEM_TYPE.equals(type)) {
                mChatView.bindChat(ContentUris.parseId(data));
            } else if (Im.Invitation.CONTENT_ITEM_TYPE.equals(type)) {
                mChatView.bindInvitation(ContentUris.parseId(data));
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.chat_screen_menu, menu);

        long providerId = mChatView.getProviderId();
        BrandingResources brandingRes = mApp.getBrandingResource(providerId);
        menu.findItem(R.id.menu_view_friend_list).setTitle(
                brandingRes.getString(BrandingResourceIDs.STRING_MENU_CONTACT_LIST));
        menu.findItem(R.id.menu_switch_chats).setTitle(
                brandingRes.getString(BrandingResourceIDs.STRING_MENU_SWITCH_CHATS));
        menu.findItem(R.id.menu_insert_smiley).setTitle(
                brandingRes.getString(BrandingResourceIDs.STRING_MENU_INSERT_SMILEY));
        menu.findItem(R.id.menu_end_conversation).setTitle(
                brandingRes.getString(BrandingResourceIDs.STRING_MENU_END_CHAT));
        menu.findItem(R.id.menu_view_profile).setTitle(
                brandingRes.getString(BrandingResourceIDs.STRING_MENU_VIEW_PROFILE));
        menu.findItem(R.id.menu_block_contact).setTitle(
                brandingRes.getString(BrandingResourceIDs.STRING_MENU_BLOCK_CONTACT));
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);

        //XXX hide the invite menu, group chat is not supported by the server.
        menu.findItem(R.id.menu_invite_contact).setVisible(false);

        //XXX HACK: Yahoo! doesn't allow to block a friend. We can only block a temporary contact.
        ProviderDef provider = mApp.getProvider(mChatView.getProviderId());
        if ((provider != null) && Im.ProviderNames.YAHOO.equals(provider.mName)) {
            if (Im.Contacts.TYPE_TEMPORARY != mChatView.mType) {
                menu.findItem(R.id.menu_block_contact).setVisible(false);
            }
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_view_friend_list:
                finish();
                showRosterScreen();
                return true;

            case R.id.menu_insert_smiley:
                showSmileyDialog();
                return true;

            case R.id.menu_end_conversation:
                mChatView.closeChatSession();
                return true;

            case R.id.menu_switch_chats:
                Dashboard.openDashboard(this, mChatView.getAccountId(),
                        mChatView.getUserName());
                return true;

            case R.id.menu_invite_contact:
                startContactPicker();
                return true;

            case R.id.menu_view_profile:
                mChatView.viewProfile();
                return true;

            case R.id.menu_block_contact:
                mChatView.blockContact();
                return true;

            case R.id.menu_prev_chat:
                switchChat(-1);
                return true;

            case R.id.menu_next_chat:
                switchChat(1);
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void showRosterScreen() {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setClass(this, ContactListActivity.class);
        intent.putExtra(ImServiceConstants.EXTRA_INTENT_ACCOUNT_ID, mChatView.getAccountId());
        startActivity(intent);
    }

    private void showSmileyDialog() {
        if (mSmileyDialog == null) {
            long providerId = mChatView.getProviderId();

            final BrandingResources brandingRes = mApp.getBrandingResource(providerId);
            int[] icons = brandingRes.getSmileyIcons();
            String[] names = brandingRes.getStringArray(
                    BrandingResourceIDs.STRING_ARRAY_SMILEY_NAMES);
            final String[] texts = brandingRes.getStringArray(
                    BrandingResourceIDs.STRING_ARRAY_SMILEY_TEXTS);

            final int N = names.length;

            List<Map<String, ?>> entries = new ArrayList<Map<String, ?>>();
            for (int i = 0; i < N; i++) {
                // We might have different ASCII for the same icon, skip it if
                // the icon is already added.
                boolean added = false;
                for (int j = 0; j < i; j++) {
                    if (icons[i] == icons[j]) {
                        added = true;
                        break;
                    }
                }
                if (!added) {
                    HashMap<String, Object> entry = new HashMap<String, Object>();

                    entry. put("icon", icons[i]);
                    entry. put("name", names[i]);
                    entry.put("text", texts[i]);

                    entries.add(entry);
                }
            }

            final SimpleAdapter a = new SimpleAdapter(
                    this,
                    entries,
                    R.layout.smiley_menu_item,
                    new String[] {"icon", "name", "text"},
                    new int[] {R.id.smiley_icon, R.id.smiley_name, R.id.smiley_text});
            SimpleAdapter.ViewBinder viewBinder = new SimpleAdapter.ViewBinder() {
                public boolean setViewValue(View view, Object data, String textRepresentation) {
                    if (view instanceof ImageView) {
                        Drawable img = brandingRes.getSmileyIcon((Integer)data);
                        ((ImageView)view).setImageDrawable(img);
                        return true;
                    }
                    return false;
                }
            };
            a.setViewBinder(viewBinder);

            AlertDialog.Builder b = new AlertDialog.Builder(this);

            b.setTitle(brandingRes.getString(
                    BrandingResourceIDs.STRING_MENU_INSERT_SMILEY));

            b.setCancelable(true);
            b.setAdapter(a, new DialogInterface.OnClickListener() {
                public final void onClick(DialogInterface dialog, int which) {
                    HashMap<String, Object> item = (HashMap<String, Object>) a.getItem(which);
                    mChatView.insertSmiley((String)item.get("text"));
                }
            });

            mSmileyDialog = b.create();
        }

        mSmileyDialog.show();
    }

    private void switchChat(int delta) {
        Cursor c = getContentResolver().query(Im.Contacts.CONTENT_URI_CHAT_CONTACTS,
                CHAT_SWITCHER_PROJECTION, null, null, null);
        if(c == null) {
            return;
        }

        final int N = c.getCount();
        if (N <= 1) {
            c.close();
            return;
        }

        int current = -1;
        // find current position
        for (int i = 0; i < N; i++) {
            c.moveToNext();
            long id = c.getLong(CHAT_SWITCHER_ID_COLUMN);
            if (id == mChatView.getChatId()) {
                current = i;
            }
        }
        if (current == -1) {
            c.close();
            return;
        }

        int newPosition = (current + delta) % N;
        if (newPosition < 0) {
            newPosition += N;
        }

        c.moveToPosition(newPosition);

        Intent intent;
        long id = c.getLong(CHAT_SWITCHER_ID_COLUMN);
        Uri uri = ContentUris.withAppendedId(Im.Chats.CONTENT_URI, id);
        intent = new Intent(Intent.ACTION_VIEW, uri);

        c.close();
        startActivity(intent);
        finish();
    }

    private void startContactPicker() {
        Uri.Builder builder = Im.Contacts.CONTENT_URI_ONLINE_CONTACTS_BY.buildUpon();
        ContentUris.appendId(builder, mChatView.getProviderId());
        ContentUris.appendId(builder, mChatView.getAccountId());
        Uri data = builder.build();

        try {
            Intent i = new Intent(Intent.ACTION_PICK, data);
            i.putExtra(ContactsPickerActivity.EXTRA_EXCLUDED_CONTACTS,
                    mChatView.getCurrentChatSession().getPariticipants());
            startActivityForResult(i, REQUEST_PICK_CONTACTS);
        } catch (RemoteException e) {
            mHandler.showServiceErrorAlert();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode,
            Intent data) {
        if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_PICK_CONTACTS) {
                String username = data.getStringExtra(
                        ContactsPickerActivity.EXTRA_RESULT_USERNAME);
                try {
                    IChatSession chatSession = mChatView.getCurrentChatSession();
                    if (chatSession.isGroupChatSession()) {
                        chatSession.inviteContact(username);
                        showInvitationHasSent(username);
                    } else {
                        chatSession.convertToGroupChat();
                        new ContactInvitor(chatSession, username).start();
                    }
                } catch (RemoteException e) {
                    mHandler.showServiceErrorAlert();
                }
            }
        }
    }

    void showInvitationHasSent(String contact) {
        Toast.makeText(NewChatActivity.this,
                getString(R.string.invitation_sent_prompt, contact),
                Toast.LENGTH_SHORT).show();
    }

    private class ContactInvitor extends ChatListenerAdapter {
        private final IChatSession mChatSession;
        String mContact;

        public ContactInvitor(IChatSession session, String data) {
            mChatSession = session;
            mContact = data;
        }

        @Override
        public void onConvertedToGroupChat(IChatSession ses) {
            try {
                final long chatId = mChatSession.getId();
                mChatSession.inviteContact(mContact);
                mHandler.post(new Runnable(){
                    public void run() {
                        mChatView.bindChat(chatId);
                        showInvitationHasSent(mContact);
                    }
                });
                mChatSession.unregisterChatListener(this);
            } catch (RemoteException e) {
                mHandler.showServiceErrorAlert();
            }
        }

        public void start() throws RemoteException {
            mChatSession.registerChatListener(this);
        }
    }
}
