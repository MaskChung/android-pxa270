/*
 * Copyright (C) 2007 Esmertec AG.
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

package com.android.im.imps;

import java.io.IOException;
import java.io.InputStream;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class WbxmlPrimitiveParser implements PrimitiveParser{
    private WbxmlParser mParser;
    private PrimitiveContentHandler mContentHandler;

    public WbxmlPrimitiveParser() {
        mParser= new WbxmlParser();
        mContentHandler = new PrimitiveContentHandler();
    }

    public Primitive parse(InputStream in) throws ParserException, IOException {
        mContentHandler.reset();
        mParser.reset();
        mParser.setContentHandler(mContentHandler);
        try {
            mParser.parse(new InputSource(in));
        } catch (SAXException e) {
            throw new ParserException(e);
        }
        return mContentHandler.getPrimitive();
    }

}
