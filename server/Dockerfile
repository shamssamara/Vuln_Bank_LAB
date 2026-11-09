# Multi-stage Kotlin build; installDist avoids needing Gradle in the runtime container.
FROM gradle:8.14-jdk17 AS build
WORKDIR /home/gradle/project
COPY --chown=gradle:gradle . .
RUN gradle :server:installDist --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /srv
COPY --from=build /home/gradle/project/server/build/install/server/ /srv/
ENV DB_URL=jdbc:postgresql://db:5432/vulnbank DB_USER=vulnbank DB_PASSWORD=vulnbank_lab
EXPOSE 8080
ENTRYPOINT ["/srv/bin/server"]
