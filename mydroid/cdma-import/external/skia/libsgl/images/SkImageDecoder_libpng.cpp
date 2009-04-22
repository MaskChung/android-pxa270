/* libs/graphics/images/SkImageDecoder_libpng.cpp
**
** Copyright 2006, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License"); 
** you may not use this file except in compliance with the License. 
** You may obtain a copy of the License at 
**
**     http://www.apache.org/licenses/LICENSE-2.0 
**
** Unless required by applicable law or agreed to in writing, software 
** distributed under the License is distributed on an "AS IS" BASIS, 
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
** See the License for the specific language governing permissions and 
** limitations under the License.
*/

#include "SkImageDecoder.h"
#include "SkColor.h"
#include "SkColorPriv.h"
#include "SkDither.h"
#include "SkMath.h"
#include "SkScaledBitmapSampler.h"
#include "SkStream.h"
#include "SkTemplates.h"
#include "SkUtils.h"

extern "C" {
#include "png.h"
}

class SkPNGImageDecoder : public SkImageDecoder {
public:
    virtual Format getFormat() const {
        return kPNG_Format;
    }
    
protected:
    virtual bool onDecode(SkStream* stream, SkBitmap* bm,
                          SkBitmap::Config pref, Mode);
};

#ifndef png_jmpbuf
#  define png_jmpbuf(png_ptr) ((png_ptr)->jmpbuf)
#endif

#define PNG_BYTES_TO_CHECK 4

/* Automatically clean up after throwing an exception */
struct PNGAutoClean {
    PNGAutoClean(png_structp p, png_infop i): png_ptr(p), info_ptr(i) {}
    ~PNGAutoClean() {
        png_destroy_read_struct(&png_ptr, &info_ptr, png_infopp_NULL);
    }
private:
    png_structp png_ptr;
    png_infop info_ptr;
};

SkImageDecoder* SkImageDecoder_PNG_Factory(SkStream* stream) {
    char buf[PNG_BYTES_TO_CHECK];
    if (stream->read(buf, PNG_BYTES_TO_CHECK) == PNG_BYTES_TO_CHECK &&
            !png_sig_cmp((png_bytep) buf, (png_size_t)0, PNG_BYTES_TO_CHECK)) {
        return SkNEW(SkPNGImageDecoder);
    }
    return NULL;
}

static void sk_read_fn(png_structp png_ptr, png_bytep data, png_size_t length) {
    SkStream* sk_stream = (SkStream*) png_ptr->io_ptr;
    size_t bytes = sk_stream->read(data, length);
    if (bytes != length) {
        png_error(png_ptr, "Read Error!");
    }
}

static int sk_read_user_chunk(png_structp png_ptr, png_unknown_chunkp chunk) {
    SkImageDecoder::Peeker* peeker =
                    (SkImageDecoder::Peeker*)png_get_user_chunk_ptr(png_ptr);
    // peek() returning true means continue decoding
    return peeker->peek((const char*)chunk->name, chunk->data, chunk->size) ?
            1 : -1;
}

static void sk_error_fn(png_structp png_ptr, png_const_charp msg) {
#if 0
    SkDebugf("------ png error %s\n", msg);
#endif
    longjmp(png_jmpbuf(png_ptr), 1);
}

static void skip_src_rows(png_structp png_ptr, uint8_t storage[], int count) {
    for (int i = 0; i < count; i++) {
        uint8_t* tmp = storage;
        png_read_rows(png_ptr, &tmp, png_bytepp_NULL, 1);
    }
}

static bool pos_le(int value, int max) {
    return value > 0 && value <= max;
}

static bool substituteTranspColor(SkBitmap* bm, SkPMColor match) {
    SkASSERT(bm->config() == SkBitmap::kARGB_8888_Config);
    
    bool reallyHasAlpha = false;

    for (int y = bm->height() - 1; y >= 0; --y) {
        SkPMColor* p = bm->getAddr32(0, y);
        for (int x = bm->width() - 1; x >= 0; --x) {
            if (match == *p) {
                *p = 0;
                reallyHasAlpha = true;
            }
            p += 1;
        }
    }
    return reallyHasAlpha;
}

bool SkPNGImageDecoder::onDecode(SkStream* sk_stream, SkBitmap* decodedBitmap,
                                 SkBitmap::Config prefConfig, Mode mode) {
//    SkAutoTrace    apr("SkPNGImageDecoder::onDecode");

    /* Create and initialize the png_struct with the desired error handler
    * functions.  If you want to use the default stderr and longjump method,
    * you can supply NULL for the last three parameters.  We also supply the
    * the compiler header file version, so that we know if the application
    * was compiled with a compatible version of the library.  */
    png_structp png_ptr = png_create_read_struct(PNG_LIBPNG_VER_STRING,
        NULL, sk_error_fn, NULL);
    //   png_voidp user_error_ptr, user_error_fn, user_warning_fn);
    if (png_ptr == NULL) {
        return false;
    }

    /* Allocate/initialize the memory for image information. */
    png_infop info_ptr = png_create_info_struct(png_ptr);
    if (info_ptr == NULL) {
        png_destroy_read_struct(&png_ptr, png_infopp_NULL, png_infopp_NULL);
        return false;
    }

    PNGAutoClean autoClean(png_ptr, info_ptr);

    /* Set error handling if you are using the setjmp/longjmp method (this is
    * the normal method of doing things with libpng).  REQUIRED unless you
    * set up your own error handlers in the png_create_read_struct() earlier.
    */
    if (setjmp(png_jmpbuf(png_ptr))) {
        return false;
    }

    /* If you are using replacement read functions, instead of calling
    * png_init_io() here you would call:
    */
    png_set_read_fn(png_ptr, (void *)sk_stream, sk_read_fn);
    /* where user_io_ptr is a structure you want available to the callbacks */
    /* If we have already read some of the signature */
//  png_set_sig_bytes(png_ptr, 0 /* sig_read */ );

    // hookup our peeker so we can see any user-chunks the caller may be interested in
    png_set_keep_unknown_chunks(png_ptr, PNG_HANDLE_CHUNK_ALWAYS, (png_byte*)"", 0);
    if (this->getPeeker()) {
        png_set_read_user_chunk_fn(png_ptr, (png_voidp)this->getPeeker(), sk_read_user_chunk);
    }

    /* The call to png_read_info() gives us all of the information from the
    * PNG file before the first IDAT (image data chunk). */
    png_read_info(png_ptr, info_ptr);
    png_uint_32 origWidth, origHeight;
    int bit_depth, color_type, interlace_type;
    png_get_IHDR(png_ptr, info_ptr, &origWidth, &origHeight, &bit_depth, &color_type,
        &interlace_type, int_p_NULL, int_p_NULL);

    /* tell libpng to strip 16 bit/color files down to 8 bits/color */
    if (bit_depth == 16) {
        png_set_strip_16(png_ptr);
    }
    /* Extract multiple pixels with bit depths of 1, 2, and 4 from a single
     * byte into separate bytes (useful for paletted and grayscale images). */
    if (bit_depth < 8) {
        png_set_packing(png_ptr);
    }
    /* Expand grayscale images to the full 8 bits from 1, 2, or 4 bits/pixel */
    if (color_type == PNG_COLOR_TYPE_GRAY && bit_depth < 8) {
        png_set_gray_1_2_4_to_8(png_ptr);
    }
    
    /* Make a grayscale image into RGB. */
    if (color_type == PNG_COLOR_TYPE_GRAY ||
        color_type == PNG_COLOR_TYPE_GRAY_ALPHA) {
        png_set_gray_to_rgb(png_ptr);
    }
        
    SkBitmap::Config    config;
    bool                hasAlpha = false;
    bool                doDither = this->getDitherImage();
    SkPMColor           theTranspColor = 0; // 0 tells us not to try to match
    
    // check for sBIT chunk data, in case we should disable dithering because
    // our data is not truely 8bits per component
    if (doDither) {
#if 0
        SkDebugf("----- sBIT %d %d %d %d\n", info_ptr->sig_bit.red,
                 info_ptr->sig_bit.green, info_ptr->sig_bit.blue,
                 info_ptr->sig_bit.alpha);
#endif
        // 0 seems to indicate no information available
        if (pos_le(info_ptr->sig_bit.red, SK_R16_BITS) &&
                pos_le(info_ptr->sig_bit.green, SK_G16_BITS) &&
                pos_le(info_ptr->sig_bit.blue, SK_B16_BITS)) {
            doDither = false;
        }
    }
    
    if (color_type == PNG_COLOR_TYPE_PALETTE) {
        config = SkBitmap::kIndex8_Config;  // defer sniffing for hasAlpha
    } else {
        png_color_16p   transpColor = NULL;
        int             numTransp = 0;
        
        png_get_tRNS(png_ptr, info_ptr, NULL, &numTransp, &transpColor);
        
        bool valid = png_get_valid(png_ptr, info_ptr, PNG_INFO_tRNS);
        
        if (valid && numTransp == 1 && transpColor != NULL) {
            /*  Compute our transparent color, which we'll match against later.
                We don't really handle 16bit components properly here, since we
                do our compare *after* the values have been knocked down to 8bit
                which means we will find more matches than we should. The real
                fix seems to be to see the actual 16bit components, do the
                compare, and then knock it down to 8bits ourselves.
            */
            if (color_type & PNG_COLOR_MASK_COLOR) {
                if (16 == bit_depth) {
                    theTranspColor = SkPackARGB32(0xFF, transpColor->red >> 8,
                              transpColor->green >> 8, transpColor->blue >> 8);
                } else {
                    theTranspColor = SkPackARGB32(0xFF, transpColor->red,
                                      transpColor->green, transpColor->blue);
                }
            } else {    // gray
                if (16 == bit_depth) {
                    theTranspColor = SkPackARGB32(0xFF, transpColor->gray >> 8,
                              transpColor->gray >> 8, transpColor->gray >> 8);
                } else {
                    theTranspColor = SkPackARGB32(0xFF, transpColor->gray,
                                          transpColor->gray, transpColor->gray);
                }
            }
        }

        if (valid ||
                PNG_COLOR_TYPE_RGB_ALPHA == color_type ||
                PNG_COLOR_TYPE_GRAY_ALPHA == color_type) {
            hasAlpha = true;
            config = SkBitmap::kARGB_8888_Config;
        } else {    // we get to choose the config
            config = prefConfig;
            if (config == SkBitmap::kNo_Config) {
                config = SkImageDecoder::GetDeviceConfig();
            }
            if (config != SkBitmap::kRGB_565_Config &&
                    config != SkBitmap::kARGB_4444_Config) {
                config = SkBitmap::kARGB_8888_Config;
            }
        }
    }
    
    if (!this->chooseFromOneChoice(config, origWidth, origHeight)) {
        return false;
    }
    
    const int sampleSize = this->getSampleSize();
    SkScaledBitmapSampler sampler(origWidth, origHeight, sampleSize);

    decodedBitmap->setConfig(config, sampler.scaledWidth(),
                             sampler.scaledHeight(), 0);
    if (SkImageDecoder::kDecodeBounds_Mode == mode) {
        return true;
    }
    
    // from here down we are concerned with colortables and pixels

    // we track if we actually see a non-opaque pixels, since sometimes a PNG sets its colortype
    // to |= PNG_COLOR_MASK_ALPHA, but all of its pixels are in fact opaque. We care, since we
    // draw lots faster if we can flag the bitmap has being opaque
    bool reallyHasAlpha = false;

    SkColorTable* colorTable = NULL;

    if (color_type == PNG_COLOR_TYPE_PALETTE) {
        int num_palette;
        png_colorp palette;
        png_bytep trans;
        int num_trans;

        png_get_PLTE(png_ptr, info_ptr, &palette, &num_palette);
        
        /*  BUGGY IMAGE WORKAROUND
            
            We hit some images (e.g. fruit_.png) who contain bytes that are == colortable_count
            which is a problem since we use the byte as an index. To work around this we grow
            the colortable by 1 (if its < 256) and duplicate the last color into that slot.
        */
        int colorCount = num_palette + (num_palette < 256);

        colorTable = SkNEW_ARGS(SkColorTable, (colorCount));

        SkPMColor* colorPtr = colorTable->lockColors();
        if (png_get_valid(png_ptr, info_ptr, PNG_INFO_tRNS)) {
            png_get_tRNS(png_ptr, info_ptr, &trans, &num_trans, NULL);
            hasAlpha = (num_trans > 0);
        } else {
            num_trans = 0;
            colorTable->setFlags(colorTable->getFlags() | SkColorTable::kColorsAreOpaque_Flag);
        }        
        // check for bad images that might make us crash
        if (num_trans > num_palette) {
            num_trans = num_palette;
        }

        int index = 0;
        int transLessThanFF = 0;

        for (; index < num_trans; index++) {
            transLessThanFF |= (int)*trans - 0xFF;
            *colorPtr++ = SkPreMultiplyARGB(*trans++, palette->red, palette->green, palette->blue);
            palette++;
        }
        reallyHasAlpha |= (transLessThanFF < 0);

        for (; index < num_palette; index++) {
            *colorPtr++ = SkPackARGB32(0xFF, palette->red, palette->green, palette->blue);
            palette++;
        }

        // see BUGGY IMAGE WORKAROUND comment above
        if (num_palette < 256) {
            *colorPtr = colorPtr[-1];
        }
        colorTable->unlockColors(true);
    }
    
    SkAutoUnref aur(colorTable);

    if (!this->allocPixelRef(decodedBitmap, colorTable)) {
        return false;
    }
    
    SkAutoLockPixels alp(*decodedBitmap);

    /* swap the RGBA or GA data to ARGB or AG (or BGRA to ABGR) */
//  if (color_type == PNG_COLOR_TYPE_RGB_ALPHA)
//      ; // png_set_swap_alpha(png_ptr);

    /* swap bytes of 16 bit files to least significant byte first */
    //   png_set_swap(png_ptr);

    /* Add filler (or alpha) byte (before/after each RGB triplet) */
    if (color_type == PNG_COLOR_TYPE_RGB || color_type == PNG_COLOR_TYPE_GRAY) {
        png_set_filler(png_ptr, 0xff, PNG_FILLER_AFTER);
    }

    /* Turn on interlace handling.  REQUIRED if you are not using
    * png_read_image().  To see how to handle interlacing passes,
    * see the png_read_row() method below:
    */
    const int number_passes = interlace_type != PNG_INTERLACE_NONE ? 
                        png_set_interlace_handling(png_ptr) : 1;

    /* Optional call to gamma correct and add the background to the palette
    * and update info structure.  REQUIRED if you are expecting libpng to
    * update the palette for you (ie you selected such a transform above).
    */
    png_read_update_info(png_ptr, info_ptr);

    if (SkBitmap::kIndex8_Config == config && 1 == sampleSize) {
        for (int i = 0; i < number_passes; i++) {
            for (png_uint_32 y = 0; y < origHeight; y++) {
                uint8_t* bmRow = decodedBitmap->getAddr8(0, y);
                png_read_rows(png_ptr, &bmRow, png_bytepp_NULL, 1);
            }
        }
    } else {
        SkScaledBitmapSampler::SrcConfig sc;
        int srcBytesPerPixel = 4;
        
        if (SkBitmap::kIndex8_Config == config) {
            sc = SkScaledBitmapSampler::kIndex;
            srcBytesPerPixel = 1;
        } else if (hasAlpha) {
            sc = SkScaledBitmapSampler::kRGBA;
        } else {
            sc = SkScaledBitmapSampler::kRGBX;
        }

        SkAutoMalloc storage(origWidth * srcBytesPerPixel);
        const int height = decodedBitmap->height();

        for (int i = 0; i < number_passes; i++) {
            if (!sampler.begin(decodedBitmap, sc, doDither)) {
                return false;
            }

            uint8_t* srcRow = (uint8_t*)storage.get();
            skip_src_rows(png_ptr, srcRow, sampler.srcY0());

            for (int y = 0; y < height; y++) {
                uint8_t* tmp = srcRow;
                png_read_rows(png_ptr, &tmp, png_bytepp_NULL, 1);
                reallyHasAlpha |= sampler.next(srcRow);
                if (y < height - 1) {
                    skip_src_rows(png_ptr, srcRow, sampler.srcDY() - 1);
                }
            }
            
            // skip the rest of the rows (if any)
            png_uint_32 read = (height - 1) * sampler.srcDY() +
                               sampler.srcY0() + 1;
            SkASSERT(read <= origHeight);
            skip_src_rows(png_ptr, srcRow, origHeight - read);
        }

        if (hasAlpha && !reallyHasAlpha) {
#if 0
            SkDEBUGF(("Image doesn't really have alpha [%d %d]\n",
                      origWidth, origHeight));
#endif
        }
    }

    /* read rest of file, and get additional chunks in info_ptr - REQUIRED */
    png_read_end(png_ptr, info_ptr);

    if (0 != theTranspColor) {
        reallyHasAlpha |= substituteTranspColor(decodedBitmap, theTranspColor);
    }
    decodedBitmap->setIsOpaque(!reallyHasAlpha);
    return true;
}

///////////////////////////////////////////////////////////////////////////////

#ifdef SK_SUPPORT_IMAGE_ENCODE

#include "SkColorPriv.h"
#include "SkUnPreMultiply.h"

static void sk_write_fn(png_structp png_ptr, png_bytep data, png_size_t len) {
    SkWStream* sk_stream = (SkWStream*)png_ptr->io_ptr;
    if (!sk_stream->write(data, len)) {
        png_error(png_ptr, "sk_write_fn Error!");
    }
}

typedef void (*transform_scanline_proc)(const char* SK_RESTRICT src,
                                        int width, char* SK_RESTRICT dst);

static void transform_scanline_565(const char* SK_RESTRICT src, int width,
                                   char* SK_RESTRICT dst) {
    const uint16_t* SK_RESTRICT srcP = (const uint16_t*)src;    
    for (int i = 0; i < width; i++) {
        unsigned c = *srcP++;
        *dst++ = SkPacked16ToR32(c);
        *dst++ = SkPacked16ToG32(c);
        *dst++ = SkPacked16ToB32(c);
    }
}

static void transform_scanline_888(const char* SK_RESTRICT src, int width,
                                   char* SK_RESTRICT dst) {
    const SkPMColor* SK_RESTRICT srcP = (const SkPMColor*)src;    
    for (int i = 0; i < width; i++) {
        SkPMColor c = *srcP++;
        *dst++ = SkGetPackedR32(c);
        *dst++ = SkGetPackedG32(c);
        *dst++ = SkGetPackedB32(c);
    }
}

static void transform_scanline_444(const char* SK_RESTRICT src, int width,
                                   char* SK_RESTRICT dst) {
    const SkPMColor16* SK_RESTRICT srcP = (const SkPMColor16*)src;    
    for (int i = 0; i < width; i++) {
        SkPMColor16 c = *srcP++;
        *dst++ = SkPacked4444ToR32(c);
        *dst++ = SkPacked4444ToG32(c);
        *dst++ = SkPacked4444ToB32(c);
    }
}

static void transform_scanline_8888(const char* SK_RESTRICT src, int width,
                                    char* SK_RESTRICT dst) {
    const SkPMColor* SK_RESTRICT srcP = (const SkPMColor*)src;
    const SkUnPreMultiply::Scale* SK_RESTRICT table = 
                                              SkUnPreMultiply::GetScaleTable();

    for (int i = 0; i < width; i++) {
        SkPMColor c = *srcP++;
        unsigned a = SkGetPackedA32(c);
        unsigned r = SkGetPackedR32(c);
        unsigned g = SkGetPackedG32(c);
        unsigned b = SkGetPackedB32(c);

        if (0 != a && 255 != a) {
            SkUnPreMultiply::Scale scale = table[a];
            r = SkUnPreMultiply::ApplyScale(scale, r);
            g = SkUnPreMultiply::ApplyScale(scale, g);
            b = SkUnPreMultiply::ApplyScale(scale, b);
        }
        *dst++ = r;
        *dst++ = g;
        *dst++ = b;
        *dst++ = a;
    }
}

static void transform_scanline_4444(const char* SK_RESTRICT src, int width,
                                    char* SK_RESTRICT dst) {
    const SkPMColor16* SK_RESTRICT srcP = (const SkPMColor16*)src;
    const SkUnPreMultiply::Scale* SK_RESTRICT table = 
                                              SkUnPreMultiply::GetScaleTable();

    for (int i = 0; i < width; i++) {
        SkPMColor16 c = *srcP++;
        unsigned a = SkPacked4444ToA32(c);
        unsigned r = SkPacked4444ToR32(c);
        unsigned g = SkPacked4444ToG32(c);
        unsigned b = SkPacked4444ToB32(c);

        if (0 != a && 255 != a) {
            SkUnPreMultiply::Scale scale = table[a];
            r = SkUnPreMultiply::ApplyScale(scale, r);
            g = SkUnPreMultiply::ApplyScale(scale, g);
            b = SkUnPreMultiply::ApplyScale(scale, b);
        }
        *dst++ = r;
        *dst++ = g;
        *dst++ = b;
        *dst++ = a;
    }
}

static transform_scanline_proc choose_proc(SkBitmap::Config config,
                                           bool hasAlpha) {
    static const struct {
        SkBitmap::Config        fConfig;
        bool                    fHasAlpha;
        transform_scanline_proc fProc;
    } gMap[] = {
        { SkBitmap::kRGB_565_Config,    false,  transform_scanline_565 },
        { SkBitmap::kARGB_8888_Config,  false,  transform_scanline_888 },
        { SkBitmap::kARGB_8888_Config,  true,   transform_scanline_8888 },
        { SkBitmap::kARGB_4444_Config,  false,  transform_scanline_444 },
        { SkBitmap::kARGB_4444_Config,  true,   transform_scanline_4444 },
    };

    for (int i = SK_ARRAY_COUNT(gMap) - 1; i >= 0; --i) {
        if (gMap[i].fConfig == config && gMap[i].fHasAlpha == hasAlpha) {
            return gMap[i].fProc;
        }
    }
    sk_throw();
    return NULL;
}

class SkPNGImageEncoder : public SkImageEncoder {
protected:
    virtual bool onEncode(SkWStream* stream, const SkBitmap& bm, int quality);
};

bool SkPNGImageEncoder::onEncode(SkWStream* stream, const SkBitmap& bitmap,
                                 int /*quality*/) {
    SkBitmap::Config config = bitmap.getConfig();

    const bool hasAlpha = !bitmap.isOpaque();
    png_color_8 sig_bit;

    switch (config) {
        case SkBitmap::kARGB_8888_Config:
            sig_bit.red = 8;
            sig_bit.green = 8;
            sig_bit.blue = 8;
            sig_bit.alpha = hasAlpha ? 8 : 0;
            break;
        case SkBitmap::kARGB_4444_Config:
            sig_bit.red = 4;
            sig_bit.green = 4;
            sig_bit.blue = 4;
            sig_bit.alpha = hasAlpha ? 4 : 0;
            break;
        case SkBitmap::kRGB_565_Config:
            sig_bit.red = 5;
            sig_bit.green = 6;
            sig_bit.blue = 5;
            sig_bit.alpha = 0;
            break;
        default:
#if 0
            SkDEBUGF(("SkPNGImageEncoder::onEncode can't encode %d config\n",
                      config));
#endif
            return false;
    }

    SkAutoLockPixels alp(bitmap);
    if (NULL == bitmap.getPixels()) {
        return false;
    }
    
    png_structp png_ptr;
    png_infop info_ptr;

    png_ptr = png_create_write_struct(PNG_LIBPNG_VER_STRING, NULL, sk_error_fn,
                                      NULL);
    if (NULL == png_ptr) {
        return false;
    }

    info_ptr = png_create_info_struct(png_ptr);
    if (NULL == info_ptr) {
        png_destroy_write_struct(&png_ptr,  png_infopp_NULL);
        return false;
    }

    /* Set error handling.  REQUIRED if you aren't supplying your own
    * error handling functions in the png_create_write_struct() call.
    */
    if (setjmp(png_jmpbuf(png_ptr))) {
        png_destroy_write_struct(&png_ptr, &info_ptr);
        return false;
    }

    png_set_write_fn(png_ptr, (void*)stream, sk_write_fn, png_flush_ptr_NULL);

    /* Set the image information here.  Width and height are up to 2^31,
    * bit_depth is one of 1, 2, 4, 8, or 16, but valid values also depend on
    * the color_type selected. color_type is one of PNG_COLOR_TYPE_GRAY,
    * PNG_COLOR_TYPE_GRAY_ALPHA, PNG_COLOR_TYPE_PALETTE, PNG_COLOR_TYPE_RGB,
    * or PNG_COLOR_TYPE_RGB_ALPHA.  interlace is either PNG_INTERLACE_NONE or
    * PNG_INTERLACE_ADAM7, and the compression_type and filter_type MUST
    * currently be PNG_COMPRESSION_TYPE_BASE and PNG_FILTER_TYPE_BASE. REQUIRED
    */

    png_set_IHDR(png_ptr, info_ptr, bitmap.width(), bitmap.height(), 8,
                 hasAlpha ? PNG_COLOR_TYPE_RGB_ALPHA : PNG_COLOR_TYPE_RGB,
                 PNG_INTERLACE_NONE, PNG_COMPRESSION_TYPE_BASE,
                 PNG_FILTER_TYPE_BASE);

#if 0   // need to support this some day
    /* set the palette if there is one.  REQUIRED for indexed-color images */
    palette = (png_colorp)png_malloc(png_ptr, PNG_MAX_PALETTE_LENGTH
             * png_sizeof (png_color));
    /* ... set palette colors ... */
    png_set_PLTE(png_ptr, info_ptr, palette, PNG_MAX_PALETTE_LENGTH);
    /* You must not free palette here, because png_set_PLTE only makes a link to
      the palette that you malloced.  Wait until you are about to destroy
      the png structure. */
#endif

    png_set_sBIT(png_ptr, info_ptr, &sig_bit);
    png_write_info(png_ptr, info_ptr);

    const char* srcImage = (const char*)bitmap.getPixels();
    SkAutoSMalloc<1024> rowStorage(bitmap.width() << 2);
    char* storage = (char*)rowStorage.get();
    transform_scanline_proc proc = choose_proc(config, hasAlpha);

    for (int y = 0; y < bitmap.height(); y++) {
        png_bytep row_ptr = (png_bytep)storage;
        proc(srcImage, bitmap.width(), storage);
        png_write_rows(png_ptr, &row_ptr, 1);
        srcImage += bitmap.rowBytes();
    }

    png_write_end(png_ptr, info_ptr);

#if 0
    /* If you png_malloced a palette, free it here (don't free info_ptr->palette,
      as recommended in versions 1.0.5m and earlier of this example; if
      libpng mallocs info_ptr->palette, libpng will free it).  If you
      allocated it with malloc() instead of png_malloc(), use free() instead
      of png_free(). */
    png_free(png_ptr, palette);
#endif

    /* clean up after the write, and free any memory allocated */
    png_destroy_write_struct(&png_ptr, &info_ptr);
    return true;
}

SkImageEncoder* SkImageEncoder_PNG_Factory();
SkImageEncoder* SkImageEncoder_PNG_Factory() {
    return SkNEW(SkPNGImageEncoder);
}

#endif /* SK_SUPPORT_IMAGE_ENCODE */
