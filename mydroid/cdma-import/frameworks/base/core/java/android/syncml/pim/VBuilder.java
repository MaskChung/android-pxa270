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

package android.syncml.pim;

import java.util.Collection;

public interface VBuilder {
    void start();

    void end();

    /**
     * @param type
     *            VXX <br>
     *            BEGIN:VXX
     */
    void startRecord(String type);

    /** END:VXX */
    void endRecord();

    void startProperty();

    void endProperty();

    /**
     * @param name
     *            a.N <br>
     *            a.N
     */
    void propertyName(String name);

    /**
     * @param type
     *            LANGUAGE \ ENCODING <br>
     *            ;LANGUage= \ ;ENCODING=
     */
    void propertyParamType(String type);

    /**
     * @param value
     *            FR-EN \ GBK <br>
     *            FR-EN \ GBK
     */
    void propertyParamValue(String value);

    void propertyValues(Collection<String> values);
}
