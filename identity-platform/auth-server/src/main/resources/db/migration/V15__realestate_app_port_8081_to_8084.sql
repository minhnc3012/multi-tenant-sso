-- realestate-app moved from port 8081 to 8084 (8081 conflicted with admin-service).
-- Update the already-seeded realestate-web-client redirect URIs accordingly.
-- seedClient only inserts if not exists, so existing rows must be patched here.

UPDATE oauth2_registered_client
SET redirect_uris = 'http://localhost:8084/login/oauth2/code/identity-platform'
WHERE client_id = 'realestate-web-client'
  AND redirect_uris = 'http://localhost:8081/login/oauth2/code/identity-platform';

UPDATE oauth2_registered_client
SET post_logout_redirect_uris = 'http://localhost:8084/'
WHERE client_id = 'realestate-web-client'
  AND post_logout_redirect_uris = 'http://localhost:8081/';
