/************************************************************************

Calculate ROM size for 3rd byte and ROM checksum for 6th byte in ROM image.

-3 option makes the last two bytes of an 8k ROM 0x80. The 3c503 ASIC will
report this value regardless of the ROM contents, so we need to make
the checksum work properly. A 3Com EtherStart ROM I have handy sets
these to 0x80, so we'll use that for now. 0x04 has also been reported.
Any more offers?

Added capability to handle PCI and PnP headers. Detection is automatic.
Need to add command line argument to change Vendor and Device ID.

************************************************************************/
#include <stdio.h>
#include <fcntl.h>
#include <stdlib.h>
#include <string.h>

#if	defined(__TURBOC__) || defined(__BORLANDC__)
typedef long	off_t;
#endif

/* should be powers of 2 and MAX a multiple of MIN */
#define MINROMSIZE	8192L
#define MAXROMSIZE	65536L

#define	MAGIC_3C503	0x80

#define	PCI_PTR_LOC	0x18		/* from beginning of ROM */
#define	PCI_HDR_SIZE	0x18
#define	PNP_PTR_LOC	0x1a		/* from beginning of ROM */
#define	PNP_HDR_SIZE	0x20
#define	PNP_CHKSUM_OFF	0x9		/* bytes from beginning of PnP header */
#define	PCI_VEND_ID_OFF	0x4		/* bytes from beginning of PCI header */
#define	PCI_DEV_ID_OFF	0x6		/* bytes from beginning of PCI header */
#define	PCI_SIZE_OFF	0x10		/* bytes from beginning of PCI header */

unsigned char	*rom;
long		romsize = 0L;		/* for autosizing */
char		*identstring = 0;
int		verbose = 0;
int		pnp_hdr_offset = 0, pci_hdr_offset = 0;
int		pci_vendor_id = 0, pci_device_id = 0;

extern int getopt(int argc, char *argv[], char *options);

/* read the first three bytes to get the ROM size */
long getromsize(FILE *fd)
{
	unsigned char	buffer[3];
	long		size, i;

	if (fread(buffer, sizeof(char), 3, fd) != 3) {
		fprintf(stderr, "Cannot get first 3 bytes of file\n");
		exit(1);
	}
	/* reset pointer to beginning of file */
	if (fseek(fd, (off_t)0, SEEK_SET) < 0) {
		perror("fseek");
		exit(1);
	}
	size = buffer[2] * 512L;
	/* sizes are usually powers of two, warn if not */
	for (i = MINROMSIZE; i < MAXROMSIZE && i < size; i *= 2)
		;
	if (size > 0 && i > size)
		fprintf(stderr, "%ld is a strange size for a boot ROM\n",
			size);
	return (size);
}

void addident(void)
{
	int		len = strlen(identstring);

	/* Put the identifier in only if the space is blank */
	if (strspn(&rom[romsize-len-2], "\377") >= len)
		memcpy(&rom[romsize-len-2], identstring, len);
}

/* Accepts a spec of the form vendorid,deviceid where the ids are
   numeric strings accepted by strtoul */
void getpciids(char *spec)
{
	char		*vendor, *device;
	unsigned long	value;
	char		*endptr;

	vendor = spec;
	device = strchr(spec, ',');
	if (device != 0)
		*device++ = '\0';
	value = strtoul(vendor, &endptr, 0);
	if (*vendor != '\0' && endptr != vendor && *endptr == '\0')
		pci_vendor_id = value;
	if (device == 0)
		return;
	value = strtoul(device, &endptr, 0);
	if (*device != '\0' && endptr != device && *endptr == '\0')
		pci_device_id = value;
}

int main(int argc, char **argv)
{
	int i;
	long fs;
	FILE *fd;
	unsigned int sum;
	char *progname;
	int is3c503;
	extern int optind;
	extern char *optarg;

	progname = argv[0];
	is3c503 = 0;
	while ((i = getopt(argc, argv, "3i:p:s:v")) >= 0) {
		switch (i) {
		case '3':
			is3c503 = 1;
			break;
		case 'i':
			identstring = optarg;
			break;
		case 'p':
			getpciids(optarg);
			break;
		case 's':
			romsize = atol(optarg);
			if (romsize <= 0)
				romsize = 32768L;
			break;
		case 'v':
		       ++verbose;
			break;
		}
	}
	argc -= optind;
	argv += optind;
	if (argc < 1) {
#if	defined(__TURBOC__) || defined(__BORLANDC__)
		fprintf(stderr, "Usage: %s [/s romsize] [/i ident] [/p vendorid,deviceid] [/3] rom-file\n", progname);
#else
		fprintf(stderr, "Usage: %s [-s romsize] [-i ident] [-p vendorid,deviceid] [-3] rom-file\n", progname);
#endif
		exit(1);
	}
	if ((fd = fopen(argv[0], "rb+")) == NULL) {
		perror(argv[0]);
		exit(1);
	}
	/* If size not specified, infer it from 3rd byte */
	if (romsize == 0)
		romsize = getromsize(fd);
	/* If that is 0, choose the right size */
	if (romsize == 0)
		romsize = MAXROMSIZE;
	if ((rom = malloc(romsize)) == 0) {
		fprintf(stderr, "Cannot malloc memory for ROM buffer\n");
		exit(1);
	}
	/* fill with FFs, slightly less work for PROM burner
	   and allows limited patching */
	memset(rom, 0xFF, romsize);
	if ((fs = fread(rom, sizeof(char), romsize, fd)) < 0) {
		perror("fread");
		exit(1);
	}
	if (verbose)
		printf("%ld bytes read\n", fs);
	if (fs == romsize && fgetc(fd) != EOF) {
		fprintf(stderr, "ROM size of %ld not big enough for data\n", romsize);
		exit(1);
	}
	/* shrink it down to the smallest size that will do */
	for (romsize = MAXROMSIZE; romsize > MINROMSIZE && romsize >= 2*fs; )
		romsize /= 2L;
	rom[2] = romsize / 512L;
	rom[5] = 0;
	if (verbose)
		printf("ROM size is %ld\n", romsize);
	if (identstring != 0)
		addident();
	pci_hdr_offset = rom[PCI_PTR_LOC] + (rom[PCI_PTR_LOC+1] << 8);
	pnp_hdr_offset = rom[PNP_PTR_LOC] + (rom[PNP_PTR_LOC+1] << 8);
	/* sanity checks */
	if (pci_hdr_offset < PCI_PTR_LOC + 2
		|| pci_hdr_offset > romsize - PCI_HDR_SIZE
		|| pnp_hdr_offset <= PCI_PTR_LOC + 2
		|| pnp_hdr_offset > romsize - PNP_HDR_SIZE)
		pci_hdr_offset = pnp_hdr_offset = 0;
	else if (memcmp(&rom[pci_hdr_offset], "PCIR", sizeof("PCIR")-1) != 0
		|| memcmp(&rom[pnp_hdr_offset], "$PnP", sizeof("$PnP")-1) != 0)
		pci_hdr_offset = pnp_hdr_offset = 0;
	else
		printf("PCI header at 0x%x and PnP header at 0x%x\n",
			pci_hdr_offset, pnp_hdr_offset);
	if (pci_hdr_offset)
	{
		/* we only fill in the low byte, this limits us to ROMs of
		   255 * 512 bytes = 127.5kB or so */
		rom[pci_hdr_offset+PCI_SIZE_OFF] = rom[2];
		if (pci_vendor_id != 0)
		{
			rom[pci_hdr_offset+PCI_VEND_ID_OFF] = pci_vendor_id & 0xff;
			rom[pci_hdr_offset+PCI_VEND_ID_OFF+1] = pci_vendor_id >> 8;
		}
		if (pci_device_id != 0)
		{
			rom[pci_hdr_offset+PCI_DEV_ID_OFF] = pci_device_id & 0xff;
			rom[pci_hdr_offset+PCI_DEV_ID_OFF+1] = pci_device_id >> 8;
		}
	}
	if (pnp_hdr_offset)
	{
		for (i = pnp_hdr_offset, sum = 0; i < pnp_hdr_offset + PNP_HDR_SIZE; ++i)
			sum += rom[i];
		rom[pnp_hdr_offset+PNP_CHKSUM_OFF] = -sum;
	}
	/* 3c503 requires last two bytes to be MAGIC_3C503 */
	if (is3c503 && romsize == MINROMSIZE) {
		rom[MINROMSIZE - 1] = rom[MINROMSIZE - 2] = MAGIC_3C503;
	}
	for (i = 0, sum = 0; i < romsize; i++)
		sum += rom[i];
	rom[5] = -sum;
	/* double check */
	for (i = 0, sum = 0; i < romsize; i++)
		sum += rom[i];
	if (sum & 0xFF)
		printf("Checksum fails.\n");
	else if (verbose)
		printf("Checksum ok\n");
	if (fseek(fd, (off_t)0, SEEK_SET) < 0) {
		perror("fseek");
		exit(1);
	}
	if (fwrite(rom, sizeof(char), romsize, fd) != romsize) {
		perror(argv[0]);
		exit(1);
	}
	fclose(fd);
	exit(0);
}
/*
 * Local variables:
 *  c-basic-offset: 8
 * End:
 */
