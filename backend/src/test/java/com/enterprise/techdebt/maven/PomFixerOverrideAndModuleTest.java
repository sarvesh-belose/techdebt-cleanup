package com.enterprise.techdebt.maven;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PomFixerOverrideAndModuleTest {

    private final PomFixer fixer = new PomFixer();

    @Test
    void overrideTransitiveAddsDependencyManagementWithComment(@TempDir Path tmp) throws Exception {
        Path pom = tmp.resolve("pom.xml");
        Files.writeString(pom, """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>app</artifactId>
                    <version>1.0.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework</groupId>
                            <artifactId>spring-core</artifactId>
                            <version>5.0.0</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        PomFixResult result = fixer.overrideTransitive(pom,
                "org.apache.logging.log4j", "log4j-core", "2.17.1", "CVE-2021-44228");

        assertThat(result.isChanged()).isTrue();
        assertThat(result.getMechanism()).isEqualTo("dependencyManagement");
        String after = Files.readString(pom);
        assertThat(after).contains("<dependencyManagement>");
        assertThat(after).contains("log4j-core");
        assertThat(after).contains("2.17.1");
        // The override is annotated so a reviewer can trace it back to the CVE.
        assertThat(after).contains("CVE-2021-44228");
    }

    @Test
    void overrideTransitiveUpdatesExistingEntry(@TempDir Path tmp) throws Exception {
        Path pom = tmp.resolve("pom.xml");
        Files.writeString(pom, """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>app</artifactId>
                    <version>1.0.0</version>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>org.apache.logging.log4j</groupId>
                                <artifactId>log4j-core</artifactId>
                                <version>2.15.0</version>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                </project>
                """);
        PomFixResult result = fixer.overrideTransitive(pom,
                "org.apache.logging.log4j", "log4j-core", "2.17.1", "CVE-2021-44228");

        assertThat(result.isChanged()).isTrue();
        assertThat(result.getPreviousVersion()).isEqualTo("2.15.0");
        String after = Files.readString(pom);
        assertThat(after).contains("2.17.1");
        assertThat(after).doesNotContain("2.15.0");
    }

    @Test
    void findOwningModuleReturnsRootForSingleModuleProject(@TempDir Path tmp) throws Exception {
        Path pom = tmp.resolve("pom.xml");
        Files.writeString(pom, """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>app</artifactId>
                    <version>1.0.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.apache.logging.log4j</groupId>
                            <artifactId>log4j-core</artifactId>
                            <version>2.14.0</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        Optional<Path> found = fixer.findOwningModule(pom,
                "org.apache.logging.log4j", "log4j-core");
        assertThat(found).contains(pom);
    }

    @Test
    void findOwningModuleLocatesChildInAggregator(@TempDir Path tmp) throws Exception {
        // Layout:
        //   tmp/pom.xml            (aggregator, no deps)
        //   tmp/svc-a/pom.xml      (declares log4j-core)
        //   tmp/svc-b/pom.xml      (declares jackson-databind)
        Files.writeString(tmp.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>parent</artifactId>
                    <version>1.0.0</version>
                    <packaging>pom</packaging>
                    <modules>
                        <module>svc-a</module>
                        <module>svc-b</module>
                    </modules>
                </project>
                """);
        Files.createDirectories(tmp.resolve("svc-a"));
        Files.writeString(tmp.resolve("svc-a/pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>svc-a</artifactId>
                    <version>1.0.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.apache.logging.log4j</groupId>
                            <artifactId>log4j-core</artifactId>
                            <version>2.14.0</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        Files.createDirectories(tmp.resolve("svc-b"));
        Files.writeString(tmp.resolve("svc-b/pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>svc-b</artifactId>
                    <version>1.0.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>com.fasterxml.jackson.core</groupId>
                            <artifactId>jackson-databind</artifactId>
                            <version>2.15.0</version>
                        </dependency>
                    </dependencies>
                </project>
                """);

        Optional<Path> log4j = fixer.findOwningModule(tmp.resolve("pom.xml"),
                "org.apache.logging.log4j", "log4j-core");
        assertThat(log4j).contains(tmp.resolve("svc-a").resolve("pom.xml"));

        Optional<Path> jackson = fixer.findOwningModule(tmp.resolve("pom.xml"),
                "com.fasterxml.jackson.core", "jackson-databind");
        assertThat(jackson).contains(tmp.resolve("svc-b").resolve("pom.xml"));

        Optional<Path> missing = fixer.findOwningModule(tmp.resolve("pom.xml"),
                "org.unknown", "missing");
        assertThat(missing).isEmpty();
    }
}
