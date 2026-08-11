package com.translatelab.backend.auth.mail;

import java.net.URI;

public interface AccountMailGateway {

    void sendEmailVerification(String email, URI actionUrl);

    void sendPasswordReset(String email, URI actionUrl);
}
