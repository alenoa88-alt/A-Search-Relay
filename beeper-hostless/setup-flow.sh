#!/bin/bash
set -u

echo
echo "Beeper one-time setup"
echo "====================="
echo "This restricted page can only run Beeper setup and account linking."
echo

beeper setup --server --install

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
