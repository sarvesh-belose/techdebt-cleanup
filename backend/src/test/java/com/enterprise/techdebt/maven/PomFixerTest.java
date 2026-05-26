package com.enterprise.techdebt.maven;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PomFixerTest {

    private final PomFixer fixer = new PomFixer();

    @Test
    void bumpsInlineVersionOfDirectDependency() throws Exception {
        String pom = """
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
                        <dependency>
                            <groupId>com.fasterxml.jackson.core</groupId>
                            <artifactId>jackson-databind</artifactId>
                            <version>2.15.0</version>
                        </dependency>
                    </dependencies>
                </project>
                """;
        String out = fixer.previewBump(pom, "org.apache.logging.log4j", "log4j-core", "2.17.1");
        assertThat(out).contains("<version>2.17.1</version>");
        assertThat(out).doesNotContain("2.14.0");
        // unrelated dep untouched
        assertThat(out).contains("2.15.0");
    }

    @Test
    void bumpsPropertyDrivenVersion() {
        String pom = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>app</artifactId>
                    <version>1.0.0</version>
                    <properties>
                        <log4j.version>2.14.0</log4j.version>
                    </properties>
                    <dependencies>
                        <dependency>
                            <groupId>org.apache.logging.log4j</groupId>
                            <artifactId>log4j-core</artifactId>
                            <version>${log4j.version}</version>
                        </dependency>
                    </dependencies>
                </project>
                """;
        String out = fixer.previewBump(pom, "org.apache.logging.log4j", "log4j-core", "2.17.1");
        assertThat(out).contains("<log4j.version>2.17.1</log4j.version>");
        // dependency version reference is preserved
        assertThat(out).contains("${log4j.version}");
    }

    @Test
    void returnsUnchangedWhenDependencyAbsent() {
        String pom = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>app</artifactId>
                    <version>1.0.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>com.fasterxml.jackson.core</groupId>
                            <artifactId>jackson-databind</artifactId>
                            <version>2.15.0</version>
                        </dependency>
                    </dependencies>
                </project>
                """;
        String out = fixer.previewBump(pom, "org.apache.logging.log4j", "log4j-core", "2.17.1");
        // No change → output preserves the original content (modulo whitespace from re-serialization).
        assertThat(out).contains("jackson-databind");
        assertThat(out).doesNotContain("log4j-core");
    }

    @Test
    void writesToFileAndReportsResult(@TempDir Path tmp) throws Exception {
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
        PomFixResult result = fixer.bumpDirectDependency(pom, "org.apache.logging.log4j", "log4j-core", "2.17.1");

        assertThat(result.isChanged()).isTrue();
        assertThat(result.getPreviousVersion()).isEqualTo("2.14.0");
        assertThat(result.getNewVersion()).isEqualTo("2.17.1");
        assertThat(result.getMechanism()).isEqualTo("dependency.version");

        String after = Files.readString(pom);
        assertThat(after).contains("<version>2.17.1</version>");
        assertThat(after).doesNotContain("2.14.0");
    }
}
