/* vi: set sw=4 ts=4: */
/*
 * popmaildir: a simple yet powerful POP3 client
 * Delivers contents of remote mailboxes to local Maildir
 *
 * Inspired by original utility by Nikola Vladov
 *
 * Copyright (C) 2008 by Vladimir Dronnikov <dronnikov@gmail.com>
 *
 * Licensed under GPLv2, see file LICENSE in this tarball for details.
 */
#include "libbb.h"
#include "mail.h"

static void pop3_checkr(const char *fmt, const char *param, char **ret)
{
	const char *msg = command(fmt, param);
	char *answer = xmalloc_fgetline(stdin);
	if (answer && '+' == *answer) {
		if (timeout)
			alarm(0);
		if (ret)
			*ret = answer+4; // skip "+OK "
		else if (ENABLE_FEATURE_CLEAN_UP)
			free(answer);
		return;
	}
	bb_error_msg_and_die("%s failed: %s", msg, answer);
}

static void pop3_check(const char *fmt, const char *param)
{
	pop3_checkr(fmt, param, NULL);
}

int popmaildir_main(int argc, char **argv) MAIN_EXTERNALLY_VISIBLE;
int popmaildir_main(int argc UNUSED_PARAM, char **argv)
{
	char *buf;
	unsigned nmsg;
	char *hostname;
	pid_t pid;
	const char *retr;
#if ENABLE_FEATURE_POPMAILDIR_DELIVERY
	const char *delivery;
#endif
	unsigned opt_nlines = 0;

	enum {
		OPT_b = 1 << 0,		// -b binary mode. Ignored
		OPT_d = 1 << 1,		// -d,-dd,-ddd debug. Ignored
		OPT_m = 1 << 2,		// -m show used memory. Ignored
		OPT_V = 1 << 3,		// -V version. Ignored
		OPT_c = 1 << 4,		// -c use tcpclient. Ignored
		OPT_a = 1 << 5,		// -a use APOP protocol
		OPT_s = 1 << 6,		// -s skip authorization
		OPT_T = 1 << 7,		// -T get messages with TOP instead with RETR
		OPT_k = 1 << 8,		// -k keep retrieved messages on the server
		OPT_t = 1 << 9,		// -t90 set timeout to 90 sec
		OPT_R = 1 << 10,	// -R20000 remove old messages on the server >= 20000 bytes (requires -k). Ignored
		OPT_Z = 1 << 11,	// -Z11-23 remove messages from 11 to 23 (dangerous). Ignored
		OPT_L = 1 << 12,	// -L50000 not retrieve new messages >= 50000 bytes. Ignored
		OPT_H = 1 << 13,	// -H30 type first 30 lines of a message; (-L12000 -H30). Ignored
		OPT_M = 1 << 14,	// -M\"program arg1 arg2 ...\"; deliver by program. Treated like -F
		OPT_F = 1 << 15,	// -F\"program arg1 arg2 ...\"; filter by program. Treated like -M
	};

	// init global variables
	INIT_G();

	// parse options
	opt_complementary = "-1:dd:t+:R+:L+:H+";
	opts = getopt32(argv,
		"bdmVcasTkt:" "R:Z:L:H:" USE_FEATURE_POPMAILDIR_DELIVERY("M:F:"),
		&timeout, NULL, NULL, NULL, &opt_nlines
		USE_FEATURE_POPMAILDIR_DELIVERY(, &delivery, &delivery) // we treat -M and -F the same
	);
	//argc -= optind;
	argv += optind;

	// get auth info
	if (!(opts & OPT_s))
		get_cred_or_die(STDIN_FILENO);

	// goto maildir
	xchdir(*argv++);

	// launch connect helper, if any
	if (*argv)
		launch_helper((const char **)argv);

	// get server greeting
	pop3_checkr(NULL, NULL, &buf);

	// authenticate (if no -s given)
	if (!(opts & OPT_s)) {
		// server supports APOP and we want it? -> use it
		if ('<' == *buf && (opts & OPT_a)) {
			md5_ctx_t md5;
			// yes! compose <stamp><password>
			char *s = strchr(buf, '>');
			if (s)
				strcpy(s+1, G.pass);
			s = buf;
			// get md5 sum of <stamp><password>
			md5_begin(&md5);
			md5_hash(s, strlen(s), &md5);
			md5_end(s, &md5);
			// NOTE: md5 struct contains enough space
			// so we reuse md5 space instead of xzalloc(16*2+1)
#define md5_hex ((uint8_t *)&md5)
//			uint8_t *md5_hex = (uint8_t *)&md5;
			*bin2hex((char *)md5_hex, s, 16) = '\0';
			// APOP
			s = xasprintf("%s %s", G.user, md5_hex);
#undef md5_hex
			pop3_check("APOP %s", s);
			if (ENABLE_FEATURE_CLEAN_UP) {
				free(s);
				free(buf-4); // buf is "+OK " away from malloc'ed string
			}
		// server ignores APOP -> use simple text authentication
		} else {
			// USER
			pop3_check("USER %s", G.user);
			// PASS
			pop3_check("PASS %s", G.pass);
		}
	}

	// get mailbox statistics
	pop3_checkr("STAT", NULL, &buf);

	// prepare message filename suffix
	hostname = safe_gethostname();
	pid = getpid();

	// get messages counter
	// NOTE: we don't use xatou(buf) since buf is "nmsg nbytes"
	// we only need nmsg and atoi is just exactly what we need
	// if atoi fails to convert buf into number it returns 0
	// in this case the following loop simply will not be executed
	nmsg = atoi(buf);
	if (ENABLE_FEATURE_CLEAN_UP)
		free(buf-4); // buf is "+OK " away from malloc'ed string

	// loop through messages
	retr = (opts & OPT_T) ? xasprintf("TOP %%u %u", opt_nlines) : "RETR %u";
	for (; nmsg; nmsg--) {

		char *filename;
		char *target;
		char *answer;
		FILE *fp;
#if ENABLE_FEATURE_POPMAILDIR_DELIVERY
		int rc;
#endif
		// generate unique filename
		filename  = xasprintf("tmp/%llu.%u.%s",
			monotonic_us(), (unsigned)pid, hostname);

		// retrieve message in ./tmp/ unless filter is specified
		pop3_check(retr, (const char *)(ptrdiff_t)nmsg);

#if ENABLE_FEATURE_POPMAILDIR_DELIVERY
		// delivery helper ordered? -> setup pipe
		if (opts & (OPT_F|OPT_M)) {
			// helper will have $FILENAME set to filename
			xsetenv("FILENAME", filename);
			fp = popen(delivery, "w");
			unsetenv("FILENAME");
			if (!fp) {
				bb_perror_msg("delivery helper");
				break;
			}
		} else
#endif
		// create and open file filename
		fp = xfopen_for_write(filename);

		// copy stdin to fp (either filename or delivery helper)
		while ((answer = xmalloc_fgets_str(stdin, "\r\n")) != NULL) {
			char *s = answer;
			if ('.' == answer[0]) {
				if ('.' == answer[1])
					s++;
				else if ('\r' == answer[1] && '\n' == answer[2] && '\0' == answer[3])
					break;
			}
			//*strchrnul(s, '\r') = '\n';
			fputs(s, fp);
			free(answer);
		}

#if ENABLE_FEATURE_POPMAILDIR_DELIVERY
		// analyse delivery status
		if (opts & (OPT_F|OPT_M)) {
			rc = pclose(fp);
			if (99 == rc) // 99 means bail out
				break;
//			if (rc) // !0 means skip to the next message
				goto skip;
//			// 0 means continue
		} else {
			// close filename
			fclose(fp);
		}
#endif

		// delete message from server
		if (!(opts & OPT_k))
			pop3_check("DELE %u", (const char*)(ptrdiff_t)nmsg);

		// atomically move message to ./new/
		target = xstrdup(filename);
		strncpy(target, "new", 3);
		// ... or just stop receiving on failure
		if (rename_or_warn(filename, target))
			break;
		free(target);

#if ENABLE_FEATURE_POPMAILDIR_DELIVERY
 skip:
#endif
		free(filename);
	}

	// Bye
	pop3_check("QUIT", NULL);

	if (ENABLE_FEATURE_CLEAN_UP) {
		free(G.user);
		free(G.pass);
	}

	return EXIT_SUCCESS;
}
