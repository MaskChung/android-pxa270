// Copyright 2007 The Android Open Source Project

package com.google.wireless.gdata.subscribedfeeds.client;

import com.google.wireless.gdata.client.AuthenticationException;
import com.google.wireless.gdata.client.GDataClient;
import com.google.wireless.gdata.client.GDataServiceClient;
import com.google.wireless.gdata.client.QueryParams;
import com.google.wireless.gdata.client.GDataParserFactory;
import com.google.wireless.gdata.data.Entry;
import com.google.wireless.gdata.parser.GDataParser;
import com.google.wireless.gdata.parser.ParseException;
import com.google.wireless.gdata.serializer.xml.XmlEntryGDataSerializer;
import com.google.wireless.gdata.subscribedfeeds.data.SubscribedFeedsEntry;
import com.google.wireless.gdata.subscribedfeeds.parser.xml.XmlSubscribedFeedsGDataParser;
import com.google.wireless.gdata.subscribedfeeds.serializer.xml.XmlSubscribedFeedsEntryGDataSerializer;

import java.io.IOException;
import java.io.InputStream;

/**
 * GDataServiceClient for accessing Subscribed Feeds.  This client can access
 * subscribed feeds for specific users. The parser this class uses handles
 * the XML version of feeds.
 */
public class SubscribedFeedsClient extends GDataServiceClient {

    /** Service value for contacts. This is only used for downloads; uploads
     * are done using the service that corresponds to the subscribed feed. */
    public static final String SERVICE = "mail";

    /**
     * Create a new SubscribedFeedsClient.
     * @param client The GDataClient that should be used to authenticate
     * requests, retrieve feeds, etc.
     */
    public SubscribedFeedsClient(GDataClient client,
                                 GDataParserFactory factory) {
        super(client, factory);
    }

    /*
     * (non-Javadoc)
     * @see GDataServiceClient#getServiceName()
     */
    public String getServiceName() {
        return SERVICE;
    }
}
