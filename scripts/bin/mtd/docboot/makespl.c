#include <stdio.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>
#include <string.h>
#include <stdlib.h>
#include "doc_bootstub.h"

/* The commandline is at the next 256-byte boundary after the parameters. */
#define CMDLINE_LOCATION ((CHECKSUM_LOCATION+PARAM_BYTES+255)&0xffffff00)

#ifdef OLD_DOC2K
unsigned char buf[0x4000];
#else
unsigned char buf[0x3000];
#endif
unsigned char ff_pad[0x100];
unsigned char spl_sig[16] =
	{ 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0x55, 0x55,
	  0x84, 0xa8, 0xac, 0xa0, 0x30, 0x30, 0x30, 0x30 };
unsigned char stub_sig[16] =
	{ 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0x55, 0x55,
	  0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff };
unsigned char image_sig[16] =
	{ 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xdb, 0xb1,
	  0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff };

/* Keep this in sync with doc_bootstub.S */
struct params {
	union {
		unsigned short setup_seg;
		unsigned char checksum;
	};
	unsigned short low_sects;
	unsigned short high_sects;
	unsigned short initrd_sects;
	unsigned long initrd_bytes;
};

void writeblocks(int outfd, unsigned char *buf, int len, unsigned char *oob,
		 unsigned int halfblock) {
	int ret;
	int bs = halfblock ? 256 : 512;

	while (len) {
		if (len < bs) {
			memset(buf + len, 0xff, bs - len);
			len = bs;
		}
		ret = write(outfd, buf, bs);
		if (ret < 0) {
			perror("write output file");
			exit(1);
		}
		if (ret < bs) {
			fprintf(stderr, "short write of output file (%d bytes < %d)\n", ret, bs);
			exit(1);
		}
		if (halfblock) {
			ret = write(outfd, ff_pad, bs);
			if (ret < 0) {
				perror("write output file");
				exit(1);
			}
			if (ret < bs) {
				fprintf(stderr, "short write of output file (%d bytes < %d)\n", ret, bs);
				exit(1);
			}
		}
		ret = write(outfd, oob, 16);
		if (ret < 0) {
			perror("write output file");
			exit(1);
		}
		if (ret < 16) {
			fprintf(stderr, "short write of output file (%d bytes < 16)\n", ret);
			exit(1);
		}
		len -= bs;
		buf += bs;
	}
}

void usage(void)
{
	fprintf(stderr,
"Usage: makespl <stub input file> <kernel input file> <stub output file>\n"
"               [-b <bios extension output file>] [-i <initrd input file>]\n"
"               [-o <initrd output file>]\n"
"Specifying -b turns bios extension mode on.  In this case the stub output\n"
"file actually contains only kernel/initrd data.\n"
"If -i is specified but -o is not, the initrd output is appended to the stub\n"
"output file (default behavior).\n"
               );
	exit(1);
}

int main(int argc, char **argv)
{
	int len, i, stubfd, imgfd, outfd, biosfd, initfd, initrd_outfd;
	int ret;
	char *bios_extension = NULL;
	char *initrd = NULL;
	char *initrd_out = NULL;
	unsigned char checksum=0;
	int imglen, initrd_len, stublen;
	int stub_sects, initrd_sects;
	int image_sects, setup_sects, kernel_sects, image_start;
	struct params *params = (struct params *) (buf + CHECKSUM_LOCATION);

	memset(ff_pad, 0xff, sizeof(ff_pad));

	if (sizeof(struct params) != PARAM_BYTES) {
		fprintf(stderr,
"Param size mismatch!  Some idiot changed the code wrong!  Aborting.\n");
		exit(1);
	}

	while (1) {
		int c = getopt(argc, argv, "b:B:i:I:");
		if (c == -1) break;

		switch (c) {
			case 'b':
			case 'B':
				bios_extension = optarg;
				break;
			case 'i':
			case 'I':
				initrd = optarg;
				break;
			case 'o':
			case 'O':
				initrd_out = optarg;
				break;
			default:
				usage();
		}
	}

	if ((argc - optind) != 3) usage();

	stubfd = open(argv[optind++], O_RDONLY);
	if (stubfd < 0) {
		perror("open stub input file");
		exit(1);
	}
	stublen = lseek(stubfd, 0, SEEK_END);
	lseek(stubfd, 0, SEEK_SET);
#ifdef OLD_DOC2K
	stub_sects = (stublen + 255) >> 8;
#else
	stub_sects = (stublen + 511) >> 9;
#endif
	image_start = stub_sects << 9;
	/* Note:  image_start should be == stublen, because doc_bootstub.S
	 * should always pad to a 512-byte boundary.  Still, better safe than
	 * sorry.
	 *
	 * (If OLD_DOC2K is defined, image_start will actually be stublen*2) */

	imgfd = open(argv[optind++], O_RDONLY);
	if (imgfd < 0) {
		perror("open kernel input file");
		exit(1);
	}

	// get image length
	imglen = lseek(imgfd, 0, SEEK_END);
	lseek(imgfd, 0, SEEK_SET);
	image_sects = (imglen + 511) >> 9;

	outfd = open(argv[optind++], O_WRONLY | O_CREAT | O_TRUNC, 0664);
	if (outfd < 0) {
		perror("open stub output file");
		exit(1);
	}

	if (bios_extension) {
		biosfd = open(bios_extension, O_WRONLY | O_CREAT | O_TRUNC, 0664);
		if (biosfd < 0) {
			perror("open BIOS extension output file");
			exit(1);
		}
	}

	if (initrd) {
		initfd = open(initrd, O_RDONLY);
		if (initfd < 0) {
			perror("open BIOS extension output file");
			exit(1);
		}
		initrd_len = lseek(initfd, 0, SEEK_END);
		lseek(initfd, 0, SEEK_SET);
		initrd_sects = (initrd_len + 511) >> 9;
		initrd_outfd = outfd;
		if (initrd_out) {
			initrd_outfd = open(initrd_out, O_WRONLY | O_CREAT | O_TRUNC, 0664);
			if (initrd_outfd < 0) {
				perror("open initrd output file");
				exit(1);
			}
		}
	}

	/* Read the bootstub */
	len = read(stubfd, buf, stublen);
	if (len < 0) {
		perror("read from stub input file");
		exit(1);
	}
	if (len != stublen) {
		fprintf(stderr, "Unexpected EOF in stub input file\n");
		exit(1);
	}
	close(stubfd);

	if (*((unsigned short *) buf) == BIOS_SIG) {
		if (!bios_extension) {
			fprintf(stderr,
"Stub file was built as a Bios Extension, but bios extension mode was not\n"
"selected.  Aborting.\n");
			exit(1);
		}
	} else if (bios_extension) {
		fprintf(stderr,
"Stub file was not built as a Bios Extension, but bios extension mode was\n"
"selected.  Aborting.\n");
		exit(1);
	}

	if (isatty(0))
		fprintf(stderr, "Enter commandline: ");
	len = strlen(fgets(buf + CMDLINE_LOCATION, 256, stdin));
	len--;
	if (buf[CMDLINE_LOCATION+len] == '\n')
		buf[CMDLINE_LOCATION+len] = 0;
	fprintf(stderr, "Commandline is \"%s\"\n", buf + CMDLINE_LOCATION);
/*fprintf(stderr, "checksum is at %x, cmdline is at %x\n", CHECKSUM_LOCATION, CMDLINE_LOCATION);*/

	/* Read the kernel */
	len = read(imgfd, buf + image_start, sizeof(buf) - image_start);
	if (len < 0) {
		perror("read from kernel input file");
		exit(1);
	}

	setup_sects = buf[image_start + SETUP_SECTS_LOCATION];
	setup_sects++;
	kernel_sects = image_sects - setup_sects;

	if (bios_extension)
		fprintf(stderr, "%d Bios Extension sectors, ", stub_sects);
	else
		fprintf(stderr, "%d bootstub sectors, ", stub_sects);
	fprintf(stderr, "%d real-mode sectors, %d kernel sectors",
		setup_sects, kernel_sects);
	if (initrd)
		fprintf(stderr, ", %d initrd sectors\n", initrd_sects);
	else
		fprintf(stderr, "\n");

	params->low_sects = setup_sects;
	params->high_sects = kernel_sects;
	if (initrd) {
		params->initrd_sects = initrd_sects;
		params->initrd_bytes = initrd_len;
	}

	/* Calculate the csum */
	params->checksum = 0;	/* unnecessary */

	if (bios_extension) {
		for (i = 0; i < image_start; i++)
			checksum += buf[i];

		/* Set the slack byte to fix the csum */
		params->checksum = 0 - checksum;
	} else {
#ifdef OLD_DOC2K
		for (i = 0; i < (image_start/2); i++)
			checksum += buf[i];
		i = image_start;
		while (i<sizeof(buf)) {
			checksum += buf[i];
			i++;
			if ((i >> 8) & 1)
				i += 256;
		}
#else
		for (i = 0; i < sizeof(buf); i++)
			checksum += buf[i];
#endif

		/* Set the slack byte to fix the csum */
		params->checksum = 0x55 - checksum;
	}

	if (bios_extension) {
		/* Write the bootstub and commandline to one file */
		ret = write(biosfd, buf, image_start);
		if (ret < 0) {
			perror("write BIOS extension output file");
			exit(1);
		}
	} else {
#ifdef OLD_DOC2K
		/* Write the bootstub in halfblock mode */
		writeblocks(outfd, buf, image_start/2, stub_sig, 1);
#else
		/* Write the first bootstub page */
		writeblocks(outfd, buf, 512, spl_sig, 0);
		/* Write the rest of the bootstub */
		writeblocks(outfd, buf + 512, image_start - 512, stub_sig, 0);
#endif
	}
	/* Write the rest of the first buffer */
	writeblocks(outfd, buf + image_start, sizeof(buf) - image_start,
		    image_sig, 0);

	/* Now chuck out the rest of the kernel */

	while (1) {
		len = read(imgfd, buf, sizeof(buf));
		if (len < 0) {
			perror("read from kernel input file");
			exit(1);
		}
		if (len == 0)
			break;

		writeblocks(outfd, buf, len, image_sig, 0);
	}

	if (!initrd) return 0;

	/* And the initrd right after that. */

	while (1) {
		len = read(initfd, buf, sizeof(buf));
		if (len < 0) {
			perror("read from initrd input file");
			exit(1);
		}
		if (len == 0)
			break;

		writeblocks(initrd_outfd, buf, len, image_sig, 0);
	}

	return 0;
}
