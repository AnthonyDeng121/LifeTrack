package com.lifetrack.support;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IsolatedDatabaseInitializerTest {
    @Test void refusesNormalDatabaseAndMissingOrMisleadingUrls() {
        for (String url : new String[] {null, "", "jdbc:mysql://localhost:3306/lifetrack",
                "jdbc:mysql://localhost/lifetrack_test", "jdbc:mysql://localhost/lifetrack_test_ok/../lifetrack",
                "jdbc:h2:mem:lifetrack_test_example", "jdbc:mysql://localhost/lifetrack?name=lifetrack_test_ok"}) {
            assertThrows(IllegalStateException.class, () -> IsolatedDatabaseInitializer.requireIsolatedUrl(url));
        }
    }
    @Test void acceptsExplicitMysqlTestSchema() {
        assertDoesNotThrow(() -> IsolatedDatabaseInitializer.requireIsolatedUrl("jdbc:mysql://127.0.0.1:3306/lifetrack_test_run123?serverTimezone=Asia/Shanghai"));
    }
}
