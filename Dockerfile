# Build : compilation et tests unitaires, l'image ne se construit pas si un test échoue.
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B verify

# Exécution : WildFly, Jakarta EE, Java 21.
FROM quay.io/wildfly/wildfly:41.0.1.Final-jdk21
ENV JAVA_OPTS="-Xms96m -Xmx420m -XX:MetaspaceSize=96M -XX:MaxMetaspaceSize=256m -Djava.net.preferIPv4Stack=true -Djava.awt.headless=true -Duser.timezone=Europe/Paris -Duser.language=fr -Duser.country=FR"
# La page d'accueil de WildFly occupe la racine : on la retire pour servir l'application sur /.
RUN /opt/jboss/wildfly/bin/jboss-cli.sh --commands="embed-server --std-out=discard,/subsystem=undertow/server=default-server/host=default-host/location=\/:remove,stop-embedded-server" \
 && rm -rf /opt/jboss/wildfly/standalone/configuration/standalone_xml_history
COPY --from=build /src/target/ROOT.war /opt/jboss/wildfly/standalone/deployments/ROOT.war
EXPOSE 8080
CMD ["/opt/jboss/wildfly/bin/standalone.sh", "-b", "0.0.0.0"]
