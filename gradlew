#!/bin/sh
#
# Copyright © 2015-2021 the original authors.
# Licensed under the Apache License, Version 2.0
#

# Determine the Java command to use to start the JVM.
if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
        # IBM's JDK on AIX uses strange locations for the executables
        JAVACMD="$JAVA_HOME/jre/sh/java"
    else
        JAVACMD="$JAVA_HOME/bin/java"
    fi
    if [ ! -x "$JAVACMD" ] ; then
        die "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME"
    fi
else
    JAVACMD="java"
    if ! command -v java >/dev/null 2>&1
    then
        die "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH."
    fi
fi

# Increase the maximum file descriptors if we can.
if ! "$cygwin" && ! "$darwin" && ! "$nonstop" ; then
    case $MAX_FD in
        max*)
            # In POSIX sh, ulimit -H is undefined. That's why the result is checked to see if it worked.
            # shellcheck disable=SC2039,SC3045
            MAX_FD=$( ulimit -H -n ) ||
                warn "Could not query maximum file descriptor limit"
    esac
    case $MAX_FD in
        '' | soft) :;;
        *)
            # In POSIX sh, ulimit -n is undefined. That's why the result is checked to see if it worked.
            # shellcheck disable=SC2039,SC3045
            ulimit -n "$MAX_FD" ||
                warn "Could not set maximum file descriptor limit to $MAX_FD"
    esac
fi

APP_HOME="$(cd "$(dirname "$0")" && pwd)"
APP_NAME="Gradle"
APP_BASE_NAME="$(basename "$0")"

exec "$JAVACMD" "${JVM_OPTS:-}" -jar "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" "$@"
