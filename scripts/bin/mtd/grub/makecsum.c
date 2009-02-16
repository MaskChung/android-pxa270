#include <stdio.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
unsigned char buf[16384];

main(int argc, char **argv)
{
	int len,i, stubfd, st2fd, outfd, retval=0;
	unsigned char checksum=0;

	memset(buf, 0xff, 16384);

	if (argc < 4) {
		fprintf(stderr, "Usage: makecsum <stubfile> <stage2> <outfile>\n");
		exit(1);
	}

	stubfd = open(argv[1], O_RDONLY);
	if (stubfd < 0) {
		perror("open stub file");
		exit(1);
	}

	st2fd = open(argv[2], O_RDONLY);
	if (st2fd < 0) {
		perror("open stage2 file");
		close(stubfd);
		exit(1);
	}

	outfd = open(argv[3], O_WRONLY | O_CREAT | O_TRUNC, 0664);
	if (outfd < 0) {
		perror("open output file");
		exit(1);
	}

	/* Read the stub loader (stage1) */

	len =  read(stubfd,buf,769);
	if (len < 0) {
		perror("read from stub file");
		close(outfd);
		close(st2fd);
		close(stubfd);
		exit(1);
	}

	if (len > 768) {
		fprintf(stderr, "stub file is too large (>768 bytes)\n");
		close(outfd);
		close(st2fd);
		close(stubfd);
		exit(1);
	}

	close(stubfd);

	/* Read enough of the remainder to calculate the csum */

	len = read(st2fd, buf+768, 16384-768);
	if (len < 0) {
		perror("read from stage2 file\n");
		close(outfd);
		close(st2fd);
		exit(1);
	}


	/* Calculate the csum for 512-byte-page devices */
	buf[767] = 0;

	i=0;
	while (i<16384) {
		checksum += buf[i];
		i++;
		if ((i >> 8)&1)
			i += 256;
	}

	/* Set the slack byte to fix the csum */
	buf[767] = 0x55 - checksum;

	/* Calculate the csum for 256-byte-page devices */
	checksum = 0;

	buf[511] = 0;

	i=0;
	while (i<8192) {
		checksum += buf[i++];
	}

	/* Set the slack byte to fix the csum */
	buf[511] = 0x55 - checksum;


	/* Write the original buffer */
	len = write(outfd, buf, 16384);

	if (len < 0) {
		perror("write output file");
		close(outfd);
		close(st2fd);
		exit(1);
	}

	if (len < 16384) {
		fprintf(stderr, "short write of output file (%d bytes < 16384)\n",
			len);
		close(outfd);
		close(st2fd);
		exit(1);
	}

	/* Now chuck out the rest of the stage2 */

	while (1) {
		len = read(st2fd, buf, 16384);
		if (len < 0) {
			perror("read stage2 file");
			retval = 1;
			break;
		}
		if (len == 0)
			break;

		i = write(outfd, buf, len);
		if (i < 0) {
			perror("write output file");
			retval = 1;
			break;
		}
		if (i<len) {
			fprintf(stderr, "short write of output file (%d bytes < %d)\n",
				i,len);
			retval = 1;
			break;
		}
	}

	close(outfd);
	close(st2fd);
	return retval;
}
