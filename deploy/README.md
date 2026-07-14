<p align="center">
  <img src="../assets/notify-ai-logo.svg" alt="Notify.ai" width="96" />
</p>

<h1 align="center">Notify.ai Deployment</h1>

This deployment runs the application, nginx, PostgreSQL, Redis, and Ollama as
one Compose stack.

## First-time setup

```bash
cd /opt/vocab-agent
cp deploy/ec2.env.example deploy/ec2.env
nano deploy/ec2.env
```

Set strong values for `DB_PASSWORD`, `REDIS_PASSWORD`, and `OPENAI_API_KEY`.
Keep `deploy/ec2.env` on the server only; it is ignored by git.
The default local models are `qwen3:1.7b` for Ollama chat calls and
`nomic-embed-text` for embeddings.

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

On first deploy or after changing local model names, pull the Ollama models:

```bash
docker compose --env-file deploy/ec2.env up -d ollama
docker compose --env-file deploy/ec2.env exec -T ollama ollama pull qwen3:1.7b
docker compose --env-file deploy/ec2.env exec -T ollama ollama pull nomic-embed-text
```

## Apply database migrations

Run the bundled SQL migration after schema changes or when an existing EC2
database reports missing columns/wrong column types:

```bash
docker compose --env-file deploy/ec2.env exec -T postgres psql -U notification_user -d notify_db -f - < deploy/migrations/001_notify_postgresql.sql
```

## Check status

```bash
docker compose ps
docker compose logs -f vocab-agent
docker compose logs -f nginx
docker compose logs -f ollama
curl -I https://app.notify-ai.dev/actuator/health
```

## Restart after EC2 reboot

Docker containers use `restart: unless-stopped`, so they should come back after
Docker starts. If needed, run:

```bash
cd /opt/vocab-agent
docker compose --env-file deploy/ec2.env up -d
```
