#!/bin/busybox sh
# Distroless has no shell. BusyBox appends BEFIVE_JAVA_OPTS, then
# replaces this process with the JVM.
set -e
exec /opt/java/bin/java \
  -XX:+UseZGC \
  -XX:+ZGenerational \
  -XX:MaxRAMPercentage=70 \
  -XX:MaxDirectMemorySize=1g \
  -XX:+ExitOnOutOfMemoryError \
  -XX:+AlwaysPreTouch \
  -Dio.netty.allocator.type=pooled \
  -Dio.netty.leakDetection.level=disabled \
  -Xlog:gc*:stderr:time,level,tags \
  ${BEFIVE_JAVA_OPTS:-} \
  -jar /opt/befive/befive.jar "$@"
