/* Copyright (C) 2007-2008 The Android Open Source Project
**
** This software is licensed under the terms of the GNU General Public
** License version 2, as published by the Free Software Foundation, and
** may be copied, distributed, and modified under those terms.
**
** This program is distributed in the hope that it will be useful,
** but WITHOUT ANY WARRANTY; without even the implied warranty of
** MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
** GNU General Public License for more details.
*/
#include "skin_window.h"
#include "skin_image.h"
#include "skin_scaler.h"
#include "android_charmap.h"
#include <SDL_syswm.h>
#include <math.h>

#include "framebuffer.h"

/* when shrinking, we reduce the pixel ratio by this fixed amount */
#define  SHRINK_SCALE  0.6

typedef struct Background {
    SkinImage*   image;
    SkinRect     rect;
    SkinPos      origin;
} Background;

static void
background_done( Background*  back )
{
    skin_image_unref( &back->image );
}

static void
background_init( Background*  back, SkinBackground*  sback, SkinLocation*  loc, SkinRect*  frame )
{
    SkinRect  r;

    back->image = skin_image_rotate( sback->image, loc->rotation );
    skin_rect_rotate( &r, &sback->rect, loc->rotation );
    r.pos.x += loc->anchor.x;
    r.pos.y += loc->anchor.y;

    back->origin = r.pos;
    skin_rect_intersect( &back->rect, &r, frame );
}

static void
background_redraw( Background*  back, SkinRect*  rect, SDL_Surface*  surface )
{
    SkinRect  r;

    if (skin_rect_intersect( &r, rect, &back->rect ) )
    {
        SDL_Rect  rd, rs;

        rd.x = r.pos.x;
        rd.y = r.pos.y;
        rd.w = r.size.w;
        rd.h = r.size.h;

        rs.x = r.pos.x - back->origin.x;
        rs.y = r.pos.y - back->origin.y;
        rs.w = r.size.w;
        rs.h = r.size.h;

        SDL_BlitSurface( skin_image_surface(back->image), &rs, surface, &rd );
        //SDL_UpdateRects( surface, 1, &rd );
    }
}


typedef struct ADisplay {
    SkinRect       rect;
    SkinPos        origin;
    SkinRotation   rotation;
    SkinSize       datasize;  /* framebuffer size */
    void*          data;      /* framebuffer pixels */
    QFrameBuffer*  qfbuff;
    SkinImage*     onion;       /* onion image */
    SkinRect       onion_rect;  /* onion rect, if any */
} ADisplay;

static void
display_done( ADisplay*  disp )
{
    disp->data   = NULL;
    disp->qfbuff = NULL;
    skin_image_unref( &disp->onion );
}

static int
display_init( ADisplay*  disp, SkinDisplay*  sdisp, SkinLocation*  loc, SkinRect*  frame )
{
    skin_rect_rotate( &disp->rect, &sdisp->rect, loc->rotation );
    disp->rect.pos.x += loc->anchor.x;
    disp->rect.pos.y += loc->anchor.y;

    disp->rotation = (loc->rotation + sdisp->rotation) & 3;
    switch (disp->rotation) {
        case SKIN_ROTATION_0:
            disp->origin = disp->rect.pos;
            break;

        case SKIN_ROTATION_90:
            disp->origin.x = disp->rect.pos.x + disp->rect.size.w;
            disp->origin.y = disp->rect.pos.y;
            break;

        case SKIN_ROTATION_180:
            disp->origin.x = disp->rect.pos.x + disp->rect.size.w;
            disp->origin.y = disp->rect.pos.y + disp->rect.size.h;
            break;

        default:
            disp->origin.x = disp->rect.pos.x;
            disp->origin.y = disp->rect.pos.y + disp->rect.size.h;
            break;
    }
    skin_size_rotate( &disp->datasize, &sdisp->rect.size, sdisp->rotation );
    skin_rect_intersect( &disp->rect, &disp->rect, frame );
#if 0
    fprintf(stderr, "... display_init  rect.pos(%d,%d) rect.size(%d,%d) datasize(%d,%d)\n",
                    disp->rect.pos.x, disp->rect.pos.y,
                    disp->rect.size.w, disp->rect.size.h,
                    disp->datasize.w, disp->datasize.h);
#endif
    disp->qfbuff = sdisp->qfbuff;
    disp->data   = sdisp->qfbuff->pixels;
    disp->onion = NULL;
    return (disp->data == NULL) ? -1 : 0;
}

static __inline__ uint32_t  rgb565_to_argb32( uint32_t  pix )
{
    uint32_t  r = ((pix & 0xf800) << 8) | ((pix & 0xe000) << 3);
    uint32_t  g = ((pix & 0x07e0) << 5) | ((pix & 0x0600) >> 1);
    uint32_t  b = ((pix & 0x001f) << 3) | ((pix & 0x001c) >> 2);

    return 0xff000000 | r | g | b;
}


static void
display_set_onion( ADisplay*  disp, SkinImage*  onion, SkinRotation  rotation, int  blend )
{
    int        onion_w, onion_h;
    SkinRect*  rect  = &disp->rect;
    SkinRect*  orect = &disp->onion_rect;

    rotation = (rotation + disp->rotation) & 3;

    skin_image_unref( &disp->onion );
    disp->onion = skin_image_clone_full( onion, rotation, blend );

    onion_w = skin_image_w(disp->onion);
    onion_h = skin_image_h(disp->onion);

    switch (rotation) {
        case SKIN_ROTATION_0:
            orect->pos = rect->pos;
            break;

        case SKIN_ROTATION_90:
            orect->pos.x = rect->pos.x + rect->size.w - onion_w;
            orect->pos.y = rect->pos.y;
            break;

        case SKIN_ROTATION_180:
            orect->pos.x = rect->pos.x + rect->size.w - onion_w;
            orect->pos.y = rect->pos.y + rect->size.h - onion_h;
            break;

        default:
            orect->pos.x = rect->pos.x;
            orect->pos.y = rect->pos.y + rect->size.h - onion_h;
    }
    orect->size.w = onion_w;
    orect->size.h = onion_h;
}

#define  DOT_MATRIX  0

#if DOT_MATRIX

static void
dotmatrix_dither_argb32( unsigned char*  pixels, int  x, int  y, int  w, int  h, int  pitch )
{
    static const unsigned dotmatrix_argb32[16] = {
        0x003f00, 0x00003f, 0x3f0000, 0x000000,
        0x3f3f3f, 0x000000, 0x3f3f3f, 0x000000,
        0x3f0000, 0x000000, 0x003f00, 0x00003f,
        0x3f3f3f, 0x000000, 0x3f3f3f, 0x000000
    };

    int   yy = y & 3;

    pixels += 4*x + y*pitch;

    for ( ; h > 0; h-- ) {
        unsigned*  line = (unsigned*) pixels;
        int        nn, xx = x & 3;

        for (nn = 0; nn < w; nn++) {
            unsigned  c = line[nn];

            c = c - ((c >> 2) & dotmatrix_argb32[(yy << 2)|xx]);

            xx = (xx + 1) & 3;
            line[nn] = c;
        }

        yy      = (yy + 1) & 3;
        pixels += pitch;
    }
}

#endif /* DOT_MATRIX */

static void
display_redraw( ADisplay*  disp, SkinRect*  rect, SDL_Surface*  surface )
{
    SkinRect  r;

    if (skin_rect_intersect( &r, rect, &disp->rect ))
    {
        int           x  = r.pos.x - disp->rect.pos.x;
        int           y  = r.pos.y - disp->rect.pos.y;
        int           w  = r.size.w;
        int           h  = r.size.h;
        int           disp_w    = disp->rect.size.w;
        int           disp_h    = disp->rect.size.h;
        int           dst_pitch = surface->pitch;
        uint8_t*      dst_line  = (uint8_t*)surface->pixels + r.pos.x*4 + r.pos.y*dst_pitch;
        int           src_pitch = disp->datasize.w*2;
        uint8_t*      src_line  = (uint8_t*)disp->data;
        int           yy, xx;
#if 0
        fprintf(stderr, "--- display redraw r.pos(%d,%d) r.size(%d,%d) "
                        "disp.pos(%d,%d) disp.size(%d,%d) datasize(%d,%d) rect.pos(%d,%d) rect.size(%d,%d)\n",
                        r.pos.x - disp->rect.pos.x, r.pos.y - disp->rect.pos.y, w, h, disp->rect.pos.x, disp->rect.pos.y,
                        disp->rect.size.w, disp->rect.size.h, disp->datasize.w, disp->datasize.h,
                        rect->pos.x, rect->pos.y, rect->size.w, rect->size.h );
#endif
        SDL_LockSurface( surface );
        switch ( disp->rotation & 3 )
        {
        case ANDROID_ROTATION_0:
            src_line += x*2 + y*src_pitch;

            for (yy = h; yy > 0; yy--)
            {
                uint32_t*  dst = (uint32_t*)dst_line;
                uint16_t*  src = (uint16_t*)src_line;

                for (xx = 0; xx < w; xx++) {
                    dst[xx] = rgb565_to_argb32(src[xx]);
                }
                src_line += src_pitch;
                dst_line += dst_pitch;
            }
            break;

        case ANDROID_ROTATION_90:
            src_line += y*2 + (disp_w - x - 1)*src_pitch;

            for (yy = h; yy > 0; yy--)
            {
                uint32_t*  dst = (uint32_t*)dst_line;
                uint8_t*   src = src_line;

                for (xx = w; xx > 0; xx--)
                {
                    dst[0] = rgb565_to_argb32(((uint16_t*)src)[0]);
                    src -= src_pitch;
                    dst += 1;
                }
                src_line += 2;
                dst_line += dst_pitch;
            }
            break;

        case ANDROID_ROTATION_180:
            src_line += (disp_w -1 - x)*2 + (disp_h-1-y)*src_pitch;

            for (yy = h; yy > 0; yy--)
            {
                uint16_t*  src = (uint16_t*)src_line;
                uint32_t*  dst = (uint32_t*)dst_line;

                for (xx = w; xx > 0; xx--) {
                    dst[0] = rgb565_to_argb32(src[0]);
                    src -= 1;
                    dst += 1;
                }

                src_line -= src_pitch;
                dst_line += dst_pitch;
           }
           break;

        default:  /* ANDROID_ROTATION_270 */
            src_line += (disp_h-1-y)*2 + x*src_pitch;

            for (yy = h; yy > 0; yy--)
            {
                uint32_t*  dst = (uint32_t*)dst_line;
                uint8_t*   src = src_line;

                for (xx = w; xx > 0; xx--) {
                    dst[0] = rgb565_to_argb32(((uint16_t*)src)[0]);
                    dst   += 1;
                    src   += src_pitch;
                }
                src_line -= 2;
                dst_line += dst_pitch;
            }
        }
#if DOT_MATRIX
        dotmatrix_dither_argb32( surface->pixels, r.pos.x, r.pos.y, r.size.w, r.size.h, surface->pitch );
#endif
        SDL_UnlockSurface( surface );

        if (disp->onion != NULL) {
            SkinRect  r2;

            if ( skin_rect_intersect( &r2, &r, &disp->onion_rect ) ) {
                SDL_Rect  rs, rd;

                rd.x = r2.pos.x;
                rd.y = r2.pos.y;
                rd.w = r2.size.w;
                rd.h = r2.size.h;

                rs.x = rd.x - disp->onion_rect.pos.x;
                rs.y = rd.y - disp->onion_rect.pos.y;
                rs.w = rd.w;
                rs.h = rd.h;

                SDL_BlitSurface( skin_image_surface(disp->onion), &rs, surface, &rd );
            }
        }

        SDL_UpdateRect( surface, r.pos.x, r.pos.y, w, h );
    }
}


typedef struct Button {
    SkinImage*       image;
    SkinRect         rect;
    SkinPos          origin;
    Background*      background;
    unsigned         keycode;
    int              down;
} Button;

static void
button_done( Button*  button )
{
    skin_image_unref( &button->image );
    button->background = NULL;
}

static void
button_init( Button*  button, SkinButton*  sbutton, SkinLocation*  loc, Background*  back, SkinRect*  frame )
{
    SkinRect  r;

    button->image      = skin_image_rotate( sbutton->image, loc->rotation );
    button->background = back;
    button->keycode    = sbutton->keycode;
    button->down       = 0;

    skin_rect_rotate( &r, &sbutton->rect, loc->rotation );
    r.pos.x += loc->anchor.x;
    r.pos.y += loc->anchor.y;
    button->origin = r.pos;
    skin_rect_intersect( &button->rect, &r, frame );
}

static void
button_redraw( Button*  button, SkinRect*  rect, SDL_Surface*  surface )
{
    SkinRect  r;

    if (skin_rect_intersect( &r, rect, &button->rect ))
    {
        if ( button->down && button->image != SKIN_IMAGE_NONE )
        {
            SDL_Rect  rs, rd;

            rs.x = r.pos.x - button->origin.x;
            rs.y = r.pos.y - button->origin.y;
            rs.w = r.size.w;
            rs.h = r.size.h;

            rd.x = r.pos.x;
            rd.y = r.pos.y;
            rd.w = r.size.w;
            rd.h = r.size.h;

            if (button->image != SKIN_IMAGE_NONE) {
                SDL_BlitSurface( skin_image_surface(button->image), &rs, surface, &rd );
                if (button->down > 1)
                    SDL_BlitSurface( skin_image_surface(button->image), &rs, surface, &rd );
            }
        }
    }
}


typedef struct {
    char      tracking;
    char      inside;
    SkinPos   pos;
    ADisplay*  display;
} FingerState;

static void
finger_state_reset( FingerState*  finger )
{
    finger->tracking = 0;
    finger->inside   = 0;
}

typedef struct {
    Button*   pressed;
    Button*   hover;
} ButtonState;

static void
button_state_reset( ButtonState*  button )
{
    button->pressed = NULL;
    button->hover   = NULL;
}

typedef struct {
    char            tracking;
    SkinTrackBall*  ball;
    SkinRect        rect;
    SkinWindow*     window;
} BallState;

static void
ball_state_reset( BallState*  state, SkinWindow*  window )
{
    state->tracking = 0;
    state->ball     = NULL;

    state->rect.pos.x  = 0;
    state->rect.pos.y  = 0;
    state->rect.size.w = 0;
    state->rect.size.h = 0;
    state->window      = window;
}

static void
ball_state_redraw( BallState*  state, SkinRect*  rect, SDL_Surface*  surface )
{
    SkinRect  r;

    if (skin_rect_intersect( &r, rect, &state->rect ))
        skin_trackball_draw( state->ball, 0, 0, surface );
}

static void
ball_state_show( BallState*  state )
{
    if ( !state->tracking ) {
        state->tracking = 1;
        SDL_ShowCursor(0);
        SDL_WM_GrabInput( SDL_GRAB_ON );
        skin_trackball_refresh( state->ball );
        skin_window_redraw( state->window, &state->rect );
    }
}

static void
ball_state_hide( BallState*  state )
{
    if ( state->tracking ) {
        state->tracking = 0;
        SDL_WM_GrabInput( SDL_GRAB_OFF );
        SDL_ShowCursor(1);

        skin_window_redraw( state->window, &state->rect );
    }
}

static void
ball_state_set( BallState*  state, SkinTrackBall*  ball )
{
    if ( state->tracking )
        ball_state_hide( state );

    state->ball = ball;
    if (ball != NULL) {
        SDL_Rect  sr;

        skin_trackball_rect( ball, &sr );
        state->rect.pos.x  = sr.x;
        state->rect.pos.y  = sr.y;
        state->rect.size.w = sr.w;
        state->rect.size.h = sr.h;
    }
}

static void
ball_state_toggle( BallState*  state )
{
    if (state->tracking)
        ball_state_hide( state );
    else
        ball_state_show( state );
}

typedef struct Layout {
    int          num_buttons;
    int          num_backgrounds;
    int          num_displays;
    unsigned     color;
    Button*      buttons;
    Background*  backgrounds;
    ADisplay*    displays;
    SkinRect     rect;
    SkinLayout*  slayout;
} Layout;

#define  LAYOUT_LOOP_BUTTONS(layout,button)                          \
    do {                                                             \
        Button*  __button = (layout)->buttons;                       \
        Button*  __button_end = __button + (layout)->num_buttons;    \
        for ( ; __button < __button_end; __button ++ ) {             \
            Button*  button = __button;

#define  LAYOUT_LOOP_END_BUTTONS \
        }                        \
    } while (0);

#define  LAYOUT_LOOP_DISPLAYS(layout,display)                          \
    do {                                                               \
        ADisplay*  __display = (layout)->displays;                     \
        ADisplay*  __display_end = __display + (layout)->num_displays; \
        for ( ; __display < __display_end; __display ++ ) {            \
            ADisplay*  display = __display;

#define  LAYOUT_LOOP_END_DISPLAYS \
        }                         \
    } while (0);


static void
layout_done( Layout*  layout )
{
    int  nn;

    for (nn = 0; nn < layout->num_buttons; nn++)
        button_done( &layout->buttons[nn] );

    for (nn = 0; nn < layout->num_backgrounds; nn++)
        background_done( &layout->backgrounds[nn] );

    for (nn = 0; nn < layout->num_displays; nn++)
        display_done( &layout->displays[nn] );

    qemu_free( layout->buttons );
    layout->buttons = NULL;

    qemu_free( layout->backgrounds );
    layout->backgrounds = NULL;

    qemu_free( layout->displays );
    layout->displays = NULL;

    layout->num_buttons     = 0;
    layout->num_backgrounds = 0;
    layout->num_displays    = 0;
}

static int
layout_init( Layout*  layout, SkinLayout*  slayout )
{
    int       n_buttons, n_backgrounds, n_displays;

    /* first, count the number of elements of each kind */
    n_buttons     = 0;
    n_backgrounds = 0;
    n_displays    = 0;

    layout->color   = slayout->color;
    layout->slayout = slayout;

    SKIN_LAYOUT_LOOP_LOCS(slayout,loc)
        SkinPart*    part = loc->part;

        if ( part->background->valid )
            n_backgrounds += 1;
        if ( part->display->valid )
            n_displays += 1;

        SKIN_PART_LOOP_BUTTONS(part, sbutton)
            n_buttons += 1;
            sbutton=sbutton;
        SKIN_PART_LOOP_END
    SKIN_LAYOUT_LOOP_END

    layout->num_buttons     = n_buttons;
    layout->num_backgrounds = n_backgrounds;
    layout->num_displays    = n_displays;

    /* now allocate arrays, then populate them */
    layout->buttons     = qemu_mallocz( sizeof(Button) *     n_buttons );
    layout->backgrounds = qemu_mallocz( sizeof(Background) * n_backgrounds );
    layout->displays    = qemu_mallocz( sizeof(ADisplay) *    n_displays );

    if (layout->buttons == NULL && n_buttons > 0) goto Fail;
    if (layout->backgrounds == NULL && n_backgrounds > 0) goto Fail;
    if (layout->displays == NULL && n_displays > 0) goto Fail;

    n_buttons     = 0;
    n_backgrounds = 0;
    n_displays    = 0;

    layout->rect.pos.x = 0;
    layout->rect.pos.y = 0;
    layout->rect.size  = slayout->size;

    SKIN_LAYOUT_LOOP_LOCS(slayout,loc)
        SkinPart*    part = loc->part;
        Background*  back = NULL;

        if ( part->background->valid ) {
            back = layout->backgrounds + n_backgrounds;
            background_init( back, part->background, loc, &layout->rect );
            n_backgrounds += 1;
        }
        if ( part->display->valid ) {
            ADisplay*  disp = layout->displays + n_displays;
            display_init( disp, part->display, loc, &layout->rect );
            n_displays += 1;
        }

        SKIN_PART_LOOP_BUTTONS(part, sbutton)
            Button*  button = layout->buttons + n_buttons;
            button_init( button, sbutton, loc, back, &layout->rect );
            n_buttons += 1;
        SKIN_PART_LOOP_END
    SKIN_LAYOUT_LOOP_END

    return 0;

Fail:
    layout_done(layout);
    return -1;
}

struct SkinWindow {
    SDL_Surface*  surface;
    Layout        layout;
    SkinPos       pos;
    FingerState   finger;
    ButtonState   button;
    BallState     ball;
    char          enabled;
    char          fullscreen;
    char          no_display;

    SkinImage*    onion;
    SkinRotation  onion_rotation;
    int           onion_alpha;

    SkinScaler*   scaler;
    int           shrink;
    double        shrink_scale;
    unsigned*     shrink_pixels;
    SDL_Surface*  shrink_surface;
};

static void
add_finger_event(unsigned x, unsigned y, unsigned state)
{
    //fprintf(stderr, "::: finger %d,%d %d\n", x, y, state);
    kbd_mouse_event(x, y, 0, state);
}

static void
skin_window_find_finger( SkinWindow*  window,
                         int          x,
                         int          y )
{
    FingerState*  finger = &window->finger;

    /* find the display that contains this movement */
    finger->display = NULL;
    finger->inside  = 0;

    LAYOUT_LOOP_DISPLAYS(&window->layout,disp)
        if ( skin_rect_contains( &disp->rect, x, y ) ) {
            finger->inside   = 1;
            finger->display  = disp;
            finger->pos.x    = x - disp->origin.x;
            finger->pos.y    = y - disp->origin.y;

            skin_pos_rotate( &finger->pos, &finger->pos, -disp->rotation );
            break;
        }
    LAYOUT_LOOP_END_DISPLAYS
}

static void
skin_window_move_mouse( SkinWindow*  window,
                        int          x,
                        int          y )
{
    FingerState*  finger = &window->finger;
    ButtonState*  button = &window->button;

    if (finger->tracking) {
        ADisplay*  disp   = finger->display;
        char       inside = 1;
        int        dx     = x - disp->rect.pos.x;
        int        dy     = y - disp->rect.pos.y;

        if (dx < 0) {
            dx = 0;
            inside = 0;
        }
        else if (dx >= disp->rect.size.w) {
            dx = disp->rect.size.w - 1;
            inside = 0;
        }
        if (dy < 0) {
            dy = 0;
            inside = 0;
        } else if (dy >= disp->rect.size.h) {
            dy = disp->rect.size.h-1;
            inside = 0;
        }
        finger->inside = inside;
        finger->pos.x  = dx + (disp->rect.pos.x - disp->origin.x);
        finger->pos.y  = dy + (disp->rect.pos.y - disp->origin.y);

        skin_pos_rotate( &finger->pos, &finger->pos, -disp->rotation );
    }

    {
        Button*  hover = button->hover;

        if (hover) {
            if ( skin_rect_contains( &hover->rect, x, y ) )
                return;

            hover->down = 0;
            skin_window_redraw( window, &hover->rect );
            button->hover = NULL;
        }

        hover = NULL;
        LAYOUT_LOOP_BUTTONS( &window->layout, butt )
            if ( skin_rect_contains( &butt->rect, x, y ) ) {
                hover = butt;
                break;
            }
        LAYOUT_LOOP_END_BUTTONS

        if (hover != NULL) {
            hover->down = 1;
            skin_window_redraw( window, &hover->rect );
            button->hover = hover;
        }
    }
}

static void
skin_window_trackball_press( SkinWindow*  window, int  down )
{
    send_key_event( kKeyCodeBtnMouse, down );
}

static void
skin_window_trackball_move( SkinWindow*  window, int  xrel, int  yrel )
{
    BallState*  state = &window->ball;

    if ( skin_trackball_move( state->ball, xrel, yrel ) ) {
        skin_trackball_refresh( state->ball );
        skin_window_redraw( window, &state->rect );
    }
}

void
skin_window_set_trackball( SkinWindow*  window, SkinTrackBall*  ball )
{
    BallState*  state = &window->ball;

    ball_state_set( state, ball );
}

void
skin_window_toggle_trackball( SkinWindow*  window )
{
    BallState*  state = &window->ball;

    if (state->ball != NULL) {
        ball_state_toggle(state);
    }
}


SkinWindow*
skin_window_create( SkinLayout*  slayout, int  x, int  y, double  scale, int  no_display )
{
    SkinWindow*  window = qemu_mallocz(sizeof(*window));

    /* position emulator window to its last position */
    {
        char  temp[32];
        sprintf(temp,"SDL_VIDEO_WINDOW_POS=%d,%d",x,y);
        putenv(temp);
        putenv("SDL_VIDEO_WINDOW_FORCE_VISIBLE=1");
    }

    window->shrink_scale = scale;
    window->shrink       = (scale != 1.0);
    window->scaler       = skin_scaler_create();
    window->no_display   = no_display;

    if (skin_window_reset(window, slayout) < 0) {
        skin_window_free( window );
        return NULL;
    }
    //SDL_WM_SetCaption( "Android Emulator", "Android Emulator" );

    SDL_WM_SetPos( x, y );
    if ( !SDL_WM_IsFullyVisible( 1 ) ) {
        dprint( "emulator window was out of view and was recentred\n" );
    }

    return window;
}

void
skin_window_set_title( SkinWindow*  window, const char*  title )
{
    if (window && title)
        SDL_WM_SetCaption( title, title );
}

int
skin_window_reset ( SkinWindow*  window, SkinLayout*  slayout )
{
    Layout         layout;
    int            flags;
    ADisplay*      disp;

    if ( layout_init( &layout, slayout ) < 0 )
        return -1;

    disp = window->layout.displays;

    layout_done( &window->layout );
    window->layout = layout;

    disp = window->layout.displays;
    if (disp != NULL && window->onion)
        display_set_onion( disp,
                           window->onion,
                           window->onion_rotation,
                           window->onion_alpha );


    /* now resize window */
    if (window->surface) {
        SDL_FreeSurface(window->surface);
        window->surface = NULL;
    }

    if (window->shrink_surface) {
        SDL_FreeSurface(window->shrink_surface);
        window->shrink_surface = NULL;
    }

    if (window->shrink_pixels) {
        qemu_free(window->shrink_pixels);
        window->shrink_pixels = NULL;
    }

    if ( !window->no_display ) {
        int           window_w = layout.rect.size.w;
        int           window_h = layout.rect.size.h;
        SDL_Surface*  surface;

        if (window->shrink) {
            window_w = (int) ceil(window_w*window->shrink_scale);
            window_h = (int) ceil(window_h*window->shrink_scale);
        }

        flags = SDL_SWSURFACE;
        if (window->fullscreen)
            flags |= SDL_FULLSCREEN;

        surface = SDL_SetVideoMode( window_w, window_h, 32, flags );
        if (surface == NULL) {
            fprintf(stderr, "### Error: could not create or resize SDL window: %s\n", SDL_GetError() );
            exit(1);
        }

        if (!window->shrink)
            window->surface = surface;
        else
        {
            window_w = (int) ceil(window_w / window->shrink_scale);
            window_h = (int) ceil(window_h / window->shrink_scale);

            window->shrink_surface = surface;
            window->shrink_pixels  = qemu_mallocz( window_w * window_h * 4 );
            if (window->shrink_pixels == NULL) {
                fprintf(stderr, "### Error: could not allocate memory for rescaling surface\n");
                exit(1);
            }
            window->surface = sdl_surface_from_argb32( window->shrink_pixels, window_w, window_h );
            if (window->surface == NULL) {
                fprintf(stderr, "### Error: could not create or resize SDL window: %s\n", SDL_GetError() );
                exit(1);
            }
            skin_scaler_set( window->scaler, window->shrink_scale );
        }
    }

    finger_state_reset( &window->finger );
    button_state_reset( &window->button );
    ball_state_reset( &window->ball, window );

    skin_window_redraw( window, NULL );

    if (slayout->event_type != 0) {
        kbd_generic_event( slayout->event_type, slayout->event_code, slayout->event_value );
    }

    return 0;
}

void
skin_window_free  ( SkinWindow*  window )
{
    if (window) {
        if (window->surface) {
            SDL_FreeSurface(window->surface);
            window->surface = NULL;
        }
        if (window->shrink_surface) {
            SDL_FreeSurface(window->shrink_surface);
            window->shrink_surface = NULL;
        }
        if (window->shrink_pixels) {
            qemu_free(window->shrink_pixels);
            window->shrink_pixels = NULL;
        }
        if (window->onion) {
            skin_image_unref( &window->onion );
            window->onion_rotation = SKIN_ROTATION_0;
        }
        if (window->scaler) {
            skin_scaler_free(window->scaler);
            window->scaler = NULL;
        }
        layout_done( &window->layout );
        qemu_free(window);
    }
}

void
skin_window_set_onion( SkinWindow*   window,
                       SkinImage*    onion,
                       SkinRotation  onion_rotation,
                       int           onion_alpha )
{
    ADisplay*  disp;
    SkinImage*  old = window->onion;

    window->onion          = skin_image_ref(onion);
    window->onion_rotation = onion_rotation;
    window->onion_alpha    = onion_alpha;

    skin_image_unref( &old );

    disp = window->layout.displays;

    if (disp != NULL)
        display_set_onion( disp, window->onion, onion_rotation, onion_alpha );
}

static void
skin_window_update_shrink( SkinWindow*  window, SkinRect*  rect )
{
    skin_scaler_scale( window->scaler, window->shrink_surface, window->surface,
                       rect->pos.x, rect->pos.y, rect->size.w, rect->size.h );
}

void
skin_window_set_scale( SkinWindow*  window, double  scale )
{
    window->shrink       = (scale != 1.0);
    window->shrink_scale = scale;

    skin_window_reset( window, window->layout.slayout );
}

void
skin_window_redraw( SkinWindow*  window, SkinRect*  rect )
{
    if (window != NULL && window->surface != NULL) {
        Layout*  layout = &window->layout;

        if (rect == NULL)
            rect = &layout->rect;

        {
            SkinRect  r;

            if ( skin_rect_intersect( &r, rect, &layout->rect ) ) {
                SDL_Rect  rd;
                rd.x = r.pos.x;
                rd.y = r.pos.y;
                rd.w = r.size.w;
                rd.h = r.size.h;

                SDL_FillRect( window->surface, &rd, layout->color );
            }
        }

        {
            Background*  back = layout->backgrounds;
            Background*  end  = back + layout->num_backgrounds;
            for ( ; back < end; back++ )
                background_redraw( back, rect, window->surface );
        }

        {
            ADisplay*  disp = layout->displays;
            ADisplay*  end  = disp + layout->num_displays;
            for ( ; disp < end; disp++ )
                display_redraw( disp, rect, window->surface );
        }

        {
            Button*  button = layout->buttons;
            Button*  end    = button + layout->num_buttons;
            for ( ; button < end; button++ )
                button_redraw( button, rect, window->surface );
        }

        if ( window->ball.tracking )
            ball_state_redraw( &window->ball, rect, window->surface );

        if (window->shrink)
            skin_window_update_shrink( window, rect );
        else
        {
            SDL_Rect  rd;
            rd.x = rect->pos.x;
            rd.y = rect->pos.y;
            rd.w = rect->size.w;
            rd.h = rect->size.h;

            SDL_UpdateRects( window->surface, 1, &rd );
        }
    }
}

void
skin_window_toggle_fullscreen( SkinWindow*  window )
{
    if (window && window->surface) {
        SDL_WM_ToggleFullScreen(window->surface);
        window->fullscreen = !window->fullscreen;
        skin_window_redraw( window, NULL );
    }
}

void
skin_window_get_display( SkinWindow*  window, ADisplayInfo  *info )
{
    ADisplay*  disp = window->layout.displays;

    if (disp != NULL) {
        info->width    = disp->datasize.w;
        info->height   = disp->datasize.h;
        info->rotation = disp->rotation;
        info->data     = disp->data;
    } else {
        info->width    = 0;
        info->height   = 0;
        info->rotation = SKIN_ROTATION_0;
        info->data     = NULL;
    }
}


void
skin_window_process_event( SkinWindow*  window, SDL_Event*  ev )
{
    Button*  button;
    int      mx, my;

    if (!window->surface)
        return;

    switch (ev->type) {
    case SDL_MOUSEBUTTONDOWN:
        if ( window->ball.tracking ) {
            skin_window_trackball_press( window, 1 );
            break;
        }

        mx = ev->button.x;
        my = ev->button.y;
        if ( window->shrink ) {
            mx /= window->shrink_scale;
            my /= window->shrink_scale;
        }
        skin_window_move_mouse( window, mx, my );
        skin_window_find_finger( window, mx, my );
#if 0
        printf("down: x=%d y=%d fx=%d fy=%d fis=%d\n",
               ev->button.x, ev->button.y, window->finger.pos.x,
               window->finger.pos.y, window->finger.inside);
#endif
        if (window->finger.inside) {
            window->finger.tracking = 1;
            add_finger_event(window->finger.pos.x, window->finger.pos.y, 1);
        } else {
            window->button.pressed = NULL;
            button = window->button.hover;
            if(button) {
                button->down += 1;
                skin_window_redraw( window, &button->rect );
                window->button.pressed = button;
                if(button->keycode) {
                    send_key_event(button->keycode, 1);
                }
            }
        }
        break;

    case SDL_MOUSEBUTTONUP:
        if ( window->ball.tracking ) {
            skin_window_trackball_press( window, 0 );
            break;
        }
        button = window->button.pressed;
        mx = ev->button.x;
        my = ev->button.y;
        if ( window->shrink ) {
            mx /= window->shrink_scale;
            my /= window->shrink_scale;
        }
        if (button)
        {
            button->down = 0;
            skin_window_redraw( window, &button->rect );
            if(button->keycode) {
                send_key_event(button->keycode, 0);
            }
            window->button.pressed = NULL;
            window->button.hover   = NULL;
            skin_window_move_mouse( window, mx, my );
        }
        else if (window->finger.tracking)
        {
            skin_window_move_mouse( window, mx, my );
            window->finger.tracking = 0;
            add_finger_event( window->finger.pos.x, window->finger.pos.y, 0);
        }
        break;

    case SDL_MOUSEMOTION:
        if ( window->ball.tracking ) {
            skin_window_trackball_move( window, ev->motion.xrel, ev->motion.yrel );
            break;
        }
        mx = ev->button.x;
        my = ev->button.y;
        if ( window->shrink ) {
            mx /= window->shrink_scale;
            my /= window->shrink_scale;
        }
        if ( !window->button.pressed )
        {
            skin_window_move_mouse( window, mx, my );
            if ( window->finger.tracking ) {
                add_finger_event( window->finger.pos.x, window->finger.pos.y, 1 );
            }
        }
        break;
    }
}

static ADisplay*
skin_window_display( SkinWindow*  window )
{
    return window->layout.displays;
}

void
skin_window_update_display( SkinWindow*  window, int  x, int  y, int  w, int  h )
{
    ADisplay*  disp = skin_window_display(window);

    if ( !window->surface )
        return;

    if (disp != NULL) {
        SkinRect  r;
        r.pos.x  = x;
        r.pos.y  = y;
        r.size.w = w;
        r.size.h = h;

        skin_rect_rotate( &r, &r, disp->rotation );
        r.pos.x += disp->origin.x;
        r.pos.y += disp->origin.y;

        if (window->shrink)
            skin_window_redraw( window, &r );
        else
            display_redraw( disp, &r, window->surface );
    }
}
