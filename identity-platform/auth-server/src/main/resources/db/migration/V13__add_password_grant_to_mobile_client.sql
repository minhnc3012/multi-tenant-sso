-- Allow realestate-mobile-client to use the custom password grant type.
-- Spring Authorization Server stores authorization_grant_types as comma-separated string.
UPDATE oauth2_registered_client
SET authorization_grant_types = CONCAT(authorization_grant_types, ',password')
WHERE client_id = 'realestate-mobile-client'
  AND authorization_grant_types NOT LIKE '%password%';
