#!/usr/bin/env bash
#
# db-tunnel.sh — open a local SSH tunnel to the EC2-hosted PostgreSQL so you can
# browse the database from psql / TablePlus / DBeaver / Postman without opening
# port 5432 to the internet.
#
# This is the database analogue of the shell's `ec2Login`: it first ensures the
# EC2 security group allows SSH from your CURRENT public IP (the thing that
# usually blocks you after your IP changes), then forwards localhost:5432 to the
# database over SSH. Nothing here is hard-coded — all host/credential values come
# from the environment (this repo is public), so set them in your shell profile
# or a local, git-ignored `.env` before running.
#
# Usage:
#   ./scripts/db-tunnel.sh up       # ensure SSH access + open the tunnel (idempotent), print connection info
#   ./scripts/db-tunnel.sh info     # just print connection strings for the running tunnel
#   ./scripts/db-tunnel.sh status   # is the tunnel up?
#   ./scripts/db-tunnel.sh down     # close the tunnel (leaves the security-group rule as-is)
#   ./scripts/db-tunnel.sh psql     # up, then drop into an interactive psql session (needs local psql)
#
# Required environment variables:
#   EC2_PEM_FILE    path to the SSH private key (e.g. ~/keys/portfolio.pem)
#   EC2_USER        SSH user on the instance    (e.g. ec2-user or ubuntu)
#   EC2_HOST        instance public IP / DNS
# Optional (sensible non-secret defaults shown):
#   SG_ID           security group id to open port 22 on (skips the SG step if unset)
#   AWS_REGION      default: us-west-2
#   LOCAL_PORT      default: 5432
#   REMOTE_PORT     default: 5432
#   POSTGRES_DB     default: edens_zac
#   POSTGRES_USER   default: zedens
#   POSTGRES_PASSWORD  (only used to print a ready-to-paste psql/JDBC line; never logged if unset)
#
set -euo pipefail

LOCAL_PORT="${LOCAL_PORT:-5432}"
REMOTE_PORT="${REMOTE_PORT:-5432}"
AWS_REGION="${AWS_REGION:-us-west-2}"
POSTGRES_DB="${POSTGRES_DB:-edens_zac}"
POSTGRES_USER="${POSTGRES_USER:-zedens}"

err() { printf '\033[31m%s\033[0m\n' "$*" >&2; }
info() { printf '%s\n' "$*"; }

require_env() {
  local missing=()
  [[ -n "${EC2_PEM_FILE:-}" ]] || missing+=("EC2_PEM_FILE")
  [[ -n "${EC2_USER:-}" ]] || missing+=("EC2_USER")
  [[ -n "${EC2_HOST:-}" ]] || missing+=("EC2_HOST")
  if ((${#missing[@]})); then
    err "Missing required environment variable(s): ${missing[*]}"
    err "Set them in your shell profile or a local .env, then re-run. See the header of this script."
    exit 1
  fi
  if [[ ! -f "${EC2_PEM_FILE}" ]]; then
    err "EC2_PEM_FILE points to a file that does not exist: ${EC2_PEM_FILE}"
    exit 1
  fi
}

# Ensure the EC2 security group allows SSH (port 22) from the current public IP.
# Mirrors the shell's `_ensure_ec2_ssh_access`. No-op if SG_ID is not set.
ensure_ssh_access() {
  if [[ -z "${SG_ID:-}" ]]; then
    info "SG_ID not set — skipping security-group check (assuming port 22 already reachable)."
    return 0
  fi
  if ! command -v aws >/dev/null 2>&1; then
    err "aws CLI not found but SG_ID is set — install it or unset SG_ID to skip the check."
    return 1
  fi

  local my_ip my_cidr current_cidr
  my_ip="$(curl -s --max-time 5 https://checkip.amazonaws.com | tr -d '[:space:]')"
  if [[ -z "$my_ip" ]]; then
    err "Could not determine current public IP; skipping security-group update."
    return 0
  fi
  my_cidr="${my_ip}/32"

  current_cidr="$(aws ec2 describe-security-group-rules \
    --region "$AWS_REGION" \
    --filters "Name=group-id,Values=$SG_ID" \
    --query "SecurityGroupRules[?FromPort==\`22\` && ToPort==\`22\` && !IsEgress].CidrIpv4 | [0]" \
    --output text 2>/dev/null || true)"

  if [[ "$current_cidr" != "$my_cidr" && -n "$current_cidr" && "$current_cidr" != "None" ]]; then
    info "Updating SSH security-group rule: $current_cidr -> $my_cidr"
    aws ec2 revoke-security-group-ingress \
      --region "$AWS_REGION" --group-id "$SG_ID" \
      --protocol tcp --port 22 --cidr "$current_cidr" --no-cli-pager
    aws ec2 authorize-security-group-ingress \
      --region "$AWS_REGION" --group-id "$SG_ID" \
      --protocol tcp --port 22 --cidr "$my_cidr" --no-cli-pager
    info "SSH rule updated to $my_cidr"
  else
    info "SSH rule already matches current IP (${my_cidr})"
  fi
}

tunnel_pid() {
  # PID of a process LISTENing on LOCAL_PORT, if any.
  lsof -ti ":${LOCAL_PORT}" -sTCP:LISTEN 2>/dev/null || true
}

tunnel_up() {
  require_env
  ensure_ssh_access
  if [[ -n "$(tunnel_pid)" ]]; then
    info "Tunnel already running on :${LOCAL_PORT}"
  else
    ssh -i "$EC2_PEM_FILE" \
      -L "${LOCAL_PORT}:localhost:${REMOTE_PORT}" \
      -o ExitOnForwardFailure=yes \
      "${EC2_USER}@${EC2_HOST}" -N -f
    info "SSH tunnel established: localhost:${LOCAL_PORT} -> ${EC2_HOST}:${REMOTE_PORT}"
  fi
  print_info
}

tunnel_down() {
  local pid
  pid="$(tunnel_pid)"
  if [[ -n "$pid" ]]; then
    kill $pid 2>/dev/null || true
    info "SSH tunnel stopped (PID $pid)"
  else
    info "No tunnel running on :${LOCAL_PORT}"
  fi
}

tunnel_status() {
  local pid
  pid="$(tunnel_pid)"
  if [[ -n "$pid" ]]; then
    info "Tunnel UP on :${LOCAL_PORT} (PID $pid)"
  else
    info "Tunnel DOWN"
  fi
}

print_info() {
  local pw_display="<POSTGRES_PASSWORD>"
  [[ -n "${POSTGRES_PASSWORD:-}" ]] && pw_display="${POSTGRES_PASSWORD}"
  printf '\n'
  printf 'Connect to the database at:\n'
  printf '  Host:     localhost\n'
  printf '  Port:     %s\n' "${LOCAL_PORT}"
  printf '  Database: %s\n' "${POSTGRES_DB}"
  printf '  User:     %s\n' "${POSTGRES_USER}"
  printf '  Password: %s\n\n' "${pw_display}"
  printf "  psql:  PGPASSWORD='%s' psql -h localhost -p %s -U %s -d %s\n" \
    "${pw_display}" "${LOCAL_PORT}" "${POSTGRES_USER}" "${POSTGRES_DB}"
  printf '  JDBC:  jdbc:postgresql://localhost:%s/%s\n' "${LOCAL_PORT}" "${POSTGRES_DB}"
  printf '  URL:   postgresql://%s@localhost:%s/%s\n\n' "${POSTGRES_USER}" "${LOCAL_PORT}" "${POSTGRES_DB}"
  printf '  TablePlus / DBeaver / Postman: use the Host/Port/Database/User/Password above.\n'
  printf '  Close the tunnel when done:  ./scripts/db-tunnel.sh down\n'
}

open_psql() {
  tunnel_up
  if ! command -v psql >/dev/null 2>&1; then
    err "psql not found locally; the tunnel is up — connect with a GUI client using the info above."
    return 0
  fi
  PGPASSWORD="${POSTGRES_PASSWORD:-}" psql -h localhost -p "${LOCAL_PORT}" -U "${POSTGRES_USER}" -d "${POSTGRES_DB}"
}

case "${1:-up}" in
  up) tunnel_up ;;
  down) tunnel_down ;;
  info) print_info ;;
  status) tunnel_status ;;
  psql) open_psql ;;
  *)
    err "Unknown command: ${1:-}"
    err "Usage: $0 {up|down|info|status|psql}"
    exit 1
    ;;
esac
