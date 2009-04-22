/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.android.contacts;

import com.google.android.collect.Lists;

import static com.android.contacts.ContactEntryAdapter.CONTACT_CUSTOM_RINGTONE_COLUMN;
import static com.android.contacts.ContactEntryAdapter.CONTACT_NAME_COLUMN;
import static com.android.contacts.ContactEntryAdapter.CONTACT_NOTES_COLUMN;
import static com.android.contacts.ContactEntryAdapter.CONTACT_PROJECTION;
import static com.android.contacts.ContactEntryAdapter.CONTACT_SEND_TO_VOICEMAIL_COLUMN;
import static com.android.contacts.ContactEntryAdapter.METHODS_AUX_DATA_COLUMN;
import static com.android.contacts.ContactEntryAdapter.METHODS_DATA_COLUMN;
import static com.android.contacts.ContactEntryAdapter.METHODS_ID_COLUMN;
import static com.android.contacts.ContactEntryAdapter.METHODS_ISPRIMARY_COLUMN;
import static com.android.contacts.ContactEntryAdapter.METHODS_KIND_COLUMN;
import static com.android.contacts.ContactEntryAdapter.METHODS_LABEL_COLUMN;
import static com.android.contacts.ContactEntryAdapter.METHODS_PROJECTION;
import static com.android.contacts.ContactEntryAdapter.METHODS_TYPE_COLUMN;
import static com.android.contacts.ContactEntryAdapter.ORGANIZATIONS_COMPANY_COLUMN;
import static com.android.contacts.ContactEntryAdapter.ORGANIZATIONS_ID_COLUMN;
import static com.android.contacts.ContactEntryAdapter.ORGANIZATIONS_ISPRIMARY_COLUMN;
import static com.android.contacts.ContactEntryAdapter.ORGANIZATIONS_LABEL_COLUMN;
import static com.android.contacts.ContactEntryAdapter.ORGANIZATIONS_PROJECTION;
import static com.android.contacts.ContactEntryAdapter.ORGANIZATIONS_TITLE_COLUMN;
import static com.android.contacts.ContactEntryAdapter.ORGANIZATIONS_TYPE_COLUMN;
import static com.android.contacts.ContactEntryAdapter.PHONES_ID_COLUMN;
import static com.android.contacts.ContactEntryAdapter.PHONES_ISPRIMARY_COLUMN;
import static com.android.contacts.ContactEntryAdapter.PHONES_LABEL_COLUMN;
import static com.android.contacts.ContactEntryAdapter.PHONES_NUMBER_COLUMN;
import static com.android.contacts.ContactEntryAdapter.PHONES_PROJECTION;
import static com.android.contacts.ContactEntryAdapter.PHONES_TYPE_COLUMN;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.provider.Contacts;
import android.provider.Contacts.ContactMethods;
import android.provider.Contacts.Intents.Insert;
import android.provider.Contacts.Organizations;
import android.provider.Contacts.People;
import android.provider.Contacts.Phones;
import android.telephony.PhoneNumberFormattingTextWatcher;
import android.text.TextUtils;
import android.text.method.DialerKeyListener;
import android.text.method.TextKeyListener;
import android.text.method.TextKeyListener.Capitalize;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ExpandableListView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SimpleExpandableListAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Activity for editing or inserting a contact. Note that if the contact data changes in the
 * background while this activity is running, the updates will be overwritten.
 */
public final class EditContactActivity extends Activity implements View.OnClickListener,
        ExpandableListView.OnChildClickListener {
    private static final String TAG = "EditContactActivity";

    private static final int STATE_UNKNOWN = 0;
    /** Editing an existing contact */
    private static final int STATE_EDIT = 1;
    /** The full insert mode */
    private static final int STATE_INSERT = 2;

    /** The launch code when picking a photo and the raw data is returned */
    private static final int PHOTO_PICKED_WITH_DATA = 3021;

    /** The launch code when picking a ringtone */
    private static final int RINGTONE_PICKED = 3023;
    
    // Label picker position info
    final static int LABEL_PICKER_PHONES_POSITION = 0;
    final static int LABEL_PICKER_EMAIL_POSITION = 1;
    final static int LABEL_PICKER_IM_POSITION = 2;
    final static int LABEL_PICKER_POSTAL_POSITION = 3;
    final static int LABEL_PICKER_OTHER_POSITION = 4;

    // These correspond to the string array in resources for picker "other" items
    final static int OTHER_ORGANIZATION = 0;
    final static int OTHER_NOTE = 1;
    
    // Dialog IDs
    final static int LABEL_PICKER_ALL_TYPES_DIALOG = 1;
    final static int DELETE_CONFIRMATION_DIALOG = 2;

    // Menu item IDs
    public static final int MENU_ITEM_SAVE = 1;
    public static final int MENU_ITEM_DONT_SAVE = 2;
    public static final int MENU_ITEM_DELETE = 3;
    public static final int MENU_ITEM_ADD = 5;
    public static final int MENU_ITEM_PHOTO = 6;
    
    // Key listener types
    final static int INPUT_TEXT = 1;
    final static int INPUT_TEXT_WORDS = 2;
    final static int INPUT_TEXT_SENTENCES = 3;
    final static int INPUT_DIALER = 4;

    /** Used to represent an invalid type for a contact entry */
    private static final int INVALID_TYPE = -1;
    
    /** The default type for a phone that is added via an intent */
    private static final int DEFAULT_PHONE_TYPE = Phones.TYPE_MOBILE;

    /** The default type for an email that is added via an intent */
    private static final int DEFAULT_EMAIL_TYPE = ContactMethods.TYPE_HOME;

    /** The default type for a postal address that is added via an intent */
    private static final int DEFAULT_POSTAL_TYPE = ContactMethods.TYPE_HOME;

    private int mState; // saved across instances
    private boolean mInsert; // saved across instances
    private Uri mUri; // saved across instances
    /** In insert mode this is the photo */
    private Bitmap mPhoto; // saved across instances
    private boolean mPhotoChanged = false; // saved across instances
    
    private EditText mNameView;
    private ImageView mPhotoImageView;
    private Button mPhotoButton;
    private CheckBox mSendToVoicemailCheckBox;
    private LinearLayout mLayout;
    private LayoutInflater mInflater;
    private MenuItem mPhotoMenuItem;
    private boolean mPhotoPresent = false;

    // These are accessed by inner classes. They're package scoped to make access more efficient.
    /* package */ ContentResolver mResolver;
    /* package */ ArrayList<EditEntry> mPhoneEntries = new ArrayList<EditEntry>();
    /* package */ ArrayList<EditEntry> mEmailEntries = new ArrayList<EditEntry>();
    /* package */ ArrayList<EditEntry> mImEntries = new ArrayList<EditEntry>();
    /* package */ ArrayList<EditEntry> mPostalEntries = new ArrayList<EditEntry>();
    /* package */ ArrayList<EditEntry> mOtherEntries = new ArrayList<EditEntry>();
    /* package */ ArrayList<ArrayList<EditEntry>> mSections = new ArrayList<ArrayList<EditEntry>>();

    /* package */ static final int MSG_DELETE = 1;
    /* package */ static final int MSG_CHANGE_LABEL = 2;
    /* package */ static final int MSG_ADD_PHONE = 3;
    /* package */ static final int MSG_ADD_EMAIL = 4;
    /* package */ static final int MSG_ADD_POSTAL = 5;

    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.photoButton:
            case R.id.photoImage: {
                doPickPhotoAction();
                break;
            }

            case R.id.addMore:
                doAddAction();
                break;

            case R.id.saveButton:
                doSaveAction();
                break;

            case R.id.discardButton:
                doRevertAction();
                break;

            case R.id.delete:
            case R.id.delete2: {
                EditEntry entry = findEntryForView(v);
                if (entry != null) {
                    // Clear the text and hide the view so it gets saved properly
                    ((TextView) entry.view.findViewById(R.id.data)).setText(null);
                    entry.view.setVisibility(View.GONE);
                    entry.isDeleted = true;
                }
                break;
            }

            case R.id.label: {
                EditEntry entry = findEntryForView(v);
                if (entry != null) {
                    String[] labels = getLabelsForKind(this, entry.kind);
                    LabelPickedListener listener = new LabelPickedListener(entry, labels);
                    new AlertDialog.Builder(EditContactActivity.this)
                            .setItems(labels, listener)
                            .setTitle(R.string.selectLabel)
                            .show();
                }
                break;
            }
                
            case R.id.data: {
                EditEntry entry = findEntryForView(v);
                if (isRingtoneEntry(entry)) {
                    doPickRingtone(entry);
                }
                break;
            }
        }
    }

    private void setPhotoPresent(boolean present) {
        mPhotoImageView.setVisibility(present ? View.VISIBLE : View.GONE);
        mPhotoButton.setVisibility(present ? View.GONE : View.VISIBLE);
        mPhotoPresent = present;
        if (mPhotoMenuItem != null) {
            if (present) {
                mPhotoMenuItem.setTitle(R.string.removePicture);
                mPhotoMenuItem.setIcon(android.R.drawable.ic_menu_delete);
            } else {
                mPhotoMenuItem.setTitle(R.string.addPicture);
                mPhotoMenuItem.setIcon(android.R.drawable.ic_menu_add);
            }
        }
    }
    
    private EditEntry findEntryForView(View v) {
        // Try to find the entry for this view
        EditEntry entry = null;
        do {
            Object tag = v.getTag();
            if (tag != null && tag instanceof EditEntry) {
                entry = (EditEntry) tag;
                break;
            } else {
                ViewParent parent = v.getParent();
                if (parent != null && parent instanceof View) {
                    v = (View) parent;
                } else {
                    v = null;
                }
            }
        } while (v != null);
        return entry;
    }

    private DialogInterface.OnClickListener mDeleteContactDialogListener =
            new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int button) {
            mResolver.delete(mUri, null, null);
            finish();
        }
    };

    private boolean mMobilePhoneAdded = false;
    private boolean mPrimaryEmailAdded = false;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        mResolver = getContentResolver();

        // Build the list of sections
        setupSections();

        // Load the UI
        setContentView(R.layout.edit_contact);
        mLayout = (LinearLayout) findViewById(R.id.list);
        mNameView = (EditText) findViewById(R.id.name);
        mPhotoImageView = (ImageView) findViewById(R.id.photoImage);
        mPhotoImageView.setOnClickListener(this);
        mPhotoImageView.setVisibility(View.GONE);
        mPhotoButton = (Button) findViewById(R.id.photoButton);
        mPhotoButton.setOnClickListener(this);
        mSendToVoicemailCheckBox = (CheckBox) findViewById(R.id.send_to_voicemail);

        // Setup the bottom buttons 
        View view = findViewById(R.id.addMore);
        view.setOnClickListener(this);
        view = findViewById(R.id.saveButton);
        view.setOnClickListener(this);
        view = findViewById(R.id.discardButton);
        view.setOnClickListener(this);

        mInflater = getLayoutInflater();

        // Resolve the intent
        mState = STATE_UNKNOWN;
        Intent intent = getIntent();
        String action = intent.getAction();
        mUri = intent.getData();
        if (mUri != null) {
            if (action.equals(Intent.ACTION_EDIT)) {
                if (icicle == null) {
                    // Build the entries & views
                    buildEntriesForEdit(getIntent().getExtras());
                    buildViews();
                }
                mState = STATE_EDIT;
            } else if (action.equals(Intent.ACTION_INSERT)) {
                if (icicle == null) {
                    // Build the entries & views
                    buildEntriesForInsert(getIntent().getExtras());
                    buildViews();
                }
                mState = STATE_INSERT;
                mInsert = true;
            }
        }

        if (mState == STATE_UNKNOWN) {
            Log.e(TAG, "Cannot resolve intent: " + intent);
            finish();
            return;
        }

        if (mState == STATE_EDIT) {
            setTitle(getResources().getText(R.string.editContact_title_edit));
        } else {
            setTitle(getResources().getText(R.string.editContact_title_insert));
        }
    }

    private void setupSections() {
        mSections.add(mPhoneEntries);
        mSections.add(mEmailEntries);
        mSections.add(mImEntries);
        mSections.add(mPostalEntries);
        mSections.add(mOtherEntries);
    }
    
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putParcelableArrayList("phoneEntries", mPhoneEntries);
        outState.putParcelableArrayList("emailEntries", mEmailEntries);
        outState.putParcelableArrayList("imEntries", mImEntries);
        outState.putParcelableArrayList("postalEntries", mPostalEntries);
        outState.putParcelableArrayList("otherEntries", mOtherEntries);
        outState.putInt("state", mState);
        outState.putBoolean("insert", mInsert);
        outState.putParcelable("uri", mUri);
        outState.putString("name", mNameView.getText().toString());
        outState.putParcelable("photo", mPhoto);
        outState.putBoolean("photoChanged", mPhotoChanged);
        outState.putBoolean("sendToVoicemail", mSendToVoicemailCheckBox.isChecked());
    }

    @Override
    protected void onRestoreInstanceState(Bundle inState) {
        mPhoneEntries = inState.getParcelableArrayList("phoneEntries");
        mEmailEntries = inState.getParcelableArrayList("emailEntries");
        mImEntries = inState.getParcelableArrayList("imEntries");
        mPostalEntries = inState.getParcelableArrayList("postalEntries");
        mOtherEntries = inState.getParcelableArrayList("otherEntries");
        setupSections();

        mState = inState.getInt("state");
        mInsert = inState.getBoolean("insert");
        mUri = inState.getParcelable("uri");
        mNameView.setText(inState.getString("name"));
        mPhoto = inState.getParcelable("photo");
        if (mPhoto != null) {
            mPhotoImageView.setImageBitmap(mPhoto);
            setPhotoPresent(true);
        } else {
            mPhotoImageView.setImageResource(R.drawable.ic_contact_picture);
            setPhotoPresent(false);
        }
        mPhotoChanged = inState.getBoolean("photoChanged");
        mSendToVoicemailCheckBox.setChecked(inState.getBoolean("sendToVoicemail"));
        
        // Now that everything is restored, build the view
        buildViews();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != RESULT_OK) {
            return;
        }

        switch (requestCode) {
            case PHOTO_PICKED_WITH_DATA: {
                final Bundle extras = data.getExtras();
                if (extras != null) {
                    Bitmap photo = extras.getParcelable("data");
                    mPhoto = photo;
                    mPhotoChanged = true;
                    mPhotoImageView.setImageBitmap(photo);
                    setPhotoPresent(true);
                }
                break;
            }

            case RINGTONE_PICKED: {
                Uri pickedUri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
                handleRingtonePicked(pickedUri);
                break;
            }
        }
    }
    
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK: {
                doSaveAction();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(0, MENU_ITEM_SAVE, 0, R.string.menu_done)
                .setIcon(android.R.drawable.ic_menu_save)
                .setAlphabeticShortcut('\n');
        menu.add(0, MENU_ITEM_DONT_SAVE, 0, R.string.menu_doNotSave)
                .setIcon(android.R.drawable.ic_menu_close_clear_cancel)
                .setAlphabeticShortcut('q');
        if (!mInsert) {
            menu.add(0, MENU_ITEM_DELETE, 0, R.string.menu_deleteContact)
                    .setIcon(android.R.drawable.ic_menu_delete);
        }

        menu.add(0, MENU_ITEM_ADD, 0, R.string.menu_addItem)
                .setIcon(android.R.drawable.ic_menu_add)
                .setAlphabeticShortcut('n');

        mPhotoMenuItem = menu.add(0, MENU_ITEM_PHOTO, 0, null);
        // Updates the state of the menu item
        setPhotoPresent(mPhotoPresent);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_ITEM_SAVE:
                doSaveAction();
                return true;
    
            case MENU_ITEM_DONT_SAVE:
                doRevertAction();
                return true;
    
            case MENU_ITEM_DELETE:
                // Get confirmation
                showDialog(DELETE_CONFIRMATION_DIALOG);
                return true;
    
            case MENU_ITEM_ADD:
                doAddAction();
                return true;

            case MENU_ITEM_PHOTO:
                if (!mPhotoPresent) {
                    doPickPhotoAction();
                } else {
                    doRemovePhotoAction();
                }
                return true;
        }

        return false;
    }

    private void doAddAction() {
        showDialog(LABEL_PICKER_ALL_TYPES_DIALOG);
    }

    private void doRevertAction() {
        finish();
    }

    private void doPickPhotoAction() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT, null);
        // TODO: get these values from constants somewhere
        intent.setType("image/*");
        intent.putExtra("crop", "true");
        intent.putExtra("aspectX", 1);
        intent.putExtra("aspectY", 1);
        intent.putExtra("outputX", 96);
        intent.putExtra("outputY", 96);
        try {
            intent.putExtra("return-data", true);
            startActivityForResult(intent, PHOTO_PICKED_WITH_DATA);
        } catch (ActivityNotFoundException e) {
            new AlertDialog.Builder(EditContactActivity.this)
                .setTitle(R.string.errorDialogTitle)
                .setMessage(R.string.photoPickerNotFoundText)
                .setPositiveButton(R.string.okButtonText, null)
                .show();
        }
    }

    private void doRemovePhotoAction() {
        mPhoto = null;
        mPhotoChanged = true;
        mPhotoImageView.setImageResource(R.drawable.ic_contact_picture);
        setPhotoPresent(false);
    }
    
    private void doPickRingtone(EditEntry entry) {
        Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
        // Allow user to pick 'Default'
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
        // Show only ringtones
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE);
        // Don't show 'Silent'
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false);
        if (entry.data != null) {
            Uri ringtoneUri = Uri.parse(entry.data);
            // Put checkmark next to the current ringtone for this contact
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, ringtoneUri);
        }
        // Launch!
        startActivityForResult(intent, RINGTONE_PICKED);
    }
    
    private void handleRingtonePicked(Uri pickedUri) {
        EditEntry entry = getRingtoneEntry();
        if (entry == null) {
            Log.w(TAG, "Ringtone picked but could not find ringtone entry");
            return;
        }
        
        if (pickedUri == null || RingtoneManager.isDefault(pickedUri)) {
            entry.data = null;
        } else {
            entry.data = pickedUri.toString();
        }
        
        updateRingtoneView(entry);
    }

    private void updateRingtoneView(EditEntry entry) {
        if (entry.data == null) {
            updateDataView(entry, getString(R.string.default_ringtone));
        } else {
            Uri ringtoneUri = Uri.parse(entry.data);
            Ringtone ringtone = RingtoneManager.getRingtone(this, ringtoneUri);
            if (ringtone == null) {
                Log.w(TAG, "ringtone's URI doesn't resolve to a Ringtone");
                return;
            }
            updateDataView(entry, ringtone.getTitle(this));
        }
    }
    
    private void updateDataView(EditEntry entry, String text) {
        TextView dataView = (TextView) entry.view.findViewById(R.id.data);
        dataView.setText(text);
    }
    
    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case LABEL_PICKER_ALL_TYPES_DIALOG:
                return createAllTypesPicker();

            case DELETE_CONFIRMATION_DIALOG:
                return new AlertDialog.Builder(EditContactActivity.this)
                        .setTitle(R.string.deleteConfirmation_title)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setMessage(R.string.deleteConfirmation)
                        .setNegativeButton(R.string.noButton, null)
                        .setPositiveButton(R.string.yesButton, mDeleteContactDialogListener)
                        .setCancelable(false)
                        .create();
        }
        return super.onCreateDialog(id);
    }
    
    static String[] getLabelsForKind(Context context, int kind) {
        final Resources resources = context.getResources();
        switch (kind) {
            case Contacts.KIND_PHONE:
                return resources.getStringArray(android.R.array.phoneTypes);
            case Contacts.KIND_EMAIL:
                return resources.getStringArray(android.R.array.emailAddressTypes);
            case Contacts.KIND_POSTAL:
                return resources.getStringArray(android.R.array.postalAddressTypes);
            case Contacts.KIND_IM:
                return resources.getStringArray(android.R.array.imProtocols);
            case Contacts.KIND_ORGANIZATION:
                return resources.getStringArray(android.R.array.organizationTypes);
            case EditEntry.KIND_CONTACT:
                return resources.getStringArray(R.array.otherLabels);
        }
        return null;
    }

    int getTypeFromLabelPosition(CharSequence[] labels, int labelPosition) {
        // In the UI Custom... comes last, but it is uses the constant 0
        // so it is in the same location across the various kinds. Fix up the
        // position to a valid type here.
        if (labelPosition == labels.length - 1) {
            return ContactMethods.TYPE_CUSTOM;
        } else {
            return labelPosition + 1;
        }
    }

    public boolean onChildClick(ExpandableListView parent, View v, int groupPosition,
            int childPosition, long id) {
        EditEntry entry = null;

        // Make the dialog go away
        dismissDialog(LABEL_PICKER_ALL_TYPES_DIALOG);

        // Create the new entry
        switch (groupPosition) {
            case LABEL_PICKER_PHONES_POSITION: {
                String[] labels = getLabelsForKind(this, Contacts.KIND_PHONE);
                final int type = getTypeFromLabelPosition(labels, childPosition);
                entry = EditEntry.newPhoneEntry(EditContactActivity.this,
                        labels[childPosition], type,
                        null, Uri.withAppendedPath(mUri, People.Phones.CONTENT_DIRECTORY), 0);
                if (type == Phones.TYPE_CUSTOM) {
                    createCustomPicker(entry, mPhoneEntries);
                    return true;
                } else {
                    mPhoneEntries.add(entry);
                }
                break;
            }

            case LABEL_PICKER_EMAIL_POSITION: {
                String[] labels = getLabelsForKind(this, Contacts.KIND_EMAIL);
                final int type = getTypeFromLabelPosition(labels, childPosition);
                entry = EditEntry.newEmailEntry(EditContactActivity.this,
                        labels[childPosition], type, null,
                        Uri.withAppendedPath(mUri, People.ContactMethods.CONTENT_DIRECTORY), 0);
                if (type == ContactMethods.TYPE_CUSTOM) {
                    createCustomPicker(entry, mEmailEntries);
                    return true;
                } else {
                    mEmailEntries.add(entry);
                }
                break;
            }
            
            case LABEL_PICKER_IM_POSITION: {
                String[] labels = getLabelsForKind(this, Contacts.KIND_IM);
                entry = EditEntry.newImEntry(EditContactActivity.this,
                        labels[childPosition], childPosition, null,
                        Uri.withAppendedPath(mUri, People.ContactMethods.CONTENT_DIRECTORY), 0);
                mImEntries.add(entry);
                break;
            }

            case LABEL_PICKER_POSTAL_POSITION: {
                String[] labels = getLabelsForKind(this, Contacts.KIND_POSTAL);
                final int type = getTypeFromLabelPosition(labels, childPosition);
                entry = EditEntry.newPostalEntry(EditContactActivity.this,
                        labels[childPosition], type, null,
                        Uri.withAppendedPath(mUri, People.ContactMethods.CONTENT_DIRECTORY), 0);
                if (type == ContactMethods.TYPE_CUSTOM) {
                    createCustomPicker(entry, mPostalEntries);
                    return true;
                } else {
                    mPostalEntries.add(entry);
                }
                break;
            }
            
            case LABEL_PICKER_OTHER_POSITION: {
                switch (childPosition) {
                    case OTHER_ORGANIZATION:
                        entry = EditEntry.newOrganizationEntry(EditContactActivity.this,
                                Uri.withAppendedPath(mUri, Organizations.CONTENT_DIRECTORY),
                                Organizations.TYPE_WORK);
                        mOtherEntries.add(entry);
                        break;

                    case OTHER_NOTE:
                        entry = EditEntry.newNotesEntry(EditContactActivity.this, null, mUri);
                        mOtherEntries.add(entry);
                        break;
                        
                    default:
                        entry = null;
                }
                break;
            }

            default:
                entry = null;
        }

        // Rebuild the views if needed
        if (entry != null) {
            buildViews();

            View dataView = entry.view.findViewById(R.id.data);
            if (dataView == null) {
                entry.view.requestFocus();
            } else {
                dataView.requestFocus();
            }
        }
        return true;
    }

    private EditEntry getRingtoneEntry() {
        for (int i = mOtherEntries.size() - 1; i >= 0; i--) {
            EditEntry entry = mOtherEntries.get(i);
            if (isRingtoneEntry(entry)) {
                return entry;
            }
        }
        return null;
    }
    
    private static boolean isRingtoneEntry(EditEntry entry) {
        return entry != null && entry.column != null && entry.column.equals(People.CUSTOM_RINGTONE);
    }
    
    private Dialog createAllTypesPicker() {
        // Setup the adapter
        List<Map<String, ?>> groupData = Lists.newArrayList();
        List<List<Map<String, ?>>> childData = Lists.newArrayList();
        List<Map<String, ?>> children;
        HashMap<String, CharSequence> curGroupMap;
        CharSequence[] labels;
        int labelsSize;

        // Phones
        curGroupMap = new HashMap<String, CharSequence>();
        groupData.add(curGroupMap);
        curGroupMap.put("data", getText(R.string.phoneLabelsGroup));

        labels = getLabelsForKind(this, Contacts.KIND_PHONE);
        labelsSize = labels.length;
        children = Lists.newArrayList();
        for (int i = 0; i < labelsSize; i++) {
            HashMap<String, CharSequence> curChildMap = new HashMap<String, CharSequence>();
            children.add(curChildMap);
            curChildMap.put("data", labels[i]);
        }
        childData.add(LABEL_PICKER_PHONES_POSITION, children);

        // Email
        curGroupMap = new HashMap<String, CharSequence>();
        groupData.add(curGroupMap);
        curGroupMap.put("data", getText(R.string.emailLabelsGroup));

        labels = getLabelsForKind(this, Contacts.KIND_EMAIL);
        labelsSize = labels.length;
        children = Lists.newArrayList();
        for (int i = 0; i < labelsSize; i++) {
            HashMap<String, CharSequence> curChildMap = new HashMap<String, CharSequence>();
            children.add(curChildMap);
            curChildMap.put("data", labels[i]);
        }
        childData.add(LABEL_PICKER_EMAIL_POSITION, children);

        // IM
        curGroupMap = new HashMap<String, CharSequence>();
        groupData.add(curGroupMap);
        curGroupMap.put("data", getText(R.string.imLabelsGroup));

        labels = getLabelsForKind(this, Contacts.KIND_IM);
        labelsSize = labels.length;
        children = Lists.newArrayList();
        for (int i = 0; i < labelsSize; i++) {
            HashMap<String, CharSequence> curChildMap = new HashMap<String, CharSequence>();
            children.add(curChildMap);
            curChildMap.put("data", labels[i]);
        }
        childData.add(LABEL_PICKER_IM_POSITION, children);
        
        // Postal
        curGroupMap = new HashMap<String, CharSequence>();
        groupData.add(curGroupMap);
        curGroupMap.put("data", getText(R.string.postalLabelsGroup));

        labels = getLabelsForKind(this, Contacts.KIND_POSTAL);
        labelsSize = labels.length;
        children = Lists.newArrayList();
        for (int i = 0; i < labelsSize; i++) {
            HashMap<String, CharSequence> curChildMap = new HashMap<String, CharSequence>();
            children.add(curChildMap);
            curChildMap.put("data", labels[i]);
        }
        childData.add(LABEL_PICKER_POSTAL_POSITION, children);

        // Other
        curGroupMap = new HashMap<String, CharSequence>();
        groupData.add(curGroupMap);
        curGroupMap.put("data", getText(R.string.otherLabelsGroup));

        labels = getLabelsForKind(this, EditEntry.KIND_CONTACT);
        labelsSize = labels.length;
        children = Lists.newArrayList();
        for (int i = 0; i < labelsSize; i++) {
            HashMap<String, CharSequence> curChildMap = new HashMap<String, CharSequence>();
            children.add(curChildMap);
            curChildMap.put("data", labels[i]);
        }
        childData.add(LABEL_PICKER_OTHER_POSITION, children);

        // Create the expandable list view
        ExpandableListView list = new ExpandableListView(new ContextThemeWrapper(this,
                android.R.style.Theme_Light));
        list.setOnChildClickListener(this);
        list.setAdapter(new SimpleExpandableListAdapter(
                new ContextThemeWrapper(this, android.R.style.Theme_Light),
                groupData,
                android.R.layout.simple_expandable_list_item_1,
                new String[] { "data" },
                new int[] { android.R.id.text1 },
                childData,
                android.R.layout.simple_expandable_list_item_1,
                new String[] { "data" },
                new int[] { android.R.id.text1 }
                ));
        // This list shouldn't have a color hint since the dialog may be transparent
        list.setCacheColorHint(0);

        // Create the dialog
        return new AlertDialog.Builder(this).setView(list).setInverseBackgroundForced(true)
                .setTitle(R.string.selectLabel).create();
    }

    private void createCustomPicker(final EditEntry entry, final ArrayList<EditEntry> addTo) {
        final EditText label = new EditText(this);
        label.setKeyListener(TextKeyListener.getInstance(false, Capitalize.WORDS));
        label.requestFocus();
        new AlertDialog.Builder(this)
                .setView(label)
                .setTitle(R.string.customLabelPickerTitle)
                .setPositiveButton(R.string.okButtonText, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        entry.setLabel(EditContactActivity.this, ContactMethods.TYPE_CUSTOM,
                                label.getText().toString());
                        if (addTo != null) {
                            addTo.add(entry);
                            buildViews();
                            entry.view.requestFocus(View.FOCUS_DOWN);
                        }
                    }
                })
                .setNegativeButton(R.string.cancelButtonText, null)
                .show();
    }
    
    /**
     * Saves or creates the contact based on the mode, and if sucessful finishes the activity.
     */
    private void doSaveAction() {
        // Save or create the contact if needed
        switch (mState) {
            case STATE_EDIT:
                save();
                break;

            case STATE_INSERT:
                create();
                break;

            default:
                Log.e(TAG, "Unknown state in doSaveOrCreate: " + mState);
                break;
        }
        finish();
    }
    
    /**
     * Save the various fields to the existing contact.
     */
    private void save() {
        ContentValues values = new ContentValues();
        String data;
        int numValues = 0;

        // Handle the name and send to voicemail specially
        final String name = mNameView.getText().toString();
        if (name != null && TextUtils.isGraphic(name)) {
            numValues++;
        }
        values.put(People.NAME, name);
        values.put(People.SEND_TO_VOICEMAIL, mSendToVoicemailCheckBox.isChecked() ? 1 : 0);
        mResolver.update(mUri, values, null, null);

        if (mPhotoChanged) {
            // Only write the photo if it's changed, since we don't initially load mPhoto
            if (mPhoto != null) {
                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                mPhoto.compress(Bitmap.CompressFormat.JPEG, 75, stream);
                Contacts.People.setPhotoData(mResolver, mUri, stream.toByteArray());
            } else {
                Contacts.People.setPhotoData(mResolver, mUri, null);
            }
        }

        int entryCount = ContactEntryAdapter.countEntries(mSections, false);
        for (int i = 0; i < entryCount; i++) {
            EditEntry entry = ContactEntryAdapter.getEntry(mSections, i, false);
            int kind = entry.kind;
            data = entry.getData();
            boolean empty = data == null || !TextUtils.isGraphic(data);
            if (kind == EditEntry.KIND_CONTACT) {
                values.clear();
                if (!empty) {
                    values.put(entry.column, data);
                    mResolver.update(entry.uri, values, null, null);
                    numValues++;
                } else {
                    values.put(entry.column, (String) null);
                    mResolver.update(entry.uri, values, null, null);
                }
            } else {
                if (!empty) {
                    values.clear();
                    entry.toValues(values);
                    if (entry.id != 0) {
                        mResolver.update(entry.uri, values, null, null);
                    } else {
                        mResolver.insert(entry.uri, values);
                    }
                    numValues++;
                } else if (entry.id != 0) {
                    mResolver.delete(entry.uri, null, null);
                }
            }
        }

        if (numValues == 0) {
            // The contact is completely empty, delete it
            mResolver.delete(mUri, null, null);
            mUri = null;
            setResult(RESULT_CANCELED);
        } else {
            // Add the entry to the my contacts group if it isn't there already
            People.addToMyContactsGroup(mResolver, ContentUris.parseId(mUri));
            setResult(RESULT_OK, new Intent().setData(mUri));
            Toast.makeText(this, R.string.contactSavedToast, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Takes the entered data and saves it to a new contact.
     */
    private void create() {
        ContentValues values = new ContentValues();
        String data;
        int numValues = 0;

        // Create the contact itself
        final String name = mNameView.getText().toString();
        if (name != null && TextUtils.isGraphic(name)) {
            numValues++;
        }
        values.put(People.NAME, name);
        values.put(People.SEND_TO_VOICEMAIL, mSendToVoicemailCheckBox.isChecked() ? 1 : 0);

        // Add the contact to the My Contacts group
        Uri contactUri = People.createPersonInMyContactsGroup(mResolver, values);

        // Add the contact to the group that is being displayed in the contact list
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        int displayType = prefs.getInt(ContactsPreferenceActivity.PREF_DISPLAY_TYPE,
                ContactsPreferenceActivity.DISPLAY_TYPE_UNKNOWN);
        if (displayType == ContactsPreferenceActivity.DISPLAY_TYPE_USER_GROUP) {
            String displayGroup = prefs.getString(ContactsPreferenceActivity.PREF_DISPLAY_INFO,
                    null);
            if (!TextUtils.isEmpty(displayGroup)) {
                People.addToGroup(mResolver, ContentUris.parseId(contactUri), displayGroup);
            }
        }

        // Handle the photo
        if (mPhoto != null) {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            mPhoto.compress(Bitmap.CompressFormat.JPEG, 75, stream);
            Contacts.People.setPhotoData(getContentResolver(), contactUri, stream.toByteArray());
        }

        // Create the contact methods
        int entryCount = ContactEntryAdapter.countEntries(mSections, false);
        for (int i = 0; i < entryCount; i++) {
            EditEntry entry = ContactEntryAdapter.getEntry(mSections, i, false);
            if (entry.kind != EditEntry.KIND_CONTACT) {
                values.clear();
                if (entry.toValues(values)) {
                    // Only create the entry if there is data
                    entry.uri = mResolver.insert(
                            Uri.withAppendedPath(contactUri, entry.contentDirectory), values);
                    entry.id = ContentUris.parseId(entry.uri);
                    numValues++;
                }
            } else {
                // Update the contact with any straggling data, like notes
                data = entry.getData();
                values.clear();
                if (data != null && TextUtils.isGraphic(data)) {
                    values.put(entry.column, data);
                    mResolver.update(contactUri, values, null, null);
                    numValues++;
                }
            }
        }

        if (numValues == 0) {
            mResolver.delete(contactUri, null, null);
            setResult(RESULT_CANCELED);
        } else {
            mUri = contactUri;
            Intent resultIntent = new Intent()
                    .setData(mUri)
                    .putExtra(Intent.EXTRA_SHORTCUT_NAME, name);
            setResult(RESULT_OK, resultIntent);
            Toast.makeText(this, R.string.contactCreatedToast, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Build up the entries to display on the screen.
     *
     * @param extras the extras used to start this activity, may be null
     */
    private void buildEntriesForEdit(Bundle extras) {
        Cursor personCursor = mResolver.query(mUri, CONTACT_PROJECTION, null, null, null);
        if (personCursor == null) {
            Log.e(TAG, "invalid contact uri: " + mUri);
            finish();
            return;
        } else if (!personCursor.moveToFirst()) {
            Log.e(TAG, "invalid contact uri: " + mUri);
            finish();
            personCursor.close();
            return;
        }

        // Clear out the old entries
        int numSections = mSections.size();
        for (int i = 0; i < numSections; i++) {
            mSections.get(i).clear();
        }

        EditEntry entry;

        // Name
        mNameView.setText(personCursor.getString(CONTACT_NAME_COLUMN));

        // Photo
        mPhoto = People.loadContactPhoto(this, mUri, 0, null);
        if (mPhoto == null) {
            setPhotoPresent(false);
        } else {
            setPhotoPresent(true);
            mPhotoImageView.setImageBitmap(mPhoto);
        }

        // Send to voicemail
        mSendToVoicemailCheckBox
                .setChecked(personCursor.getInt(CONTACT_SEND_TO_VOICEMAIL_COLUMN) == 1);

        // Organizations
        Uri organizationsUri = Uri.withAppendedPath(mUri, Organizations.CONTENT_DIRECTORY);
        Cursor organizationsCursor = mResolver.query(organizationsUri, ORGANIZATIONS_PROJECTION,
                null, null, null);

        if (organizationsCursor != null) {
            while (organizationsCursor.moveToNext()) {
                int type = organizationsCursor.getInt(ORGANIZATIONS_TYPE_COLUMN);
                String label = organizationsCursor.getString(ORGANIZATIONS_LABEL_COLUMN);
                String company = organizationsCursor.getString(ORGANIZATIONS_COMPANY_COLUMN);
                String title = organizationsCursor.getString(ORGANIZATIONS_TITLE_COLUMN);
                long id = organizationsCursor.getLong(ORGANIZATIONS_ID_COLUMN);
                Uri uri = ContentUris.withAppendedId(Organizations.CONTENT_URI, id);

                // Add an organization entry
                entry = EditEntry.newOrganizationEntry(this, label, type, company, title, uri, id);
                entry.isPrimary = organizationsCursor.getLong(ORGANIZATIONS_ISPRIMARY_COLUMN) != 0;
                mOtherEntries.add(entry);
            }
            organizationsCursor.close();
        }

        // Notes
        if (!personCursor.isNull(CONTACT_NOTES_COLUMN)) {
            entry = EditEntry.newNotesEntry(this, personCursor.getString(CONTACT_NOTES_COLUMN),
                    mUri);
            mOtherEntries.add(entry);
        }

        // Ringtone
        entry = EditEntry.newRingtoneEntry(this,
                personCursor.getString(CONTACT_CUSTOM_RINGTONE_COLUMN), mUri);
        mOtherEntries.add(entry);
        personCursor.close();

        // Build up the phone entries
        Uri phonesUri = Uri.withAppendedPath(mUri, People.Phones.CONTENT_DIRECTORY);
        Cursor phonesCursor = mResolver.query(phonesUri, PHONES_PROJECTION,
                null, null, null);

        if (phonesCursor != null) {
            while (phonesCursor.moveToNext()) {
                int type = phonesCursor.getInt(PHONES_TYPE_COLUMN);
                String label = phonesCursor.getString(PHONES_LABEL_COLUMN);
                String number = phonesCursor.getString(PHONES_NUMBER_COLUMN);
                long id = phonesCursor.getLong(PHONES_ID_COLUMN);
                boolean isPrimary = phonesCursor.getLong(PHONES_ISPRIMARY_COLUMN) != 0;
                Uri uri = ContentUris.withAppendedId(phonesUri, id);

                // Add a phone number entry
                entry = EditEntry.newPhoneEntry(this, label, type, number, uri, id);
                entry.isPrimary = isPrimary;
                mPhoneEntries.add(entry);

                // Keep track of which primary types have been added
                if (type == Phones.TYPE_MOBILE) {
                    mMobilePhoneAdded = true;
                }
            }

            phonesCursor.close();
        }

        // Build the contact method entries
        Uri methodsUri = Uri.withAppendedPath(mUri, People.ContactMethods.CONTENT_DIRECTORY);
        Cursor methodsCursor = mResolver.query(methodsUri, METHODS_PROJECTION, null, null, null);

        if (methodsCursor != null) {
            while (methodsCursor.moveToNext()) {
                int kind = methodsCursor.getInt(METHODS_KIND_COLUMN);
                String label = methodsCursor.getString(METHODS_LABEL_COLUMN);
                String data = methodsCursor.getString(METHODS_DATA_COLUMN);
                String auxData = methodsCursor.getString(METHODS_AUX_DATA_COLUMN);
                int type = methodsCursor.getInt(METHODS_TYPE_COLUMN);
                long id = methodsCursor.getLong(METHODS_ID_COLUMN);
                boolean isPrimary = methodsCursor.getLong(METHODS_ISPRIMARY_COLUMN) != 0;
                Uri uri = ContentUris.withAppendedId(methodsUri, id);

                switch (kind) {
                    case Contacts.KIND_EMAIL: {
                        entry = EditEntry.newEmailEntry(this, label, type, data, uri, id);
                        entry.isPrimary = isPrimary;
                        mEmailEntries.add(entry);
    
                        if (isPrimary) {
                            mPrimaryEmailAdded = true;
                        }
                        break;
                    }

                    case Contacts.KIND_POSTAL: {
                        entry = EditEntry.newPostalEntry(this, label, type, data, uri, id);
                        entry.isPrimary = isPrimary;
                        mPostalEntries.add(entry);
                        break;
                    }

                    case Contacts.KIND_IM: {
                        Object protocolObj = ContactMethods.decodeImProtocol(auxData);
                        if (protocolObj == null) {
                            // Invalid IM protocol, log it then ignore.
                            Log.e(TAG, "Couldn't decode IM protocol: " + auxData);
                            continue;
                        } else {
                            if (protocolObj instanceof Number) {
                                int protocol = ((Number) protocolObj).intValue();
                                entry = EditEntry.newImEntry(this,
                                        getLabelsForKind(this, Contacts.KIND_IM)[protocol], protocol, 
                                        data, uri, id);
                            } else {
                                entry = EditEntry.newImEntry(this, protocolObj.toString(), -1, data,
                                        uri, id);
                            }
                            mImEntries.add(entry);
                        }
                        break;
                    }
                }
            }

            methodsCursor.close();
        }

        // Add values from the extras, if there are any
        if (extras != null) {
            addFromExtras(extras, phonesUri, methodsUri);
        }

        // Add the base types if needed
        if (!mMobilePhoneAdded) {
            entry = EditEntry.newPhoneEntry(this,
                    Uri.withAppendedPath(mUri, People.Phones.CONTENT_DIRECTORY),
                    DEFAULT_PHONE_TYPE);
            mPhoneEntries.add(entry);
        }

        if (!mPrimaryEmailAdded) {
            entry = EditEntry.newEmailEntry(this,
                    Uri.withAppendedPath(mUri, People.ContactMethods.CONTENT_DIRECTORY),
                    DEFAULT_EMAIL_TYPE);
            entry.isPrimary = true;
            mEmailEntries.add(entry);
        }
    }

    /**
     * Build the list of EditEntries for full mode insertions.
     * 
     * @param extras the extras used to start this activity, may be null
     */
    private void buildEntriesForInsert(Bundle extras) {
        // Clear out the old entries
        int numSections = mSections.size();
        for (int i = 0; i < numSections; i++) {
            mSections.get(i).clear();
        }

        EditEntry entry;

        // Check the intent extras
        if (extras != null) {
            addFromExtras(extras, null, null);
        }

        // Photo
        mPhotoImageView.setImageResource(R.drawable.ic_contact_picture);

        // Add the base entries if they're not already present
        if (!mMobilePhoneAdded) {
            entry = EditEntry.newPhoneEntry(this, null, Phones.TYPE_MOBILE);
            entry.isPrimary = true;
            mPhoneEntries.add(entry);
        }

        if (!mPrimaryEmailAdded) {
            entry = EditEntry.newEmailEntry(this, null, DEFAULT_EMAIL_TYPE);
            entry.isPrimary = true;
            mEmailEntries.add(entry);
        }

        // Ringtone
        entry = EditEntry.newRingtoneEntry(this, null, mUri);
        mOtherEntries.add(entry);
        
    }

    private void addFromExtras(Bundle extras, Uri phonesUri, Uri methodsUri) {
        EditEntry entry;

        // Read the name from the bundle
        CharSequence name = extras.getCharSequence(Insert.NAME);
        if (name != null && TextUtils.isGraphic(name)) {
            mNameView.setText(name);
        }
        
        // Postal entries from extras
        CharSequence postal = extras.getCharSequence(Insert.POSTAL);
        int postalType = extras.getInt(Insert.POSTAL_TYPE, INVALID_TYPE);
        if (!TextUtils.isEmpty(postal) && postalType == INVALID_TYPE) {
            postalType = DEFAULT_POSTAL_TYPE;
        }
        
        if (postalType != INVALID_TYPE) {
            entry = EditEntry.newPostalEntry(this, null, postalType, postal.toString(),
                    methodsUri, 0);
            entry.isPrimary = extras.getBoolean(Insert.POSTAL_ISPRIMARY);
            mPostalEntries.add(entry);
        }

        // Email entries from extras
        CharSequence email = extras.getCharSequence(Insert.EMAIL);
        int emailType = extras.getInt(Insert.EMAIL_TYPE, INVALID_TYPE);
        if (!TextUtils.isEmpty(email) && emailType == INVALID_TYPE) {
            emailType = DEFAULT_EMAIL_TYPE;
            mPrimaryEmailAdded = true;
        }
   
        if (emailType != INVALID_TYPE) {
            entry = EditEntry.newEmailEntry(this, null, emailType, email.toString(), methodsUri, 0);
            entry.isPrimary = extras.getBoolean(Insert.EMAIL_ISPRIMARY);
            mEmailEntries.add(entry);

            // Keep track of which primary types have been added
            if (entry.isPrimary) {
                mPrimaryEmailAdded = true;
            }
        }
   
        // Phone entries from extras 
        CharSequence phoneNumber = extras.getCharSequence(Insert.PHONE);
        int phoneType = extras.getInt(Insert.PHONE_TYPE, INVALID_TYPE);
        if (!TextUtils.isEmpty(phoneNumber) && phoneType == INVALID_TYPE) {
            phoneType = DEFAULT_PHONE_TYPE;
        }
   
        if (phoneType != INVALID_TYPE) {
            entry = EditEntry.newPhoneEntry(this, null, phoneType,
                    phoneNumber.toString(), phonesUri, 0);
            entry.isPrimary = extras.getBoolean(Insert.PHONE_ISPRIMARY);
            mPhoneEntries.add(entry);

            // Keep track of which primary types have been added
            if (phoneType == Phones.TYPE_MOBILE) {
                mMobilePhoneAdded = true;
            }
        }

        // IM entries from extras
        CharSequence imHandle = extras.getCharSequence(Insert.IM_HANDLE);
        CharSequence imProtocol = extras.getCharSequence(Insert.IM_PROTOCOL);
   
        if (imHandle != null && imProtocol != null) {
            Object protocolObj = ContactMethods.decodeImProtocol(imProtocol.toString());
            if (protocolObj instanceof Number) {
                int protocol = ((Number) protocolObj).intValue();
                entry = EditEntry.newImEntry(this,
                        getLabelsForKind(this, Contacts.KIND_IM)[protocol], protocol, 
                        imHandle.toString(), null, 0);
            } else {
                entry = EditEntry.newImEntry(this, protocolObj.toString(), -1, imHandle.toString(),
                        null, 0);
            }
            entry.isPrimary = extras.getBoolean(Insert.IM_ISPRIMARY);
            mImEntries.add(entry);
        }
    }

    /**
     * Removes all existing views, builds new ones for all the entries, and adds them.
     */
    private void buildViews() {
        // Remove existing views
        final LinearLayout layout = mLayout;
        layout.removeAllViews();

        buildViewsForSection(layout, mPhoneEntries, R.string.listSeparatorCallNumber);
        buildViewsForSection(layout, mEmailEntries, R.string.listSeparatorSendEmail);
        buildViewsForSection(layout, mImEntries, R.string.listSeparatorSendIm);
        buildViewsForSection(layout, mPostalEntries, R.string.listSeparatorMapAddress);
        buildViewsForSection(layout, mOtherEntries, R.string.listSeparatorOtherInformation);
    }


    /**
     * Builds the views for a specific section.
     * 
     * @param layout the container
     * @param section the section to build the views for
     */
    private void buildViewsForSection(final LinearLayout layout, ArrayList<EditEntry> section,
            int separatorResource) {
        // Build the separator if the section isn't empty
        if (section.size() > 0) {
            View separator = mInflater.inflate(R.layout.edit_separator, layout, false);
            TextView text = (TextView) separator.findViewById(R.id.text);
            text.setText(getText(separatorResource));
            layout.addView(separator);
        }

        // Build views for the current section
        for (EditEntry entry : section) {
            entry.activity = this; // this could be null from when the state is restored
            if (!entry.isDeleted) {
                View view = buildViewForEntry(entry);
                layout.addView(view);
            }
        }
    }

    /**
     * Builds a view to display an EditEntry.
     * 
     * @param entry the entry to display
     * @return a view that will display the given entry
     */
    /* package */ View buildViewForEntry(final EditEntry entry) {
        // Look for any existing entered text, and save it if found
        if (entry.view != null && entry.syncDataWithView) {
            String enteredText = ((TextView) entry.view.findViewById(R.id.data))
                    .getText().toString();
            if (!TextUtils.isEmpty(enteredText)) {
                entry.data = enteredText;
            }
        }

        // Build a new view
        final ViewGroup parent = mLayout;
        View view;

        if (entry.kind == Contacts.KIND_ORGANIZATION) {
            view = mInflater.inflate(R.layout.edit_contact_entry_org, parent, false);
        } else if (isRingtoneEntry(entry)) {
            view = mInflater.inflate(R.layout.edit_contact_entry_ringtone, parent, false);
        } else if (!entry.isStaticLabel) {
            view = mInflater.inflate(R.layout.edit_contact_entry, parent, false);
        } else {
            view = mInflater.inflate(R.layout.edit_contact_entry_static_label, parent, false);
        }
        entry.view = view;
        
        // Set the entry as the tag so we can find it again later given just the view
        view.setTag(entry);

        // Bind the label
        entry.bindLabel(this);

        // Bind data
        TextView data = (TextView) view.findViewById(R.id.data);
        TextView data2 = (TextView) view.findViewById(R.id.data2);

        if (data instanceof Button) {
            data.setOnClickListener(this);
        }
        if (data.length() == 0) {
            if (entry.syncDataWithView) {
                // If there is already data entered don't overwrite it
                data.setText(entry.data);
            } else {
                fillViewData(entry);
            }
        }
        if (data2 != null && data2.length() == 0) {
            // If there is already data entered don't overwrite it
            data2.setText(entry.data2);
        }
        data.setHint(entry.hint);
        if (data2 != null) data2.setHint(entry.hint2);
        if (entry.lines > 1) {
            data.setLines(entry.lines);
            data.setMaxLines(entry.maxLines);
            if (data2 != null) {
                data2.setLines(entry.lines);
                data2.setMaxLines(entry.maxLines);
            }
        } else if (entry.lines >= 0) {
            data.setSingleLine();
            if (data2 != null) {
                data2.setSingleLine();
            }
        }
        switch (entry.keyListener) {
            case INPUT_TEXT:
                data.setKeyListener(TextKeyListener.getInstance());
                if (data2 != null) {
                    data2.setKeyListener(TextKeyListener.getInstance());
                }
                break;
                
            case INPUT_TEXT_WORDS:
                data.setKeyListener(TextKeyListener.getInstance(true, Capitalize.WORDS));
                if (data2 != null) {
                    data2.setKeyListener(TextKeyListener.getInstance(true, Capitalize.WORDS));
                }
                break;
                
            case INPUT_TEXT_SENTENCES:
                data.setKeyListener(TextKeyListener.getInstance(true, Capitalize.SENTENCES));
                if (data2 != null) {
                    data2.setKeyListener(TextKeyListener.getInstance(true, Capitalize.SENTENCES));
                }
                break;
                
            case INPUT_DIALER:
                data.setKeyListener(DialerKeyListener.getInstance());
                data.addTextChangedListener(new PhoneNumberFormattingTextWatcher());
                if (data2 != null) {
                    data2.setKeyListener(DialerKeyListener.getInstance());
                    data2.addTextChangedListener(new PhoneNumberFormattingTextWatcher());
                }
                break;
        }

        // Hook up the delete button
        View delete = view.findViewById(R.id.delete);
        if (delete != null) delete.setOnClickListener(this);
        View delete2 = view.findViewById(R.id.delete2);
        if (delete2 != null) delete2.setOnClickListener(this);
        
        return view;
    }

    private void fillViewData(final EditEntry entry) {
        if (isRingtoneEntry(entry)) {
            updateRingtoneView(entry);
        }
    }
    
    /**
     * Handles the results from the label change picker.
     */
    private final class LabelPickedListener implements DialogInterface.OnClickListener {
        EditEntry mEntry;
        String[] mLabels;

        public LabelPickedListener(EditEntry entry, String[] labels) {
            mEntry = entry;
            mLabels = labels;
        }

        public void onClick(DialogInterface dialog, int which) {
            // TODO: Use a managed dialog
            if (mEntry.kind != Contacts.KIND_IM) {
                final int type = getTypeFromLabelPosition(mLabels, which);
                if (type == ContactMethods.TYPE_CUSTOM) {
                    createCustomPicker(mEntry, null);
                } else {
                    mEntry.setLabel(EditContactActivity.this, type, mLabels[which]);
                }
            } else {
                mEntry.setLabel(EditContactActivity.this, which, mLabels[which]);
            }
        }
    }

    /**
     * A basic structure with the data for a contact entry in the list.
     */
    private static final class EditEntry extends ContactEntryAdapter.Entry implements Parcelable {
        // These aren't stuffed into the parcel
        public EditContactActivity activity;
        public View view;

        // These are stuffed into the parcel
        public String hint;
        public String hint2;
        public String column;
        public String contentDirectory;
        public String data2;
        public int keyListener;
        public int type;
        /**
         * If 0 or 1, setSingleLine will be called. If negative, setSingleLine
         * will not be called.
         */
        public int lines = 1;
        public boolean isPrimary;
        public boolean isDeleted = false;
        public boolean isStaticLabel = false;
        public boolean syncDataWithView = true;

        private EditEntry() {
            // only used by CREATOR
        }

        public EditEntry(EditContactActivity activity) {
            this.activity = activity;
        }

        public EditEntry(EditContactActivity activity, String label,
                int type, String data, Uri uri, long id) {
            this.activity = activity;
            this.isPrimary = false;
            this.label = label;
            this.type = type;
            this.data = data;
            this.uri = uri;
            this.id = id;
        }

        public int describeContents() {
            return 0;
        }

        public void writeToParcel(Parcel parcel, int flags) {
            // Make sure to read data from the input field, if anything is entered
            data = getData();

            // Write in our own fields.
            parcel.writeString(hint);
            parcel.writeString(hint2);
            parcel.writeString(column);
            parcel.writeString(contentDirectory);
            parcel.writeString(data2);
            parcel.writeInt(keyListener);
            parcel.writeInt(type);
            parcel.writeInt(lines);
            parcel.writeInt(isPrimary ? 1 : 0);
            parcel.writeInt(isDeleted ? 1 : 0);
            parcel.writeInt(isStaticLabel ? 1 : 0);
            parcel.writeInt(syncDataWithView ? 1 : 0);

            // Write in the fields from Entry
            super.writeToParcel(parcel);
        }

        public static final Parcelable.Creator<EditEntry> CREATOR =
            new Parcelable.Creator<EditEntry>() {
            public EditEntry createFromParcel(Parcel in) {
                EditEntry entry = new EditEntry();

                // Read out our own fields
                entry.hint = in.readString();
                entry.hint2 = in.readString();
                entry.column = in.readString();
                entry.contentDirectory = in.readString();
                entry.data2 = in.readString();
                entry.keyListener = in.readInt();
                entry.type = in.readInt();
                entry.lines = in.readInt();
                entry.isPrimary = in.readInt() == 1;
                entry.isDeleted = in.readInt() == 1;
                entry.isStaticLabel = in.readInt() == 1;
                entry.syncDataWithView = in.readInt() == 1;
                
                // Read out the fields from Entry
                entry.readFromParcel(in);

                return entry;
            }
            
            public EditEntry[] newArray(int size) {
                return new EditEntry[size];
            }
        };

        public void setLabel(Context context, int typeIn, String labelIn) {
            type = typeIn;
            label = labelIn;
            if (view != null) {
                bindLabel(context);
            }
        }
        
        public void bindLabel(Context context) {
            TextView v = (TextView) view.findViewById(R.id.label);
            if (isStaticLabel) {
                v.setText(label);
                return;
            }

            switch (kind) {
                case Contacts.KIND_PHONE: {
                    v.setText(Phones.getDisplayLabel(context, type, label));
                    break;
                }

                case Contacts.KIND_IM: {
                    v.setText(getLabelsForKind(activity, kind)[type]);
                    break;
                }
                
                case Contacts.KIND_ORGANIZATION: {
                    v.setText(Organizations.getDisplayLabel(activity, type, label));
                    break;
                }

                default: {
                    v.setText(Contacts.ContactMethods.getDisplayLabel(context, kind, type, label));
                    if (kind == Contacts.KIND_POSTAL) {
                        v.setMaxLines(3);
                    }
                    break;
                }
            }
            v.setOnClickListener(activity);
        }

        /**
         * Returns the data for the entry
         * @return the data for the entry
         */
        public String getData() {
            if (view != null && syncDataWithView) {
                CharSequence text = ((TextView) view.findViewById(R.id.data)).getText();
                if (text != null) {
                    return text.toString();
                }
            }

            if (data != null) {
                return data.toString();
            }

            return null;
        }

        /**
         * Dumps the entry into a HashMap suitable for passing to the database.
         * 
         * @param values the HashMap to fill in.
         * @return true if the value should be saved, false otherwise
         */
        public boolean toValues(ContentValues values) {
            boolean success = false;
            String labelString = null;
            // Save the type and label
            if (view != null) {
                // Read the possibly updated label from the text field
                labelString = ((TextView) view.findViewById(R.id.label)).getText().toString();
            }
            switch (kind) {
                case Contacts.KIND_PHONE:
                    if (type != Phones.TYPE_CUSTOM) {
                        labelString = null;
                    }
                    values.put(Phones.LABEL, labelString);
                    values.put(Phones.TYPE, type);
                    break;

                case Contacts.KIND_EMAIL:
                    if (type != ContactMethods.TYPE_CUSTOM) {
                        labelString = null;
                    }
                    values.put(ContactMethods.LABEL, labelString);
                    values.put(ContactMethods.KIND, kind);
                    values.put(ContactMethods.TYPE, type);
                    break;

                case Contacts.KIND_IM:
                    values.put(ContactMethods.KIND, kind);
                    values.put(ContactMethods.TYPE, ContactMethods.TYPE_OTHER);
                    values.putNull(ContactMethods.LABEL);
                    if (type != -1) {
                        values.put(ContactMethods.AUX_DATA,
                                ContactMethods.encodePredefinedImProtocol(type));
                    } else {
                        values.put(ContactMethods.AUX_DATA,
                                ContactMethods.encodeCustomImProtocol(label.toString()));
                    }
                    break;

                case Contacts.KIND_POSTAL:
                    if (type != ContactMethods.TYPE_CUSTOM) {
                        labelString = null;
                    }
                    values.put(ContactMethods.LABEL, labelString);
                    values.put(ContactMethods.KIND, kind);
                    values.put(ContactMethods.TYPE, type);
                    break;

                case Contacts.KIND_ORGANIZATION:
                    if (type != ContactMethods.TYPE_CUSTOM) {
                        labelString = null;
                    }
                    values.put(ContactMethods.LABEL, labelString);
                    values.put(ContactMethods.TYPE, type);
                    // Save the title
                    if (view != null) {
                        // Read the possibly updated data from the text field
                        data2 = ((TextView) view.findViewById(R.id.data2)).getText().toString();
                    }
                    if (!TextUtils.isGraphic(data2)) {
                        values.putNull(Organizations.TITLE);
                    } else {
                        values.put(Organizations.TITLE, data2.toString());
                        success = true;
                    }
                    break;

                default:
                    Log.w(TAG, "unknown kind " + kind);
                    values.put(ContactMethods.LABEL, labelString);
                    values.put(ContactMethods.KIND, kind);
                    values.put(ContactMethods.TYPE, type);
                    break;
            }

            values.put(ContactMethods.ISPRIMARY, isPrimary ? "1" : "0");

            // Save the data
            if (view != null && syncDataWithView) {
                // Read the possibly updated data from the text field
                data = ((TextView) view.findViewById(R.id.data)).getText().toString();
            }
            if (!TextUtils.isGraphic(data)) {
                values.putNull(column);
                return success;
            } else {
                values.put(column, data.toString());
                return true;
            }
        }

        /**
         * Create a new empty organization entry
         */
        public static final EditEntry newOrganizationEntry(EditContactActivity activity,
                Uri uri, int type) {
            return newOrganizationEntry(activity, null, type, null, null, uri, 0);
        }

        /**
         * Create a new company entry with the given data.
         */
        public static final EditEntry newOrganizationEntry(EditContactActivity activity,
                String label, int type, String company, String title, Uri uri, long id) {
            EditEntry entry = new EditEntry(activity, label, type, company, uri, id);
            entry.hint = activity.getString(R.string.ghostData_company);
            entry.hint2 = activity.getString(R.string.ghostData_title);
            entry.data2 = title;
            entry.column = Organizations.COMPANY;
            entry.contentDirectory = Organizations.CONTENT_DIRECTORY;
            entry.kind = Contacts.KIND_ORGANIZATION;
            entry.keyListener = INPUT_TEXT_WORDS;
            return entry;
        }

        /**
         * Create a new notes entry with the given data.
         */
        public static final EditEntry newNotesEntry(EditContactActivity activity,
                String data, Uri uri) {
            EditEntry entry = new EditEntry(activity);
            entry.label = activity.getString(R.string.label_notes);
            entry.hint = activity.getString(R.string.ghostData_notes);
            entry.data = data;
            entry.uri = uri;
            entry.column = People.NOTES;
            entry.maxLines = 10;
            entry.lines = 2;
            entry.id = 0;
            entry.kind = KIND_CONTACT;
            entry.keyListener = INPUT_TEXT_SENTENCES;
            entry.isStaticLabel = true;
            return entry;
        }

        /**
         * Create a new ringtone entry with the given data.
         */
        public static final EditEntry newRingtoneEntry(EditContactActivity activity,
                String data, Uri uri) {
            EditEntry entry = new EditEntry(activity);
            entry.label = activity.getString(R.string.label_ringtone);
            entry.data = data;
            entry.uri = uri;
            entry.column = People.CUSTOM_RINGTONE;
            entry.kind = KIND_CONTACT;
            entry.isStaticLabel = true;
            entry.syncDataWithView = false;
            entry.lines = -1;
            return entry;
        }

        /**
         * Create a new empty email entry
         */
        public static final EditEntry newPhoneEntry(EditContactActivity activity,
                Uri uri, int type) {
            return newPhoneEntry(activity, null, type, null, uri, 0);
        }

        /**
         * Create a new phone entry with the given data.
         */
        public static final EditEntry newPhoneEntry(EditContactActivity activity,
                String label, int type, String data, Uri uri,
                long id) {
            EditEntry entry = new EditEntry(activity, label, type, data, uri, id);
            entry.hint = activity.getString(R.string.ghostData_phone);
            entry.column = People.Phones.NUMBER;
            entry.contentDirectory = People.Phones.CONTENT_DIRECTORY;
            entry.kind = Contacts.KIND_PHONE;
            entry.keyListener = INPUT_DIALER;
            return entry;
        }

        /**
         * Create a new empty email entry
         */
        public static final EditEntry newEmailEntry(EditContactActivity activity,
                Uri uri, int type) {
            return newEmailEntry(activity, null, type, null, uri, 0);
        }

        /**
         * Create a new email entry with the given data.
         */
        public static final EditEntry newEmailEntry(EditContactActivity activity,
                String label, int type, String data, Uri uri,
                long id) {
            EditEntry entry = new EditEntry(activity, label, type, data, uri, id);
            entry.hint = activity.getString(R.string.ghostData_email);
            entry.column = ContactMethods.DATA;
            entry.contentDirectory = People.ContactMethods.CONTENT_DIRECTORY;
            entry.kind = Contacts.KIND_EMAIL;
            entry.keyListener = INPUT_TEXT;
            return entry;
        }

        /**
         * Create a new empty postal address entry
         */
        public static final EditEntry newPostalEntry(EditContactActivity activity,
                Uri uri, int type) {
            return newPostalEntry(activity, null, type, null, uri, 0);
        }

        /**
         * Create a new postal address entry with the given data.
         *
         * @param label label for the item, from the db not the display label
         * @param type the type of postal address
         * @param data the starting data for the entry, may be null
         * @param uri the uri for the entry if it already exists, may be null
         * @param id the id for the entry if it already exists, 0 it it doesn't
         * @return the new EditEntry
         */
        public static final EditEntry newPostalEntry(EditContactActivity activity,
                String label, int type, String data, Uri uri, long id) {
            EditEntry entry = new EditEntry(activity, label, type, data, uri, id);
            entry.hint = activity.getString(R.string.ghostData_postal);
            entry.column = ContactMethods.DATA;
            entry.contentDirectory = People.ContactMethods.CONTENT_DIRECTORY;
            entry.kind = Contacts.KIND_POSTAL;
            entry.keyListener = INPUT_TEXT_WORDS;
            entry.maxLines = 4;
            entry.lines = 2;
            return entry;
        }

        /**
         * Create a new postal address entry with the given data.
         *
         * @param label label for the item, from the db not the display label
         * @param protocol the type used
         * @param data the starting data for the entry, may be null
         * @param uri the uri for the entry if it already exists, may be null
         * @param id the id for the entry if it already exists, 0 it it doesn't
         * @return the new EditEntry
         */
        public static final EditEntry newImEntry(EditContactActivity activity,
                String label, int protocol, String data, Uri uri, long id) {
            EditEntry entry = new EditEntry(activity, label, protocol, data, uri, id);
            entry.hint = activity.getString(R.string.ghostData_im);
            entry.column = ContactMethods.DATA;
            entry.contentDirectory = People.ContactMethods.CONTENT_DIRECTORY;
            entry.kind = Contacts.KIND_IM;
            entry.keyListener = INPUT_TEXT;
            return entry;
        }
    }
}
