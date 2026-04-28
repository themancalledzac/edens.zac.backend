# INTERNAL_API_SECRET Rotation Runbook

Cadence: quarterly + immediately after suspected compromise.

1. Generate new secret:
   ```
   openssl rand -hex 32
   ```

2. SSH to EC2; edit `~/portfolio-backend/.env`:
   ```
   INTERNAL_API_SECRET=<current>
   INTERNAL_API_SECRET_NEXT=<new>
   ```

3. Redeploy backend:
   ```
   cd ~/portfolio-backend && ./deploy.sh
   ```
   Verify health:
   ```
   curl http://localhost:8080/actuator/health
   ```
   Backend now accepts BOTH old and new secrets.

4. In Vercel dashboard, update `INTERNAL_API_SECRET` env var to `<new>`; redeploy frontend.

5. Verify:
   ```
   curl -X POST https://yourdomain/api/proxy/api/public/messages \
     -H 'content-type: application/json' \
     -H 'origin: https://yourdomain' \
     -d '{"email":"test@example.com","message":"rotation check"}'
   ```
   Expect: 201.

6. SSH to EC2; remove `INTERNAL_API_SECRET_NEXT` from `.env`. Redeploy.
   Backend now only accepts the new secret.

7. Set calendar reminder for +90 days. Add a row to the rotation log below.

## Rotation log

| Date | Rotated by | Notes |
|------|-----------|-------|
| YYYY-MM-DD | <name> | initial entry |
