/*
 * Copyright (C) 2008 Esmertec AG.
 * Copyright (C) 2008 The Android Open Source Project
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
package com.android.im.plugin;

/**
 * Defines the IDs of branding resources.
 *
 */
public interface BrandingResourceIDs {
    /**
     * The logo icon of the provider which is displayed in the landing page.
     */
    public static final int DRAWABLE_LOGO                = 100;
    /**
     * The icon of online presence status.
     */
    public static final int DRAWABLE_PRESENCE_ONLINE     = 102;
    /**
     * The icon of busy presence status.
     */
    public static final int DRAWABLE_PRESENCE_BUSY       = 103;
    /**
     * The icon of away presence status.
     */
    public static final int DRAWABLE_PRESENCE_AWAY       = 104;
    /**
     * The icon of invisible presence status.
     */
    public static final int DRAWABLE_PRESENCE_INVISIBLE  = 105;
    /**
     * The icon of offline presence status.
     */
    public static final int DRAWABLE_PRESENCE_OFFLINE    = 106;
    
    /**
     * The image displayed on the splash screen while logging in.
     */
    public static final int DRAWABLE_SPLASH_SCREEN       = 107;
    /**
     * The icon for blocked contacts.
     */
    public static final int DRAWABLE_BLOCK               = 108;
    /**
     * The water mark background for chat screen.
     */
    public static final int DRAWABLE_CHAT_WATERMARK      = 109;
    /**
     * The icon for the read conversation.
     */
    public static final int DRAWABLE_READ_CHAT           = 110;
    /**
     * The icon for the unread conversation.
     */
    public static final int DRAWABLE_UNREAD_CHAT         = 111;
    
    /**
     * The title of buddy list screen. It's conjuncted with the current username
     * and should be formatted as a string like
     * "Contact List - &lt;xliff:g id="username"&gt;%1$s&lt;/xliff:g&gt;
     */
    public static final int STRING_BUDDY_LIST_TITLE      = 200;
    
    /**
     * A string array of the smiley names.
     */
    public static final int STRING_ARRAY_SMILEY_NAMES    = 201;
    /**
     * A string array of the smiley texts.
     */
    public static final int STRING_ARRAY_SMILEY_TEXTS    = 202;
    
    /**
     * The string of available presence status.
     */
    public static final int STRING_PRESENCE_AVAILABLE    = 203;
    /**
     * The string of away presence status.
     */
    public static final int STRING_PRESENCE_AWAY         = 204;
    /**
     * The string of busy presence status.
     */
    public static final int STRING_PRESENCE_BUSY         = 205;
    /**
     * The string of the idle presence status.
     */
    public static final int STRING_PRESENCE_IDLE         = 206;
    /**
     * The string of the invisible presence status.
     */
    public static final int STRING_PRESENCE_INVISIBLE    = 207;
    /**
     * The string of the offline presence status.
     */
    public static final int STRING_PRESENCE_OFFLINE      = 208;
    
    /**
     * The label of username displayed on the account setup screen.
     */
    public static final int STRING_LABEL_USERNAME        = 209;
    /**
     * The label of the ongoing conversation group.
     */
    public static final int STRING_ONGOING_CONVERSATION  = 210;
    /**
     * The title of add contact screen.
     */
    public static final int STRING_ADD_CONTACT_TITLE     = 211;
    /**
     * The label of the contact input box on the add contact screen.
     */
    public static final int STRING_LABEL_INPUT_CONTACT   = 212;
    /**
     * The label of the add contact button on the add contact screen
     */
    public static final int STRING_BUTTON_ADD_CONTACT    = 213;
    /**
     * The title of the contact info dialog.
     */
    public static final int STRING_CONTACT_INFO_TITLE    = 214;
    /**
     * The label of the menu to add a contact.
     */
    public static final int STRING_MENU_ADD_CONTACT      = 220;
    /**
     * The label of the menu to start a conversation.
     */
    public static final int STRING_MENU_START_CHAT       = 221;
    /**
     * The label of the menu to view contact profile info.
     */
    public static final int STRING_MENU_VIEW_PROFILE     = 222;
    /**
     * The label of the menu to end a conversation.
     */
    public static final int STRING_MENU_END_CHAT         = 223;
    /**
     * The label of the menu to block a contact.
     */
    public static final int STRING_MENU_BLOCK_CONTACT    = 224;
    /**
     * The label of the menu to delete a contact.
     */
    public static final int STRING_MENU_DELETE_CONTACT   = 225;
    /**
     * The label of the menu to go to the contact list screen.
     */
    public static final int STRING_MENU_CONTACT_LIST     = 226;
    /**
     * The label of the menu to insert a smiley.
     */
    public static final int STRING_MENU_INSERT_SMILEY    = 227;
    /**
     * The label of the menu to switch conversations.
     */
    public static final int STRING_MENU_SWITCH_CHATS     = 228;
    /**
     * The string of the toast displayed when auto sign in button on the account
     * setup screen is checked.
     */
    public static final int STRING_TOAST_CHECK_AUTO_SIGN_IN  = 230;
    /**
     * The string of the toast displayed when the remember password button on
     * the account setup screen is checked.
     */
    public static final int STRING_TOAST_CHECK_SAVE_PASSWORD = 231;
    /**
     * The label of sign up a new account on the account setup screen.
     */
    public static final int STRING_LABEL_SIGN_UP         = 240;
    /**
     * The term of use message. If provided, a dialog will be shown at the first
     * time login to ask the user if he would accept the term or not.
     */
    public static final int STRING_TOU_MESSAGE             = 241;
    /**
     * The title of the term of use dialog.
     */
    public static final int STRING_TOU_TITLE             = 242;
    /**
     * The label of the button to accept the term of use.
     */
    public static final int STRING_TOU_ACCEPT             = 243;
    /**
     * The label of the button to decline the term of use.
     */
    public static final int STRING_TOU_DECLINE             = 244;
}
