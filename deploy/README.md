<p align="center">
  <img src="../assets/notify-ai-logo.svg" alt="Notify.ai" width="96" />
</p>

<h1 align="center">Notify.ai Deployment</h1>

This deployment runs the application, nginx, PostgreSQL, and Redis as one
Compose stack.

## First-time setup

```bash
cd /opt/notify
cp deploy/ec2.env.example deploy/ec2.env
nano deploy/ec2.env
```

Set strong values for `DB_PASSWORD`, `REDIS_PASSWORD`, and `OPENAI_API_KEY`.
Keep `deploy/ec2.env` on the server only; it is ignored by git.

If nginx is already installed on the EC2 host, stop it before starting the
Compose nginx container:

```bash
sudo systemctl disable --now nginx
```

The nginx container expects Let's Encrypt certificates at:

```text
/etc/letsencrypt/live/app.notify-ai.dev/fullchain.pem
/etc/letsencrypt/live/app.notify-ai.dev/privkey.pem
```

## Start or redeploy

```bash
docker compose --env-file deploy/ec2.env up -d --build
```

## Check status

```bash
docker compose ps
docker compose logs -f vocab-agent
docker compose logs -f nginx
curl -I https://app.notify-ai.dev/actuator/health
```

## Restart after EC2 reboot

Docker containers use `restart: unless-stopped`, so they should come back after
Docker starts. If needed, run:

```bash
cd /opt/notify
docker compose --env-file deploy/ec2.env up -d
```
