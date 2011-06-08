/* androidunix.c
 * based on unixunix.c
 */

/* This file collects some Unix dependencies */

#include "hack.h"	/* mainly for index() which depends on BSD */

#include <errno.h>
#include <sys/stat.h>
#if defined(NO_FILE_LINKS) || defined(SUNOS4) || defined(POSIX_TYPES)
#include <fcntl.h>
#endif

static struct stat buf;

/* see whether we should throw away this xlock file */
static int
veryold(fd)
int fd;
{
	time_t date;

	if(fstat(fd, &buf)) return(0);			/* cannot get status */
#ifndef INSURANCE
	if(buf.st_size != sizeof(int)) return(0);	/* not an xlock file */
#endif
	(void) time(&date);
	if(date - buf.st_mtime < 100L*24L*60L*60L)
		return(0);
	(void) close(fd);
	return(1);
}

static int
eraseoldlocks()
{
	register int i;

	/* cannot use maxledgerno() here, because we need to find a lock name
	 * before starting everything (including the dungeon initialization
	 * that sets astral_level, needed for maxledgerno()) up
	 */
	for(i = 1; i <= MAXDUNGEON*MAXLEVEL + 1; i++) {
		/* try to remove all */
		set_levelfile_name(lock, i);
		(void) unlink(fqname(lock, LEVELPREFIX, 0));
	}
	set_levelfile_name(lock, 0);
	if (unlink(fqname(lock, LEVELPREFIX, 0)))
		return(0);				/* cannot remove it */
	return(1);					/* success! */
}

void
regularize(s)	/* normalize file name - we don't like .'s, /'s, spaces */
char *s;
{
	register char *lp;

	while((lp=index(s, '.')) || (lp=index(s, '/')) || (lp=index(s,' ')))
		*lp = '_';
}

void
getlock()
{
	register int i = 0, fd, c;
	const char *fq_lock;

	/* we ignore QUIT and INT at this point */
	if (!lock_file(HLOCK, LOCKPREFIX, 10))
	{
		wait_synch();
		error("%s", "");
	}

	regularize(lock);
	set_levelfile_name(lock, 0);

	fq_lock = fqname(lock, LEVELPREFIX, 0);
	if((fd = open(fq_lock, 0)) == -1)
	{
		debuglog("no lock yet: %s", fq_lock);
		if(errno == ENOENT) goto gotlock;    /* no such file */
		perror(fq_lock);
		unlock_file(HLOCK);
		error("Cannot open %s", fq_lock);
	}

	if(veryold(fd) /* closes fd if true */ && eraseoldlocks())
	{
		debuglog("lock is too old: %s", fq_lock);
		goto gotlock;
	}
	(void) close(fd);

# ifdef SELF_RECOVER
	c = yn("There are files from a game in progress under your name. Recover?");
# else
	c = yn("There is already a game in progress under your name.  Destroy old game?");
# endif

	if(c == 'y' || c == 'Y')
	{
# ifdef SELF_RECOVER
		if(!recover_savefile())
		{
			unlock_file(HLOCK);
			error("Couldn't recover old game.");
		}
# else
		if(!eraseoldlocks())
		{
			unlock_file(HLOCK);
			error("Couldn't destroy old game.");
		}
# endif
	}
	else
	{
		unlock_file(HLOCK);
		error("%s", "");
	}

gotlock:
	fd = creat(fq_lock, FCMASK);
	unlock_file(HLOCK);
	if(fd == -1)
	{
		error("cannot creat lock file (%s).", fq_lock);
	}
	else
	{
		debuglog("created lock(%s)", fq_lock);

		if(write(fd, (genericptr_t) &hackpid, sizeof(hackpid)) != sizeof(hackpid))
		{
			error("cannot write lock (%s)", fq_lock);
		}
		if(close(fd) == -1)
		{
			error("cannot close lock (%s)", fq_lock);
		}
	}
}

