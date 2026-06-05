<template>
  <main class="dashboard-view">
    <section class="dashboard-card">
      <p class="dashboard-eyebrow">Console</p>
      <h1>仪表盘</h1>
      <p class="dashboard-copy">IAM 管理后台骨架已就绪。</p>
    </section>
  </main>
</template>

<style scoped>
.dashboard-view {
    min-height: 100vh;
    padding: 32px;
}

.dashboard-card {
    max-width: 720px;
    padding: 32px;
    border-radius: 24px;
    background: var(--iam-surface);
    border: 1px solid var(--iam-border);
}

.dashboard-eyebrow {
    margin: 0 0 12px;
    font-size: 12px;
    letter-spacing: 0.2em;
    text-transform: uppercase;
    color: var(--iam-accent);
}

h1 {
    margin: 0;
}

.dashboard-copy {
    margin: 12px 0 0;
    color: var(--iam-muted);
}
</style>
