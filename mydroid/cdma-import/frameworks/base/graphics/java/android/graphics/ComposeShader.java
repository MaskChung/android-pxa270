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

package android.graphics;

/** A subclass of shader that returns the coposition of two other shaders, combined by
    an {@link android.graphics.Xfermode} subclass.
*/
public class ComposeShader extends Shader {
    /** Create a new compose shader, given shaders A, B, and a combining mode.
        When the mode is applied, it will be given the result from shader A as its
        "dst", and the result of from shader B as its "src".
        @param shaderA  The colors from this shader are seen as the "dst" by the mode
        @param shaderB  The colors from this shader are seen as the "src" by the mode
        @param mode     The mode that combines the colors from the two shaders. If mode
                        is null, then SRC_OVER is assumed.
    */
    public ComposeShader(Shader shaderA, Shader shaderB, Xfermode mode) {
        native_instance = nativeCreate1(shaderA.native_instance, shaderB.native_instance,
                                        (mode != null) ? mode.native_instance : 0);
    }

    /** Create a new compose shader, given shaders A, B, and a combining PorterDuff mode.
        When the mode is applied, it will be given the result from shader A as its
        "dst", and the result of from shader B as its "src".
        @param shaderA  The colors from this shader are seen as the "dst" by the mode
        @param shaderB  The colors from this shader are seen as the "src" by the mode
        @param mode     The PorterDuff mode that combines the colors from the two shaders.
    */
    public ComposeShader(Shader shaderA, Shader shaderB, PorterDuff.Mode mode) {
        native_instance = nativeCreate2(shaderA.native_instance, shaderB.native_instance,
                                        mode.nativeInt);
    }

    private static native int nativeCreate1(int native_shaderA, int native_shaderB, int native_mode);
    private static native int nativeCreate2(int native_shaderA, int native_shaderB, int porterDuffMode);
}

