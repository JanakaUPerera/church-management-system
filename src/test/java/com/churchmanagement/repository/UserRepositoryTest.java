package com.churchmanagement.repository;

import com.churchmanagement.testsupport.RecordingDataSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserRepositoryTest {
    @Test
    void loginLookupReadsLandingScreen() {
        RecordingDataSource dataSource = new RecordingDataSource();

        new UserRepository(dataSource).findByUsername("admin");

        assertTrue(dataSource.lastSql().contains("u.landing_screen"));
    }

    @Test
    void updatesLandingScreen() {
        RecordingDataSource dataSource = new RecordingDataSource();

        new UserRepository(dataSource).updateLandingScreen(7L, "collections.receipt");

        assertTrue(dataSource.lastSql().contains("SET landing_screen = ?"));
        assertEquals("collections.receipt", dataSource.lastValues().get(0));
        assertEquals(7L, dataSource.lastValues().get(1));
    }

    @Test
    void readsLandingScreenById() {
        RecordingDataSource dataSource = new RecordingDataSource();

        assertTrue(new UserRepository(dataSource).findLandingScreen(7L).isEmpty());
        assertTrue(dataSource.lastSql().contains("SELECT landing_screen FROM users WHERE id = ?"));
    }
}
