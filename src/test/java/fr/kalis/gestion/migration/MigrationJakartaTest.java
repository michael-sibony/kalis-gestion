package fr.kalis.gestion.migration;

import fr.kalis.gestion.migration.MigrationJakarta.Resultat;
import fr.kalis.gestion.migration.MigrationJakarta.TypeFichier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrationJakartaTest {

    @Test
    void basculeLesImportsDesSpecifications() {
        Resultat r = MigrationJakarta.convertir("Commande.java", """
                import javax.persistence.Entity;
                import javax.ws.rs.core.Response;
                import javax.inject.Inject;
                import javax.transaction.Transactional;
                """);
        assertTrue(r.contenu().contains("import jakarta.persistence.Entity;"));
        assertTrue(r.contenu().contains("import jakarta.ws.rs.core.Response;"));
        assertTrue(r.contenu().contains("import jakarta.inject.Inject;"));
        assertTrue(r.contenu().contains("import jakarta.transaction.Transactional;"));
        assertFalse(r.contenu().contains("javax"));
        assertEquals(4, r.changements().size());
    }

    @Test
    void laisseLesPaquetsDuJdkEnJavax() {
        Resultat r = MigrationJakarta.convertir("Dao.java", """
                import javax.sql.DataSource;
                import javax.naming.InitialContext;
                import javax.transaction.xa.XAResource;
                import javax.annotation.processing.Processor;
                import javax.crypto.Cipher;
                """);
        assertTrue(r.changements().isEmpty());
        assertEquals(5, r.conservesEnJavax());
        assertTrue(r.contenu().contains("javax.transaction.xa.XAResource"));
    }

    @Test
    void distingueJavaxAnnotationDuProcessing() {
        assertEquals("javax.annotation", MigrationJakarta.paquetJakarta("javax.annotation.PostConstruct"));
        assertNull(MigrationJakarta.paquetJakarta("javax.annotation.processing.Generated"));
        assertEquals("javax.security.auth.message", MigrationJakarta.paquetJakarta("javax.security.auth.message.AuthStatus"));
        assertNull(MigrationJakarta.paquetJakarta("javax.security.auth.Subject"));
    }

    @Test
    void basculeAussiLesNomsQualifiesDansLeCode() {
        Resultat r = MigrationJakarta.convertir("A.java",
                "@javax.ejb.Stateless public class A { @javax.persistence.PersistenceContext Object em; }");
        assertEquals("@jakarta.ejb.Stateless public class A { @jakarta.persistence.PersistenceContext Object em; }", r.contenu());
    }

    @Test
    void persistenceXmlPasseEnVersionTroisAvecSesProprietes() {
        Resultat r = MigrationJakarta.convertir("persistence.xml", """
                <persistence xmlns="http://xmlns.jcp.org/xml/ns/persistence"
                             xsi:schemaLocation="http://xmlns.jcp.org/xml/ns/persistence http://xmlns.jcp.org/xml/ns/persistence/persistence_2_2.xsd"
                             version="2.2">
                  <persistence-unit name="pu">
                    <provider>org.hibernate.ejb.HibernatePersistence</provider>
                    <properties>
                      <property name="javax.persistence.schema-generation.database.action" value="none"/>
                      <property name="hibernate.dialect" value="org.hibernate.dialect.PostgreSQL95Dialect"/>
                    </properties>
                  </persistence-unit>
                </persistence>
                """);
        assertEquals(TypeFichier.PERSISTENCE, r.type());
        assertTrue(r.contenu().contains("xmlns=\"https://jakarta.ee/xml/ns/persistence\""));
        assertTrue(r.contenu().contains("persistence_3_0.xsd"));
        assertTrue(r.contenu().contains("version=\"3.0\""));
        assertTrue(r.contenu().contains("jakarta.persistence.schema-generation.database.action"));
        assertTrue(r.contenu().contains("org.hibernate.jpa.HibernatePersistenceProvider"));
        assertTrue(r.alertes().stream().anyMatch(a -> a.message().contains("Dialecte versionné")));
    }

    @Test
    void beansXmlGardeLaDecouverteAll() {
        Resultat r = MigrationJakarta.convertir("beans.xml", """
                <beans xmlns="http://xmlns.jcp.org/xml/ns/javaee" version="2.0">
                </beans>
                """);
        assertTrue(r.contenu().contains("bean-discovery-mode=\"all\""));
        assertTrue(r.contenu().contains("version=\"4.0\""));
        assertTrue(r.contenu().contains("https://jakarta.ee/xml/ns/jakartaee"));
        assertEquals(1, r.changements().size());
        assertTrue(r.alertes().stream().anyMatch(a -> a.message().contains("annotated")));
    }

    @Test
    void beansXmlDejaExpliciteResteTel() {
        Resultat r = MigrationJakarta.convertir("beans.xml", """
                <beans xmlns="https://jakarta.ee/xml/ns/jakartaee"
                       bean-discovery-mode="annotated" version="4.0">
                </beans>
                """);
        assertTrue(r.changements().isEmpty());
        assertTrue(r.alertes().isEmpty());
    }

    @Test
    void pomRemplaceLApiJavaEe() {
        Resultat r = MigrationJakarta.convertir("pom.xml", """
                <project>
                  <properties>
                    <maven.compiler.source>1.8</maven.compiler.source>
                  </properties>
                  <dependencies>
                    <dependency>
                      <groupId>javax</groupId>
                      <artifactId>javaee-api</artifactId>
                      <version>8.0.1</version>
                      <scope>provided</scope>
                    </dependency>
                    <dependency>
                      <groupId>javax.servlet</groupId>
                      <artifactId>javax.servlet-api</artifactId>
                      <version>4.0.1</version>
                    </dependency>
                  </dependencies>
                </project>
                """);
        assertTrue(r.contenu().contains("<groupId>jakarta.platform</groupId>"));
        assertTrue(r.contenu().contains("<artifactId>jakarta.jakartaee-api</artifactId>"));
        assertTrue(r.contenu().contains("<version>10.0.0</version>"));
        assertTrue(r.contenu().contains("<artifactId>jakarta.servlet-api</artifactId>"));
        assertTrue(r.contenu().contains("<version>6.0.0</version>"));
        assertTrue(r.alertes().stream().anyMatch(a -> a.message().contains("Java 11")));
    }

    @Test
    void alerteSurLesApisSupprimees() {
        Resultat r = MigrationJakarta.convertir("Vue.java", """
                import javax.faces.bean.ManagedBean;
                import javax.xml.bind.JAXBContext;
                Criteria c = session.createCriteria(Commande.class);
                """);
        assertEquals(3, r.alertes().stream().filter(a -> !a.niveau().equals("info")).count());
    }

    @Test
    void webXmlPasseEnServletSix() {
        Resultat r = MigrationJakarta.convertir("web.xml", """
                <web-app xmlns="http://xmlns.jcp.org/xml/ns/javaee"
                         xsi:schemaLocation="http://xmlns.jcp.org/xml/ns/javaee http://xmlns.jcp.org/xml/ns/javaee/web-app_4_0.xsd"
                         version="4.0">
                  <context-param><param-name>javax.faces.PROJECT_STAGE</param-name><param-value>Production</param-value></context-param>
                </web-app>
                """);
        assertTrue(r.contenu().contains("web-app_6_0.xsd"));
        assertTrue(r.contenu().contains("version=\"6.0\""));
        assertTrue(r.contenu().contains("jakarta.faces.PROJECT_STAGE"));
    }

    @Test
    void detecteLeTypeSansNomDeFichier() {
        assertEquals(TypeFichier.PERSISTENCE, MigrationJakarta.detecter(null, "<persistence version=\"2.2\">"));
        assertEquals(TypeFichier.JAVA, MigrationJakarta.detecter("", "import javax.ejb.EJB;"));
    }
}
