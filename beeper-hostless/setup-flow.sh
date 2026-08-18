#!/bin/bash
set -u

readiness_state() {
  beeper status --json 2>/dev/null | node -e '
    let input = "";
    process.stdin.on("data", chunk => input += chunk);
    process.stdin.on("end", () => {
      try {
        const parsed = JSON.parse(input);
        const data = parsed.data || parsed;
        process.stdout.write(data.readiness?.state || "unknown");
      } catch {
        process.stdout.write("unknown");
      }
    });
  '
}

echo
echo "Beeper one-time setup"
echo "====================="
echo "This restricted page can only run Beeper setup and account linking."
echo

beeper targets add server server --port 23373 --default >/dev/null 2>&1 || true

state="$(readiness_state)"
case "$state" in
  needs-login|login-in-progress|target-unreachable|unknown)
    read -r -p "Beeper account email: " beeper_email
    if [ -z "$beeper_email" ]; then
      echo "Email is required to sign in to Beeper."
      exit 1
    fi
    setup_log="$(mktemp)"
    if beeper setup --server --email "$beeper_email" >"$setup_log" 2>&1; then
      cat "$setup_log"
    elif grep -qi "already signed in" "$setup_log"; then
      cat "$setup_log"
      echo
      echo "Existing Beeper sign-in confirmed; continuing without another login."
    else
      cat "$setup_log"
      rm -f "$setup_log"
      echo
      echo "Beeper sign-in did not complete. Chat-network setup has NOT started."
      echo "Refresh this page to retry Beeper sign-in."
      exit 1
    fi
    rm -f "$setup_log"
    ;;
  *)
    echo "Existing Beeper sign-in found; skipping email login."
    ;;
esac

attempt=0
while true; do
  state="$(readiness_state)"
  case "$state" in
    ready)
      echo "Beeper is ready."
      break
      ;;
    initializing|needs-first-sync|login-in-progress|unknown)
      attempt=$((attempt + 1))
      if [ "$attempt" -gt 60 ]; then
        echo "Beeper is still initializing after 5 minutes."
        echo "No chat-network login was started. Refresh later to continue safely."
        exit 1
      fi
      echo "Beeper is still initializing; waiting... ($attempt/60)"
      sleep 5
      ;;
    needs-verification|verification-in-progress)
      echo "This server must be verified with your existing Beeper device first."
      if ! beeper verify; then
        state="$(readiness_state)"
        if [ "$state" = "ready" ] || [ "$state" = "initializing" ] || [ "$state" = "needs-first-sync" ]; then
          echo "Verification was already accepted; continuing."
        else
          exit 1
        fi
      fi
      ;;
    needs-recovery-key|needs-secrets)
      read -r -s -p "Beeper Recovery Code: " beeper_recovery_key
      echo
      beeper verify recovery-key --key "$beeper_recovery_key" || exit 1
      unset beeper_recovery_key
      ;;
    needs-cross-signing-setup)
      echo "Beeper requires encrypted-storage setup before the server can continue."
      echo "Use an already verified Beeper device or your Recovery Code, then refresh."
      exit 1
      ;;
    *)
      echo "Beeper is not ready (state: $state)."
      beeper doctor || true
      echo "No chat-network login was started. Refresh after fixing the state above."
      exit 1
      ;;
  esac
done

read -r -p "Add a NEW chat-network login on this server? [y/N] " add_network
case "$add_network" in
  y|Y|yes|YES)
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
    ;;
  *)
    echo "Keeping the chat networks already connected to your Beeper account."
    ;;
esac

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
