# Beeper on Oracle Cloud Always Free

This deployment runs the Beeper Server and MCP gateway on an Oracle Cloud Always Free VM.
Beeper state is stored under `/opt/beeper/data` on the VM's persistent boot disk.
Caddy provides automatic HTTPS using the VM public IPv4 address through `sslip.io`.

## Required VM

- Shape: `VM.Standard.A1.Flex` with the **Always Free-eligible** label
- Image: Ubuntu 24.04 or Ubuntu 22.04
- Resources: 1 OCPU and 6 GB RAM is sufficient
- Boot volume: 50 GB, Always Free-eligible
- Ingress rules: TCP 22, 80, and 443

Do not select paid shapes, extra block volumes, load balancers, or paid public IP options.

## Install

SSH into the VM, then run:

```bash
curl -fsSLO https://raw.githubusercontent.com/alenoa88-alt/A-Search-Relay/codex/beeper-hostless/beeper-oracle/install.sh
sudo bash install.sh
```

The installer prints the one-time setup URL and generated setup password. Complete Beeper
verification, answer `n` when asked whether to add a new chat-network login, then type `READY`.

The final MCP endpoint is:

```text
https://VM_PUBLIC_IP.sslip.io/v0/mcp
```

## Operations

```bash
cd /opt/beeper
sudo docker compose ps
sudo docker compose logs --tail=200 beeper
sudo docker compose restart
```

The Docker services restart automatically after VM reboots. Do not delete `/opt/beeper/data`.
