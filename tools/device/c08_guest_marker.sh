#!/bin/sh
set -eu

phase="${1:-}"
reason="${2:-poweroff}"
nonce=""

for token in $(cat /proc/cmdline 2>/dev/null || true); do
  case "$token" in
    rafaelia.boot_nonce=*) nonce=${token#rafaelia.boot_nonce=} ;;
  esac
done

case "$nonce" in
  *[!0-9a-f]*|'')
    echo "C08 guest marker: nonce absent or invalid" >&2
    exit 64
    ;;
esac

if [ "${#nonce}" -ne 64 ]; then
  echo "C08 guest marker: nonce length invalid" >&2
  exit 64
fi

sanitize() {
  printf '%s' "$1" | tr -c 'A-Za-z0-9._/+:-' '_'
}

case "$phase" in
  boot)
    arch=$(sanitize "$(uname -m)")
    kernel=$(sanitize "$(uname -r)")
    printf 'RAFAELIA_GUEST_BOOT_V1 nonce=%s arch=%s kernel=%s\n' \
      "$nonce" "$arch" "$kernel"
    ;;
  userspace)
    init_path=$(readlink /proc/1/exe 2>/dev/null || printf '%s' /sbin/init)
    init_path=$(sanitize "$init_path")
    printf 'RAFAELIA_GUEST_USERSPACE_V1 nonce=%s init=%s\n' \
      "$nonce" "$init_path"
    ;;
  shutdown)
    case "$reason" in
      poweroff|halt|reboot) ;;
      *) echo "C08 guest marker: invalid shutdown reason" >&2; exit 64 ;;
    esac
    printf 'RAFAELIA_GUEST_SHUTDOWN_V1 nonce=%s reason=%s\n' \
      "$nonce" "$reason"
    ;;
  *)
    echo "usage: c08_guest_marker.sh boot|userspace|shutdown [poweroff|halt|reboot]" >&2
    exit 64
    ;;
esac
