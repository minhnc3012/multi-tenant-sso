-- RP-Initiated Logout: register the allowed post-logout redirect URI for realestate-web-client.
-- Required by Spring Authorization Server's /connect/logout endpoint to validate the redirect target.
UPDATE oauth2_registered_client
SET    post_logout_redirect_uris = 'http://localhost:8081/'
WHERE  client_id = 'realestate-web-client';
