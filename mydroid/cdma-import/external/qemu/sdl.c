/*
 * QEMU SDL display driver
 * 
 * Copyright (c) 2003 Fabrice Bellard
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
#include "vl.h"

#include <SDL.h>

#ifndef _WIN32
#include <signal.h>
#endif

static SDL_Surface *screen;
static int gui_grab; /* if true, all keyboard/mouse events are grabbed */
static int last_vm_running;
static int gui_saved_grab;
static int gui_fullscreen;
static int gui_key_modifier_pressed;
static int gui_keysym;
static int gui_fullscreen_initial_grab;
static int gui_grab_code = KMOD_LALT | KMOD_LCTRL;
static uint8_t modifiers_state[256];
static int width, height;
static SDL_Cursor *sdl_cursor_normal;
static SDL_Cursor *sdl_cursor_hidden;
static int absolute_enabled = 0;

static void sdl_update(DisplayState *ds, int x, int y, int w, int h)
{
    //    printf("updating x=%d y=%d w=%d h=%d\n", x, y, w, h);
    SDL_UpdateRect(screen, x, y, w, h);
}

static void sdl_resize(DisplayState *ds, int w, int h, int  rotation)
{
    int flags;

    //    printf("resizing to %d %d\n", w, h);

    flags = SDL_HWSURFACE|SDL_ASYNCBLIT|SDL_HWACCEL;
    if (gui_fullscreen)
        flags |= SDL_FULLSCREEN;

    width = w;
    height = h;

 again:
    screen = SDL_SetVideoMode(w, h, 16, flags);
    if (!screen) {
        fprintf(stderr, "Could not open SDL display\n");
        exit(1);
    }
    if (!screen->pixels && (flags & SDL_HWSURFACE) && (flags & SDL_FULLSCREEN)) {
        flags &= ~SDL_HWSURFACE;
        goto again;
    }

    if (!screen->pixels) {
        fprintf(stderr, "Could not open SDL display\n");
        exit(1);
    }
    ds->data = screen->pixels;
    ds->linesize = screen->pitch;
    ds->depth = screen->format->BitsPerPixel;
    if (ds->depth == 32 && screen->format->Rshift == 0) {
        ds->bgr = 1;
    } else {
        ds->bgr = 0;
    }
    ds->width = w;
    ds->height = h;
}

/* generic keyboard conversion */

#include "sdl_keysym.h"
#include "keymaps.c"

static kbd_layout_t *kbd_layout = NULL;

static uint8_t sdl_keyevent_to_keycode_generic(const SDL_KeyboardEvent *ev)
{
    int keysym;
    /* workaround for X11+SDL bug with AltGR */
    keysym = ev->keysym.sym;
    if (keysym == 0 && ev->keysym.scancode == 113)
        keysym = SDLK_MODE;
    return keysym2scancode(kbd_layout, keysym);
}

/* specific keyboard conversions from scan codes */

#if defined(_WIN32)

static uint8_t sdl_keyevent_to_keycode(const SDL_KeyboardEvent *ev)
{
    return ev->keysym.scancode;
}

#else

static const uint8_t x_keycode_to_pc_keycode[61] = {
   0xc7,      /*  97  Home   */
   0xc8,      /*  98  Up     */
   0xc9,      /*  99  PgUp   */
   0xcb,      /* 100  Left   */
   0x4c,        /* 101  KP-5   */
   0xcd,      /* 102  Right  */
   0xcf,      /* 103  End    */
   0xd0,      /* 104  Down   */
   0xd1,      /* 105  PgDn   */
   0xd2,      /* 106  Ins    */
   0xd3,      /* 107  Del    */
   0x9c,      /* 108  Enter  */
   0x9d,      /* 109  Ctrl-R */
   0x0,       /* 110  Pause  */
   0xb7,      /* 111  Print  */
   0xb5,      /* 112  Divide */
   0xb8,      /* 113  Alt-R  */
   0xc6,      /* 114  Break  */   
   0x0,         /* 115 */
   0x0,         /* 116 */
   0x0,         /* 117 */
   0x0,         /* 118 */
   0x0,         /* 119 */
   0x70,         /* 120 Hiragana_Katakana */
   0x0,         /* 121 */
   0x0,         /* 122 */
   0x73,         /* 123 backslash */
   0x0,         /* 124 */
   0x0,         /* 125 */
   0x0,         /* 126 */
   0x0,         /* 127 */
   0x0,         /* 128 */
   0x79,         /* 129 Henkan */
   0x0,         /* 130 */
   0x7b,         /* 131 Muhenkan */
   0x0,         /* 132 */
   0x7d,         /* 133 Yen */
   0x0,         /* 134 */
   0x0,         /* 135 */
   0x47,         /* 136 KP_7 */
   0x48,         /* 137 KP_8 */
   0x49,         /* 138 KP_9 */
   0x4b,         /* 139 KP_4 */
   0x4c,         /* 140 KP_5 */
   0x4d,         /* 141 KP_6 */
   0x4f,         /* 142 KP_1 */
   0x50,         /* 143 KP_2 */
   0x51,         /* 144 KP_3 */
   0x52,         /* 145 KP_0 */
   0x53,         /* 146 KP_. */
   0x47,         /* 147 KP_HOME */
   0x48,         /* 148 KP_UP */
   0x49,         /* 149 KP_PgUp */
   0x4b,         /* 150 KP_Left */
   0x4c,         /* 151 KP_ */
   0x4d,         /* 152 KP_Right */
   0x4f,         /* 153 KP_End */
   0x50,         /* 154 KP_Down */
   0x51,         /* 155 KP_PgDn */
   0x52,         /* 156 KP_Ins */
   0x53,         /* 157 KP_Del */
};

static uint8_t sdl_keyevent_to_keycode(const SDL_KeyboardEvent *ev)
{
    int keycode;

    keycode = ev->keysym.scancode;

    if (keycode < 9) {
        keycode = 0;
    } else if (keycode < 97) {
        keycode -= 8; /* just an offset */
    } else if (keycode < 158) {
        /* use conversion table */
        keycode = x_keycode_to_pc_keycode[keycode - 97];
    } else {
        keycode = 0;
    }
    return keycode;
}

#endif

static void reset_keys(void)
{
    int i;
    for(i = 0; i < 256; i++) {
        if (modifiers_state[i]) {
            if (i & 0x80)
                kbd_put_keycode(0xe0);
            kbd_put_keycode(i | 0x80);
            modifiers_state[i] = 0;
        }
    }
}

static void sdl_process_key(SDL_KeyboardEvent *ev)
{
    int keycode, v;

    if (ev->keysym.sym == SDLK_PAUSE) {
        /* specific case */
        v = 0;
        if (ev->type == SDL_KEYUP)
            v |= 0x80;
        kbd_put_keycode(0xe1);
        kbd_put_keycode(0x1d | v);
        kbd_put_keycode(0x45 | v);
        return;
    }

    if (kbd_layout) {
        keycode = sdl_keyevent_to_keycode_generic(ev);
    } else {
        keycode = sdl_keyevent_to_keycode(ev);
    }

    switch(keycode) {
    case 0x00:
        /* sent when leaving window: reset the modifiers state */
        reset_keys();
        return;
    case 0x2a:                          /* Left Shift */
    case 0x36:                          /* Right Shift */
    case 0x1d:                          /* Left CTRL */
    case 0x9d:                          /* Right CTRL */
    case 0x38:                          /* Left ALT */
    case 0xb8:                         /* Right ALT */
        if (ev->type == SDL_KEYUP)
            modifiers_state[keycode] = 0;
        else
            modifiers_state[keycode] = 1;
        break;
    case 0x45: /* num lock */
    case 0x3a: /* caps lock */
        /* SDL does not send the key up event, so we generate it */
        kbd_put_keycode(keycode);
        kbd_put_keycode(keycode | 0x80);
        return;
    }

    /* now send the key code */
    if (keycode & 0x80)
        kbd_put_keycode(0xe0);
    if (ev->type == SDL_KEYUP)
        kbd_put_keycode(keycode | 0x80);
    else
        kbd_put_keycode(keycode & 0x7f);
}

static void sdl_update_caption(void)
{
    char buf[1024];
    strcpy(buf, "QEMU");
    if (!vm_running) {
        strcat(buf, " [Stopped]");
    }
    if (gui_grab) {
        strcat(buf, " - Press Ctrl-Alt to exit grab");
    }
    SDL_WM_SetCaption(buf, "QEMU");
}

static void sdl_hide_cursor(void)
{
    if (kbd_mouse_is_absolute()) {
        SDL_ShowCursor(1);
        SDL_SetCursor(sdl_cursor_hidden);
    } else {
        SDL_ShowCursor(0);
    }
}

static void sdl_show_cursor(void)
{
    if (!kbd_mouse_is_absolute()) {
        SDL_ShowCursor(1);
    }
}

static void sdl_grab_start(void)
{
    sdl_hide_cursor();
    SDL_WM_GrabInput(SDL_GRAB_ON);
    /* dummy read to avoid moving the mouse */
    SDL_GetRelativeMouseState(NULL, NULL);
    gui_grab = 1;
    sdl_update_caption();
}

static void sdl_grab_end(void)
{
    SDL_WM_GrabInput(SDL_GRAB_OFF);
    sdl_show_cursor();
    gui_grab = 0;
    sdl_update_caption();
}

static void sdl_send_mouse_event(int dz)
{
    int dx, dy, state, buttons;
    state = SDL_GetRelativeMouseState(&dx, &dy);
    buttons = 0;
    if (state & SDL_BUTTON(SDL_BUTTON_LEFT))
        buttons |= MOUSE_EVENT_LBUTTON;
    if (state & SDL_BUTTON(SDL_BUTTON_RIGHT))
        buttons |= MOUSE_EVENT_RBUTTON;
    if (state & SDL_BUTTON(SDL_BUTTON_MIDDLE))
        buttons |= MOUSE_EVENT_MBUTTON;

    if (kbd_mouse_is_absolute()) {
	if (!absolute_enabled) {
	    sdl_hide_cursor();
	    if (gui_grab) {
		sdl_grab_end();
	    }
	    absolute_enabled = 1;
	}

	SDL_GetMouseState(&dx, &dy);
	dx = dx * 0x7FFF / width;
	dy = dy * 0x7FFF / height;
    }

    kbd_mouse_event(dx, dy, dz, buttons);
}

static void toggle_full_screen(DisplayState *ds)
{
    gui_fullscreen = !gui_fullscreen;
    sdl_resize(ds, screen->w, screen->h, 0);
    if (gui_fullscreen) {
        gui_saved_grab = gui_grab;
        sdl_grab_start();
    } else {
        if (!gui_saved_grab)
            sdl_grab_end();
    }
    vga_hw_invalidate();
    vga_hw_update();
}

static void sdl_refresh(DisplayState *ds)
{
    SDL_Event ev1, *ev = &ev1;
    int mod_state;
                     
    if (last_vm_running != vm_running) {
        last_vm_running = vm_running;
        sdl_update_caption();
    }

    vga_hw_update();

    while (SDL_PollEvent(ev)) {
        switch (ev->type) {
        case SDL_VIDEOEXPOSE:
            sdl_update(ds, 0, 0, screen->w, screen->h);
            break;
        case SDL_KEYDOWN:
        case SDL_KEYUP:
            if (ev->type == SDL_KEYDOWN) {
                mod_state = (SDL_GetModState() & gui_grab_code) ==
                    gui_grab_code;
                gui_key_modifier_pressed = mod_state;
                if (gui_key_modifier_pressed) {
                    int keycode;
                    keycode = sdl_keyevent_to_keycode(&ev->key);
                    switch(keycode) {
                    case 0x21: /* 'f' key on US keyboard */
                        toggle_full_screen(ds);
                        gui_keysym = 1;
                        break;
                    case 0x02 ... 0x0a: /* '1' to '9' keys */ 
                        console_select(keycode - 0x02);
                        if (!is_graphic_console()) {
                            /* display grab if going to a text console */
                            if (gui_grab)
                                sdl_grab_end();
                        }
                        gui_keysym = 1;
                        break;
                    default:
                        break;
                    }
                } else if (!is_graphic_console()) {
                    int keysym;
                    keysym = 0;
                    if (ev->key.keysym.mod & (KMOD_LCTRL | KMOD_RCTRL)) {
                        switch(ev->key.keysym.sym) {
                        case SDLK_UP: keysym = QEMU_KEY_CTRL_UP; break;
                        case SDLK_DOWN: keysym = QEMU_KEY_CTRL_DOWN; break;
                        case SDLK_LEFT: keysym = QEMU_KEY_CTRL_LEFT; break;
                        case SDLK_RIGHT: keysym = QEMU_KEY_CTRL_RIGHT; break;
                        case SDLK_HOME: keysym = QEMU_KEY_CTRL_HOME; break;
                        case SDLK_END: keysym = QEMU_KEY_CTRL_END; break;
                        case SDLK_PAGEUP: keysym = QEMU_KEY_CTRL_PAGEUP; break;
                        case SDLK_PAGEDOWN: keysym = QEMU_KEY_CTRL_PAGEDOWN; break;
                        default: break;
                        }
                    } else {
                        switch(ev->key.keysym.sym) {
                        case SDLK_UP: keysym = QEMU_KEY_UP; break;
                        case SDLK_DOWN: keysym = QEMU_KEY_DOWN; break;
                        case SDLK_LEFT: keysym = QEMU_KEY_LEFT; break;
                        case SDLK_RIGHT: keysym = QEMU_KEY_RIGHT; break;
                        case SDLK_HOME: keysym = QEMU_KEY_HOME; break;
                        case SDLK_END: keysym = QEMU_KEY_END; break;
                        case SDLK_PAGEUP: keysym = QEMU_KEY_PAGEUP; break;
                        case SDLK_PAGEDOWN: keysym = QEMU_KEY_PAGEDOWN; break;
                        case SDLK_BACKSPACE: keysym = QEMU_KEY_BACKSPACE; break;                        case SDLK_DELETE: keysym = QEMU_KEY_DELETE; break;
                        default: break;
                        }
                    }
                    if (keysym) {
                        kbd_put_keysym(keysym);
                    } else if (ev->key.keysym.unicode != 0) {
                        kbd_put_keysym(ev->key.keysym.unicode);
                    }
                }
            } else if (ev->type == SDL_KEYUP) {
                mod_state = (ev->key.keysym.mod & gui_grab_code);
                if (!mod_state) {
                    if (gui_key_modifier_pressed) {
                        gui_key_modifier_pressed = 0;
                        if (gui_keysym == 0) {
                            /* exit/enter grab if pressing Ctrl-Alt */
                            if (!gui_grab) {
                                /* if the application is not active,
                                   do not try to enter grab state. It
                                   prevents
                                   'SDL_WM_GrabInput(SDL_GRAB_ON)'
                                   from blocking all the application
                                   (SDL bug). */
                                if (SDL_GetAppState() & SDL_APPACTIVE)
                                    sdl_grab_start();
                            } else {
                                sdl_grab_end();
                            }
                            /* SDL does not send back all the
                               modifiers key, so we must correct it */
                            reset_keys();
                            break;
                        }
                        gui_keysym = 0;
                    }
                }
            }
            if (is_graphic_console()) 
                sdl_process_key(&ev->key);
            break;
        case SDL_QUIT:
            qemu_system_shutdown_request();
            break;
        case SDL_MOUSEMOTION:
            if (gui_grab || kbd_mouse_is_absolute()) {
                sdl_send_mouse_event(0);
            }
            break;
        case SDL_MOUSEBUTTONDOWN:
        case SDL_MOUSEBUTTONUP:
            {
                SDL_MouseButtonEvent *bev = &ev->button;
                if (!gui_grab && !kbd_mouse_is_absolute()) {
                    if (ev->type == SDL_MOUSEBUTTONDOWN &&
                        (bev->state & SDL_BUTTON_LMASK)) {
                        /* start grabbing all events */
                        sdl_grab_start();
                    }
                } else {
                    int dz;
                    dz = 0;
#ifdef SDL_BUTTON_WHEELUP
                    if (bev->button == SDL_BUTTON_WHEELUP && ev->type == SDL_MOUSEBUTTONDOWN) {
                        dz = -1;
                    } else if (bev->button == SDL_BUTTON_WHEELDOWN && ev->type == SDL_MOUSEBUTTONDOWN) {
                        dz = 1;
                    }
#endif               
                    sdl_send_mouse_event(dz);
                }
            }
            break;
        case SDL_ACTIVEEVENT:
            if (gui_grab && ev->active.state == SDL_APPINPUTFOCUS &&
                !ev->active.gain && !gui_fullscreen_initial_grab) {
                sdl_grab_end();
            }
            break;
        default:
            break;
        }
    }
}

static void sdl_cleanup(void) 
{
    SDL_Quit();
}

void sdl_display_init(DisplayState *ds, int full_screen)
{
    int flags;
    uint8_t data = 0;

#if defined(__APPLE__)
    /* always use generic keymaps */
    if (!keyboard_layout)
        keyboard_layout = "en-us";
#endif
    if(keyboard_layout) {
        kbd_layout = init_keyboard_layout(keyboard_layout);
        if (!kbd_layout)
            exit(1);
    }

    flags = SDL_INIT_VIDEO | SDL_INIT_NOPARACHUTE;
    if (SDL_Init (flags)) {
        fprintf(stderr, "Could not initialize SDL - exiting\n");
        exit(1);
    }
#ifndef _WIN32
    /* NOTE: we still want Ctrl-C to work, so we undo the SDL redirections */
    signal(SIGINT, SIG_DFL);
    signal(SIGQUIT, SIG_DFL);
#endif

    ds->dpy_update = sdl_update;
    ds->dpy_resize = sdl_resize;
    ds->dpy_refresh = sdl_refresh;

//    sdl_resize(ds, 640, 400, 0);
    sdl_resize(ds, 240, 320, 0);
    sdl_update_caption();
    SDL_EnableKeyRepeat(250, 50);
    SDL_EnableUNICODE(1);
    gui_grab = 0;

    sdl_cursor_hidden = SDL_CreateCursor(&data, &data, 8, 1, 0, 0);
    sdl_cursor_normal = SDL_GetCursor();

    atexit(sdl_cleanup);
    if (full_screen) {
        gui_fullscreen = 1;
        gui_fullscreen_initial_grab = 1;
        sdl_grab_start();
    }
}
