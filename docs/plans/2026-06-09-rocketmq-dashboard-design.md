# RocketMQ Dashboard Design

**Goal:** Add a local RocketMQ Dashboard service to the Docker Compose deployment so developers can inspect brokers, topics, and consumers.

**Scope:**
- Add one `rocketmq-dashboard` service to `deploy/docker-compose.yml`.
- Reuse the existing `rocketmq-nameserver` and `rocketmq-broker`.
- Expose the dashboard UI on a local host port.

**Approach:**
- Use the official `apacherocketmq/rocketmq-dashboard:latest` image.
- Configure the dashboard with `JAVA_OPTS` so it points to `rocketmq-nameserver:9876`.
- Keep the setup stateless: no custom config files, volumes, authentication, or extra persistence.

**Why this approach:**
- Smallest possible change to the current local deployment.
- No changes to broker settings or application service environment variables.
- Easy to start independently with Docker Compose when only MQ inspection is needed.

**Non-goals:**
- Production hardening
- Dashboard authentication
- Custom dashboard theming or persistence
