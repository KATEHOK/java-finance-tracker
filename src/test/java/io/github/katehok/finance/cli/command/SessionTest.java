package io.github.katehok.finance.cli.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SessionTest {

    private final SessionFixture fixture = new SessionFixture();

    @Test
    void exitRequestIsTakenOnce() {
        assertEquals("", fixture.run(new ExitCommand(), "exit"));
        assertTrue(fixture.session.takeExitRequest());
        assertFalse(fixture.session.takeExitRequest());
    }

    @Test
    void logInAndOut() {
        assertFalse(fixture.session.isLoggedIn());
        fixture.logIn("alice");
        assertTrue(fixture.session.isLoggedIn());
        assertEquals("alice", fixture.session.getUser().getLogin());
        assertEquals("Logged out. Your wallet is saved\n", fixture.run(new LogoutCommand(), "logout"));
        assertFalse(fixture.session.isLoggedIn());
    }
}
