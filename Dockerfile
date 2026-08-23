# syntax=docker/dockerfile:1

# ---- Stage 1: build the production jar (incl. the Vaadin frontend bundle) ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# The BuildKit cache mount keeps the local Maven repo across builds, so only the first
# build pays the full dependency + Vaadin frontend download cost.
COPY pom.xml .
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -Pproduction clean package -DskipTests

# ---- Stage 2: slim runtime ----
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# curl is only needed for the container HEALTHCHECK below.
RUN apt-get update \
    && apt-get install --yes --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Run as an unprivileged account.
RUN groupadd --system prioritize \
    && useradd --system --gid prioritize --home-dir /app/data prioritize

# The production build emits exactly one bootable jar; the *.original artifact does not
# end in .jar, so the wildcard picks only the runnable one regardless of the version.
COPY --from=build /build/target/prioritize-*.jar app.jar

# /app/data holds the H2 file DB (default profile) and the Tomcat work/log dirs; declare
# it a volume so a standalone `docker run` keeps its data across restarts.
RUN mkdir -p /app/data \
    && chown -R prioritize:prioritize /app

# The H2 URL uses ~ (home) for its file DB, and the JVM takes user.home from the passwd
# entry - not from $HOME - so the account above is created with /app/data as its home.
ENV HOME=/app/data
VOLUME ["/app/data"]

# Heap budget. The JVM is container-aware (UseContainerSupport is on by default), so it sizes the
# heap from the container's memory limit rather than the host's RAM - but the default ceiling is a
# conservative 25% of it, and with no limit at all that becomes 25% of the whole machine: on a 32 GB
# host this image was measured with a max heap of 8.4 GB. Hence a percentage, not a fixed -Xmx: a
# fixed value is wrong in both directions, OOM-killed on a small host and wasteful on a large one.
#
# 60 and not the customary 75: this application's non-heap footprint - metaspace, code cache,
# threads, the Vaadin and engine natives - was measured at roughly 310 MiB. At a 1 GiB limit, 75%
# would put the ceiling at 768 MiB heap plus that 310 MiB, i.e. past the limit, so a heap that
# genuinely filled up would get the container OOM-killed instead of collected. 60% leaves the margin.
#
# JDK_JAVA_OPTIONS is the JVM's own mechanism, so no shell wrapping is needed and the launcher
# echoes the value it picked up. Override it to tune without rebuilding:
#   docker run -e JDK_JAVA_OPTIONS="-XX:MaxRAMPercentage=75" ...   (with >= 2 GiB, see above)
ENV JDK_JAVA_OPTIONS="-XX:MaxRAMPercentage=60"

EXPOSE 8080

# /login is public (Vaadin login view) and returns 200 once the app is fully up.
# start-period covers the frontend/engine warm-up so early probes don't mark it unhealthy.
HEALTHCHECK --start-period=120s --interval=15s --timeout=5s --retries=5 \
    CMD curl --fail --silent --show-error http://localhost:8080/login || exit 1

# The entrypoint runs as root only long enough to make the data volume writable for the
# service account, then drops to it. See docker/entrypoint.sh.
COPY docker/entrypoint.sh /usr/local/bin/entrypoint.sh
RUN chmod +x /usr/local/bin/entrypoint.sh

ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
CMD ["java", "-jar", "app.jar"]
