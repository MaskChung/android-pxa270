/* tools/mkbootimg/mkbootimg.c
**
** Copyright 2007, The Android Open Source Project
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

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <fcntl.h>
#include <errno.h>
#include <time.h>

#include "bootimg.h"

static void *load_file(const char *fn, unsigned *_sz)
{
    char *data;
    int sz;
    int fd;

    data = 0;
    fd = open(fn, O_RDONLY);
    if(fd < 0) return 0;

    sz = lseek(fd, 0, SEEK_END);
    if(sz < 0) goto oops;

    if(lseek(fd, 0, SEEK_SET) != 0) goto oops;

    data = (char*) malloc(sz);
    if(data == 0) goto oops;

    if(read(fd, data, sz) != sz) goto oops;
    close(fd);

    if(_sz) *_sz = sz;
    return data;

oops:
    close(fd);
    if(data != 0) free(data);
    return 0;
}

int usage(void)
{
    fprintf(stderr,"usage: mkbootimg\n"
            "       --kernel <filename>\n"
            "       --ramdisk <filename>\n"
            "       [ --second <2ndbootloader-filename> ]\n"
            "       [ --cmdline <kernel-commandline> ]\n"
            "       [ --board <boardname> ]\n"
            "       -o|--output <filename>\n"
            );
    return 1;
}



static unsigned char padding[2048] = { 0, };

int write_padding(int fd, unsigned pagesize, unsigned itemsize)
{
    unsigned pagemask = pagesize - 1;
    unsigned count;

    if((itemsize & pagemask) == 0) {
        return 0;
    }

    count = pagesize - (itemsize & pagemask);
    
    if(write(fd, padding, count) != count) {
        return -1;
    } else {
        return 0;
    }
}

unsigned checksum(void *_ptr, unsigned len)
{
    unsigned chk = 0;
    unsigned char *ptr = _ptr;
    while (len > 0) {
        chk += *ptr++;
        len--;
    }
    return chk;
}

int main(int argc, char **argv)
{
    boot_img_hdr hdr;
    
    char *kernel_fn = 0;
    void *kernel_data = 0;
    char *ramdisk_fn = 0;
    void *ramdisk_data = 0;
    char *second_fn = 0;
    void *second_data = 0;
    char *cmdline = "";
    char *bootimg = 0;
    char *board = "";
    unsigned pagesize = 2048;
    unsigned saddr = 0;
    int fd;
    
    argc--;
    argv++;

    memset(&hdr, 0, sizeof(hdr));
    
    while(argc > 0){
        char *arg = argv[0];
        char *val = argv[1];
        if(argc < 2) {
            return usage();
        }
        argc -= 2;
        argv += 2;
        if(!strcmp(arg, "--output") || !strcmp(arg, "-o")) {
            bootimg = val;
        } else if(!strcmp(arg, "--kernel")) {
            kernel_fn = val;
        } else if(!strcmp(arg, "--ramdisk")) {
            ramdisk_fn = val;
        } else if(!strcmp(arg, "--second")) {
            second_fn = val;
        } else if(!strcmp(arg, "--cmdline")) {
            cmdline = val;
        } else if(!strcmp(arg, "--saddr")) {
            saddr = strtoul(val, 0, 16);
        } else if(!strcmp(arg, "--board")) {
            board = val;
        } else {
            return usage();
        }
    }

    if(bootimg == 0) {
        fprintf(stderr,"error: no output filename specified\n");
        return usage();
    }

    if(kernel_fn == 0) {
        fprintf(stderr,"error: no kernel image specified\n");
        return usage();
    }

    if(ramdisk_fn == 0) {
        fprintf(stderr,"error: no ramdisk image specified\n");
        return usage();
    }

    if(strlen(board) >= BOOT_NAME_SIZE) {
        fprintf(stderr,"error: board name too large\n");
        return usage();
    }

    strcpy(hdr.name, board);
    
    hdr.kernel_addr =  0x10008000;
    hdr.ramdisk_addr = 0x11000000;
    if(saddr) {
        hdr.second_addr =  0x00300000;
    } else {
        hdr.second_addr =  0x10F00000;
    }
    hdr.tags_addr   =  0x10000100;
    hdr.page_size = pagesize;
    
    memcpy(hdr.magic, BOOT_MAGIC, BOOT_MAGIC_SIZE);
    
    if(strlen(cmdline) > (BOOT_ARGS_SIZE - 1)) {
        fprintf(stderr,"error: kernel commandline too large\n");
        return 1;
    }
    strcpy((char*)hdr.cmdline, cmdline);
    
    kernel_data = load_file(kernel_fn, &hdr.kernel_size);
    if(kernel_data == 0) {
        fprintf(stderr,"error: could not load kernel '%s'\n", kernel_fn);
        return 1;
    }
    
    if(!strcmp(ramdisk_fn,"NONE")) {
        ramdisk_data = 0;
        hdr.ramdisk_size = 0;
    } else {
        ramdisk_data = load_file(ramdisk_fn, &hdr.ramdisk_size);
        if(ramdisk_data == 0) {
            fprintf(stderr,"error: could not load ramdisk '%s'\n", ramdisk_fn);
            return 1;
        }
    }

    if(second_fn) {
        second_data = load_file(second_fn, &hdr.second_size);
        if(second_data == 0) {
            fprintf(stderr,"error: could not load secondstage '%s'\n", second_fn);
            return 1;
        }
    }

        /* put some stuff in the header to differentiate between
         * different boot images.  SHA1 would be nicer, but this
         * isn't for crypto grade anything, just to have a quick
         * way to compare boot.imgs based on their first 2k
         */
    hdr.id[0] = (unsigned) time(0);
    hdr.id[1] = checksum(kernel_data, hdr.kernel_size);
    hdr.id[2] = checksum(ramdisk_data, hdr.ramdisk_size);
    hdr.id[3] = checksum(second_data, hdr.second_size);

    fd = open(bootimg, O_CREAT | O_TRUNC | O_WRONLY, 0644);
    if(fd < 0) {
        fprintf(stderr,"error: could not create '%s'\n", bootimg);
        return 1;
    }

    if(write(fd, &hdr, sizeof(hdr)) != sizeof(hdr)) goto fail;
    if(write_padding(fd, pagesize, sizeof(hdr))) goto fail;

    if(write(fd, kernel_data, hdr.kernel_size) != hdr.kernel_size) goto fail;
    if(write_padding(fd, pagesize, hdr.kernel_size)) goto fail;

    if(write(fd, ramdisk_data, hdr.ramdisk_size) != hdr.ramdisk_size) goto fail;
    if(write_padding(fd, pagesize, hdr.ramdisk_size)) goto fail;

    if(second_data) {
        if(write(fd, second_data, hdr.second_size) != hdr.second_size) goto fail;
        if(write_padding(fd, pagesize, hdr.ramdisk_size)) goto fail;
    }

    return 0;
    
fail:
    unlink(bootimg);
    close(fd);
    fprintf(stderr,"error: failed writing '%s': %s\n", bootimg,
            strerror(errno));
    return 1;
}

