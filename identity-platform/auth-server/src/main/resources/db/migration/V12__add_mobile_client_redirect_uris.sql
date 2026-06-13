-- Add Expo web (localhost:8081) redirect URI to realestate-mobile-client.
-- Spring Authorization Server stores redirect_uris as comma-separated string.
-- This migration runs once; seedClient only inserts if not exists, so existing
-- clients keep their redirect_uris and this migration patches them cleanly.

UPDATE oauth2_registered_client
SET redirect_uris = CONCAT(redirect_uris, ',http://localhost:8081/callback')
WHERE client_id = 'realestate-mobile-client'
  AND redirect_uris NOT LIKE '%http://localhost:8081/callback%';
