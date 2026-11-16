# Initial administrator bootstrap

ShelfSense does not provide public registration. Provision the first administrator out of band during deployment.

## Procedure

1. Provision `SHELF_SENSE_BOOTSTRAP_ADMIN_USERNAME` and `SHELF_SENSE_BOOTSTRAP_ADMIN_PASSWORD` through the deployment platform's secret/environment-variable mechanism. Use the intended login identifier and a unique, high-entropy password of at least 12 characters; the password must fit within 72 UTF-8 bytes. Do not put either value in source control, command-line arguments, container image layers, or logs.
2. Set the required JWT signing key and issuer (`SHELF_SENSE_JWT_SECRET` and `SHELF_SENSE_JWT_ISSUER`) and configure the PostgreSQL connection as usual.
3. Start ShelfSense once. On startup it checks the database and provisions an enabled `ADMIN` account with a BCrypt password hash only if bootstrap has not completed and there is no administrator. The bootstrap state is committed in the same database transaction as the account. If an administrator already exists, it marks bootstrap complete without changing accounts. If the requested login belongs to a non-admin account, startup fails without modifying that account.
4. After the startup reports that bootstrap completed, remove both bootstrap variables from the deployment environment or secret binding, then restart/deploy normally. The persistent database marker keeps bootstrap inactive even if those variables are accidentally retained later.
5. Verify using a database role allowed to read account metadata. Substitute the normalized login identifier in the query; do not select or print the password hash:

   ```sql
   SELECT login_identifier, role, enabled, (password_hash LIKE '$2%') AS bcrypt_encoded
   FROM accounts
   WHERE login_identifier = lower(btrim('<normalized-admin-login>'));

   SELECT completed, completed_at
   FROM account_bootstrap_state
   WHERE id = 1;
   ```

   Expect one row with role `ADMIN`, `enabled = true`, `bcrypt_encoded = true`, and a completed bootstrap marker. Then verify the administrator can sign in through `POST /api/v1/auth/login` using the deployment's protected secret input; never place the password in shell history or logs.

If both bootstrap variables are absent, startup skips provisioning. If only one is supplied, startup fails with a generic configuration error and creates no account. Bootstrap never updates an existing account and cannot be re-armed by deleting an administrator because the completion marker is persistent.
