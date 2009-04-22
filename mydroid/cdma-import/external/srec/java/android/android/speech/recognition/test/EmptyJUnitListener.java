/*---------------------------------------------------------------------------*
 *  EmptyJUnitListener.java                                                  *
 *                                                                           *
 *  Copyright 2007, 2008 Nuance Communciations, Inc.                               *
 *                                                                           *
 *  Licensed under the Apache License, Version 2.0 (the 'License');          *
 *  you may not use this file except in compliance with the License.         *
 *                                                                           *
 *  You may obtain a copy of the License at                                  *
 *      http://www.apache.org/licenses/LICENSE-2.0                           *
 *                                                                           *
 *  Unless required by applicable law or agreed to in writing, software      *
 *  distributed under the License is distributed on an 'AS IS' BASIS,        *
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. *
 *  See the License for the specific language governing permissions and      *
 *  limitations under the License.                                           *
 *                                                                           *
 *---------------------------------------------------------------------------*/

package android.speech.recognition.test;

import android.speech.recognition.RecognizerListener.FailureReason;

/**
 * A JUnitListener whose methods are empty. This class exists as convenience
 * for creating listener objects.
 */
public class EmptyJUnitListener implements JUnitListener
{
  public void afterTest()
  {
  }

  public void onException(Exception e)
  {
    e.printStackTrace();
  }

  public void onRecognitionFailure(FailureReason reason)
  {
    System.err.println("onRecognitionFailure=" + reason.toString());
  }
}
