# Public-Exposure Notes

This repository is **public on GitHub**. Everything committed here -- including full git
history -- is world-readable. This file inventories identifiers and artifacts that are
already exposed, and recommends owner actions. It intentionally does **not** reproduce any
secret values.

Nothing in this document is a live credential. The concern is that several **AWS resource
identifiers** are committed in the Terraform files (and in history), which narrows an
attacker's reconnaissance.

## Already committed (treat as burned)

The Terraform import/IAM files embed real AWS identifiers. Because they are in git history,
editing or deleting the files now does **not** un-expose them:

| Where | Kind of identifier |
|-------|--------------------|
| `terraform/imports.tf` | EC2 instance id, security group id, CloudFront distribution id, CloudFront Origin Access Control id |
| `terraform/imports.tf`, `terraform/iam.tf` | AWS account id, IAM user name, managed-policy ARNs |

These are identifiers, not secrets -- none of them alone grants access. But an AWS account id
+ instance id + security-group id + IAM user name is useful reconnaissance and should be
treated as public going forward.

## Recommended owner actions

These are recommendations for the repository owner; they are **out of scope for this docs
change** (the `.tf` files were not modified, and no history rewrite was attempted -- either
could break the live infrastructure/IaC).

1. **Rotate anything that could be a real secret.** Confirm that no `.env`, `.pem`, AWS
   access key, database password, `INTERNAL_API_SECRET`, or `ACCESS_TOKEN_SECRET` has ever
   been committed (a quick `git log -p -- .env* '*.pem'` and a secret-scanner pass). Rotate
   any that were. `.gitignore` already excludes `.env`.
2. **Least-privilege the IAM user.** The committed IAM user carries an S3 policy plus a
   broad AWS-managed policy (`AmplifyBackendDeployFullAccess`). Confirm the user still needs
   the managed policy; if not, detach it. Prefer scoping the S3 policy to the single bucket
   and required actions.
3. **Treat resource ids as public.** The instance id, security-group id, and CloudFront
   distribution id are exposed. Ensure security posture does not rely on their secrecy:
   SSH (22) restricted to known IPs, database port 5432 closed (already the case -- access is
   via `scripts/db-tunnel.sh`), and S3 reachable only through CloudFront (Origin Access
   Control), never public-read.
4. **Consider a private overlay for infrastructure.** If future Terraform must reference real
   account/resource ids, keep those in a private repo or a git-ignored `*.auto.tfvars`, and
   keep the public repo's Terraform parameterised via variables.
5. **Enable push protection / secret scanning** on the GitHub repo to catch future leaks.

## What this docs pass changed vs. left alone

- **Sanitised (docs, safe to edit):** `ai_docs/ai_ec2.md` was reconciled to the current
  infrastructure and continues to use `<ec2-ip>`-style placeholders (no concrete IPs).
- **Removed (dead / never-implemented):** `ai_docs/ml_image_tagging_design.md` (a design
  diary referencing personal hardware; the feature was never built).
- **Left alone (out of scope):** `terraform/*.tf` were not modified -- editing them cannot
  un-expose history and risks breaking IaC. The items above are for the owner to action.
