#!/bin/bash
set -u

echo
echo "Beeper one-time setup"
echo "====================="
echo "This restricted page can only run Beeper setup and account linking."
echo

beeper targets add server server --port 23373 --default >/dev/null 2>&1 || true
read -r -p "Beeper account email: " beeper_email
if [ -z "$beeper_email" ]; then
  echo "Email is required to sign in to Beeper."
  exit 1
fi
beeper setup --server --email "$beeper_email"

while true; do
  echo
  echo "Connect a network (WhatsApp, Instagram or Facebook Messenger)."
  beeper accounts add
  echo
  read -r -p "Connect another network now? [y/N] " answer
  case "$answer" in
    y|Y|yes|YES) ;;
    *) break ;;
  esac
done

echo
beeper doctor || true
echo
read -r -p "If Beeper is ready, type READY to switch this URL to OAuth-protected MCP mode: " confirmation
if [ "$confirmation" != "READY" ]; then
  echo "Not switched. Refresh the page whenever you want to continue setup."
  exit 1
fi

touch "$HOME/.beeper-hostless-ready"
echo "Setup complete. This page will close and become the Beeper MCP endpoint."
sleep 3
