#ifndef _UTILNT_H
#define _UTILNT_H

char *strtok_r(char *s1, char *s2, char **lasts);
double drand48();
int getopt(int nargc, char * const *nargv, const char *ostr);

/*
 * gettimeofday() function
 * Used in gettimeofdayf.c, error.c
 */
int gettimeofday(struct timeval *tp, void * );

/*
 * For syslog, openlog, closelog functions
 * Used in error.c
 */
#define LOG_ERR         3       /* error conditions */
#define LOG_PID         0x01    /* log the pid with each message */
#define LOG_DAEMON      (3<<3)  /* system daemons */

void closelog(void);
void openlog(const char *ident, int logopt, int facility);
void syslog(int priority, const char *message, ... /* arguments */);

/*
 * struct group *getgrnam(const char *name) funciton
 * Used in gname2id.c
 */
#ifndef _WIN32                  /* Conflicts with glib under windows */
typedef long    pid_t;                                  /* PID type                             */
#endif
typedef long    uid_t;                  /* UID type             */
typedef uid_t   gid_t;                  /* GID type             */

struct  group { /* see getgrent(3) */
        char    *gr_name;
        char    *gr_passwd;
        gid_t   gr_gid;
        char    **gr_mem;
};

int uname(struct utsname *name);

long gethostid(void);

long getuid(void);

long getgid(void);

int pthread_create(HANDLE *new_thread_ID, const void *attr, void * (*start_func)(void *), void *arg);

struct group *getgrnam(const char *name);

struct passwd *getpwnam(const char *name);

struct passwd *getpwuid(uid_t uid);

/*
 * char *strptime(const char *buf, const char *format, struct tm *tm);
 * used in http.c
 */

/*-
 * Copyright (c) 1997 FreeBSD Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR AND CONTRIBUTORS ``AS IS'' AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE AUTHOR OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
 * OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 *
 *
 */

/*
 * Private header file for the strftime and strptime localization
 * stuff.
 */
struct lc_time_T {
        const char *    mon[12];
        const char *    month[12];
        const char *    wday[7];
        const char *    weekday[7];
        const char *    X_fmt;
        const char *    x_fmt;
        const char *    c_fmt;
        const char *    am;
        const char *    pm;
        const char *    date_fmt;
};

extern struct lc_time_T _time_localebuf; /* extern added by n-kns10 */

extern int _time_using_locale;
extern const struct lc_time_T _C_time_locale;

#define Locale  (_time_using_locale ? &_time_localebuf : &_C_time_locale)

char *strptime(const char *buf, const char *format, struct tm *tm)
#ifdef __GNUC__
    // with the -Wformat switch, this enables format string checking
    __attribute__ ((format (strftime, 2, 3)))
#endif
;


#endif
